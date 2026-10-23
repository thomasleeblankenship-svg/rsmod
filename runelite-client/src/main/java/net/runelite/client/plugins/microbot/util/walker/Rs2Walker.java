package net.runelite.client.plugins.microbot.util.walker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

/**
 * Mobile walking: static same-plane walking edges plus live scene collision and door recovery.
 * Walking success means observed arrival, never just a queued minimap/engine action.
 * Explicit Lumbridge castle stair transitions support its upstairs bank. Other transports
 * (teleports, ships and ladders elsewhere) require a separate implementation.
 */
public final class Rs2Walker
{
	private static final AtomicLong GENERATION = new AtomicLong();
	private static volatile WorldPoint target;
	private static volatile String lastStatus = "Idle";
	private static final long MAX_WALK_MILLIS = 120_000;
	private static final long STALL_MILLIS = 15_000;

	private Rs2Walker()
	{
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
			WalkLeg leg = selectLeg(scene, destination, radius);
			if (leg == null)
			{
				return fail("No walking route; a transport or an unmapped region may be required");
			}
			List<WorldPoint> global = leg.route;
			long lastProgress = System.currentTimeMillis();
			WorldPoint previous = scene.player;
			int progress = 0;
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
					if (!useStairs(leg.stairs, generation, deadline))
					{
						return fail("Lumbridge staircase failed: " + lastStatus);
					}
					scene = awaitSnapshot(generation, deadline);
					if (scene == null || !leg.stairs.observedArrival(scene.player))
					{
						return fail("No collision snapshot after Lumbridge staircase");
					}
					leg = selectLeg(scene, destination, radius);
					if (leg == null)
					{
						return fail("No walking route after Lumbridge staircase");
					}
					global = leg.route;
					progress = 0;
					previous = scene.player;
					lastProgress = System.currentTimeMillis();
					continue;
				}
				if (!scene.player.equals(previous))
				{
					previous = scene.player;
					lastProgress = System.currentTimeMillis();
				}
				if (System.currentTimeMillis() - lastProgress >= STALL_MILLIS)
				{
					return fail("Walking stopped: no movement for 15 seconds");
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
						waypoint = local.get(Math.min(local.size() - 1, 10));
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
				else if (!waypoint.equals(scene.player))
				{
					lastStatus = "Walking to " + destination;
					MobileActions.walk(waypoint);
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
		return LumbridgeStairs.supported(origin, destination);
	}

	private static final class WalkLeg
	{
		final WorldPoint destination;
		final int radius;
		final LumbridgeStairs.Step stairs;
		final List<WorldPoint> route;

		WalkLeg(WorldPoint destination, int radius, LumbridgeStairs.Step stairs, List<WorldPoint> route)
		{
			this.destination = destination;
			this.radius = radius;
			this.stairs = stairs;
			this.route = route;
		}
	}

	private static WalkLeg selectLeg(MobileRoutePlanner.Scene scene, WorldPoint destination, int radius)
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
		for (LumbridgeStairs.Step stairs : LumbridgeStairs.candidates(scene.player, destination))
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
	private static boolean useStairs(LumbridgeStairs.Step stairs, long generation, long walkDeadline) throws InterruptedException
	{
		long deadline = Math.min(walkDeadline, System.currentTimeMillis() + 20_000);
		long lastAttempt = 0;
		boolean dispatched = false;
		lastStatus = "Using Lumbridge staircase: " + stairs.action;
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
					dispatched |= MobileActions.objectAt(staircase.getId(), staircase.getWorldLocation(), stairs.action);
				}
			}
			long remaining = deadline - System.currentTimeMillis();
			if (remaining > 0) { Thread.sleep(Math.min(300, remaining)); }
		}
		lastStatus = !MobileActions.active() || GENERATION.get() != generation
			? "staircase cancelled" : dispatched ? "plane change was not observed" : "staircase action unavailable";
		return false;
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
