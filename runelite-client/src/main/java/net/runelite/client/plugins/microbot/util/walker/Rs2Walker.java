package net.runelite.client.plugins.microbot.util.walker;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WalkerPathReads;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorClassifier;
import net.runelite.client.plugins.microbot.util.walker.puzzles.DraynorBasementSolver;
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
	// The running walk's route (pathfinder or local tiles), for isNear/isNearPath; empty when no walk runs.
	private static volatile List<WorldPoint> activeRoute = Collections.emptyList();
	// isNearPath's last checked position (upstream's routeState.lastPosition).
	private static volatile WorldPoint lastNearPathPosition;
	private static volatile String lastStatus = "Idle";
	// Why and when the target was last cleared (upstream's routeState.lastRouteClearReason / AtMs).
	private static volatile String lastRouteClearReason = "";
	private static volatile long lastRouteClearAtMs;
	private static final long MAX_WALK_MILLIS = 120_000;
	/** No observed movement for this long triggers a replan and retry, never a stop. */
	private static final long DEFAULT_STALL_MILLIS = 15_000;
	/** Route steps from which a waypoint is walked through the minimap rather than a scene tile. */
	static final int MINIMAP_MIN_STEPS = 4;
	/** While moving, the next click waits until the player is this close to the last target. */
	static final int RECLICK_DISTANCE = 4;
	/** Extra random tiles added to {@link #RECLICK_DISTANCE} per click, so the next click lands before the player stops. */
	static final int RECLICK_SPREAD = 3;
	/** Route steps ahead of the player a walk click aims for (minimap range is about 16 tiles). */
	static final int WAYPOINT_STEPS = 14;
	/** Poll interval of the walk loop; the game moves the player every 600 ms. */
	private static final long WALK_POLL_MILLIS = 300;
	/** A click counts as accepted (no re-click) for this long, while the player starts moving. */
	private static final long CLICK_SETTLE_MILLIS = 900;
	/** Stairs are clicked from this many route steps away, so the player walks onto them without stopping first. */
	static final int STAIRS_EARLY_STEPS = 10;
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

	/** The running walk's route tiles (empty when no walk runs), for {@link Rs2PathApi}. */
	static List<WorldPoint> activeRoutePath()
	{
		return activeRoute;
	}

	/** Null cancels an outstanding walk without issuing any game input. */
	public static void setTarget(WorldPoint point)
	{
		setTarget(point, point == null ? "cleared" : "set");
	}

	/** Upstream overload; the reason is logged, and kept for {@link #getLastRouteClearReason()} when the target is cleared. */
	public static void setTarget(WorldPoint point, String reason)
	{
		LOG.debug("Walker target {} ({})", point, reason);
		if (point == null)
		{
			lastRouteClearReason = reason == null ? "" : reason;
			lastRouteClearAtMs = System.currentTimeMillis();
		}
		target = point;
		GENERATION.incrementAndGet();
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
		if (arrivedOnFoot(scene, destination, radius))
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

	/** Item IDs any teleport item transport needs, plus the Dramen and Lunar staves (as upstream); built once. */
	private static volatile Set<Integer> teleportItemIds;

	/** As upstream: whether this item is used to teleport (any teleport-item transport needs it). */
	public static boolean isTeleportItem(int itemId)
	{
		Set<Integer> ids = teleportItemIds;
		if (ids == null)
		{
			Set<Integer> built = new HashSet<>(List.of(
				net.runelite.api.gameval.ItemID.DRAMEN_STAFF, net.runelite.api.gameval.ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF));
			for (Set<Transport> set
				: Transport.loadAllFromResources().values())
			{
				for (Transport transport : set)
				{
					if (transport.getType() == TransportType.TELEPORTATION_ITEM
						&& transport.getItemIdRequirements() != null)
					{
						transport.getItemIdRequirements().forEach(built::addAll);
					}
				}
			}
			ids = Set.copyOf(built);
			teleportItemIds = ids;
		}
		return ids.contains(itemId);
	}

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
		if (!prepareDraynorBasement(destination))
		{
			return fail("Draynor Manor basement entrance not reached");
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
			if (arrivedOnFoot(scene, destination, radius))
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
			activeRoute = global;
			long lastProgress = System.currentTimeMillis();
			WorldPoint previous = scene.player;
			int progress = 0;
			boolean recovered = false;
			int acceptedWalks = 0;
			int stalls = 0;
			WorldPoint clicked = null;
			long clickedAt = 0;
			long lastMoveAt = System.currentTimeMillis();
			int reclickDistance = RECLICK_DISTANCE;
			while (System.currentTimeMillis() < deadline && GENERATION.get() == generation
				&& MobileActions.active() && !Thread.currentThread().isInterrupted())
			{
				scene = awaitSnapshot(generation, deadline);
				if (scene == null)
				{
					return fail("Walking stopped: no collision snapshot or game state changed");
				}
				boolean houseStep = leg.stairs != null && leg.stairs.house != null;
				if (!houseStep && scene.player.getPlane() != leg.destination.getPlane())
				{
					return fail("Walking stopped: unexpected plane change");
				}
				if (arrivedOnFoot(scene, destination, radius))
				{
					lastStatus = "Arrived";
					return true;
				}
				if (completionMet())
				{
					lastStatus = "Walk condition met";
					return true;
				}
				// A way out of the house runs from wherever the player stands in it (its origin is synthetic).
				if (leg.stairs != null && (arrived(scene.player, leg.destination, leg.radius)
					|| (houseStep && net.runelite.client.plugins.microbot.util.poh.PohPresence.isInHouse())
					|| stairsInReach(scene, leg.stairs)))
				{
					if (!(leg.stairs.house != null && leg.stairs.house != net.runelite.client.plugins.microbot.shortestpath.PohDevice.FAIRY_RING
						? useHouseDevice(leg.stairs, generation, deadline)
						: leg.stairs.canoe ? useCanoe(leg.stairs, generation, deadline)
						: leg.stairs.fairyRing ? useFairyRing(leg.stairs, generation, deadline)
						: leg.stairs.minigame ? useMinigameTeleport(leg.stairs, generation, deadline)
						: leg.stairs.obelisk ? useObelisk(leg.stairs, generation, deadline)
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
						activeRoute = global;
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
					activeRoute = global;
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
					lastMoveAt = lastProgress;
					// A recovery only shortens steps until the player is moving again.
					recovered = false;
				}
				if (MicrobotRuntime.humanOwnsInput())
				{
					// The user's own taps own input; the walk yields and its stall timer waits too.
					lastStatus = "Waiting: manual input";
					lastProgress = System.currentTimeMillis();
				}
				if (REPLAN.getAndSet(false))
				{
					// A script asked for a fresh route (recalculatePath); plan it from where the player stands.
					List<WorldPoint> replanned = plan(scene, leg.destination, leg.radius);
					if (!replanned.isEmpty()) { global = replanned; activeRoute = global; progress = 0; }
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
					if (!replanned.isEmpty()) { global = replanned; activeRoute = global; progress = 0; }
					recovered = true;
					// The last click went nowhere: the retry clicks afresh.
					clicked = null;
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
					if (!replanned.isEmpty()) { global = replanned; activeRoute = global; progress = 0; }
					recovered = true;
					LOG.info("Mobile walk recovery: player={} target={} bridge={}", scene.player, destination, JavaBridge.getScriptWalkStatus());
				}
				progress = nearestProgress(global, scene.player, progress);
				WorldPoint waypoint = null;
				for (int i = Math.min(global.size() - 1, progress + 22); i > progress; i--)
				{
					WorldPoint candidate = global.get(i);
					if (!scene.contains(candidate) || scene.player.distanceTo(candidate) > WAYPOINT_STEPS)
					{
						continue;
					}
					List<WorldPoint> local = MobileRoutePlanner.route(scene.player, candidate, 0, scene::canStep);
					if (!local.isEmpty())
					{
						waypoint = local.get(Math.min(local.size() - 1, recovered ? 3 : WAYPOINT_STEPS));
						break;
					}
				}
				long now = System.currentTimeMillis();
				// Moving toward the last click: the poll may fall between game ticks, so "moving" is any step
				// in the last tick or so, not only since the previous poll.
				boolean walking = clicked != null && (now - clickedAt < CLICK_SETTLE_MILLIS || now - lastMoveAt < 1_200);
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
				else if (walking && scene.player.distanceTo(clicked) > reclickDistance)
				{
					// Still heading for the last accepted target: do not re-click every tick.
					lastStatus = "Walking to " + destination;
				}
				else if (!waypoint.equals(scene.player) && (!walking || !waypoint.equals(clicked)))
				{
					lastStatus = "Walking to " + destination;
					List<WorldPoint> steps = MobileRoutePlanner.route(scene.player, waypoint, 0, scene::canStep);
					WorldPoint accepted = null;
					// Longer steps go through the minimap, which is visible whatever the camera does.
					if (steps.size() - 1 >= MINIMAP_MIN_STEPS && MobileActions.walkMinimap(waypoint))
					{
						accepted = waypoint;
					}
					else
					{
						// A scene tile must be on screen; try nearer reachable tiles instead of
						// repeating an invisible destination.
						for (int i = steps.size() - 1; i > 0; i--)
						{
							if (MobileActions.walk(steps.get(i))) { accepted = steps.get(i); break; }
						}
					}
					if (accepted != null)
					{
						acceptedWalks++;
						clicked = accepted;
						clickedAt = System.currentTimeMillis();
						reclickDistance = RECLICK_DISTANCE + java.util.concurrent.ThreadLocalRandom.current().nextInt(RECLICK_SPREAD + 1);
					}
				}
				long remaining = deadline - System.currentTimeMillis();
				if (remaining > 0) { Thread.sleep(Math.min(WALK_POLL_MILLIS, remaining)); }
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
				activeRoute = Collections.emptyList();
			}
		}
	}

	public static boolean walkFastCanvas(WorldPoint destination)
	{
		return walkTo(destination, 0);
	}

	/** As upstream: sets run to {@code toggleRun}, then {@link #walkFastCanvas(WorldPoint)}. */
	public static boolean walkFastCanvas(WorldPoint destination, boolean toggleRun)
	{
		Rs2Player.toggleRunEnergy(toggleRun);
		return walkFastCanvas(destination);
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

	/**
	 * As upstream: the walker's settings scripts read (for example {@code config.reachedDistance()}).
	 * Mobile: upstream's defaults; the pathfinder's own settings are Claude 2's {@code PathfinderConfig}.
	 */
	public static final net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig config =
		new net.runelite.client.plugins.microbot.shortestpath.ShortestPathConfig()
		{
		};

	/** At most one dodge in flight; a tile added while dodging is answered by that dodge. */
	private static final java.util.concurrent.atomic.AtomicBoolean DODGING = new java.util.concurrent.atomic.AtomicBoolean();
	private static volatile java.util.concurrent.ExecutorService dodgeExecutor;

	/**
	 * As upstream's dangerous-tile handling: when a script marks the tile the player stands on as
	 * dangerous ({@link net.runelite.client.plugins.microbot.util.tile.Rs2Tile}), one scene walk to
	 * {@code Rs2Tile.getSafeTile()}, off the adding thread. Only while a script session is active,
	 * so the player is never moved without a running script. Registered by the runtime on install.
	 */
	public static void registerDangerDodge()
	{
		net.runelite.client.plugins.microbot.util.tile.Rs2Tile.onPlayerOnDangerousTile = tile -> dodgeDangerousTile();
	}

	/** Removes the dodge (runtime uninstall). */
	public static void clearDangerDodge()
	{
		net.runelite.client.plugins.microbot.util.tile.Rs2Tile.onPlayerOnDangerousTile = null;
	}

	/**
	 * Starts one dodge unless one is running or no script is active; never blocks the caller.
	 * Returns whether a dodge was started.
	 */
	static boolean dodgeDangerousTile()
	{
		if (!MobileActions.active() || !DODGING.compareAndSet(false, true))
		{
			return false;
		}
		try
		{
			dodgeExecutor().execute(() ->
			{
				try
				{
					dodgeToSafeTile();
				}
				finally
				{
					DODGING.set(false);
				}
			});
			return true;
		}
		catch (java.util.concurrent.RejectedExecutionException e)
		{
			DODGING.set(false);
			return false;
		}
	}

	/** The dodge itself: one scene walk to the nearest safe tile; the tile walked to, else null. */
	static WorldPoint dodgeToSafeTile()
	{
		WorldPoint safe = net.runelite.client.plugins.microbot.util.tile.Rs2Tile.getSafeTile();
		return safe == null ? null : walkCanvas(safe);
	}

	private static java.util.concurrent.ExecutorService dodgeExecutor()
	{
		java.util.concurrent.ExecutorService executor = dodgeExecutor;
		if (executor != null)
		{
			return executor;
		}
		synchronized (DODGING)
		{
			if (dodgeExecutor == null)
			{
				dodgeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r ->
				{
					Thread thread = new Thread(r, "rs2walker-dodge");
					thread.setDaemon(true);
					return thread;
				});
			}
			return dodgeExecutor;
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
		// The basement solver walks on its own (each walk advances the generation): solve first.
		if (!prepareDraynorBasement(target))
		{
			return !MobileActions.active() || Thread.currentThread().isInterrupted() ? WalkerState.EXIT : WalkerState.UNREACHABLE;
		}
		long before = GENERATION.get();
		if (walkTo(target, distance))
		{
			return WalkerState.ARRIVED;
		}
		boolean cancelled = !MobileActions.active() || Thread.currentThread().isInterrupted() || GENERATION.get() != before + 1;
		return cancelled ? WalkerState.EXIT : WalkerState.UNREACHABLE;
	}

	// ---- Util action batch X (Claude 1): walker public API ----

	/** Set by {@link #recalculatePath()}; the running walk replans on its next pass. */
	private static final java.util.concurrent.atomic.AtomicBoolean REPLAN = new java.util.concurrent.atomic.AtomicBoolean();

	/** As upstream: the running walk plans its route again from where the player stands, on its next pass. */
	public static void recalculatePath()
	{
		if (target != null)
		{
			REPLAN.set(true);
		}
	}

	/** As upstream: whether a route from {@code start} to {@code end} can be planned (mobile plans on demand). */
	public static boolean restartPathfinding(WorldPoint start, WorldPoint end)
	{
		return start != null && end != null && getWalkPath(start, end).size() > 0;
	}

	/** As upstream: whether a route from {@code start} to any of {@code ends} can be planned. */
	public static boolean restartPathfinding(WorldPoint start, Set<WorldPoint> ends)
	{
		return ends != null && ends.stream().anyMatch(end -> restartPathfinding(start, end));
	}

	/** As upstream: walks until within {@code distance} or {@code completionCondition} holds. */
	public static boolean walkUntil(WorldPoint target, int distance, java.util.function.BooleanSupplier completionCondition)
	{
		return walkWithStateUntil(target, distance, completionCondition) == WalkerState.ARRIVED;
	}

	/** The walkable tiles around this object, else the ring just outside it (as upstream). */
	private static List<WorldPoint> besideObject(GameObject object)
	{
		net.runelite.api.coords.WorldArea area = net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.getWorldArea(object);
		if (area == null)
		{
			return Collections.emptyList();
		}
		net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea objectArea = new net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea(area);
		List<WorldPoint> points = new ArrayList<>(objectArea.getInteractable());
		if (points.isEmpty())
		{
			points.addAll(objectArea.offset(1).toWorldPointList());
			points.removeIf(objectArea::contains);
		}
		return points;
	}

	/** As upstream: walks to a walkable tile beside {@code target} (true when already on one). */
	public static boolean walkNextTo(GameObject target)
	{
		List<WorldPoint> beside = besideObject(target);
		if (beside.isEmpty())
		{
			return false;
		}
		WorldPoint here = Rs2Player.getWorldLocation();
		WorldPoint walkable = beside.stream().filter(net.runelite.client.plugins.microbot.util.tile.Rs2Tile::isWalkable).findFirst().orElse(null);
		if (walkable != null && walkable.equals(here))
		{
			return true;
		}
		return walkTo(walkable != null ? walkable : beside.get(0));
	}

	/** As upstream: one short step to the nearest tile beside {@code target}, for instances (no route planning). */
	public static void walkNextToInstance(GameObject target)
	{
		WorldPoint here = Rs2Player.getWorldLocation();
		List<WorldPoint> beside = besideObject(target);
		if (here == null || beside.isEmpty())
		{
			return;
		}
		WorldPoint nearest = beside.stream().filter(net.runelite.client.plugins.microbot.util.tile.Rs2Tile::isWalkable)
			.min(java.util.Comparator.comparingInt(here::distanceTo))
			.orElseGet(() -> beside.stream().min(java.util.Comparator.comparingInt(here::distanceTo)).orElse(null));
		if (nearest != null && !nearest.equals(here))
		{
			walkFastCanvas(nearest);
		}
	}

	/** As upstream: closes the world map when it is open; true once the close was dispatched. */
	public static boolean closeWorldMap()
	{
		return net.runelite.client.plugins.microbot.util.widget.Rs2Widget.isWidgetVisible(InterfaceID.Worldmap.CLOSE)
			&& MobileActions.widget(InterfaceID.Worldmap.CLOSE, -1, "Close");
	}

	/** As upstream: {@link #walkWithBankedTransports(WorldPoint, boolean)} without forcing the bank. */
	public static boolean walkWithBankedTransports(WorldPoint target)
	{
		return walkWithBankedTransports(target, false);
	}

	/** As upstream, with the default arrival distance. */
	public static boolean walkWithBankedTransports(WorldPoint target, boolean forceBanking)
	{
		return walkWithBankedTransportsAndState(target, DEFAULT_ARRIVAL_TILES, forceBanking) == WalkerState.ARRIVED;
	}

	/** As upstream. */
	public static boolean walkWithBankedTransports(WorldPoint target, int distance, boolean forceBanking)
	{
		return walkWithBankedTransportsAndState(target, distance, forceBanking) == WalkerState.ARRIVED;
	}

	/**
	 * As upstream: when a route using banked transport items (teleports and the like) needs items not
	 * carried, goes to the nearest bank first and withdraws them, then walks. Mobile adaptation, in
	 * place of upstream's route comparison: the bank is used when {@code forceBanking}, when the target
	 * cannot be reached with what is carried, or when the bank is under half as far as the direct walk.
	 * {@link WalkerState#MOVING} while the bank trip is under way (call again).
	 */
	public static WalkerState walkWithBankedTransportsAndState(WorldPoint target, int distance, boolean forceBanking)
	{
		if (target == null)
		{
			return WalkerState.EXIT;
		}
		Map<Integer, Integer> missing = getMissingTransportItemIdsWithQuantities(getTransportsForDestination(target, true));
		if (!missing.isEmpty() && shouldBankFirst(target, forceBanking))
		{
			if (!net.runelite.client.plugins.microbot.util.bank.Rs2Bank.isOpen()
				&& !net.runelite.client.plugins.microbot.util.bank.Rs2Bank.walkToBankAndUseBank())
			{
				return MobileActions.active() ? WalkerState.MOVING : WalkerState.EXIT;
			}
			for (Map.Entry<Integer, Integer> item : missing.entrySet())
			{
				if (net.runelite.client.plugins.microbot.util.bank.Rs2Bank.hasItem(item.getKey()))
				{
					net.runelite.client.plugins.microbot.util.bank.Rs2Bank.withdrawX(item.getKey(), Math.max(1, item.getValue()));
				}
			}
			net.runelite.client.plugins.microbot.util.bank.Rs2Bank.closeBank();
		}
		return walkWithState(target, distance);
	}

	private static boolean shouldBankFirst(WorldPoint target, boolean forceBanking)
	{
		if (forceBanking)
		{
			return true;
		}
		int direct = getTotalTiles(target);
		if (direct == Integer.MAX_VALUE)
		{
			return true;
		}
		net.runelite.client.plugins.microbot.util.bank.enums.BankLocation bank = net.runelite.client.plugins.microbot.util.bank.Rs2Bank.getNearestBank();
		int toBank = bank == null ? Integer.MAX_VALUE : getTotalTiles(bank.getWorldPoint());
		return toBank != Integer.MAX_VALUE && toBank < direct / 2;
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

	/** As upstream: the pathfinder's tiles from {@code start} to {@code target}; see {@link WalkerPathReads}. */
	public static List<WorldPoint> getWalkPath(WorldPoint start, WorldPoint target)
	{
		return WalkerPathReads.getWalkPath(start, target);
	}

	/** As upstream: the pathfinder's tiles from the player to {@code target}. */
	public static List<WorldPoint> getWalkPath(WorldPoint target)
	{
		return WalkerPathReads.getWalkPath(target);
	}

	/** As upstream: the route length in tiles from {@code start} to {@code destination}. */
	public static int getTotalTiles(WorldPoint start, WorldPoint destination)
	{
		return WalkerPathReads.getTotalTiles(start, destination);
	}

	/** As upstream: the route length in tiles from the player to {@code destination}. */
	public static int getTotalTiles(WorldPoint destination)
	{
		return WalkerPathReads.getTotalTiles(destination);
	}

	/** As upstream: the length of {@code path} in tiles up to {@code destination}. */
	public static int getTotalTilesFromPath(List<WorldPoint> path, WorldPoint destination)
	{
		return WalkerPathReads.getTotalTilesFromPath(path, destination);
	}

	/** As upstream: the index of the tile in {@code path} closest to the player. */
	public static int getClosestTileIndex(List<WorldPoint> path)
	{
		return WalkerPathReads.getClosestTileIndex(path);
	}

	/** As upstream: whether the collision map lets the player stand on {@code tile}. */
	public static boolean isWalkableInCollisionMap(WorldPoint tile)
	{
		return WalkerPathReads.isWalkableInCollisionMap(tile);
	}

	/** As upstream: the loaded scene tile at {@code point}, or null when it is not loaded. */
	public static Tile getTile(WorldPoint point)
	{
		return WalkerPathReads.getTile(point);
	}

	/** As upstream: the transports a route to {@code destination} uses that bank planning withdraws for. */
	public static List<Transport> getTransportsForDestination(WorldPoint destination, boolean useBankItems)
	{
		return WalkerPathReads.getTransportsForDestination(destination, useBankItems);
	}

	/** As upstream, with a preferred transport type. */
	public static List<Transport> getTransportsForDestination(WorldPoint destination, boolean useBankItems,
		TransportType prefTransportType)
	{
		return WalkerPathReads.getTransportsForDestination(destination, useBankItems, prefTransportType);
	}

	/** As upstream: item id to quantity to withdraw for {@code transports}. */
	public static Map<Integer, Integer> getMissingTransportItemIdsWithQuantities(List<Transport> transports)
	{
		return WalkerPathReads.getMissingTransportItemIdsWithQuantities(transports);
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

	/**
	 * As upstream: {@link #canReach(WorldPoint)}, or, with {@code useBankedItems}, also reachable by
	 * a route that may use transport items from the remembered bank (planned off the client thread).
	 */
	public static boolean canReach(WorldPoint destination, boolean useBankedItems)
	{
		if (canReach(destination))
		{
			return true;
		}
		if (!useBankedItems || destination == null)
		{
			return false;
		}
		Client client = Microbot.getClient();
		WorldPoint start = Rs2Player.getWorldLocation();
		return client != null && !client.isClientThread() && start != null
			&& MobilePathfinder.find(start, destination, true).isFound();
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
	static final Map<String, Integer> GLIDER_BUTTONS = Map.of(
		"ta quir priw", InterfaceID.Glidermap.GRANDTREE_BUTTON,
		"sindarpos", InterfaceID.Glidermap.WHITEWOLFMOUNTAIN_BUTTON,
		"lemanto andra", InterfaceID.Glidermap.VARROCK_BUTTON,
		"kar-hewo", InterfaceID.Glidermap.ALKHARID_BUTTON,
		"gandius", InterfaceID.Glidermap.KARAMJA_BUTTON,
		"lemantolly undri", InterfaceID.Glidermap.OGREAREA_BUTTON,
		"ookookolly undri", InterfaceID.Glidermap.APEATOLL_BUTTON);

	/** Each hot air balloon destination's button on the balloon map, as upstream's {@code Rs2HotAirBalloon}. */
	static final Map<String, Integer> BALLOON_BUTTONS = Map.of(
		"castle wars", InterfaceID.ZepBalloonMap.BTN_CAST,
		"grand tree", InterfaceID.ZepBalloonMap.BTN_GNO,
		"crafting guild", InterfaceID.ZepBalloonMap.BTN_CRAFT,
		"entrana", InterfaceID.ZepBalloonMap.BTN_ENT,
		"taverley", InterfaceID.ZepBalloonMap.BTN_TAV,
		"varrock", InterfaceID.ZepBalloonMap.BTN_VARR);

	/** Destination maps answered with one button per destination, by the map's root widget. */
	private static final Map<Integer, Map<String, Integer>> DESTINATION_MAPS = Map.of(
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
		TransportType.GNOME_GLIDER, TransportType.HOT_AIR_BALLOON, TransportType.QUETZAL, TransportType.MAGIC_MUSHTREE,
		TransportType.CANOE, TransportType.POH, TransportType.TELEPORTATION_MINIGAME, TransportType.WILDERNESS_OBELISK);

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

	/** The station's options in the order a canoe trip goes through them, as upstream's {@code handleCanoe}. */
	static final List<String> CANOE_STAGES = List.of("Chop-down", "Shape-Canoe", "Float Canoe", "Paddle Canoe");
	/** A canoe lands at the destination station's jetty (upstream allows twice its usual offset). */
	private static final int CANOE_ARRIVAL_TILES = 10;
	/** Chopping, shaping, floating and the paddle (with its cutscene) together. */
	private static final long CANOE_MILLIS = 90_000;

	/** A home teleport channels for about ten seconds before the landing. */
	private static final long SPELL_TELEPORT_MILLIS = 30_000;
	/** Activating an obelisk, stepping into its square and the teleport itself (a few seconds in game). */
	private static final long OBELISK_MILLIS = 25_000;
	/** Re-activate when the obelisk has not changed this long after a click (the click did not land). */
	private static final long OBELISK_RETRY_MILLIS = 6_000;

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
		// The house is an instance, but the pathfinder knows it (it starts a search inside it at the house tile).
		if (pathfinder && (!scene.instance || net.runelite.client.plugins.microbot.util.poh.PohPresence.isInHouse()))
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
			// An obelisk already stands in the Wilderness, so its landing does not take the player into it.
			if (transport.getType() != TransportType.WILDERNESS_OBELISK && entersWilderness(transport.getDestination(), destination))
			{
				LOG.info("Mobile walk: route takes {} into the Wilderness; using the same-plane planner", transport);
				return null;
			}
			boolean intoHouse = transport.isIntoHouse();
			if (transport.getType() == TransportType.POH)
			{
				if (transport.getPohDevice() == null || transport.getPohOption() == null || transport.getPohOption().isBlank())
				{
					LOG.info("Mobile walk: route needs house step {} without a device or option; using the same-plane planner", transport);
					return null;
				}
				ObjectTransports.Step step = new ObjectTransports.Step(transport.getOrigin(), transport.getDestination(),
					transport.getObjectId(), transport.getAction(), transport.getName(), TELEPORT_ARRIVAL_TILES, false,
					transport.getPohOption().trim(), false, Set.of(), false,
					transport.getPohDevice() == net.runelite.client.plugins.microbot.shortestpath.PohDevice.FAIRY_RING, false, intoHouse,
					transport.getPohDevice());
				return new WalkLeg(transport.getOrigin(), 1, step, new ArrayList<>(tiles.subList(0, i + 1)), true);
			}
			if (!EXECUTABLE_TRANSPORTS.contains(transport.getType())
				|| (transport.getAction() == null && transport.getType() != TransportType.TELEPORTATION_ITEM
					&& transport.getType() != TransportType.TELEPORTATION_SPELL && transport.getType() != TransportType.FAIRY_RING
					&& transport.getType() != TransportType.POH && transport.getType() != TransportType.TELEPORTATION_MINIGAME))
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
				return new WalkLeg(from, 1, intoHouse ? step.toHouse() : step, new ArrayList<>(tiles.subList(0, i + 1)), true);
			}
			if (transport.getType() == TransportType.TELEPORTATION_MINIGAME)
			{
				if (disableTeleports || MinigameTeleports.isRefused(transport.getDisplayInfo(), transport.getDestination()))
				{
					LOG.info("Mobile walk: route needs minigame teleport {}; using the same-plane planner", transport);
					return null;
				}
				// Like a spell, a minigame teleport is used where the route reaches it.
				ObjectTransports.Step step = ObjectTransports.Step.minigame(tiles.get(i), transport.getDestination(),
					transport.getDisplayInfo().trim(), TELEPORT_ARRIVAL_TILES);
				return new WalkLeg(tiles.get(i), 1, intoHouse ? step.toHouse() : step, new ArrayList<>(tiles.subList(0, i + 1)), true);
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
				return new WalkLeg(from, 1, intoHouse ? step.toHouse() : step, new ArrayList<>(tiles.subList(0, i + 1)), true);
			}
			if (transport.getType() == TransportType.CANOE)
			{
				if (transport.getDisplayInfo() == null || transport.getDisplayInfo().isBlank() || transport.getOrigin() == null)
				{
					LOG.info("Mobile walk: route needs canoe {} without a destination; using the same-plane planner", transport);
					return null;
				}
				ObjectTransports.Step step = new ObjectTransports.Step(transport.getOrigin(), transport.getDestination(),
					transport.getObjectId(), transport.getAction(), transport.getName(), CANOE_ARRIVAL_TILES, false,
					transport.getDisplayInfo().trim(), false, Set.of(), false, false, true);
				return new WalkLeg(transport.getOrigin(), 1, intoHouse ? step.toHouse() : step, new ArrayList<>(tiles.subList(0, i + 1)), true);
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
				return new WalkLeg(transport.getOrigin(), 1, intoHouse ? step.toHouse() : step, new ArrayList<>(tiles.subList(0, i + 1)), true);
			}
			if (transport.getType() == TransportType.WILDERNESS_OBELISK)
			{
				ObjectTransports.Step step = ObjectTransports.Step.obelisk(transport.getOrigin(), transport.getDestination(),
					transport.getObjectId(), transport.getAction(), transport.getName());
				return new WalkLeg(transport.getOrigin(), 1, step, new ArrayList<>(tiles.subList(0, i + 1)), true);
			}
			boolean travel = TRAVEL_TRANSPORTS.contains(transport.getType());
			int arrival = travel ? TRAVEL_ARRIVAL_TILES : TELEPORT_OBJECTS.contains(transport.getType()) ? TELEPORT_ARRIVAL_TILES : 1;
			ObjectTransports.Step step = new ObjectTransports.Step(transport.getOrigin(), transport.getDestination(),
				transport.getObjectId(), transport.getAction(), transport.getName(), arrival, travel, transport.getDisplayInfo());
			return new WalkLeg(transport.getOrigin(), 1, intoHouse ? step.toHouse() : step, new ArrayList<>(tiles.subList(0, i + 1)), true);
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

	/**
	 * A plain staircase, ladder or door step whose origin is a short open walk away: it is clicked from here and the
	 * game walks the player onto it, instead of stopping beside it first.
	 */
	private static boolean stairsInReach(MobileRoutePlanner.Scene scene, ObjectTransports.Step stairs)
	{
		if (stairs.travel || stairs.item || stairs.spell || stairs.fairyRing || stairs.canoe || stairs.intoHouse
			|| stairs.house != null || stairs.minigame || stairs.obelisk
			|| scene.player.getPlane() != stairs.origin.getPlane()
			|| scene.player.distanceTo(stairs.origin) > STAIRS_EARLY_STEPS || !scene.contains(stairs.origin))
		{
			return false;
		}
		List<WorldPoint> route = MobileRoutePlanner.route(scene.player, stairs.origin, 1, scene::canStep);
		return !route.isEmpty() && route.size() - 1 <= STAIRS_EARLY_STEPS;
	}

	/** Dispatch exact live staircase actions, then require observed plane and landing-location changes. */
	private static boolean useStairs(ObjectTransports.Step stairs, long generation, long walkDeadline) throws InterruptedException
	{
		long deadline = Math.min(walkDeadline, System.currentTimeMillis() + 20_000);
		long lastAttempt = 0;
		boolean dispatched = false;
		WorldPoint lastPosition = null;
		long lastMoveAt = System.currentTimeMillis();
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
			if (dispatched && acceptWarning())
			{
				continue;
			}
			if (scene.player.getPlane() != stairs.origin.getPlane())
			{
				lastStatus = "unexpected staircase plane or landing";
				return false;
			}
			if (!scene.player.equals(lastPosition))
			{
				lastPosition = scene.player;
				lastMoveAt = System.currentTimeMillis();
			}
			int away = scene.player.distanceTo(stairs.origin);
			if (dispatched && away > 2 && System.currentTimeMillis() - lastMoveAt >= 1_800
				&& System.currentTimeMillis() - lastAttempt >= 1_800)
			{
				// The early click did not move the player toward the stairs: walk there, then click again up close.
				lastAttempt = System.currentTimeMillis();
				if (!MobileActions.walkMinimap(stairs.origin))
				{
					MobileActions.walk(stairs.origin);
				}
			}
			// The first click may come from a short walk away (see stairsInReach); retries only from beside them.
			else if (away <= (dispatched ? 2 : STAIRS_EARLY_STEPS) && System.currentTimeMillis() - lastAttempt >= 1200)
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

	/** The "are you sure" warnings some entrances raise (desert heat, Stronghold of Security ladders). */
	private static final int[] WARNING_GROUPS = {InterfaceID.CWS_WARNING_10, InterfaceID.CWS_WARNING_4};
	/** The warning's proceed button. */
	private static final int WARNING_PROCEED_CHILD = 17;

	/**
	 * As upstream 2.6.47 ({@code acceptCwsWarning}), inside the transport so it does not time out first: presses
	 * proceed on a visible warning. Mobile: the "don't ask again" box is left as the player set it.
	 */
	static boolean acceptWarning()
	{
		for (int group : WARNING_GROUPS)
		{
			int proceed = group << 16 | WARNING_PROCEED_CHILD;
			if (net.runelite.client.plugins.microbot.util.widget.Rs2Widget.isWidgetVisible(group, WARNING_PROCEED_CHILD)
				&& Global.sleepUntil(() -> MobileActions.widget(proceed, -1, ""), 2_000))
			{
				Global.sleepUntil(() -> !net.runelite.client.plugins.microbot.util.widget.Rs2Widget.isWidgetVisible(group, WARNING_PROCEED_CHILD), 3_000);
				return true;
			}
		}
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
			Map<String, Integer> buttons = visibleDestinationMap();
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

	/** Teleports with the grouping tab ({@link MinigameTeleports}), then waits to observe the landing. */
	/**
	 * A Wilderness obelisk, as upstream's {@code handleWildernessObelisk}: "Activate" it, then stand on
	 * the square's tile ({@code step.origin}) until it teleports the player. Clicking the obelisk walks
	 * the player beside it, which can be outside the square. The obelisk changes ID while active, so the
	 * catalogued ID disappearing is the observed activation. The landing is random (any other obelisk);
	 * the walk replans from wherever it lands.
	 */
	private static boolean useObelisk(ObjectTransports.Step step, long generation, long walkDeadline) throws InterruptedException
	{
		long deadline = Math.min(walkDeadline, System.currentTimeMillis() + OBELISK_MILLIS);
		lastStatus = "Activating obelisk at " + step.origin;
		long clickedAt = 0;
		long lastStep = 0;
		boolean activated = false;
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
			if (step.observedArrival(scene.player))
			{
				return true;
			}
			long now = System.currentTimeMillis();
			Rs2TileObjectModel obelisk = idleObelisk(step);
			if (clickedAt > 0 && obelisk == null)
			{
				activated = true;
			}
			if (!activated && (clickedAt == 0 || now - clickedAt >= OBELISK_RETRY_MILLIS))
			{
				if (obelisk == null)
				{
					if (++missingLookups >= 3)
					{
						lastStatus = STAIRS_UNAVAILABLE;
						return false;
					}
				}
				else if (MobileActions.objectAt(obelisk.getId(), obelisk.getWorldLocation(), step.action))
				{
					clickedAt = now;
				}
			}
			else if (activated && !scene.player.equals(step.origin) && now - lastStep >= 1200)
			{
				lastStep = now;
				lastStatus = "Stepping into the obelisk square at " + step.origin;
				MobileActions.walk(step.origin);
			}
			long remaining = deadline - System.currentTimeMillis();
			if (remaining > 0) { Thread.sleep(Math.min(300, remaining)); }
		}
		lastStatus = !MobileActions.active() || GENERATION.get() != generation
			? "obelisk cancelled" : activated ? "obelisk teleport was not observed" : STAIRS_UNAVAILABLE;
		return false;
	}

	/** The catalogued (not yet active) obelisk at a corner of {@code step}'s square, or null. */
	private static Rs2TileObjectModel idleObelisk(ObjectTransports.Step step)
	{
		return MobileActions.read(() ->
			MicrobotReadServices.require().getRs2TileObjectCache().query()
				.where(object -> object.getId() == step.objectId
					&& object.getWorldLocation().getPlane() == step.origin.getPlane()
					&& object.getWorldLocation().distanceTo(step.origin) <= 3).nearest(), null);
	}

	private static boolean useMinigameTeleport(ObjectTransports.Step step, long generation, long walkDeadline) throws InterruptedException
	{
		long deadline = Math.min(walkDeadline, System.currentTimeMillis() + SPELL_TELEPORT_MILLIS);
		lastStatus = "Minigame teleport to " + step.destinationText;
		if (!MinigameTeleports.teleport(step.destinationText, step.arrival))
		{
			lastStatus = "cannot use the minigame teleport to " + step.destinationText;
			return false;
		}
		while (System.currentTimeMillis() < deadline && GENERATION.get() == generation
			&& MobileActions.active() && !Thread.currentThread().isInterrupted())
		{
			MobileRoutePlanner.Scene scene = awaitSnapshot(generation, deadline);
			if (scene == null)
			{
				lastStatus = "no collision snapshot, loading timed out or game state changed";
				return false;
			}
			if (step.observedArrival(scene.player))
			{
				return true;
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
			boolean inHouse = step.house != null;
			if (!dispatched && (inHouse || (scene.player.getPlane() == step.origin.getPlane() && scene.player.distanceTo(step.origin) <= 3))
				&& System.currentTimeMillis() - lastAttempt >= 6_000)
			{
				lastAttempt = System.currentTimeMillis();
				Object[] ring = MobileActions.read(() ->
				{
					// The house ring is found as detected (id or name); an outside ring stands on its catalogue tile.
					Rs2TileObjectModel model = MicrobotReadServices.require().getRs2TileObjectCache().query()
						.where(candidate -> (inHouse ? isHouseDevice(step, candidate) : step.origin.equals(candidate.getWorldLocation()))
							&& candidate.getObjectComposition() != null)
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

	/** How long a way out of the house may take, menus and the teleport included. */
	private static final long HOUSE_DEVICE_MILLIS = 30_000;
	/** Portal nexus entries show their shortcut key as {@code <col=ffffff>K</col>}; upstream types it. */
	private static final java.util.regex.Pattern NEXUS_KEY = java.util.regex.Pattern.compile("<col=ffffff>(.*?)</col>");

	/**
	 * Leaves the house through a detected device, as upstream's {@code PohTeleports}: portals and
	 * mounted items by their own option; the portal nexus by the destination entry's shortcut key
	 * (entries can be scrolled out of view); the jewellery box by its entry (refusing one that is
	 * struck through, not unlocked); Xeric's talisman and the digsite pendant by their menu; the
	 * spirit tree by "Travel" and its menu. Never confirms a Wilderness warning. Requires an observed
	 * landing. The house fairy ring goes through {@link #useFairyRing}.
	 */
	private static boolean useHouseDevice(ObjectTransports.Step step, long generation, long walkDeadline) throws InterruptedException
	{
		long deadline = Math.min(walkDeadline, System.currentTimeMillis() + HOUSE_DEVICE_MILLIS);
		long lastAttempt = 0;
		boolean dispatched = false;
		String option = step.destinationText;
		lastStatus = "House " + step.house + ": " + option;
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
			if (net.runelite.client.plugins.microbot.util.widget.Rs2Widget.isWildernessInterfaceOpen())
			{
				lastStatus = "house teleport leads into the Wilderness; not confirmed";
				return false;
			}
			if (widgetVisible(InterfaceID.TelenexusTeleport.UNIVERSE))
			{
				if (!chooseNexusDestination(option))
				{
					lastStatus = "portal nexus does not offer " + option;
					return false;
				}
				dispatched = true;
				Thread.sleep(600);
				continue;
			}
			if (widgetVisible(InterfaceID.PohJewelleryBox.UNIVERSE))
			{
				String locked = jewelleryBoxEntryState(option);
				if (!"open".equals(locked))
				{
					lastStatus = "jewellery box: " + option + (locked == null ? " is not listed" : " is not unlocked");
					return false;
				}
				if (!chooseTextMapDestination(InterfaceID.PohJewelleryBox.UNIVERSE, option))
				{
					lastStatus = "jewellery box does not offer " + option;
					return false;
				}
				dispatched = true;
				Thread.sleep(600);
				continue;
			}
			if (destinationMenuVisible())
			{
				if (!chooseMenuDestination(option))
				{
					lastStatus = "destination menu does not offer " + option;
					return false;
				}
				dispatched = true;
				Thread.sleep(600);
				continue;
			}
			if (Rs2Dialogue.hasSelectAnOption())
			{
				if (!Rs2Dialogue.clickOption(menuDestinationName(option)))
				{
					lastStatus = "house teleport menu does not offer " + option;
					return false;
				}
				dispatched = true;
				Thread.sleep(600);
				continue;
			}
			if (!dispatched && System.currentTimeMillis() - lastAttempt >= 6_000)
			{
				lastAttempt = System.currentTimeMillis();
				Object[] device = MobileActions.read(() ->
				{
					Rs2TileObjectModel model = MicrobotReadServices.require().getRs2TileObjectCache().query()
						.where(candidate -> isHouseDevice(step, candidate) && candidate.getObjectComposition() != null)
						.nearest();
					String use = model == null ? null : houseDeviceOption(step, model.getObjectComposition().getActions());
					return use == null ? null : new Object[]{model.getId(), model.getWorldLocation(), use};
				}, null);
				if (device == null)
				{
					lastStatus = "no house " + step.house + " with a usable option";
					return false;
				}
				String use = (String) device[2];
				if (Global.sleepUntil(() -> MobileActions.objectAt((int) device[0], (WorldPoint) device[1], use), 2_000)
					&& !opensAMenu(step.house, use))
				{
					dispatched = true;
				}
			}
			long remaining = deadline - System.currentTimeMillis();
			if (remaining > 0) { Thread.sleep(Math.min(300, remaining)); }
		}
		lastStatus = !MobileActions.active() || GENERATION.get() != generation
			? "house teleport cancelled" : dispatched ? "house teleport landing was not observed" : "house device was not used";
		return false;
	}

	/** A live object is this step's house device: its detected id, else its detected name. */
	static boolean isHouseDevice(ObjectTransports.Step step, Rs2TileObjectModel candidate)
	{
		return candidate.getId() == step.objectId
			|| (!step.target.isEmpty() && step.target.equalsIgnoreCase(candidate.getName()));
	}

	/**
	 * The device option to use: the step's own action when the device offers it; for the spirit
	 * tree "Travel"; for the nexus and jewellery box their menu option; for Xeric's and the digsite
	 * their menu when the direct option is missing. Null when the device offers none of these.
	 */
	static String houseDeviceOption(ObjectTransports.Step step, String[] actions)
	{
		if (actions == null)
		{
			return null;
		}
		List<String> wanted = new ArrayList<>();
		if (step.action != null && !step.action.isBlank())
		{
			wanted.add(step.action);
		}
		if (step.house == net.runelite.client.plugins.microbot.shortestpath.PohDevice.SPIRIT_TREE)
		{
			wanted.add("Travel");
		}
		wanted.add("Teleport Menu");
		wanted.add("Teleport menu");
		for (String candidate : wanted)
		{
			for (String action : actions)
			{
				if (action != null && action.equalsIgnoreCase(candidate))
				{
					return action;
				}
			}
		}
		return null;
	}

	/** Whether this device option opens a menu or interface (the teleport follows a later choice). */
	static boolean opensAMenu(net.runelite.client.plugins.microbot.shortestpath.PohDevice device, String action)
	{
		return action.equalsIgnoreCase("Teleport Menu") || device == net.runelite.client.plugins.microbot.shortestpath.PohDevice.SPIRIT_TREE;
	}

	/** As upstream: types the nexus entry's shortcut key, or taps the entry when it shows none. */
	private static boolean chooseNexusDestination(String destination)
	{
		String needle = Text.removeTags(destination).trim().toLowerCase(java.util.Locale.ROOT);
		String key = MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget root = Microbot.getClient().getWidget(InterfaceID.TelenexusTeleport.UNIVERSE);
			net.runelite.api.widgets.Widget entry = root == null || needle.isEmpty() ? null : findCharterEntry(root, needle);
			return entry == null || entry.getText() == null ? null : nexusKey(entry.getText());
		}, null);
		if (key != null && key.length() == 1)
		{
			return Global.sleepUntil(() -> MobileActions.type(Character.toLowerCase(key.charAt(0))), 2_000);
		}
		return chooseTextMapDestination(InterfaceID.TelenexusTeleport.UNIVERSE, destination);
	}

	/** Picks {@code destination} in the open portal nexus (its shortcut key, else its entry); for {@code PohTeleports}. */
	public static boolean selectNexusDestination(String destination)
	{
		return destination != null && chooseNexusDestination(destination);
	}

	/** Picks an unlocked (not struck-through) jewellery box entry in the open box; for {@code PohTeleports}. */
	public static boolean selectJewelleryBoxDestination(String destination)
	{
		return destination != null && "open".equals(jewelleryBoxEntryState(destination))
			&& chooseTextMapDestination(InterfaceID.PohJewelleryBox.UNIVERSE, destination);
	}

	/** The shortcut key a nexus entry shows ("<col=ffffff>K</col> : Lumbridge" gives "K"), or null. */
	static String nexusKey(String entryText)
	{
		java.util.regex.Matcher matcher = NEXUS_KEY.matcher(entryText);
		return matcher.find() ? matcher.group(1).trim() : null;
	}

	/** "open", "locked" (struck through, as upstream checks) or null when the box does not list it. */
	private static String jewelleryBoxEntryState(String destination)
	{
		String needle = Text.removeTags(destination).trim().toLowerCase(java.util.Locale.ROOT);
		return MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget root = Microbot.getClient().getWidget(InterfaceID.PohJewelleryBox.UNIVERSE);
			net.runelite.api.widgets.Widget entry = root == null || needle.isEmpty() ? null : findCharterEntry(root, needle);
			if (entry == null)
			{
				return null;
			}
			return entry.getText() != null && entry.getText().contains("<str>") ? "locked" : "open";
		}, null);
	}

	/**
	 * Takes a canoe as upstream does, one stage per visit to the station: its live option is
	 * Chop-down, then Shape-Canoe (the best canoe the Woodcutting level allows, on the shaping
	 * interface), then Float Canoe, then Paddle Canoe (the destination on the river map). Requires an
	 * observed landing.
	 */
	private static boolean useCanoe(ObjectTransports.Step step, long generation, long walkDeadline) throws InterruptedException
	{
		long deadline = Math.min(walkDeadline, System.currentTimeMillis() + CANOE_MILLIS);
		long lastAttempt = 0;
		boolean paddled = false;
		lastStatus = "Canoe to " + step.destinationText;
		while (System.currentTimeMillis() < deadline && GENERATION.get() == generation
			&& MobileActions.active() && !Thread.currentThread().isInterrupted())
		{
			MobileRoutePlanner.Scene scene = awaitSnapshot(generation, deadline);
			if (scene == null)
			{
				lastStatus = "no collision snapshot, loading timed out or game state changed";
				return false;
			}
			if (paddled && step.observedArrival(scene.player))
			{
				return true;
			}
			int map = visibleCanoeMap();
			if (map >= 0)
			{
				if (!chooseTextMapDestination(map, "Travel to " + step.destinationText))
				{
					lastStatus = "canoe map does not offer " + step.destinationText;
					return false;
				}
				paddled = true;
				Thread.sleep(600);
				continue;
			}
			if (widgetVisible(InterfaceID.Canoeing.QUESTION))
			{
				String canoe = canoeFor(Rs2Player.getRealSkillLevel(net.runelite.api.Skill.WOODCUTTING));
				if (canoe == null || !chooseTextMapDestination(InterfaceID.Canoeing.UNIVERSE, "Make " + canoe))
				{
					lastStatus = "cannot shape a canoe at this Woodcutting level";
					return false;
				}
				Thread.sleep(600);
				continue;
			}
			if (!paddled && !Rs2Player.isAnimating() && !Rs2Player.isMoving()
				&& scene.player.getPlane() == step.origin.getPlane() && scene.player.distanceTo(step.origin) <= 3
				&& System.currentTimeMillis() - lastAttempt >= 2_000)
			{
				lastAttempt = System.currentTimeMillis();
				Object[] station = MobileActions.read(() ->
				{
					Rs2TileObjectModel model = MicrobotReadServices.require().getRs2TileObjectCache().query()
						.where(candidate -> candidate.getWorldLocation().getPlane() == step.origin.getPlane()
							&& candidate.getWorldLocation().distanceTo(step.origin) <= 6
							&& (candidate.getId() == step.objectId || "Canoe Station".equalsIgnoreCase(candidate.getName()))
							&& candidate.getObjectComposition() != null)
						.nearest();
					String stage = model == null ? null : canoeStage(model.getObjectComposition().getActions());
					return stage == null ? null : new Object[]{model.getId(), model.getWorldLocation(), stage};
				}, null);
				if (station == null)
				{
					lastStatus = "no canoe station with a canoe option near " + step.origin;
					return false;
				}
				Global.sleepUntil(() -> MobileActions.objectAt((int) station[0], (WorldPoint) station[1], (String) station[2]), 2_000);
			}
			long remaining = deadline - System.currentTimeMillis();
			if (remaining > 0) { Thread.sleep(Math.min(300, remaining)); }
		}
		lastStatus = !MobileActions.active() || GENERATION.get() != generation
			? "canoe cancelled" : paddled ? "canoe landing was not observed" : "canoe was not reached";
		return false;
	}

	/** The station's current canoe option, the first of {@link #CANOE_STAGES} it offers; null when none. */
	static String canoeStage(String[] actions)
	{
		if (actions == null)
		{
			return null;
		}
		for (String stage : CANOE_STAGES)
		{
			for (String action : actions)
			{
				if (stage.equalsIgnoreCase(action))
				{
					return action;
				}
			}
		}
		return null;
	}

	/** As upstream: the best canoe for a Woodcutting level (waka 57, stable dugout 42, dugout 27, log 12), or null. */
	static String canoeFor(int woodcuttingLevel)
	{
		return woodcuttingLevel >= 57 ? "Waka canoe" : woodcuttingLevel >= 42 ? "Stable dugout canoe"
			: woodcuttingLevel >= 27 ? "Dugout canoe" : woodcuttingLevel >= 12 ? "Log canoe" : null;
	}

	/** The destination list of whichever river's canoe map is showing, or -1. */
	private static int visibleCanoeMap()
	{
		if (widgetVisible(InterfaceID.CanoeMapLum.MAIN_MAP))
		{
			return InterfaceID.CanoeMapLum.DESTINATIONS;
		}
		return widgetVisible(InterfaceID.CanoeMapDougne.MAIN_MAP) ? InterfaceID.CanoeMapDougne.DESTINATIONS : -1;
	}

	private static boolean widgetVisible(int widgetId)
	{
		return MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget widget = Microbot.getClient().getWidget(widgetId);
			return widget != null && !widget.isHidden();
		}, false);
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
	static Integer destinationButton(Map<String, Integer> buttons, String destination)
	{
		return destination == null ? null : buttons.get(destination.trim().toLowerCase(java.util.Locale.ROOT));
	}

	/** The buttons of whichever destination map (glider or balloon) is showing, or null when none is. */
	private static Map<String, Integer> visibleDestinationMap()
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
				// Mobile: the first tile inside the radius can be behind a wall; then walk to beside the goal.
				if (radius > 1 && walledOff(scene, local.get(local.size() - 1), destination))
				{
					List<WorldPoint> closer = MobileRoutePlanner.route(scene.player, destination, 1, scene::canStep);
					if (!closer.isEmpty())
					{
						return closer;
					}
				}
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

	/**
	 * Mobile: {@link #arrived} that also needs the walk to the goal not to go round a wall. Within the radius in a
	 * straight line is not enough: behind Draynor bank's south wall the bank is 7 tiles away but 20 steps.
	 */
	private static boolean arrivedOnFoot(MobileRoutePlanner.Scene scene, WorldPoint goal, int radius)
	{
		return arrived(scene.player, goal, radius) && (radius <= 1 || !walledOff(scene, scene.player, goal));
	}

	/** Whether the in-scene walk from {@code from} to within a tile of {@code goal} is a {@link #isWallDetour wall detour}. */
	static boolean walledOff(MobileRoutePlanner.Scene scene, WorldPoint from, WorldPoint goal)
	{
		if (scene == null || !scene.contains(from) || !scene.contains(goal))
		{
			return false;
		}
		List<WorldPoint> route = MobileRoutePlanner.route(from, goal, 1, scene::canStep);
		return !route.isEmpty() && isWallDetour(route.size() - 1, Math.max(0, from.distanceTo(goal) - 1));
	}

	/**
	 * Mobile: whether a walk of {@code steps} where the straight line is {@code straightTiles} means a wall is in the
	 * way: more than {@value #WALL_DETOUR_SLACK_STEPS} steps longer and more than twice the straight line. Walking
	 * round bank booths is not a wall (Lumbridge top floor: 10 steps for 6); Draynor bank's south wall is (20 for 7).
	 */
	public static boolean isWallDetour(int steps, int straightTiles)
	{
		return steps - straightTiles > WALL_DETOUR_SLACK_STEPS && steps > 2 * straightTiles;
	}

	static final int WALL_DETOUR_SLACK_STEPS = 4;

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

	/**
	 * The action route door recovery uses on an object, by upstream's door priority
	 * ({@link Rs2DoorClassifier}): never Close/Shut, never one that carries the player across
	 * (stiles, which are transports), and never Pay-toll, Pick-lock, Force or Exit (they spend coins,
	 * train Thieving, or leave somewhere; the route plans those as transports when it means to).
	 */
	private static final List<String> UNSAFE_DOOR_ACTIONS = List.of("pay-toll", "pick-lock", "force", "exit");

	static String routeDoorAction(String[] actions)
	{
		if (actions == null)
		{
			return null;
		}
		return Arrays.stream(actions).filter(java.util.Objects::nonNull)
			.filter(action -> !Rs2DoorClassifier.isDoorCloseOrShutAction(action) && !Rs2DoorClassifier.isMovesYouAction(action))
			.filter(action -> UNSAFE_DOOR_ACTIONS.stream().noneMatch(action.toLowerCase(java.util.Locale.ROOT)::startsWith))
			.filter(action -> Rs2DoorClassifier.doorActionPriorityIndex(action) < Integer.MAX_VALUE)
			.min(java.util.Comparator.comparingInt(Rs2DoorClassifier::doorActionPriorityIndex)).orElse(null);
	}

	/**
	 * As upstream's {@code processWalk}: before walking to a Draynor Manor basement tile, enter the
	 * basement and set its levers ({@link DraynorBasementSolver}); the puzzle doors are varbit-gated
	 * transports. False when the target is in the basement but the player could not get in.
	 */
	private static boolean prepareDraynorBasement(WorldPoint destination)
	{
		if (!DraynorBasementSolver.isBasementTarget(destination))
		{
			return true;
		}
		DraynorBasementSolver.solveIfNeeded(destination);
		return MobileActions.active() && !Thread.currentThread().isInterrupted()
			&& DraynorBasementSolver.isBasementTarget(Rs2Player.getWorldLocation());
	}

	private static boolean tryOpenDoor(WorldPoint routeTile)
	{
		// The door is found on the client thread; the click and any Stronghold question are handled off it.
		Object[] door = MobileActions.read(() ->
		{
			Client client = Microbot.getClient();
			Player player = client == null ? null : client.getLocalPlayer();
			WorldView view = player == null ? null : player.getWorldView();
			if (view == null || view.getScene() == null || player.getWorldLocation() == null)
			{
				return null;
			}
			Tile[][][] tiles = view.getScene().getTiles();
			int plane = player.getWorldLocation().getPlane();
			if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null)
			{
				return null;
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
						String action = routeDoorAction(composition.getActions());
						if (action != null && (StrongholdDoors.isStrongholdDoor(name)
							|| Rs2DoorClassifier.isRouteDoorObject(object == tile.getWallObject(), name, action)))
						{
							return new Object[]{object.getId(), object.getWorldLocation(), name, action};
						}
					}
				}
			}
			return null;
		}, null);
		if (door == null || !MobileActions.objectAt((int) door[0], (WorldPoint) door[1], (String) door[3]))
		{
			return false;
		}
		// As upstream: a Stronghold of Security door asks its question once; answer it so the door opens.
		if (StrongholdDoors.isStrongholdDoor((String) door[2]) && StrongholdDoors.isInStronghold((WorldPoint) door[1])
			&& !StrongholdDoors.answerIfAsked())
		{
			LOG.info("Mobile walk: unknown Stronghold of Security question at {}", door[1]);
		}
		return true;
	}

	// ---- Walker batch (user request: 13 walker tasks), upstream Rs2Walker 2.6.38 ----

	/** As upstream: {@link #canReach(WorldPoint, int, int, int, int)} with a 3x3 route-end area. */
	public static boolean canReach(WorldPoint worldPoint, int sizeX, int sizeY)
	{
		return canReach(worldPoint, sizeX, sizeY, 3, 3);
	}

	/** As upstream: an object of {@code sizeX} by {@code sizeY} at {@code worldPoint} can be reached on foot. */
	public static boolean canReach(WorldPoint worldPoint, int sizeX, int sizeY, int pathSizeX, int pathSizeY)
	{
		return canReach(worldPoint, sizeX, sizeY, pathSizeX, pathSizeY, false);
	}

	/** As upstream: {@link #canReach(WorldPoint, int, int, int, int, boolean)} with a 2x2 route-end area. */
	public static boolean canReach(WorldPoint worldPoint, int sizeX, int sizeY, boolean useBankedItems)
	{
		return canReach(worldPoint, sizeX, sizeY, 2, 2, useBankedItems);
	}

	/** As upstream: the argument order with {@code useBankedItems} before the route-end area. */
	public static boolean canReach(WorldPoint worldPoint, int sizeX, int sizeY, boolean useBankedItems, int pathSizeX, int pathSizeY)
	{
		return canReach(worldPoint, sizeX, sizeY, pathSizeX, pathSizeY, useBankedItems);
	}

	/** As upstream: whether the planned route's end touches the object footprint; see {@link WalkerPathReads}. */
	public static boolean canReach(WorldPoint worldPoint, int sizeX, int sizeY, int pathSizeX, int pathSizeY, boolean useBankedItems)
	{
		return WalkerPathReads.canReach(worldPoint, sizeX, sizeY, pathSizeX, pathSizeY, useBankedItems);
	}

	/** As upstream: the index of the nearest target reachable from the player (tolerance 2, no bank items), or -1. */
	public static int findNearestAccessibleTarget(List<WorldPoint> targets)
	{
		return findNearestAccessibleTarget(Rs2Player.getWorldLocation(), targets, false, 2);
	}

	/** As upstream: {@link #findNearestAccessibleTarget(List)}, optionally planning with the bank's items. */
	public static int findNearestAccessibleTarget(List<WorldPoint> targets, boolean useBankItems)
	{
		return findNearestAccessibleTarget(Rs2Player.getWorldLocation(), targets, useBankItems, 2);
	}

	/** As upstream: the index of the nearest target reachable from {@code startPoint} (tolerance 2), or -1. */
	public static int findNearestAccessibleTarget(WorldPoint startPoint, List<WorldPoint> targets)
	{
		return findNearestAccessibleTarget(startPoint, targets, false, 2);
	}

	/** As upstream: one multi-target search; see {@link WalkerPathReads#findNearestAccessibleTarget}. */
	public static int findNearestAccessibleTarget(WorldPoint startPoint, List<WorldPoint> targets, boolean useBankItems, int tolerance)
	{
		return WalkerPathReads.findNearestAccessibleTarget(startPoint, targets, useBankItems, tolerance);
	}

	/** As upstream: the candidate a route from {@code start} reaches, or null; see {@link WalkerPathReads}. */
	public static WorldPoint nearestReachable(WorldPoint start, java.util.Collection<WorldPoint> candidates)
	{
		return WalkerPathReads.nearestReachable(start, candidates);
	}

	/** As upstream: the planned route's tile count between two points. */
	public static int getDistanceBetween(WorldPoint startpoint, WorldPoint endpoint)
	{
		return WalkerPathReads.getDistanceBetween(startpoint, endpoint);
	}

	/** As upstream: the player is within {@code distance} (exclusive) of region tile {@code (regionX, regionY)}. */
	public static boolean isCloseToRegion(int distance, int regionX, int regionY)
	{
		int to = distanceToRegion(regionX, regionY);
		return to != Integer.MAX_VALUE && to < distance;
	}

	/** As upstream: the distance to region tile {@code (regionX, regionY)} of the player's region and plane. */
	public static int distanceToRegion(int regionX, int regionY)
	{
		WorldPoint player = Rs2Player.getWorldLocation();
		if (player == null)
		{
			return Integer.MAX_VALUE;
		}
		return WorldPoint.fromRegion(player.getRegionID(), regionX, regionY, player.getPlane()).distanceTo(player);
	}

	/** As upstream: the player is on one of the running walk's last 10 route tiles (within 2); false with no walk. */
	public static boolean isNear()
	{
		return isNear(activeRoute, Rs2Player.getWorldLocation());
	}

	static boolean isNear(List<WorldPoint> path, WorldPoint player)
	{
		if (path == null || path.isEmpty() || player == null)
		{
			return false;
		}
		int index = -1;
		for (int i = 0; i < path.size(); i++)
		{
			WorldPoint point = path.get(i);
			if (point.getPlane() == player.getPlane() && point.distanceTo2D(player) < 3)
			{
				index = i;
				break;
			}
		}
		return index >= Math.max(path.size() - 10, 0);
	}

	/** As upstream: the player stands exactly on {@code target}. */
	public static boolean isNear(WorldPoint target)
	{
		WorldPoint player = Rs2Player.getWorldLocation();
		return player != null && player.equals(target);
	}

	/**
	 * As upstream: true with no walk running, when the player has not moved since the last call (upstream's
	 * side effect: the position is remembered), in the player-owned house, or when a route tile is reachable
	 * within {@code config.recalculateDistance() - 1}; false means the player has left the route.
	 */
	public static boolean isNearPath()
	{
		List<WorldPoint> path = activeRoute;
		WorldPoint player = Rs2Player.getWorldLocation();
		if (path == null || path.isEmpty() || player == null)
		{
			return true;
		}
		WorldPoint previous = lastNearPathPosition;
		lastNearPathPosition = player;
		if (config.recalculateDistance() < 0 || player.equals(previous)
			|| net.runelite.client.plugins.microbot.util.poh.PohPresence.isInHouse())
		{
			return true;
		}
		Map<WorldPoint, Integer> reachable = net.runelite.client.plugins.microbot.util.tile.Rs2Tile
			.getReachableTilesFromTile(player, config.recalculateDistance() - 1);
		return path.stream().anyMatch(reachable::containsKey);
	}

	/** As upstream: {@link #getTransportsForPath(List, int, TransportType, boolean)} preferring teleport items, unfiltered. */
	public static List<Transport> getTransportsForPath(List<WorldPoint> path, int indexOfStartPoint)
	{
		return getTransportsForPath(path, indexOfStartPoint, TransportType.TELEPORTATION_ITEM, false);
	}

	/** As upstream: {@link #getTransportsForPath(List, int, TransportType, boolean)} unfiltered. */
	public static List<Transport> getTransportsForPath(List<WorldPoint> path, int indexOfStartPoint, TransportType prefTransportType)
	{
		return getTransportsForPath(path, indexOfStartPoint, prefTransportType, false);
	}

	/** As upstream: the transports along {@code path}; see {@link WalkerPathReads#getTransportsForPath}. */
	public static List<Transport> getTransportsForPath(List<WorldPoint> path, int indexOfStartPoint, TransportType prefTransportType,
		boolean applyFiltering)
	{
		return WalkerPathReads.getTransportsForPath(path, indexOfStartPoint, prefTransportType, applyFiltering);
	}

	/** As upstream: the carried items cover {@code transport}; see {@link WalkerPathReads#hasRequiredTransportItems}. */
	public static boolean hasRequiredTransportItems(Transport transport)
	{
		return WalkerPathReads.hasRequiredTransportItems(transport);
	}

	/** As upstream: the transports whose required items are not carried. */
	public static List<Transport> getMissingTransports(List<Transport> transports)
	{
		return WalkerPathReads.getMissingTransports(transports);
	}

	/** As upstream: the item ids to withdraw for {@code transports}. */
	public static List<Integer> getMissingTransportItemIds(List<Transport> transports)
	{
		return WalkerPathReads.getMissingTransportItemIds(transports);
	}

	// ---- Walker batch W1 (Claude 1): upstream's planner-independent transport-edge queries ----

	/** As upstream: {@link #getTransportsForDestination(WorldPoint, boolean)} as immutable edges. */
	public static List<Rs2TransportEdge> getTransportEdgesForDestination(WorldPoint destination, boolean useBankItems)
	{
		return getTransportsForDestination(destination, useBankItems).stream()
			.filter(transport -> transport.getDestination() != null)
			.map(Rs2PathApi::toTransportEdge).collect(java.util.stream.Collectors.toUnmodifiableList());
	}

	/** As upstream: the edges whose required items are not carried ({@link #getMissingTransports}). */
	public static List<Rs2TransportEdge> getMissingTransportEdges(List<Rs2TransportEdge> transports)
	{
		if (transports == null || transports.isEmpty())
		{
			return List.of();
		}
		Set<Transport> missing = new HashSet<>(getMissingTransports(sources(transports)));
		return transports.stream().filter(edge -> missing.contains(edge.getSourceIdentity()))
			.collect(java.util.stream.Collectors.toUnmodifiableList());
	}

	/** As upstream: {@link #getMissingTransportItemIdsWithQuantities} for edges. */
	public static Map<Integer, Integer> getMissingTransportEdgeItemIdsWithQuantities(List<Rs2TransportEdge> transports)
	{
		return transports == null || transports.isEmpty() ? Map.of()
			: getMissingTransportItemIdsWithQuantities(sources(transports));
	}

	/**
	 * As upstream: the withdrawals that prepare {@code transports}. Mobile: equipment is not
	 * planned separately, and the loadout is satisfiable when nothing is missing or the bank
	 * covers something missing (upstream's rune-pouch and fare accounting is not ported).
	 */
	public static Rs2TransportLoadout getMissingTransportEdgeLoadout(List<Rs2TransportEdge> transports)
	{
		if (getMissingTransportEdges(transports).isEmpty())
		{
			return Rs2TransportLoadout.empty();
		}
		Map<Integer, Integer> withdrawals = new java.util.LinkedHashMap<>();
		getMissingTransportEdgeItemIdsWithQuantities(transports).forEach((id, quantity) ->
		{
			if (id != null && id > 0 && quantity != null && quantity > 0)
			{
				withdrawals.put(id, quantity);
			}
		});
		return withdrawals.isEmpty() ? Rs2TransportLoadout.unavailable() : new Rs2TransportLoadout(withdrawals, List.of(), true);
	}

	/** As upstream: compares walking straight to {@code target} with a trip through the nearest bank. */
	public static TransportRouteAnalysis compareRoutes(WorldPoint startPoint, WorldPoint target)
	{
		return net.runelite.client.plugins.microbot.util.walker.banking.Rs2WalkerBankingPlanner.compareRoutes(startPoint, target);
	}

	/** As upstream: {@link #compareRoutes(WorldPoint, WorldPoint)} from the player. */
	public static TransportRouteAnalysis compareRoutes(WorldPoint target)
	{
		return compareRoutes(null, target);
	}

	/** As upstream: why the walker's target was last cleared ("" before the first clear). */
	public static String getLastRouteClearReason()
	{
		return lastRouteClearReason;
	}

	/** As upstream: when the walker's target was last cleared (epoch millis, 0 before the first clear). */
	public static long getLastRouteClearAtMs()
	{
		return lastRouteClearAtMs;
	}

	/** As upstream: {@link #getPointWithWallDistance(WorldPoint, WorldPoint)} without a player side. */
	public static WorldPoint getPointWithWallDistance(WorldPoint target)
	{
		return getPointWithWallDistance(target, null);
	}

	/**
	 * As upstream: nudges a click target off a wall edge onto an open neighbour, preferring one the
	 * player can reach and nearest to {@code playerLoc} (so the click stays on the player's side).
	 * Collision is read on the client thread; the target comes back unchanged when it has no wall,
	 * is outside the scene, or no clean neighbour exists.
	 */
	public static WorldPoint getPointWithWallDistance(WorldPoint target, WorldPoint playerLoc)
	{
		if (target == null)
		{
			return null;
		}
		Map<WorldPoint, Integer> flags = MobileActions.read(() -> collisionAround(target), null);
		if (flags == null || flags.get(target) == null || (flags.get(target) & WALL_FLAGS) == 0)
		{
			return target;
		}
		Set<WorldPoint> neighbours = net.runelite.client.plugins.microbot.util.tile.Rs2Tile.getReachableTilesFromTile(target, 1).keySet();
		Set<WorldPoint> fromPlayer = playerLoc == null ? Set.of()
			: net.runelite.client.plugins.microbot.util.tile.Rs2Tile.getReachableTilesFromTile(playerLoc, 20).keySet();
		return chooseWallDistanceNeighbour(target, neighbours, flags, playerLoc, fromPlayer);
	}

	/** Directional walls and full blocks: a tile with none of these is a clean click target. */
	static final int WALL_FLAGS = net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_NORTH | net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_EAST
		| net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_SOUTH | net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_WEST
		| net.runelite.api.CollisionDataFlag.BLOCK_MOVEMENT_FULL;

	/** The clean neighbour of {@code target}: reachable from the player when known, then nearest to the player (or target). */
	static WorldPoint chooseWallDistanceNeighbour(WorldPoint target, Set<WorldPoint> neighbours, Map<WorldPoint, Integer> flags,
		WorldPoint playerLoc, Set<WorldPoint> fromPlayer)
	{
		WorldPoint anchor = playerLoc != null ? playerLoc : target;
		return neighbours.stream()
			.filter(tile -> !tile.equals(target) && flags.get(tile) != null && (flags.get(tile) & WALL_FLAGS) == 0)
			.min(java.util.Comparator.comparing((WorldPoint tile) -> !fromPlayer.isEmpty() && !fromPlayer.contains(tile))
				.thenComparingInt(tile -> tile.distanceTo2D(anchor)).thenComparingInt(WorldPoint::getX).thenComparingInt(WorldPoint::getY))
			.orElse(target);
	}

	/** Collision flags for {@code target} and its eight neighbours in the top-level scene (client thread). */
	private static Map<WorldPoint, Integer> collisionAround(WorldPoint target)
	{
		Client client = Microbot.getClient();
		WorldView view = client == null ? null : client.getTopLevelWorldView();
		if (view == null || view.getCollisionMaps() == null || target.getPlane() != view.getPlane()
			|| view.getCollisionMaps()[view.getPlane()] == null)
		{
			return null;
		}
		int[][] scene = view.getCollisionMaps()[view.getPlane()].getFlags();
		Map<WorldPoint, Integer> out = new java.util.HashMap<>();
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				WorldPoint tile = target.dx(dx).dy(dy);
				net.runelite.api.coords.LocalPoint local = net.runelite.api.coords.LocalPoint.fromWorld(view, tile);
				if (local != null && local.getSceneX() >= 0 && local.getSceneY() >= 0
					&& local.getSceneX() < scene.length && local.getSceneY() < scene[local.getSceneX()].length)
				{
					out.put(tile, scene[local.getSceneX()][local.getSceneY()]);
				}
			}
		}
		return out;
	}

	/** The catalogue transports behind edges built by {@link Rs2PathApi} (other edges have none). */
	private static List<Transport> sources(List<Rs2TransportEdge> transports)
	{
		return transports.stream().map(Rs2TransportEdge::getSourceIdentity).filter(Transport.class::isInstance)
			.map(Transport.class::cast).collect(java.util.stream.Collectors.toList());
	}

	/** As upstream: {@link #getTransportsForDestination(WorldPoint, boolean)} planned with the bank's items. */
	public static List<Transport> prepareTransportsForDestination(WorldPoint destination)
	{
		return getTransportsForDestination(destination, true);
	}

	/**
	 * As upstream: {@link #walkWithState(WorldPoint, int)}, but when another walk is running it waits at most
	 * {@code lockWaitMs} for it to finish (0: no wait) and returns {@link WalkerState#EXIT} if it does not.
	 * Mobile has no walker lock (a new walk replaces the running one), so the running walk is what it waits on.
	 *
	 * @throws IllegalArgumentException when {@code lockWaitMs} is negative
	 */
	public static WalkerState walkWithStateTry(WorldPoint target, int distance, long lockWaitMs)
	{
		if (target == null)
		{
			return WalkerState.EXIT;
		}
		if (lockWaitMs < 0)
		{
			throw new IllegalArgumentException("lockWaitMs must be >= 0");
		}
		Client client = Microbot.getClient();
		if (client != null && client.isClientThread())
		{
			return WalkerState.EXIT;
		}
		if (Rs2Walker.target != null && (lockWaitMs == 0
			|| !Global.sleepUntil(() -> Rs2Walker.target == null, (int) Math.min(Integer.MAX_VALUE, lockWaitMs))))
		{
			return WalkerState.EXIT;
		}
		return walkWithState(target, distance);
	}
}
