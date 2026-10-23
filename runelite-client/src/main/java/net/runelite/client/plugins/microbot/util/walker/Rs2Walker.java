package net.runelite.client.plugins.microbot.util.walker;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.headless.JavaBridge;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.shortestpath.MobilePathfinder;
import net.runelite.client.plugins.microbot.shortestpath.MobileTransportPolicy;
import net.runelite.client.plugins.microbot.shortestpath.PathResult;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mobile walking: static same-plane walking edges plus live scene collision and door recovery.
 * Walking success means observed arrival, never just a queued minimap/engine action.
 * Explicit Lumbridge castle stair transitions support its upstairs bank. Other transports
 * (teleports, ships and ladders elsewhere) require a separate implementation.
 */
public final class Rs2Walker
{
	private static final Logger LOG = LoggerFactory.getLogger(Rs2Walker.class);
	private static final String STAIRS_UNAVAILABLE = "staircase action unavailable";
	private static final AtomicLong GENERATION = new AtomicLong();
	private static volatile WorldPoint target;
	private static volatile String lastStatus = "Idle";
	private static final long MAX_WALK_MILLIS = 120_000;
	/** No observed movement for this long triggers a replan and retry, never a stop. */
	private static final long DEFAULT_STALL_MILLIS = 15_000;
	/** Route steps from which a waypoint is walked through the minimap rather than a scene tile. */
	static final int MINIMAP_MIN_STEPS = 4;
	/** While moving, the next click waits until the player is this close to the last target. */
	static final int RECLICK_DISTANCE = 4;
	private static volatile long stallMillis = DEFAULT_STALL_MILLIS;
	/** Plan with {@link MobilePathfinder} first (Microbot Mobile setting; on by default). */
	private static volatile boolean usePathfinder = true;
	/** As upstream: when true, routes use no teleports. Read by the pathfinder. */
	public static boolean disableTeleports = false;
	private static final long STALL_RETRY_BACKOFF_MILLIS = 1_200;
	private static final long STALL_RETRY_MAX_BACKOFF_MILLIS = 6_000;

	/** Test hook: shortens the no-movement window; {@code <= 0} restores the 15 s default. */
	@VisibleForTesting
	public static void setStallMillis(long millis)
	{
		stallMillis = millis > 0 ? millis : DEFAULT_STALL_MILLIS;
	}

	private Rs2Walker()
	{
	}

	/** Turns {@link MobilePathfinder} planning on or off for later walks. */
	public static void setUsePathfinder(boolean enabled)
	{
		usePathfinder = enabled;
	}

	public static boolean isUsingPathfinder()
	{
		return usePathfinder;
	}

	public static String getLastStatus()
	{
		return lastStatus;
	}

	public static WorldPoint getTarget()
	{
		return target;
	}

	/** Null cancels an outstanding walk without issuing any game input. */
	public static void setTarget(WorldPoint point)
	{
		target = point;
		GENERATION.incrementAndGet();
	}

	/** Upstream overload; the reason is logged only. {@code null} cancels any walk in progress. */
	public static void setTarget(WorldPoint point, String reason)
	{
		LOG.debug("Walker target {} ({})", point, reason);
		setTarget(point);
	}

	/**
	 * Non-blocking walk, as upstream: at most one walk dispatch per call, returning
	 * {@link WalkerState#ARRIVED} within {@code distance}, {@link WalkerState#MOVING} while making
	 * progress, {@link WalkerState#UNREACHABLE} when no same-floor route exists, and
	 * {@link WalkerState#EXIT} when cancelled, called on the client thread or while the human owns
	 * input. Like upstream it does not cross transports; use {@link #walkTo} for those.
	 * Mobile: a minimap tap for longer steps, otherwise a scene-resolved walk.
	 */
	public static WalkerState walkStep(WorldPoint destination, int distance)
	{
		Client client = Microbot.getClient();
		if (destination == null || client == null || client.isClientThread() || !MobileActions.active()
			|| MicrobotRuntime.humanOwnsInput())
		{
			return WalkerState.EXIT;
		}
		MobileRoutePlanner.Scene scene = snapshot();
		if (scene == null || scene.player == null)
		{
			return WalkerState.MOVING;
		}
		int radius = Math.max(0, distance);
		if (arrived(scene.player, destination, radius))
		{
			return WalkerState.ARRIVED;
		}
		if (scene.player.getPlane() != destination.getPlane())
		{
			return WalkerState.UNREACHABLE;
		}
		List<WorldPoint> route = plan(scene, destination, radius);
		if (route.isEmpty())
		{
			return WalkerState.UNREACHABLE;
		}
		target = destination;
		// The furthest route tile within reach through the minimap; otherwise the furthest scene
		// tile, then nearer ones if it is offscreen.
		int furthest = Math.min(route.size() - 1, 10);
		if (furthest >= MINIMAP_MIN_STEPS && MobileActions.walkMinimap(route.get(furthest)))
		{
			lastStatus = "Walking to " + destination;
			return WalkerState.MOVING;
		}
		for (int i = furthest; i > 0; i--)
		{
			WorldPoint step = route.get(i);
			if (scene.contains(step) && MobileActions.walk(step))
			{
				lastStatus = "Walking to " + destination;
				break;
			}
		}
		return WalkerState.MOVING;
	}

	/**
	 * As upstream: the player stands inside the box spanned by the first two points (floor
	 * ignored, as upstream). Mobile: false, rather than an exception, without a player location.
	 */
	public static boolean isInArea(WorldPoint... worldPoints)
	{
		if (worldPoints == null || worldPoints.length < 2 || worldPoints[0] == null || worldPoints[1] == null)
		{
			throw new IllegalArgumentException("isInArea requires two WorldPoints.");
		}
		WorldPoint player = MobileActions.read(() -> Microbot.getClient().getLocalPlayer() == null ? null
			: Microbot.getClient().getLocalPlayer().getWorldLocation(), null);
		if (player == null) { return false; }
		WorldPoint a = worldPoints[0];
		WorldPoint b = worldPoints[1];
		return player.getX() >= Math.min(a.getX(), b.getX()) && player.getX() <= Math.max(a.getX(), b.getX())
			&& player.getY() >= Math.min(a.getY(), b.getY()) && player.getY() <= Math.max(a.getY(), b.getY());
	}

