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
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
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

	/** The arrival distance when none is given. */
	private static final int DEFAULT_ARRIVAL_TILES = 1;

	public static boolean walkTo(WorldPoint destination)
	{
		return walkTo(destination, DEFAULT_ARRIVAL_TILES);
	}

	/** As upstream: walks to {@code (x, y, plane)} with the default arrival distance. */
	public static boolean walkTo(int x, int y, int plane)
	{
		return walkTo(new WorldPoint(x, y, plane));
	}

	/** As upstream: walks to within {@code distance} tiles of {@code (x, y, plane)}. */
	public static boolean walkTo(int x, int y, int plane, int distance)
	{
		return walkTo(new WorldPoint(x, y, plane), distance);
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
				if (completionMet())
				{
					lastStatus = "Walk condition met";
					return true;
				}
				if (arrived(scene.player, leg.destination, leg.radius) && leg.stairs != null)
				{
					if (!(leg.stairs.fairyRing ? useFairyRing(leg.stairs, generation, deadline)
						: leg.stairs.spell ? useTeleportSpell(leg.stairs, generation, deadline)
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

	/** {@link #walkWithStateUntil}'s condition for the walk running on this thread. */
	private static final ThreadLocal<java.util.function.BooleanSupplier> COMPLETION = new ThreadLocal<>();

	private static boolean completionMet()
	{
		java.util.function.BooleanSupplier condition = COMPLETION.get();
		try
		{
			return condition != null && condition.getAsBoolean();
		}
		catch (RuntimeException e)
		{
			LOG.warn("Mobile walk: completion condition threw", e);
			return false;
		}
	}

	/**
	 * As upstream: one minimap tap toward {@code worldPoint} (no route, no transports). False when
	 * the point is not safely inside the minimap or no tap could be dispatched within two ticks.
	 */
	public static boolean walkMiniMap(WorldPoint worldPoint)
	{
		return worldPoint != null && (MobileActions.walkMinimap(worldPoint) || Global.sleepUntil(() -> MobileActions.walkMinimap(worldPoint), 1_200));
	}

	/** As upstream, except that the minimap zoom stays the user's: mobile never changes it. */
	public static boolean walkMiniMap(WorldPoint worldPoint, double zoomDistance)
	{
		return walkMiniMap(worldPoint);
	}

	/**
	 * As upstream (used in instances such as Vorkath or the Inferno): one scene walk to a local
	 * point, resolved to its world tile on the client thread so instanced scenes work too.
	 */
	public static void walkFastLocal(net.runelite.api.coords.LocalPoint localPoint)
	{
		if (localPoint == null)
		{
			return;
		}
		WorldPoint world = MobileActions.read(() -> WorldPoint.fromLocal(Microbot.getClient(), localPoint), null);
		if (world != null)
		{
			if (!MobileActions.walk(world))
			{
				Global.sleepUntil(() -> MobileActions.walk(world), 1_200);
			}
		}
	}

	/** As upstream: one scene walk to {@code worldPoint}; the point when it was dispatched, else null. */
	public static WorldPoint walkCanvas(WorldPoint worldPoint)
	{
		return worldPoint != null && (MobileActions.walk(worldPoint) || Global.sleepUntil(() -> MobileActions.walk(worldPoint), 1_200))
			? worldPoint : null;
	}

	/**
	 * As upstream: a full walk ({@link #walkTo(WorldPoint, int)}), reported as {@link WalkerState#ARRIVED},
	 * {@link WalkerState#EXIT} when cancelled (stop, a new target, an interrupt) and
	 * {@link WalkerState#UNREACHABLE} otherwise.
	 */
	public static WalkerState walkWithState(WorldPoint target, int distance)
	{
		if (target == null)
		{
			return WalkerState.EXIT;
		}
		long before = GENERATION.get();
		if (walkTo(target, distance))
		{
			return WalkerState.ARRIVED;
		}
		boolean cancelled = !MobileActions.active() || Thread.currentThread().isInterrupted() || GENERATION.get() != before + 1;
		return cancelled ? WalkerState.EXIT : WalkerState.UNREACHABLE;
	}

	/** As upstream: {@link #walkWithState(WorldPoint, int)} with the default arrival distance. */
	public static WalkerState walkWithState(WorldPoint target)
	{
		return walkWithState(target, DEFAULT_ARRIVAL_TILES);
	}

	/**
	 * As upstream: walks until within {@code distance} or until {@code completionCondition} holds
	 * (checked before starting and on every walking pass), either of which is {@link WalkerState#ARRIVED}.
	 */
	public static WalkerState walkWithStateUntil(WorldPoint target, int distance, java.util.function.BooleanSupplier completionCondition)
	{
		java.util.Objects.requireNonNull(completionCondition, "completionCondition");
		if (target == null)
		{
			return walkWithState(null, distance);
		}
		if (completionCondition.getAsBoolean())
		{
			return WalkerState.ARRIVED;
		}
		java.util.function.BooleanSupplier previous = COMPLETION.get();
		COMPLETION.set(completionCondition);
		try
		{
			return walkWithState(target, distance);
		}
		finally
		{
			if (previous == null)
			{
				COMPLETION.remove();
			}
			else
			{
				COMPLETION.set(previous);
			}
		}
	}

	/** As upstream: cancels any walk in progress; the reason is logged. */
	public static void clearWalkingRoute(String reason)
	{
		setTarget(null, reason == null || reason.isBlank() ? "unspecified" : reason);
	}

	/** As upstream: the active walk's destination, or null when no walk is running. */
	public static WorldPoint getCurrentTarget()
	{
		return target;
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
		TransportType.CHARTER_SHIP, TransportType.SPIRIT_TREE, TransportType.MINECART, TransportType.MAGIC_CARPET,
		TransportType.GNOME_GLIDER, TransportType.HOT_AIR_BALLOON, TransportType.QUETZAL, TransportType.MAGIC_MUSHTREE);

	/** One object click that teleports: arrival is anywhere near the catalogued landing. */
	static final Set<TransportType> TELEPORT_OBJECTS = EnumSet.of(TransportType.TELEPORTATION_LEVER,
		TransportType.TELEPORTATION_PORTAL);

	/** Each gnome glider destination's button on the glider map (interface 138), as upstream's {@code handleGlider}. */
	static final java.util.Map<String, Integer> GLIDER_BUTTONS = java.util.Map.of(
		"ta quir priw", InterfaceID.Glidermap.GRANDTREE_BUTTON,
		"sindarpos", InterfaceID.Glidermap.WHITEWOLFMOUNTAIN_BUTTON,
		"lemanto andra", InterfaceID.Glidermap.VARROCK_BUTTON,
		"kar-hewo", InterfaceID.Glidermap.ALKHARID_BUTTON,
		"gandius", InterfaceID.Glidermap.KARAMJA_BUTTON,
		"lemantolly undri", InterfaceID.Glidermap.OGREAREA_BUTTON,
		"ookookolly undri", InterfaceID.Glidermap.APEATOLL_BUTTON);

	/** Each hot air balloon destination's button on the balloon map, as upstream's {@code Rs2HotAirBalloon}. */
	static final java.util.Map<String, Integer> BALLOON_BUTTONS = java.util.Map.of(
		"castle wars", InterfaceID.ZepBalloonMap.BTN_CAST,
		"grand tree", InterfaceID.ZepBalloonMap.BTN_GNO,
		"crafting guild", InterfaceID.ZepBalloonMap.BTN_CRAFT,
		"entrana", InterfaceID.ZepBalloonMap.BTN_ENT,
		"taverley", InterfaceID.ZepBalloonMap.BTN_TAV,
		"varrock", InterfaceID.ZepBalloonMap.BTN_VARR);

	/** Destination maps answered with one button per destination, by the map's root widget. */
	private static final java.util.Map<Integer, java.util.Map<String, Integer>> DESTINATION_MAPS = java.util.Map.of(
		InterfaceID.Glidermap.UNIVERSE, GLIDER_BUTTONS,
		InterfaceID.ZepBalloonMap.ROOT_RECT0, BALLOON_BUTTONS);

	/** The charter-ship destination map (upstream: widget 885, child 4). */
	private static final int CHARTER_GROUP = 885;
	private static final int CHARTER_CHILD = 4;
	/**
	 * Destination maps answered by the entry whose text names the destination, by the widgets to
	 * search: the charter map, Renu's quetzal map and the whistle's (any layer: the root can stay
	 * hidden while the map shows, as upstream notes), and the Fossil Island mushtree menu.
	 */
	private static final int[] TEXT_MAPS = {(CHARTER_GROUP << 16) | CHARTER_CHILD,
		InterfaceID.QuetzalMenu.UNIVERSE, InterfaceID.QuetzalMenu.CONTENTS, InterfaceID.QuetzalMenu.MAP, InterfaceID.QuetzalMenu.ICONS,
		InterfaceID.QuetzalwhistleMenu.UNIVERSE, InterfaceID.QuetzalwhistleMenu.CONTENTS, InterfaceID.QuetzalwhistleMenu.MAP,
		InterfaceID.QuetzalwhistleMenu.ICONS, InterfaceID.FossilMushtrees.UNIVERSE};

	/** Transport types the walker can execute; the pathfinder is told to admit only these. */
	static final Set<TransportType> EXECUTABLE_TRANSPORTS = EnumSet.of(TransportType.TRANSPORT, TransportType.AGILITY_SHORTCUT,
		TransportType.BOAT, TransportType.SHIP, TransportType.NPC, TransportType.CHARTER_SHIP, TransportType.TELEPORTATION_ITEM,
		TransportType.TELEPORTATION_SPELL, TransportType.SPIRIT_TREE, TransportType.FAIRY_RING, TransportType.MINECART,
		TransportType.MAGIC_CARPET, TransportType.TELEPORTATION_LEVER, TransportType.TELEPORTATION_PORTAL,
		TransportType.GNOME_GLIDER, TransportType.HOT_AIR_BALLOON, TransportType.QUETZAL, TransportType.MAGIC_MUSHTREE);

	/**
	 * Destination lists as {group, child}: the spirit tree menu (upstream {@code ADVENTURE_LOG_CONTAINER},
	 * 187:3) and the Lovakengj minecart list (947:9, "Minecart rides").
	 */
	private static final int[][] DESTINATION_MENUS = {{187, 3}, {947, 9}};

	/** Staves that open fairy rings until the Lumbridge elite diary is done. */
	private static final int[] FAIRY_RING_STAVES = {net.runelite.api.gameval.ItemID.DRAMEN_STAFF,
		net.runelite.api.gameval.ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF};
	/** Each dial's model, clockwise and anticlockwise buttons (upstream {@code SLOT_ONE} ... {@code SLOT_THREE_ACW_ROTATION}). */
	private static final int[][] FAIRY_RING_DIALS = {
		{InterfaceID.Fairyrings.ROOT_MODEL3, InterfaceID.Fairyrings._1_CLOCKWISE, InterfaceID.Fairyrings._1_ANTICLOCKWISE},
		{InterfaceID.Fairyrings.ROOT_MODEL4, InterfaceID.Fairyrings._2_CLOCKWISE, InterfaceID.Fairyrings._2_ANTICLOCKWISE},
		{InterfaceID.Fairyrings.ROOT_MODEL5, InterfaceID.Fairyrings._3_CLOCKWISE, InterfaceID.Fairyrings._3_ANTICLOCKWISE}};
	/** A fairy ring lands on the destination ring. */
	private static final int FAIRY_RING_ARRIVAL_TILES = 2;

	/** A home teleport channels for about ten seconds before the landing. */
	private static final long SPELL_TELEPORT_MILLIS = 30_000;

	/** Teleports land in an area rather than on one tile. */
	private static final int TELEPORT_ARRIVAL_TILES = 8;
	private static final long TELEPORT_MILLIS = 20_000;
	/** Inventory options that open a teleport item's destinations or use it, in preference order. */
	private static final List<String> TELEPORT_ITEM_OPTIONS = List.of("Rub", "Teleport", "Break", "Read", "Commune");

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
			if (entersWilderness(transport.getDestination(), destination))
			{
				LOG.info("Mobile walk: route takes {} into the Wilderness; using the same-plane planner", transport);
				return null;
			}
			if (!EXECUTABLE_TRANSPORTS.contains(transport.getType())
				|| (transport.getAction() == null && transport.getType() != TransportType.TELEPORTATION_ITEM
					&& transport.getType() != TransportType.TELEPORTATION_SPELL && transport.getType() != TransportType.FAIRY_RING))
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
				// "Item: destination", or only the item ("Varrock tablet"): a tablet or scroll used with its own option.
				if (disableTeleports || transport.getDisplayInfo() == null || transport.getDisplayInfo().isBlank())
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
			if (transport.getType() == TransportType.FAIRY_RING)
			{
				String code = fairyRingCode(transport.getDisplayInfo());
				if (code == null)
				{
					LOG.info("Mobile walk: route needs fairy ring {} without a code; using the same-plane planner", transport);
					return null;
				}
				ObjectTransports.Step step = new ObjectTransports.Step(transport.getOrigin(), transport.getDestination(), -1, "",
					"Fairy ring", FAIRY_RING_ARRIVAL_TILES, false, code, false, Set.of(), false, true);
				return new WalkLeg(transport.getOrigin(), 1, step, new ArrayList<>(tiles.subList(0, i + 1)), true);
			}
			boolean travel = TRAVEL_TRANSPORTS.contains(transport.getType());
			int arrival = travel ? TRAVEL_ARRIVAL_TILES : TELEPORT_OBJECTS.contains(transport.getType()) ? TELEPORT_ARRIVAL_TILES : 1;
			ObjectTransports.Step step = new ObjectTransports.Step(transport.getOrigin(), transport.getDestination(),
				transport.getObjectId(), transport.getAction(), transport.getName(), arrival, travel, transport.getDisplayInfo());
			return new WalkLeg(transport.getOrigin(), 1, step, new ArrayList<>(tiles.subList(0, i + 1)), true);
		}
		return new WalkLeg(destination, radius, null, new ArrayList<>(tiles), true);
	}

	/**
	 * A transport landing in the Wilderness when the walk's goal is not there. The pathfinder avoids
	 * the Wilderness only where it can; the walker never takes the player into it unasked.
	 */
	static boolean entersWilderness(WorldPoint landing, WorldPoint goal)
	{
		return landing != null && PathfinderConfig.isInWilderness(landing)
			&& (goal == null || !PathfinderConfig.isInWilderness(goal));
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
			java.util.Map<String, Integer> buttons = visibleDestinationMap();
			if (buttons != null)
			{
				Integer button = destinationButton(buttons, step.destinationText);
				if (button == null || !Global.sleepUntil(() -> MobileActions.widget(button, -1, ""), 2_000))
				{
					lastStatus = "destination map does not offer " + step.destinationText;
					return false;
				}
				Thread.sleep(600);
				continue;
			}
			if (destinationMenuVisible())
			{
				if (!chooseMenuDestination(step.destinationText))
				{
					lastStatus = "destination menu does not offer " + step.destinationText;
					return false;
				}
				Thread.sleep(600);
				continue;
			}
			int textMap = visibleTextMap();
			if (textMap >= 0)
			{
				if (!chooseTextMapDestination(textMap, step.destinationText))
				{
					lastStatus = "destination map does not offer " + step.destinationText;
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
		// Worn items are used by their destination option, so only when there is a destination.
		Rs2ItemModel worn = place.isEmpty() ? null : Rs2Equipment.get(isItem);
		Integer widget = worn == null ? null : Rs2Equipment.wornSlotWidget(worn.getSlot());
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

	/**
	 * Uses a fairy ring as upstream does: wields a Dramen or Lunar staff unless the Lumbridge elite
	 * diary is done (and wields the previous weapon again afterwards), takes the ring's
	 * "last-destination" option when it names this code, else configures it (each dial turned to
	 * the code, then Confirm), and requires an observed landing.
	 */
	private static boolean useFairyRing(ObjectTransports.Step step, long generation, long walkDeadline) throws InterruptedException
	{
		String code = step.destinationText;
		lastStatus = "Fairy ring: " + code;
		int previousWeapon = -1;
		if (Microbot.getVarbitValue(net.runelite.api.gameval.VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) != 1
			&& !Rs2Equipment.isWearing(FAIRY_RING_STAVES))
		{
			Rs2ItemModel weapon = Rs2Equipment.get(net.runelite.api.EquipmentInventorySlot.WEAPON);
			if (!Global.sleepUntil(() -> Rs2Inventory.wield(FAIRY_RING_STAVES), 2_000)
				|| !Global.sleepUntil(() -> Rs2Equipment.isWearing(FAIRY_RING_STAVES), 3_000))
			{
				lastStatus = "no Dramen or Lunar staff for the fairy ring";
				return false;
			}
			previousWeapon = weapon == null ? -1 : weapon.getId();
		}
		try
		{
			return dialAndUseFairyRing(step, code, generation, Math.min(walkDeadline, System.currentTimeMillis() + TELEPORT_MILLIS));
		}
		finally
		{
			int weapon = previousWeapon;
			if (weapon >= 0 && MobileActions.active() && GENERATION.get() == generation)
			{
				Global.sleepUntil(() -> Rs2Inventory.wield(weapon), 2_000);
			}
		}
	}

	private static boolean dialAndUseFairyRing(ObjectTransports.Step step, String code, long generation, long deadline)
		throws InterruptedException
	{
		long lastAttempt = 0;
		boolean dispatched = false;
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
			if (fairyRingInterfaceVisible())
			{
				if (!dialFairyRing(code))
				{
					lastStatus = "fairy ring dials did not reach " + code;
					return false;
				}
				dispatched |= Global.sleepUntil(() -> MobileActions.widget(InterfaceID.Fairyrings.CONFIRM, -1, ""), 2_000);
				Thread.sleep(600);
				continue;
			}
			if (!dispatched && scene.player.getPlane() == step.origin.getPlane() && scene.player.distanceTo(step.origin) <= 3
				&& System.currentTimeMillis() - lastAttempt >= 6_000)
			{
				lastAttempt = System.currentTimeMillis();
				Object[] ring = MobileActions.read(() ->
				{
					Rs2TileObjectModel model = MicrobotReadServices.require().getRs2TileObjectCache().query()
						.where(candidate -> step.origin.equals(candidate.getWorldLocation()) && candidate.getObjectComposition() != null)
						.nearest();
					String option = model == null ? null : fairyRingOption(model.getObjectComposition().getActions(), code);
					return option == null ? null : new Object[]{model.getId(), model.getWorldLocation(), option};
				}, null);
				if (ring == null)
				{
					lastStatus = "no fairy ring with a usable option at " + step.origin;
					return false;
				}
				String option = (String) ring[2];
				if (Global.sleepUntil(() -> MobileActions.objectAt((int) ring[0], (WorldPoint) ring[1], option), 2_000)
					&& !isFairyRingConfigure(option))
				{
					dispatched = true;
				}
			}
			long remaining = deadline - System.currentTimeMillis();
			if (remaining > 0) { Thread.sleep(Math.min(300, remaining)); }
		}
		lastStatus = !MobileActions.active() || GENERATION.get() != generation
			? "fairy ring cancelled" : dispatched ? "fairy ring landing was not observed" : "fairy ring was not used";
		return false;
	}

	/** "AIQ" from the catalogue's display info ("AIQ", or several codes, the first used); null when it has none. */
	static String fairyRingCode(String displayInfo)
	{
		if (displayInfo == null)
		{
			return null;
		}
		String first = displayInfo.trim().split("\\s+")[0].toUpperCase(java.util.Locale.ROOT);
		return first.matches("[A-D][I-L][P-S]") ? first : null;
	}

	/** The ring's "last-destination (CODE)" option for this code, else its configure option; null when neither. */
	static String fairyRingOption(String[] actions, String code)
	{
		if (actions == null)
		{
			return null;
		}
		for (String action : actions)
		{
			String text = action == null ? "" : Text.removeTags(action).trim();
			if (text.equalsIgnoreCase("last-destination (" + code + ")") || text.equalsIgnoreCase("Ring-last-destination (" + code + ")"))
			{
				return action;
			}
		}
		return Arrays.stream(actions).filter(Rs2Walker::isFairyRingConfigure).findFirst().orElse(null);
	}

	private static boolean isFairyRingConfigure(String action)
	{
		return "Configure".equalsIgnoreCase(action) || "Ring-configure".equalsIgnoreCase(action);
	}

	private static boolean fairyRingInterfaceVisible()
	{
		return MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget confirm = Microbot.getClient().getWidget(InterfaceID.Fairyrings.CONFIRM);
			return confirm != null && !confirm.isHidden();
		}, false);
	}

	/** Turns each dial to its letter, one press per quarter turn, the shorter way round; true when all three read right. */
	private static boolean dialFairyRing(String code)
	{
		for (int i = 0; i < 3; i++)
		{
			int dial = FAIRY_RING_DIALS[i][0];
			int desired = fairyRingRotation(code.charAt(i));
			for (int turn = 0; turn < 3; turn++)
			{
				int current = dialRotation(dial);
				if (current < 0 || current == desired)
				{
					break;
				}
				int button = fairyRingTurnsClockwise(current, desired) ? FAIRY_RING_DIALS[i][1] : FAIRY_RING_DIALS[i][2];
				if (!Global.sleepUntil(() -> MobileActions.widget(button, -1, ""), 2_000))
				{
					return false;
				}
				Global.sleepUntil(() -> dialRotation(dial) != current && dialRotation(dial) % 512 == 0, 2_000);
			}
			if (!Global.sleepUntil(() -> dialRotation(dial) == desired, 3_000))
			{
				return false;
			}
		}
		return true;
	}

	private static int dialRotation(int dial)
	{
		return MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget widget = Microbot.getClient().getWidget(dial);
			return widget == null || widget.isHidden() ? -1 : widget.getRotationY();
		}, -1);
	}

	/** As upstream: a letter's dial rotation (A, I, P 0; B, J, Q 512; C, K, R 1024; D, L, S 1536), or -1. */
	static int fairyRingRotation(char letter)
	{
		switch (Character.toUpperCase(letter))
		{
			case 'A': case 'I': case 'P': return 0;
			case 'B': case 'J': case 'Q': return 512;
			case 'C': case 'K': case 'R': return 1024;
			case 'D': case 'L': case 'S': return 1536;
			default: return -1;
		}
	}

	/** As upstream: clockwise when that way round is no longer than anticlockwise. */
	static boolean fairyRingTurnsClockwise(int current, int desired)
	{
		int anticlockwise = (desired - current + 2048) % 2048;
		int clockwise = (current - desired + 2048) % 2048;
		return clockwise <= anticlockwise;
	}

	private static boolean destinationMenuVisible()
	{
		return MobileActions.read(() -> visibleDestinationMenu() != null, false);
	}

	/** The option list of whichever destination menu is showing, or null; client thread only. */
	private static net.runelite.api.widgets.Widget visibleDestinationMenu()
	{
		for (int[] menu : DESTINATION_MENUS)
		{
			net.runelite.api.widgets.Widget list = Microbot.getClient().getWidget(menu[0], menu[1]);
			if (list != null && !list.isHidden())
			{
				return list;
			}
		}
		return null;
	}

	/**
	 * As upstream: the destination menu's entry naming the destination ("4: Grand Exchange" names
	 * "Grand Exchange"), else the entry's shortcut key (the character before the colon).
	 */
	private static boolean chooseMenuDestination(String displayInfo)
	{
		if (displayInfo == null || displayInfo.isBlank())
		{
			return false;
		}
		String needle = menuDestinationName(displayInfo).toLowerCase(java.util.Locale.ROOT);
		int[] entry = MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget root = visibleDestinationMenu();
			net.runelite.api.widgets.Widget match = root == null || needle.isEmpty() ? null : findCharterEntry(root, needle);
			return match == null ? null : new int[]{match.getId(), match.getIndex()};
		}, null);
		if (entry != null && Global.sleepUntil(() -> MobileActions.widget(entry[0], entry[1], ""), 2_000))
		{
			return true;
		}
		char key = menuShortcut(displayInfo);
		return key != 0 && Global.sleepUntil(() -> MobileActions.type(key), 2_000);
	}

	/**
	 * "4: Grand Exchange" gives "Grand Exchange", and the mushtree form "4. Mushroom Meadow" gives
	 * "Mushroom Meadow"; text without such a prefix is returned trimmed.
	 */
	static String menuDestinationName(String displayInfo)
	{
		return displayInfo.trim().replaceAll("^[0-9A-Za-z][:.]\\s*", "").trim();
	}

	/** "4: Grand Exchange" gives '4', "A: Port Piscarilius" gives 'a'; 0 without a shortcut prefix. */
	static char menuShortcut(String displayInfo)
	{
		String text = displayInfo.trim();
		return text.length() > 1 && text.charAt(1) == ':' && Character.isLetterOrDigit(text.charAt(0))
			? Character.toLowerCase(text.charAt(0)) : 0;
	}

	/** A destination map's button for a destination name (case-insensitive), or null. */
	static Integer destinationButton(java.util.Map<String, Integer> buttons, String destination)
	{
		return destination == null ? null : buttons.get(destination.trim().toLowerCase(java.util.Locale.ROOT));
	}

	/** The buttons of whichever destination map (glider or balloon) is showing, or null when none is. */
	private static java.util.Map<String, Integer> visibleDestinationMap()
	{
		Integer root = MobileActions.read(() ->
		{
			for (Integer candidate : DESTINATION_MAPS.keySet())
			{
				net.runelite.api.widgets.Widget map = Microbot.getClient().getWidget(candidate);
				if (map != null && !map.isHidden())
				{
					return candidate;
				}
			}
			return null;
		}, null);
		return root == null ? null : DESTINATION_MAPS.get(root);
	}

	/** The first of {@link #TEXT_MAPS} that is showing, or -1. */
	private static int visibleTextMap()
	{
		return MobileActions.read(() ->
		{
			for (int root : TEXT_MAPS)
			{
				net.runelite.api.widgets.Widget map = Microbot.getClient().getWidget(root);
				if (map != null && !map.isHidden())
				{
					return root;
				}
			}
			return -1;
		}, -1);
	}

	/**
	 * As upstream: the map entry under {@code rootId} whose text, name or option names the
	 * destination ("4. Mushroom Meadow" names "Mushroom Meadow"), dispatched with the first option
	 * of it or its nearest ancestor that has one, else tapped.
	 */
	private static boolean chooseTextMapDestination(int rootId, String destination)
	{
		if (destination == null || destination.isBlank())
		{
			return false;
		}
		String needle = menuDestinationName(Text.removeTags(destination)).toLowerCase(java.util.Locale.ROOT);
		Object[] target = MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget root = Microbot.getClient().getWidget(rootId);
			net.runelite.api.widgets.Widget match = root == null || needle.isEmpty() ? null : findCharterEntry(root, needle);
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
			return match == null ? null : new Object[]{match.getId(), match.getIndex(), ""};
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
