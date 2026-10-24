package net.runelite.client.plugins.microbot.shortestpath;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Path reads behind upstream's {@code Rs2Walker} API (Microbot 2.6.38), for the walker's one-line
 * statics: {@link #getWalkPath}, {@link #getTotalTiles}, {@link #getTotalTilesFromPath},
 * {@link #getClosestTileIndex}, {@link #isWalkableInCollisionMap} and {@link #getTile}.
 * <p>
 * Adaptations: routes come from {@link MobilePathfinder} (upstream: {@code Rs2PathApi}), so they
 * use mobile's transport policy and never bank items; the closest-index search measures steps on
 * the pathfinder's collision map, the map the route was planned on (upstream: a live-scene BFS).
 * Searches block the calling thread and must not run on the client thread.
 */
public final class WalkerPathReads
{
	/** Upstream's step budget for the closest-index search. */
	static final int CLOSEST_INDEX_STEP_BUDGET = 20;

	private static final int[][] DIRECTIONS = {
		{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}
	};

	private WalkerPathReads()
	{
	}

	/** As upstream: the tile path from {@code start} towards {@code target} (partial if blocked), or empty. */
	public static List<WorldPoint> getWalkPath(WorldPoint start, WorldPoint target)
	{
		if (start == null || target == null)
		{
			return Collections.emptyList();
		}
		return MobilePathfinder.find(start, target).getTiles();
	}

	public static List<WorldPoint> getWalkPath(WorldPoint target)
	{
		return getWalkPath(Rs2Player.getWorldLocation(), target);
	}

	/**
	 * As upstream: the number of path tiles from {@code start} to {@code destination}, or
	 * {@link Integer#MAX_VALUE} when the path ends on another floor or more than 4 tiles (on either
	 * axis) from the destination.
	 */
	public static int getTotalTiles(WorldPoint start, WorldPoint destination)
	{
		if (destination == null)
		{
			return Integer.MAX_VALUE;
		}
		return tilesIfEndsNear(getWalkPath(start, destination), destination, 5);
	}

	public static int getTotalTiles(WorldPoint destination)
	{
		return getTotalTiles(Rs2Player.getWorldLocation(), destination);
	}

	/** As upstream: {@code path.size()}, or {@link Integer#MAX_VALUE} unless it ends within 7 tiles of {@code destination}. */
	public static int getTotalTilesFromPath(List<WorldPoint> path, WorldPoint destination)
	{
		return path == null || destination == null ? Integer.MAX_VALUE : tilesIfEndsNear(path, destination, 8);
	}

	/** Upstream's check: two {@code size} x {@code size} areas around the path end and the destination overlap. */
	private static int tilesIfEndsNear(List<WorldPoint> path, WorldPoint destination, int size)
	{
		if (path.isEmpty())
		{
			return Integer.MAX_VALUE;
		}
		WorldPoint end = path.get(path.size() - 1);
		if (end.getPlane() != destination.getPlane()
			|| Math.abs(end.getX() - destination.getX()) >= size || Math.abs(end.getY() - destination.getY()) >= size)
		{
			return Integer.MAX_VALUE;
		}
		return path.size();
	}

	/**
	 * As upstream: the index of the path tile fewest steps from the player (within 20 steps), else
	 * of the tile nearest in a straight line; -1 for an empty path or no player.
	 */
	public static int getClosestTileIndex(List<WorldPoint> path)
	{
		PathfinderConfig config = MobilePathfinder.sharedConfig();
		return getClosestTileIndex(path, Rs2Player.getWorldLocation(), config == null ? null : config.getMap());
	}

	static int getClosestTileIndex(List<WorldPoint> path, WorldPoint player, CollisionMap map)
	{
		if (path == null || path.isEmpty() || player == null)
		{
			return -1;
		}
		Map<WorldPoint, Integer> steps = map == null ? Collections.emptyMap() : stepsFrom(map, player);
		int best = -1;
		int bestSteps = Integer.MAX_VALUE;
		for (int i = 0; i < path.size(); i++)
		{
			Integer distance = path.get(i) == null ? null : steps.get(path.get(i));
			if (distance != null && distance < bestSteps)
			{
				bestSteps = distance;
				best = i;
				if (distance == 0)
				{
					break;
				}
			}
		}
		if (best >= 0)
		{
			return best;
		}
		int closest = -1;
		int closestDistance = Integer.MAX_VALUE;
		for (int i = 0; i < path.size(); i++)
		{
			WorldPoint point = path.get(i);
			int distance = point == null ? Integer.MAX_VALUE : player.distanceTo(point);
			if (point != null && distance < closestDistance)
			{
				closestDistance = distance;
				closest = i;
			}
		}
		return closest;
	}

	/** Breadth-first step counts from {@code origin} on {@code map}, up to the step budget. */
	private static Map<WorldPoint, Integer> stepsFrom(CollisionMap map, WorldPoint origin)
	{
		Map<WorldPoint, Integer> steps = new HashMap<>();
		ArrayDeque<WorldPoint> queue = new ArrayDeque<>();
		steps.put(origin, 0);
		queue.add(origin);
		while (!queue.isEmpty())
		{
			WorldPoint point = queue.removeFirst();
			int distance = steps.get(point);
			if (distance >= CLOSEST_INDEX_STEP_BUDGET)
			{
				continue;
			}
			for (int[] d : DIRECTIONS)
			{
				WorldPoint next = point.dx(d[0]).dy(d[1]);
				if (!steps.containsKey(next) && map.canStep(point.getX(), point.getY(), point.getPlane(), d[0], d[1]))
				{
					steps.put(next, distance + 1);
					queue.addLast(next);
				}
			}
		}
		return steps;
	}

	/**
	 * As upstream ({@code Rs2PathApi.hasWalkableTileWithin(tile, 0)}): the tile is open in the
	 * pathfinder's collision map (mapped and not blocked on all four sides).
	 */
	public static boolean isWalkableInCollisionMap(WorldPoint tile)
	{
		PathfinderConfig config = MobilePathfinder.sharedConfig();
		return config != null && isWalkableInCollisionMap(config.getMap(), tile);
	}

	static boolean isWalkableInCollisionMap(CollisionMap map, WorldPoint tile)
	{
		return tile != null && map.hasRegion(tile.getX(), tile.getY())
			&& !map.isBlocked(tile.getX(), tile.getY(), tile.getPlane());
	}

	/**
	 * As upstream: the live scene tile at {@code point} (resolved into the instance when in one), or
	 * {@code null} outside the loaded scene. Read it on the client thread.
	 */
	public static Tile getTile(WorldPoint point)
	{
		if (point == null)
		{
			return null;
		}
		return MobileActions.read(() ->
		{
			Client client = Microbot.getClient();
			WorldView view = client.getTopLevelWorldView();
			if (view == null || view.getScene() == null)
			{
				return null;
			}
			WorldPoint target = point;
			if (view.isInstance())
			{
				target = WorldPoint.toLocalInstance(view, point).stream().findFirst().orElse(null);
				if (target == null)
				{
					return null;
				}
			}
			LocalPoint local = LocalPoint.fromWorld(view, target);
			if (local == null)
			{
				return null;
			}
			Tile[][][] tiles = view.getScene().getTiles();
			return tiles == null ? null : tiles[point.getPlane()][local.getSceneX()][local.getSceneY()];
		}, null);
	}
}