	/** As upstream: within {@code range} tiles of {@code centerOfArea} on both axes. */
	public static boolean isInArea(WorldPoint centerOfArea, int range)
	{
		return isInArea(new WorldPoint(centerOfArea.getX() + range, centerOfArea.getY() - range, centerOfArea.getPlane()),
			new WorldPoint(centerOfArea.getX() - range, centerOfArea.getY() + range, centerOfArea.getPlane()));
	}

	public static boolean walkTo(WorldPoint destination)
	{
		return walkTo(destination, 1);
	}

	/**
 * Bounded off-client-thread operation. It follows walking edges across scene loads, rechecking
 * real movement, collision and cancellation before every waypoint. Never waits on the game thread.
 */
	public static boolean walkTo(WorldPoint destination, int distance)
	{
		Client client = Microbot.getClient();
		if (client == null || client.isClientThread())
		{
			lastStatus = "Walking must be called off the client thread";
			return false;
		}
		if (destination == null || !MobileActions.active())
		{
			lastStatus = "Walking cancelled";
			return false;
		}
		int radius = Math.max(0, Math.min(10, distance));
		long generation = GENERATION.incrementAndGet();
		target = destination;
		long deadline = System.currentTimeMillis() + MAX_WALK_MILLIS;
		try
		{
			MobileRoutePlanner.Scene scene = awaitSnapshot(generation, deadline);
			if (scene == null || scene.player == null)
			{
				return fail("No logged-in walking collision snapshot");
			}
			if (arrived(scene.player, destination, radius))
			{
				lastStatus = "Arrived";
				return true;
			}
			Set<ObjectTransports.Step> unusable = new HashSet<>();
			boolean pathfinder = usePathfinder;
			WalkLeg leg = nextLeg(scene, destination, radius, unusable, pathfinder);
			if (leg == null)
			{
				return fail("No walking route; a transport or an unmapped region may be required");
			}
			List<WorldPoint> global = leg.route;
			long lastProgress = System.currentTimeMillis();
			WorldPoint previous = scene.player;
			int progress = 0;
			boolean recovered = false;
			int acceptedWalks = 0;
			int stalls = 0;
			WorldPoint clicked = null;
			while (System.currentTimeMillis() < deadline && GENERATION.get() == generation
				&& MobileActions.active() && !Thread.currentThread().isInterrupted())
			{
				scene = awaitSnapshot(generation, deadline);
				if (scene == null)
				{
					return fail("Walking stopped: no collision snapshot or game state changed");
				}
				if (scene.player.getPlane() != leg.destination.getPlane())
				{
					return fail("Walking stopped: unexpected plane change");
				}
				if (arrived(scene.player, destination, radius))
				{
					lastStatus = "Arrived";
					return true;
				}
				if (arrived(scene.player, leg.destination, leg.radius) && leg.stairs != null)
				{
					if (!(leg.stairs.spell ? useTeleportSpell(leg.stairs, generation, deadline)
						: leg.stairs.item ? useTeleportItem(leg.stairs, generation, deadline)
						: leg.stairs.travel ? useTravel(leg.stairs, generation, deadline)
						: useStairs(leg.stairs, generation, deadline)))
					{
						if (!STAIRS_UNAVAILABLE.equals(lastStatus))
						{
							return fail("Transport failed (" + leg.stairs + "): " + lastStatus);
						}
						// The live object does not offer this action here: try the next transport.
						unusable.add(leg.stairs);
						// A pathfinder transport missing from the live scene: finish on the same-plane planner.
						pathfinder &= !leg.fromPathfinder;
						LOG.info("Mobile walk: transport unavailable, trying another: {}", leg.stairs);
						scene = awaitSnapshot(generation, deadline);
						leg = scene == null ? null : nextLeg(scene, destination, radius, unusable, pathfinder);
						if (leg == null)
						{
							return fail("No usable transport toward " + destination);
						}
						global = leg.route;
						progress = 0;
						previous = scene.player;
						lastProgress = System.currentTimeMillis();
						continue;
					}
					ObjectTransports.Step used = leg.stairs;
					scene = awaitSnapshot(generation, deadline);
					if (scene == null || !used.observedArrival(scene.player))
					{
						return fail("No collision snapshot after transport " + used);
					}
					leg = nextLeg(scene, destination, radius, unusable, pathfinder);
					if (leg == null)
					{
						return fail("No walking route after transport " + used);
					}
					global = leg.route;
					progress = 0;
					previous = scene.player;
					lastProgress = System.currentTimeMillis();
					continue;
				}
				boolean moved = !scene.player.equals(previous);
				if (moved)
				{
					previous = scene.player;
					lastProgress = System.currentTimeMillis();
					// A recovery only shortens steps until the player is moving again.
					recovered = false;
				}
				if (MicrobotRuntime.humanOwnsInput())
				{
					// The user's own taps own input; the walk yields and its stall timer waits too.
					lastStatus = "Waiting: manual input";
					lastProgress = System.currentTimeMillis();
				}
				if (System.currentTimeMillis() - lastProgress >= stallMillis)
				{
					// Retry rather than stop: replan from the observed position, use short steps
					// and back off, bounded by the overall walk deadline. An interrupt (Stop) still
					// ends the wait at once.
					stalls++;
					LOG.warn("Mobile walk stalled, retry {}: player={} target={} accepted={} bridge={}",
						stalls, scene.player, destination, acceptedWalks, JavaBridge.getScriptWalkStatus());
					List<WorldPoint> replanned = plan(scene, leg.destination, leg.radius);
					if (!replanned.isEmpty()) { global = replanned; progress = 0; }
					recovered = true;
					lastStatus = "Walk stalled; retrying (" + stalls + ")";
					long backoff = Math.min(STALL_RETRY_MAX_BACKOFF_MILLIS, STALL_RETRY_BACKOFF_MILLIS * stalls);
					long remaining = deadline - System.currentTimeMillis();
					if (remaining > 0) { Thread.sleep(Math.min(backoff, remaining)); }
					lastProgress = System.currentTimeMillis();
					continue;
				}
				if (!recovered && System.currentTimeMillis() - lastProgress >= stallMillis / 2)
				{
					List<WorldPoint> replanned = plan(scene, leg.destination, leg.radius);
					if (!replanned.isEmpty()) { global = replanned; progress = 0; }
					recovered = true;
					LOG.info("Mobile walk recovery: player={} target={} bridge={}", scene.player, destination, JavaBridge.getScriptWalkStatus());
				}
				progress = nearestProgress(global, scene.player, progress);
				WorldPoint waypoint = null;
				for (int i = Math.min(global.size() - 1, progress + 18); i > progress; i--)
				{
					WorldPoint candidate = global.get(i);
					if (!scene.contains(candidate) || scene.player.distanceTo(candidate) > 12)
					{
						continue;
					}
					List<WorldPoint> local = MobileRoutePlanner.route(scene.player, candidate, 0, scene::canStep);
					if (!local.isEmpty())
					{
						waypoint = local.get(Math.min(local.size() - 1, recovered ? 3 : 10));
						break;
					}
				}
				if (waypoint == null)
				{
					WorldPoint next = global.get(Math.min(global.size() - 1, progress + 1));
					if (tryOpenDoor(next))
					{
						lastStatus = "Opening route door";
					}
					else
					{
						lastStatus = "Waiting for a traversable route (door, obstacle or scene load)";
					}
				}
				else if (moved && clicked != null && scene.player.distanceTo(clicked) > RECLICK_DISTANCE)
				{
					// Still heading for the last accepted target: do not re-click every tick.
					lastStatus = "Walking to " + destination;
				}
				else if (!waypoint.equals(scene.player))
				{
					lastStatus = "Walking to " + destination;
					List<WorldPoint> steps = MobileRoutePlanner.route(scene.player, waypoint, 0, scene::canStep);
					// Longer steps go through the minimap, which is visible whatever the camera does.
					if (steps.size() - 1 >= MINIMAP_MIN_STEPS && MobileActions.walkMinimap(waypoint))
					{
						acceptedWalks++;
						clicked = waypoint;
					}
					else
					{
						// A scene tile must be on screen; try nearer reachable tiles instead of
						// repeating an invisible destination.
						for (int i = steps.size() - 1; i > 0; i--)
						{
							if (MobileActions.walk(steps.get(i))) { acceptedWalks++; clicked = steps.get(i); break; }
						}
					}
				}
				long remaining = deadline - System.currentTimeMillis();
				if (remaining > 0) { Thread.sleep(Math.min(600, remaining)); }
			}
			return fail(GENERATION.get() != generation || !MobileActions.active()
				? "Walking cancelled" : "Walking timed out");
		}
		catch (InterruptedException exception)
		{
			Thread.currentThread().interrupt();
			return fail("Walking interrupted");
		}
		finally
		{
			if (GENERATION.get() == generation)
			{
				target = null;
			}
		}
	}

	public static boolean walkFastCanvas(WorldPoint destination)
	{
		return walkTo(destination, 0);
	}

	/** Reachability query only; no movement or door clicks. */
	public static boolean canReach(WorldPoint destination)
	{
		MobileRoutePlanner.Scene scene = snapshot();
		if (scene == null || destination == null)
		{
			return false;
		}
		if (scene.player.getPlane() != destination.getPlane())
		{
			Client installed = Microbot.getClient();
			return installed != null && !installed.isClientThread() && selectLeg(scene, destination, 1) != null;
		}
		if (scene.contains(destination))
		{
			return !MobileRoutePlanner.route(scene.player, destination, 0, scene::canStep).isEmpty();
		}
		Client client = Microbot.getClient();
		if (scene.instance || client == null || client.isClientThread())
		{
			return false;
		}
		return !MobileRoutePlanner.route(scene.player, destination, 0, MobileRoutePlanner.worldEdges()).isEmpty();
	}

	/** Structural capability check for bank selection; actual collision routing is still validated when walking. */
	public static boolean canReachVertical(WorldPoint origin, WorldPoint destination)
	{
		return ObjectTransports.verticalChainExists(origin, destination);
	}

	static final class WalkLeg
	{
		final WorldPoint destination;
		final int radius;
		final ObjectTransports.Step stairs;
		final List<WorldPoint> route;
		/** Planned by {@link MobilePathfinder} rather than the same-plane planner. */
		final boolean fromPathfinder;

		WalkLeg(WorldPoint destination, int radius, ObjectTransports.Step stairs, List<WorldPoint> route)
		{
			this(destination, radius, stairs, route, false);
		}

		WalkLeg(WorldPoint destination, int radius, ObjectTransports.Step stairs, List<WorldPoint> route, boolean fromPathfinder)
		{
			this.destination = destination;
			this.radius = radius;
			this.stairs = stairs;
			this.route = route;
			this.fromPathfinder = fromPathfinder;
		}
	}

	/** Rides on an NPC or vehicle: interact, answer the destination dialogue, wait for the landing. */
	static final Set<TransportType> TRAVEL_TRANSPORTS = EnumSet.of(TransportType.BOAT, TransportType.SHIP, TransportType.NPC,
		TransportType.CHARTER_SHIP);

	/** The charter-ship destination map (upstream: widget 885, child 4). */
	private static final int CHARTER_GROUP = 885;
	private static final int CHARTER_CHILD = 4;

	/** Transport types the walker can execute; the pathfinder is told to admit only these. */
	static final Set<TransportType> EXECUTABLE_TRANSPORTS = EnumSet.of(TransportType.TRANSPORT, TransportType.AGILITY_SHORTCUT,
		TransportType.BOAT, TransportType.SHIP, TransportType.NPC, TransportType.CHARTER_SHIP, TransportType.TELEPORTATION_ITEM,
		TransportType.TELEPORTATION_SPELL);

	/** A home teleport channels for about ten seconds before the landing. */
	private static final long SPELL_TELEPORT_MILLIS = 30_000;

	/** Teleports land in an area rather than on one tile. */
	private static final int TELEPORT_ARRIVAL_TILES = 8;
	private static final long TELEPORT_MILLIS = 20_000;
	/** Inventory options that open a teleport item's destinations or use it, in preference order. */
	private static final List<String> TELEPORT_ITEM_OPTIONS = List.of("Rub", "Teleport", "Break", "Read", "Commune");
	/** Worn-equipment widgets by equipment slot index (head 0 ... ammo 13; 6, 8 and 11 are unused). */
	private static final java.util.Map<Integer, Integer> WORN_SLOT_WIDGETS = java.util.Map.ofEntries(
		java.util.Map.entry(0, InterfaceID.Wornitems.SLOT0), java.util.Map.entry(1, InterfaceID.Wornitems.SLOT1),
		java.util.Map.entry(2, InterfaceID.Wornitems.SLOT2), java.util.Map.entry(3, InterfaceID.Wornitems.SLOT3),
		java.util.Map.entry(4, InterfaceID.Wornitems.SLOT4), java.util.Map.entry(5, InterfaceID.Wornitems.SLOT5),
		java.util.Map.entry(7, InterfaceID.Wornitems.SLOT7), java.util.Map.entry(9, InterfaceID.Wornitems.SLOT9),
		java.util.Map.entry(10, InterfaceID.Wornitems.SLOT10), java.util.Map.entry(12, InterfaceID.Wornitems.SLOT12),
		java.util.Map.entry(13, InterfaceID.Wornitems.SLOT13));

	/** A ride lands roughly where the catalogue says (gangplanks and docks shift by a few tiles). */
	private static final int TRAVEL_ARRIVAL_TILES = 5;
	/** Longest a ride (interaction, dialogue, the trip and its scene load) may take. */
	private static final long TRAVEL_MILLIS = 45_000;
	/** NPC options upstream treats as explicit; any other configured option also tries "Travel". */
	private static final Set<String> EXPLICIT_TRAVEL_OPTIONS = Set.of("travel", "talk-to", "quick-travel", "take-boat");

	static
	{
		MobilePathfinder.setTransportPolicy(MobileTransportPolicy.admitting(EXECUTABLE_TRANSPORTS));
	}

	/**
	 * The next leg: {@link MobilePathfinder} when enabled (whole-world map and transports), else
	 * or when it finds no full route, the same-plane planner and its staircases.
	 */
	private static WalkLeg nextLeg(MobileRoutePlanner.Scene scene, WorldPoint destination, int radius,
		Set<ObjectTransports.Step> excluded, boolean pathfinder)
	{
		if (pathfinder && !scene.instance)
		{
			WalkLeg leg = pathfinderLeg(scene.player, destination, radius);
			if (leg != null)
			{
				return leg;
			}
		}
		return selectLeg(scene, destination, radius, excluded);
	}

	/**
	 * Walks the pathfinder's tiles up to its first transport, which becomes the leg's transport
	 * (arrival within one tile of its destination); null unless the route is complete and every
	 * transport on it is one the walker executes.
	 */
	static WalkLeg pathfinderLeg(WorldPoint start, WorldPoint destination, int radius)
	{
		PathResult result;
		try
		{
			result = MobilePathfinder.find(start, destination);
		}
		catch (RuntimeException e)
		{
			LOG.warn("Mobile walk: pathfinder failed for {} -> {}", start, destination, e);
			return null;
		}
		List<WorldPoint> tiles = result.getTiles();
		if (result.getStatus() != PathResult.Status.FOUND || tiles.isEmpty())
		{
			LOG.info("Mobile walk: pathfinder {} for {} -> {}; using the same-plane planner", result.getStatus(), start, destination);
			return null;
		}
		for (int i = 0; i < tiles.size() - 1; i++)
		{
			PathResult.TransportStep transport = result.transportAt(i);
			if (transport == null)
			{
				continue;
			}
			if (!EXECUTABLE_TRANSPORTS.contains(transport.getType())
				|| (transport.getAction() == null && transport.getType() != TransportType.TELEPORTATION_ITEM
					&& transport.getType() != TransportType.TELEPORTATION_SPELL))
			{
				LOG.info("Mobile walk: route needs {} ({}); using the same-plane planner", transport.getType(), transport);
				return null;
			}
			if (transport.getType() == TransportType.TELEPORTATION_SPELL)
			{
				if (disableTeleports || transport.getDisplayInfo() == null || transport.getDisplayInfo().isBlank())
				{
					LOG.info("Mobile walk: route needs teleport spell {}; using the same-plane planner", transport);
					return null;
				}
				WorldPoint from = tiles.get(i);
				ObjectTransports.Step step = new ObjectTransports.Step(from, transport.getDestination(), -1, "",
					transport.getName(), TELEPORT_ARRIVAL_TILES, false, transport.getDisplayInfo().trim(), false, Set.of(), true);
				return new WalkLeg(from, 1, step, new ArrayList<>(tiles.subList(0, i + 1)), true);
			}
			if (transport.getType() == TransportType.TELEPORTATION_ITEM)
			{
				if (disableTeleports || transport.getDisplayInfo() == null || !transport.getDisplayInfo().contains(":"))
				{
					LOG.info("Mobile walk: route needs teleport item {}; using the same-plane planner", transport);
					return null;
				}
				// A teleport item is used where the route reaches it (usually where the player stands).
				WorldPoint from = tiles.get(i);
				ObjectTransports.Step step = new ObjectTransports.Step(from, transport.getDestination(), -1, "",
					transport.getName(), TELEPORT_ARRIVAL_TILES, false, transport.getDisplayInfo(), true, transport.getItemIds());
				return new WalkLeg(from, 1, step, new ArrayList<>(tiles.subList(0, i + 1)), true);
			}
			boolean travel = TRAVEL_TRANSPORTS.contains(transport.getType());
			ObjectTransports.Step step = new ObjectTransports.Step(transport.getOrigin(), transport.getDestination(),
				transport.getObjectId(), transport.getAction(), transport.getName(), travel ? TRAVEL_ARRIVAL_TILES : 1,
				travel, transport.getDisplayInfo());
			return new WalkLeg(transport.getOrigin(), 1, step, new ArrayList<>(tiles.subList(0, i + 1)), true);
		}
		return new WalkLeg(destination, radius, null, new ArrayList<>(tiles), true);
	}

	private static WalkLeg selectLeg(MobileRoutePlanner.Scene scene, WorldPoint destination, int radius)
	{
		return selectLeg(scene, destination, radius, Collections.emptySet());
	}

	private static WalkLeg selectLeg(MobileRoutePlanner.Scene scene, WorldPoint destination, int radius,
		Set<ObjectTransports.Step> excluded)
	{
		if (scene.player.getPlane() == destination.getPlane())
		{
			List<WorldPoint> route = plan(scene, destination, radius);
			return route.isEmpty() ? null : new WalkLeg(destination, radius, null, route);
		}
		if (scene.instance)
		{
			return null;
		}
		for (ObjectTransports.Step stairs : ObjectTransports.candidates(ObjectTransports.all(), scene.player, destination, excluded))
		{
			List<WorldPoint> route = plan(scene, stairs.origin, 1);
			if (!route.isEmpty())
			{
				return new WalkLeg(stairs.origin, 1, stairs, route);
			}
		}
		return null;
	}

	/** Dispatch exact live staircase actions, then require observed plane and landing-location changes. */
	private static boolean useStairs(ObjectTransports.Step stairs, long generation, long walkDeadline) throws InterruptedException
	{
		long deadline = Math.min(walkDeadline, System.currentTimeMillis() + 20_000);
		long lastAttempt = 0;
		boolean dispatched = false;
		lastStatus = "Using " + stairs.target + ": " + stairs.action;
		int missingLookups = 0;
		while (System.currentTimeMillis() < deadline && GENERATION.get() == generation
			&& MobileActions.active() && !Thread.currentThread().isInterrupted())
		{
			MobileRoutePlanner.Scene scene = awaitSnapshot(generation, deadline);
			if (scene == null)
			{
				lastStatus = "no collision snapshot, loading timed out or game state changed";
				return false;
			}
			if (dispatched && stairs.observedArrival(scene.player))
			{
				return true;
			}
			if (scene.player.getPlane() != stairs.origin.getPlane())
			{
				lastStatus = "unexpected staircase plane or landing";
				return false;
			}
			if (scene.player.getPlane() == stairs.origin.getPlane()
				&& scene.player.distanceTo(stairs.origin) <= 2 && System.currentTimeMillis() - lastAttempt >= 1200)
			{
				lastAttempt = System.currentTimeMillis();
				Rs2TileObjectModel staircase = MobileActions.read(() ->
				{
					Player player = Microbot.getClient().getLocalPlayer();
					if (player == null || player.getWorldLocation().getPlane() != stairs.origin.getPlane())
					{
						return null;
					}
					return MicrobotReadServices.require().getRs2TileObjectCache().query()
						.where(object -> object.getWorldLocation().getPlane() == stairs.origin.getPlane()
							&& object.getWorldLocation().distanceTo(stairs.origin) <= 3
							&& object.getObjectComposition() != null
							&& stairs.matches(object.getId(), object.getObjectComposition().getName(),
								object.getObjectComposition().getActions())).nearest();
				}, null);
				if (staircase != null)
				{
					missingLookups = 0;
					dispatched |= MobileActions.objectAt(staircase.getId(), staircase.getWorldLocation(), stairs.action);
				}
				else if (!dispatched && ++missingLookups >= 3)
				{
					lastStatus = STAIRS_UNAVAILABLE;
					return false;
				}
			}
			long remaining = deadline - System.currentTimeMillis();
			if (remaining > 0) { Thread.sleep(Math.min(300, remaining)); }
		}
		lastStatus = !MobileActions.active() || GENERATION.get() != generation
			? "staircase cancelled" : dispatched ? "plane change was not observed" : STAIRS_UNAVAILABLE;
		return false;
	}

	/**
	 * Rides an NPC or vehicle transport: interacts with the catalogued NPC (or, failing that, an
	 * object at the origin) using its option, answers the destination dialogue, and requires an
	 * observed landing near the catalogued destination.
	 */
	private static boolean useTravel(ObjectTransports.Step step, long generation, long walkDeadline) throws InterruptedException
	{
		long deadline = Math.min(walkDeadline, System.currentTimeMillis() + TRAVEL_MILLIS);
		long lastAttempt = 0;
		boolean dispatched = false;
		int missingLookups = 0;
		lastStatus = "Travelling: " + step;
		while (System.currentTimeMillis() < deadline && GENERATION.get() == generation
			&& MobileActions.active() && !Thread.currentThread().isInterrupted())
		{
			MobileRoutePlanner.Scene scene = awaitSnapshot(generation, deadline);
			if (scene == null)
			{
				lastStatus = "no collision snapshot, loading timed out or game state changed";
				return false;
			}
			if (dispatched && step.observedArrival(scene.player))
			{
				return true;
			}
			if (charterMapVisible())
			{
				if (!chooseCharterDestination(step.destinationText))
				{
					lastStatus = "charter map does not offer " + step.destinationText;
					return false;
				}
				Thread.sleep(600);
				continue;
			}
			if (Rs2Dialogue.hasSelectAnOption())
			{
				if (!Rs2Dialogue.clickOption(travelOptions(step)))
				{
					lastStatus = "unexpected travel dialogue options for " + step;
					return false;
				}
			}
			else if (Rs2Dialogue.hasContinue())
			{
				Rs2Dialogue.clickContinue();
			}
			else if (scene.player.getPlane() == step.origin.getPlane() && scene.player.distanceTo(step.origin) <= 3
				&& System.currentTimeMillis() - lastAttempt >= 3_000)
			{
				lastAttempt = System.currentTimeMillis();
				if (dispatchTravel(step))
				{
					dispatched = true;
					missingLookups = 0;
				}
				else if (!dispatched && ++missingLookups >= 3)
				{
					lastStatus = STAIRS_UNAVAILABLE;
					return false;
				}
			}
			long remaining = deadline - System.currentTimeMillis();
			if (remaining > 0) { Thread.sleep(Math.min(300, remaining)); }
		}
		lastStatus = !MobileActions.active() || GENERATION.get() != generation
			? "travel cancelled" : dispatched ? "travel landing was not observed" : STAIRS_UNAVAILABLE;
		return false;
	}

	/**
	 * Uses a teleport item: a worn item's destination option on the equipment tab, else the carried
	 * item's destination option or Rub/Teleport/Break, then the destination in the dialogue; requires
	 * an observed landing near the destination. Never answers "Yes": a confirmation could be a
	 * Wilderness warning.
	 */
	private static boolean useTeleportItem(ObjectTransports.Step step, long generation, long walkDeadline) throws InterruptedException
	{
		String itemName = teleportItemName(step);
		String place = teleportDestination(step);
		long deadline = Math.min(walkDeadline, System.currentTimeMillis() + TELEPORT_MILLIS);
		long lastAttempt = 0;
		boolean dispatched = false;
		lastStatus = "Teleporting: " + step.destinationText;
		while (System.currentTimeMillis() < deadline && GENERATION.get() == generation
			&& MobileActions.active() && !Thread.currentThread().isInterrupted())
		{
			MobileRoutePlanner.Scene scene = awaitSnapshot(generation, deadline);
			if (scene == null)
			{
				lastStatus = "no collision snapshot, loading timed out or game state changed";
				return false;
			}
			if (dispatched && step.observedArrival(scene.player))
			{
				return true;
			}
			if (Rs2Dialogue.hasSelectAnOption())
			{
				if (!Rs2Dialogue.clickOption(place))
				{
					lastStatus = "teleport dialogue does not offer " + place;
					return false;
				}
			}
			else if (Rs2Dialogue.hasContinue())
			{
				Rs2Dialogue.clickContinue();
			}
			else if (!dispatched || System.currentTimeMillis() - lastAttempt >= 6_000)
			{
				lastAttempt = System.currentTimeMillis();
				if (dispatchTeleportItem(teleportItem(step, itemName), place))
				{
					dispatched = true;
				}
				else if (!dispatched)
				{
					lastStatus = "no usable " + itemName + " for " + place;
					return false;
				}
			}
			long remaining = deadline - System.currentTimeMillis();
			if (remaining > 0) { Thread.sleep(Math.min(300, remaining)); }
		}
		lastStatus = !MobileActions.active() || GENERATION.get() != generation
			? "teleport cancelled" : dispatched ? "teleport landing was not observed" : "teleport was not used";
		return false;
	}

	/**
	 * Casts a teleport spell named by the step (a {@link MagicAction} when one has that name, else
	 * the spellbook entry by name) and requires an observed landing; recasts once if, 15 seconds
	 * on, the player is neither there nor animating.
	 */
	private static boolean useTeleportSpell(ObjectTransports.Step step, long generation, long walkDeadline) throws InterruptedException
	{
		long deadline = Math.min(walkDeadline, System.currentTimeMillis() + SPELL_TELEPORT_MILLIS);
		long castAt = 0;
		int casts = 0;
		lastStatus = "Casting " + step.destinationText;
		while (System.currentTimeMillis() < deadline && GENERATION.get() == generation
			&& MobileActions.active() && !Thread.currentThread().isInterrupted())
		{
			MobileRoutePlanner.Scene scene = awaitSnapshot(generation, deadline);
			if (scene == null)
			{
				lastStatus = "no collision snapshot, loading timed out or game state changed";
				return false;
			}
			if (casts > 0 && step.observedArrival(scene.player))
			{
				return true;
			}
			boolean retry = casts == 1 && System.currentTimeMillis() - castAt >= 15_000 && !Rs2Player.isAnimating();
			if (casts == 0 || retry)
			{
				if (!castTeleport(step.destinationText))
				{
					if (casts == 0)
					{
						lastStatus = "cannot cast " + step.destinationText;
						return false;
					}
				}
				castAt = System.currentTimeMillis();
				casts++;
			}
			long remaining = deadline - System.currentTimeMillis();
			if (remaining > 0) { Thread.sleep(Math.min(300, remaining)); }
		}
		lastStatus = !MobileActions.active() || GENERATION.get() != generation
			? "teleport cancelled" : "teleport landing was not observed";
		return false;
	}

	private static boolean castTeleport(String spellName)
	{
		MagicAction action = Arrays.stream(MagicAction.values())
			.filter(candidate -> candidate.getName().equalsIgnoreCase(spellName)).findFirst().orElse(null);
		return action != null ? Rs2Magic.cast(action) : Rs2Magic.quickCast(spellName);
	}

	/** "Games necklace: Burthorpe" gives "Games necklace". */
	static String teleportItemName(ObjectTransports.Step step)
	{
		int colon = step.destinationText.indexOf(':');
		return (colon < 0 ? step.destinationText : step.destinationText.substring(0, colon)).trim();
	}

	/** "Games necklace: Burthorpe" gives "Burthorpe". */
	static String teleportDestination(ObjectTransports.Step step)
	{
		int colon = step.destinationText.indexOf(':');
		return colon < 0 ? "" : step.destinationText.substring(colon + 1).trim();
	}

	/** The step's item: by the catalogue's ids when it lists them, else by the label's item name. */
	static java.util.function.Predicate<Rs2ItemModel> teleportItem(ObjectTransports.Step step, String itemName)
	{
		if (!step.itemIds.isEmpty())
		{
			return item -> step.itemIds.contains(item.getId());
		}
		String prefix = itemName.toLowerCase(java.util.Locale.ROOT);
		return item -> item.getName() != null && item.getName().toLowerCase(java.util.Locale.ROOT).startsWith(prefix);
	}

	/** One use of the item: worn first (its destination option), then carried. */
	private static boolean dispatchTeleportItem(java.util.function.Predicate<Rs2ItemModel> isItem, String place)
	{
		Rs2ItemModel worn = Rs2Equipment.get(isItem);
		Integer widget = worn == null ? null : WORN_SLOT_WIDGETS.get(worn.getSlot());
		if (widget != null)
		{
			String option = MobileActions.read(() ->
			{
				net.runelite.api.widgets.Widget slot = Microbot.getClient().getWidget(widget);
				return slot == null || slot.getActions() == null ? null : Arrays.stream(slot.getActions())
					.filter(action -> action != null && action.toLowerCase(java.util.Locale.ROOT).contains(place.toLowerCase(java.util.Locale.ROOT)))
					.findFirst().orElse(null);
			}, null);
			if (option != null && Rs2Tab.switchTo(InterfaceTab.EQUIPMENT)
				&& Global.sleepUntil(() -> MobileActions.widget(widget, -1, option), 2_000))
			{
				return true;
			}
		}
		Rs2ItemModel carried = Rs2Inventory.items(isItem).findFirst().orElse(null);
		if (carried == null || carried.getInventoryActions() == null)
		{
			return false;
		}
		String direct = Arrays.stream(carried.getInventoryActions())
			.filter(action -> action != null && !place.isEmpty() && action.toLowerCase(java.util.Locale.ROOT).contains(place.toLowerCase(java.util.Locale.ROOT)))
			.findFirst().orElse(null);
		String option = direct != null ? direct : TELEPORT_ITEM_OPTIONS.stream()
			.filter(candidate -> Arrays.stream(carried.getInventoryActions()).anyMatch(candidate::equalsIgnoreCase))
			.findFirst().orElse(null);
		return option != null && Global.sleepUntil(() -> Rs2Inventory.interact(carried, option), 2_000);
	}

	private static boolean charterMapVisible()
	{
		return MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget map = Microbot.getClient().getWidget(CHARTER_GROUP, CHARTER_CHILD);
			return map != null && !map.isHidden();
		}, false);
	}

	/**
	 * As upstream: the charter map entry whose text, name or option names {@code destination}, or its
	 * nearest ancestor with an option, dispatched with its first option.
	 */
	private static boolean chooseCharterDestination(String destination)
	{
		if (destination == null || destination.isBlank())
		{
			return false;
		}
		String needle = Text.removeTags(destination).trim().toLowerCase(java.util.Locale.ROOT);
		Object[] target = MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget root = Microbot.getClient().getWidget(CHARTER_GROUP, CHARTER_CHILD);
			net.runelite.api.widgets.Widget match = root == null ? null : findCharterEntry(root, needle);
			for (net.runelite.api.widgets.Widget current = match; current != null; current = current == root ? null : current.getParent())
			{
				String option = current.getActions() == null ? null : Arrays.stream(current.getActions())
					.filter(action -> action != null && !action.isEmpty()).findFirst().orElse(null);
				if (option != null)
				{
					// A dynamic child is addressed through its parent's id and its index.
					return new Object[]{current.getId(), current.getIndex(), option};
				}
			}
			return null;
		}, null);
		return target != null && Global.sleepUntil(() -> MobileActions.widget((int) target[0], (int) target[1], (String) target[2]), 2_000);
	}

	static net.runelite.api.widgets.Widget findCharterEntry(net.runelite.api.widgets.Widget widget, String needle)
	{
		if (widget == null || widget.isHidden())
		{
			return null;
		}
		if (charterText(widget.getText()).contains(needle) || charterText(widget.getName()).contains(needle)
			|| (widget.getActions() != null && Arrays.stream(widget.getActions()).anyMatch(action -> charterText(action).contains(needle))))
		{
			return widget;
		}
		for (net.runelite.api.widgets.Widget[] children : new net.runelite.api.widgets.Widget[][]{
			widget.getStaticChildren(), widget.getDynamicChildren(), widget.getNestedChildren()})
		{
			if (children == null)
			{
				continue;
			}
			for (net.runelite.api.widgets.Widget child : children)
			{
				net.runelite.api.widgets.Widget found = findCharterEntry(child, needle);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static String charterText(String text)
	{
		return text == null ? "" : Text.removeTags(text).trim().toLowerCase(java.util.Locale.ROOT);
	}

	/** Dialogue choices that mean "go to this step's destination", most specific first. */
	static String[] travelOptions(ObjectTransports.Step step)
	{
		List<String> options = new ArrayList<>();
		if (!step.destinationText.isBlank())
		{
			options.add(step.destinationText);
		}
		if (step.action != null && !EXPLICIT_TRAVEL_OPTIONS.contains(step.action.toLowerCase(java.util.Locale.ROOT)))
		{
			options.add(step.action);
		}
		options.add("Yes");
		return options.toArray(new String[0]);
	}

	/** One interaction: the catalogued NPC with its option (or "Travel"), else an object at the origin. */
	private static boolean dispatchTravel(ObjectTransports.Step step)
	{
		Rs2NpcModel npc = MobileActions.read(() -> MicrobotReadServices.require().getRs2NpcCache().query()
			.where(candidate -> candidate.getWorldLocation() != null
				&& candidate.getWorldLocation().getPlane() == step.origin.getPlane()
				&& candidate.getWorldLocation().distanceTo(step.origin) <= 12
				&& (candidate.getId() == step.objectId || (!step.target.isEmpty() && step.target.equalsIgnoreCase(candidate.getName()))))
			.nearest(), null);
		if (npc != null)
		{
			for (String option : npcOptions(step))
			{
				if (MobileActions.npcAt(npc.getId(), npc.getIndex(), option))
				{
					return true;
				}
			}
			return false;
		}
		Rs2TileObjectModel object = MobileActions.read(() -> MicrobotReadServices.require().getRs2TileObjectCache().query()
			.where(candidate -> candidate.getWorldLocation().getPlane() == step.origin.getPlane()
				&& candidate.getWorldLocation().distanceTo(step.origin) <= 6
				&& candidate.getObjectComposition() != null
				&& step.matches(candidate.getId(), candidate.getObjectComposition().getName(), candidate.getObjectComposition().getActions()))
			.nearest(), null);
		return object != null && MobileActions.objectAt(object.getId(), object.getWorldLocation(), step.action);
	}

	/** As upstream: the configured option, and "Travel" when the configured one is a destination name. */
	static List<String> npcOptions(ObjectTransports.Step step)
	{
		List<String> options = new ArrayList<>();
		if (step.action != null && !step.action.isBlank())
		{
			options.add(step.action);
		}
		if (step.action == null || !EXPLICIT_TRAVEL_OPTIONS.contains(step.action.toLowerCase(java.util.Locale.ROOT)))
		{
			options.add("Travel");
		}
		return options;
	}

	public static List<WorldPoint> getWalkableTilesAroundPlayer(int distance)
	{
		return MobileRoutePlanner.reachable(snapshot(), distance);
	}

	private static List<WorldPoint> plan(MobileRoutePlanner.Scene scene, WorldPoint destination, int radius)
	{
		if (scene.contains(destination))
		{
			List<WorldPoint> local = MobileRoutePlanner.route(scene.player, destination, radius, scene::canStep);
			if (!local.isEmpty())
			{
				return local;
			}
		}
		return scene.instance ? Collections.emptyList()
			: MobileRoutePlanner.route(scene.player, destination, radius, MobileRoutePlanner.worldEdges());
	}

	private static int nearestProgress(List<WorldPoint> route, WorldPoint player, int current)
	{
		int nearest = current;
		int distance = Integer.MAX_VALUE;
		for (int i = Math.max(0, current - 5); i < Math.min(route.size(), current + 40); i++)
		{
			int next = player.distanceTo(route.get(i));
			if (next <= distance)
			{
				distance = next;
				nearest = i;
			}
		}
		return nearest;
	}

	private static boolean arrived(WorldPoint player, WorldPoint goal, int radius)
	{
		return player != null && player.getPlane() == goal.getPlane() && player.distanceTo(goal) <= radius;
	}

	private static boolean fail(String status)
	{
		lastStatus = status;
		Microbot.status = status;
		LOG.info("Mobile walk ended: {}", status);
		return false;
	}

	/** Off-client-thread only: retry known LOADING, never logout, missing data or an unknown state. */
	private static MobileRoutePlanner.Scene awaitSnapshot(long generation, long walkDeadline) throws InterruptedException
	{
		Client client = Microbot.getClient();
		if (client == null || client.isClientThread())
		{
			return null;
		}
		return LoadingSnapshotWaiter.await(remaining ->
		{
			MicrobotReadServices services = MicrobotReadServices.current();
			if (services == null)
			{
				return null;
			}
			// A stalled client read cannot consume more than the remaining loading/walk budget.
			return services.getClientThread().runOnClientThreadOptional(() ->
			{
				GameState state = services.getClient().getGameState();
				return new LoadingSnapshotWaiter.Observation<>(state,
					state == GameState.LOGGED_IN ? snapshot() : null);
			}, Math.min(1500, remaining)).orElse(null);
		}, () -> GENERATION.get() == generation && MobileActions.active(),
			System::currentTimeMillis, Thread::sleep, walkDeadline);
	}

	/** Copy collision while on the game thread so path search never touches live arrays. */
	private static MobileRoutePlanner.Scene snapshot()
	{
		return MobileActions.read(() ->
		{
			Client client = Microbot.getClient();
			if (client == null || client.getGameState() != GameState.LOGGED_IN)
			{
				return null;
			}
			Player player = client.getLocalPlayer();
			WorldView view = player == null ? null : player.getWorldView();
			if (view == null)
			{
				view = client.getTopLevelWorldView();
			}
			if (player == null || view == null || !view.isTopLevel())
			{
				return null;
			}
			WorldPoint position = player.getWorldLocation();
			if (position == null) { return null; }
			// The actor is authoritative during floor transitions; a worldview plane can lag behind.
			int plane = position.getPlane();
			CollisionData[] maps = view.getCollisionMaps();
			if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null)
			{
				return null;
			}
			int[][] live = maps[plane].getFlags();
			if (live == null)
			{
				return null;
			}
			int[][] flags = new int[live.length][];
			for (int i = 0; i < live.length; i++)
			{
				flags[i] = live[i] == null ? null : Arrays.copyOf(live[i], live[i].length);
			}
			return new MobileRoutePlanner.Scene(view.getBaseX(), view.getBaseY(), plane, flags,
				position, view.isInstance());
		}, null);
	}

	private static boolean tryOpenDoor(WorldPoint routeTile)
	{
		return MobileActions.read(() ->
		{
			Client client = Microbot.getClient();
			Player player = client == null ? null : client.getLocalPlayer();
			WorldView view = player == null ? null : player.getWorldView();
			if (view == null || view.getScene() == null || player.getWorldLocation() == null)
			{
				return false;
			}
			Tile[][][] tiles = view.getScene().getTiles();
			int plane = player.getWorldLocation().getPlane();
			if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null)
			{
				return false;
			}
			for (Tile[] column : tiles[plane])
			{
				if (column == null) { continue; }
				for (Tile tile : column)
				{
					if (tile == null || tile.getWorldLocation().distanceTo(player.getWorldLocation()) > 1
						|| tile.getWorldLocation().distanceTo(routeTile) > 1)
					{
						continue;
					}
					List<TileObject> objects = new ArrayList<>();
					if (tile.getWallObject() != null) { objects.add(tile.getWallObject()); }
					GameObject[] games = tile.getGameObjects();
					if (games != null) { objects.addAll(Arrays.asList(games)); }
					for (TileObject object : objects)
					{
						if (object == null) { continue; }
						ObjectComposition composition = client.getObjectDefinition(object.getId());
						if (composition != null && composition.getImpostorIds() != null) { composition = composition.getImpostor(); }
						if (composition == null || composition.getName() == null || composition.getActions() == null)
						{
							continue;
						}
						String name = composition.getName().toLowerCase(java.util.Locale.ENGLISH);
						if ((name.contains("door") || name.contains("gate"))
							&& Arrays.stream(composition.getActions()).anyMatch("Open"::equalsIgnoreCase))
						{
							return MobileActions.objectAt(object.getId(), object.getWorldLocation(), "Open");
						}
					}
				}
			}
			return false;
		}, false);
	}
}
