package net.runelite.client.plugins.microbot.util.tile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.CollisionDataFlag;

/**
 * Upstream 2.6.38's scene-grid reachability rules for {@link Rs2Tile}, with no client reads: a
 * breadth-first flood over the 104 by 104 collision flags (diagonals need both cardinal sides open),
 * the single-step rule, and whether an object, wall or footprint can be used from a reached tile.
 */
final class TileReachability
{
	/** Scene width and height in tiles. */
	static final int FLAG_DATA_SIZE = 104;

	/** Wall orientation bit and the neighbour offset that bit faces. */
	private static final int[][] WALL_EDGES = {
		{1, -1, 0}, {2, 0, 1}, {4, 1, 0}, {8, 0, -1},
		{16, -1, 0}, {16, 0, 1}, {32, 1, 0}, {32, 0, 1},
		{64, 1, 0}, {64, 0, -1}, {128, -1, 0}, {128, 0, -1},
	};

	/** Neighbour offset, the neighbour's facing block flag and the target's facing block flag. */
	private static final int[][] CARDINAL_SIDES = {
		{-1, 0, CollisionDataFlag.BLOCK_MOVEMENT_EAST, CollisionDataFlag.BLOCK_MOVEMENT_WEST},
		{1, 0, CollisionDataFlag.BLOCK_MOVEMENT_WEST, CollisionDataFlag.BLOCK_MOVEMENT_EAST},
		{0, -1, CollisionDataFlag.BLOCK_MOVEMENT_NORTH, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH},
		{0, 1, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH, CollisionDataFlag.BLOCK_MOVEMENT_NORTH},
	};

	private TileReachability()
	{
	}

	static boolean isWithinBounds(int x, int y)
	{
		return x >= 0 && y >= 0 && x < FLAG_DATA_SIZE && y < FLAG_DATA_SIZE;
	}

	/** Every scene tile reachable from {@code (startX, startY)}. */
	static boolean[][] reachableFrom(int[][] flags, int startX, int startY)
	{
		boolean[][] visited = new boolean[FLAG_DATA_SIZE][FLAG_DATA_SIZE];
		if (!isWithinBounds(startX, startY))
		{
			return visited;
		}
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		queue.add((startX << 16) | startY);
		visited[startX][startY] = true;
		while (!queue.isEmpty())
		{
			int point = queue.poll();
			int x = point >> 16;
			int y = point & 0xFFFF;
			boolean canN = (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0;
			boolean canS = (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0;
			boolean canE = (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0;
			boolean canW = (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0;
			addNeighbour(queue, visited, flags, x, y, -1, 0, CollisionDataFlag.BLOCK_MOVEMENT_WEST);
			addNeighbour(queue, visited, flags, x, y, 1, 0, CollisionDataFlag.BLOCK_MOVEMENT_EAST);
			addNeighbour(queue, visited, flags, x, y, 0, -1, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH);
			addNeighbour(queue, visited, flags, x, y, 0, 1, CollisionDataFlag.BLOCK_MOVEMENT_NORTH);
			addDiagonal(queue, visited, flags, x, y, canN && canE, 1, 1,
				CollisionDataFlag.BLOCK_MOVEMENT_NORTH, CollisionDataFlag.BLOCK_MOVEMENT_EAST);
			addDiagonal(queue, visited, flags, x, y, canN && canW, -1, 1,
				CollisionDataFlag.BLOCK_MOVEMENT_NORTH, CollisionDataFlag.BLOCK_MOVEMENT_WEST);
			addDiagonal(queue, visited, flags, x, y, canS && canE, 1, -1,
				CollisionDataFlag.BLOCK_MOVEMENT_SOUTH, CollisionDataFlag.BLOCK_MOVEMENT_EAST);
			addDiagonal(queue, visited, flags, x, y, canS && canW, -1, -1,
				CollisionDataFlag.BLOCK_MOVEMENT_SOUTH, CollisionDataFlag.BLOCK_MOVEMENT_WEST);
		}
		return visited;
	}

	private static void addNeighbour(ArrayDeque<Integer> queue, boolean[][] visited, int[][] flags, int x, int y, int dx, int dy, int block)
	{
		int nx = x + dx;
		int ny = y + dy;
		if (isWithinBounds(nx, ny) && !visited[nx][ny] && (flags[x][y] & block) == 0
			&& (flags[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0)
		{
			queue.add((nx << 16) | ny);
			visited[nx][ny] = true;
		}
	}

	/** Upstream's corner rule: the side tiles cut through must not block towards the target. */
	private static void addDiagonal(ArrayDeque<Integer> queue, boolean[][] visited, int[][] flags, int x, int y, boolean open,
		int dx, int dy, int verticalBlock, int horizontalBlock)
	{
		int nx = x + dx;
		int ny = y + dy;
		if (open && isWithinBounds(nx, ny) && !visited[nx][ny]
			&& (flags[nx][y] & (CollisionDataFlag.BLOCK_MOVEMENT_FULL | verticalBlock)) == 0
			&& (flags[x][ny] & (CollisionDataFlag.BLOCK_MOVEMENT_FULL | horizontalBlock)) == 0
			&& (flags[nx][ny] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0)
		{
			queue.add((nx << 16) | ny);
			visited[nx][ny] = true;
		}
	}

	/** Whether one {@code (dx, dy)} step out of {@code (fx, fy)} is unobstructed. */
	static boolean isStepAllowed(int[][] flags, int fx, int fy, int dx, int dy)
	{
		if (dx == 0 && dy == 0)
		{
			return true;
		}
		int tx = fx + dx;
		int ty = fy + dy;
		if ((flags[tx][ty] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0)
		{
			return false;
		}
		if (dx == 0 || dy == 0)
		{
			return (flags[fx][fy] & cardinalBlockFlag(dx, dy)) == 0;
		}
		return (flags[fx][fy] & cardinalBlockFlag(dx, 0)) == 0
			&& (flags[fx][fy] & cardinalBlockFlag(0, dy)) == 0
			&& (flags[tx][fy] & (CollisionDataFlag.BLOCK_MOVEMENT_FULL | cardinalBlockFlag(0, dy))) == 0
			&& (flags[fx][ty] & (CollisionDataFlag.BLOCK_MOVEMENT_FULL | cardinalBlockFlag(dx, 0))) == 0;
	}

	private static int cardinalBlockFlag(int dx, int dy)
	{
		if (dx > 0)
		{
			return CollisionDataFlag.BLOCK_MOVEMENT_EAST;
		}
		if (dx < 0)
		{
			return CollisionDataFlag.BLOCK_MOVEMENT_WEST;
		}
		return dy > 0 ? CollisionDataFlag.BLOCK_MOVEMENT_NORTH : CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
	}

	/** Whether the tile at {@code (x, y)}, holding a wall with {@code wallOrientation} (0 for none), can be used from a reached tile. */
	static boolean canInteractFromReachableTile(int[][] flags, boolean[][] reachable, int x, int y, int wallOrientation)
	{
		if (!isWithinBounds(x, y))
		{
			return false;
		}
		if (reachable[x][y])
		{
			return true;
		}
		for (int[] edge : WALL_EDGES)
		{
			int nx = x + edge[1];
			int ny = y + edge[2];
			if ((wallOrientation & edge[0]) != 0 && isWithinBounds(nx, ny) && reachable[nx][ny])
			{
				return true;
			}
		}
		for (int[] side : CARDINAL_SIDES)
		{
			int nx = x + side[0];
			int ny = y + side[1];
			if (!isBehindWallFace(wallOrientation, side[0], side[1]) && isWithinBounds(nx, ny) && reachable[nx][ny]
				&& (flags[nx][ny] & side[2]) == 0 && (flags[x][y] & side[3]) == 0)
			{
				return true;
			}
		}
		return false;
	}

	/** Whether any tile of the footprint {@code (minX, minY)} to {@code (maxX, maxY)} can be used from a reached tile. */
	static boolean canInteractWithFootprintFromReachableTile(int[][] flags, boolean[][] reachable, int minX, int minY, int maxX, int maxY)
	{
		for (int x = minX; x <= maxX; x++)
		{
			for (int y = minY; y <= maxY; y++)
			{
				if (canInteractFromReachableTile(flags, reachable, x, y, 0))
				{
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isBehindWallFace(int wallOrientation, int dx, int dy)
	{
		for (int[] edge : WALL_EDGES)
		{
			if ((wallOrientation & edge[0]) != 0 && edge[1] == -dx && edge[2] == -dy)
			{
				return true;
			}
		}
		return false;
	}

	// ---- Util action batch AF (Claude 1): upstream's scene path search ----

	/** Upstream's route-finder masks: the flags on the entered tile that block a step in each direction. */
	private static final int BLOCK_WEST = 19136776;
	private static final int BLOCK_EAST = 19136896;
	private static final int BLOCK_SOUTH = 19136770;
	private static final int BLOCK_NORTH = 19136800;
	private static final int BLOCK_SOUTH_WEST = 19136782;
	private static final int BLOCK_NORTH_WEST = 19136824;
	private static final int BLOCK_SOUTH_EAST = 19136899;
	private static final int BLOCK_NORTH_EAST = 19136992;

	/** Search window (tiles) centred on the start, as upstream. */
	private static final int WINDOW = 128;
	private static final int HALF_WINDOW = 64;

	/**
	 * Upstream {@code Rs2Tile.fullPathTo}'s search over scene collision flags: every scene tile from
	 * the start to the target, both included, in walking order. When the target cannot be reached, the
	 * path ends at the reachable tile nearest it within 10 tiles (upstream's fallback). Null when
	 * neither exists. Each element is {@code {sceneX, sceneY}}.
	 */
	static List<int[]> scenePath(int[][] flags, int startX, int startY, int targetX, int targetY)
	{
		if (flags == null || !isWithinBounds(startX, startY))
		{
			return null;
		}
		int[][] directions = new int[WINDOW][WINDOW];
		int[][] distances = new int[WINDOW][WINDOW];
		for (int[] row : distances)
		{
			java.util.Arrays.fill(row, Integer.MAX_VALUE);
		}
		ArrayDeque<Integer> queue = new ArrayDeque<>();
		directions[HALF_WINDOW][HALF_WINDOW] = 99;
		distances[HALF_WINDOW][HALF_WINDOW] = 0;
		queue.add((startX << 16) | startY);
		int currentX = startX;
		int currentY = startY;
		boolean reached = false;
		while (!queue.isEmpty())
		{
			int point = queue.poll();
			currentX = point >> 16;
			currentY = point & 0xFFFF;
			if (currentX == targetX && currentY == targetY)
			{
				reached = true;
				break;
			}
			int mapX = currentX - startX + HALF_WINDOW;
			int mapY = currentY - startY + HALF_WINDOW;
			int next = distances[mapX][mapY] + 1;
			step(queue, flags, directions, distances, currentX, currentY, mapX, mapY, -1, 0, 2, next, BLOCK_WEST);
			step(queue, flags, directions, distances, currentX, currentY, mapX, mapY, 1, 0, 8, next, BLOCK_EAST);
			step(queue, flags, directions, distances, currentX, currentY, mapX, mapY, 0, -1, 1, next, BLOCK_SOUTH);
			step(queue, flags, directions, distances, currentX, currentY, mapX, mapY, 0, 1, 4, next, BLOCK_NORTH);
			diagonal(queue, flags, directions, distances, currentX, currentY, mapX, mapY, -1, -1, 3, next, BLOCK_SOUTH_WEST, BLOCK_WEST, BLOCK_SOUTH);
			diagonal(queue, flags, directions, distances, currentX, currentY, mapX, mapY, -1, 1, 6, next, BLOCK_NORTH_WEST, BLOCK_WEST, BLOCK_NORTH);
			diagonal(queue, flags, directions, distances, currentX, currentY, mapX, mapY, 1, -1, 9, next, BLOCK_SOUTH_EAST, BLOCK_EAST, BLOCK_SOUTH);
			diagonal(queue, flags, directions, distances, currentX, currentY, mapX, mapY, 1, 1, 12, next, BLOCK_NORTH_EAST, BLOCK_EAST, BLOCK_NORTH);
		}
		if (!reached)
		{
			int[] nearest = nearestReached(distances, startX, startY, targetX, targetY);
			if (nearest == null)
			{
				return null;
			}
			currentX = nearest[0];
			currentY = nearest[1];
		}
		List<int[]> path = new ArrayList<>();
		path.add(new int[]{currentX, currentY});
		while (currentX != startX || currentY != startY)
		{
			int direction = directions[currentX - startX + HALF_WINDOW][currentY - startY + HALF_WINDOW];
			if ((direction & 2) != 0)
			{
				++currentX;
			}
			else if ((direction & 8) != 0)
			{
				--currentX;
			}
			if ((direction & 1) != 0)
			{
				++currentY;
			}
			else if ((direction & 4) != 0)
			{
				--currentY;
			}
			path.add(new int[]{currentX, currentY});
		}
		Collections.reverse(path);
		return path;
	}

	/**
	 * Upstream {@code Rs2Tile.pathTo}'s checkpoints for a {@link #scenePath} result: the tiles where
	 * the path turns, then its last tile, without the start, at most the first 25.
	 */
	static List<int[]> checkpoints(List<int[]> path)
	{
		if (path == null)
		{
			return null;
		}
		List<int[]> corners = new ArrayList<>();
		int last = path.size() - 1;
		for (int i = 1; i < last; i++)
		{
			int[] before = path.get(i - 1);
			int[] here = path.get(i);
			int[] after = path.get(i + 1);
			if (here[0] - before[0] != after[0] - here[0] || here[1] - before[1] != after[1] - here[1])
			{
				corners.add(here);
			}
		}
		corners.add(path.get(last));
		return corners.size() > 25 ? new ArrayList<>(corners.subList(0, 25)) : corners;
	}

	private static void step(ArrayDeque<Integer> queue, int[][] flags, int[][] directions, int[][] distances,
		int x, int y, int mapX, int mapY, int dx, int dy, int direction, int distance, int block)
	{
		int nx = x + dx;
		int ny = y + dy;
		int nmx = mapX + dx;
		int nmy = mapY + dy;
		if (nmx >= 0 && nmy >= 0 && nmx < WINDOW && nmy < WINDOW && isWithinBounds(nx, ny)
			&& directions[nmx][nmy] == 0 && (flags[nx][ny] & block) == 0)
		{
			queue.add((nx << 16) | ny);
			directions[nmx][nmy] = direction;
			distances[nmx][nmy] = distance;
		}
	}

	/** A diagonal step also needs both cardinal tiles it cuts past open (upstream's rule). */
	private static void diagonal(ArrayDeque<Integer> queue, int[][] flags, int[][] directions, int[][] distances,
		int x, int y, int mapX, int mapY, int dx, int dy, int direction, int distance, int block, int horizontalBlock, int verticalBlock)
	{
		int nx = x + dx;
		int ny = y + dy;
		if (isWithinBounds(nx, ny) && (flags[nx][y] & horizontalBlock) == 0 && (flags[x][ny] & verticalBlock) == 0)
		{
			step(queue, flags, directions, distances, x, y, mapX, mapY, dx, dy, direction, distance, block);
		}
	}

	/** The searched tile within 10 of the target nearest it (then shortest path), as upstream; null when none. */
	private static int[] nearestReached(int[][] distances, int startX, int startY, int targetX, int targetY)
	{
		int best = Integer.MAX_VALUE;
		int bestLength = Integer.MAX_VALUE;
		int[] nearest = null;
		for (int x = targetX - 10; x <= targetX + 10; x++)
		{
			for (int y = targetY - 10; y <= targetY + 10; y++)
			{
				int mapX = x - startX + HALF_WINDOW;
				int mapY = y - startY + HALF_WINDOW;
				if (mapX < 0 || mapY < 0 || mapX >= WINDOW || mapY >= WINDOW || distances[mapX][mapY] >= 100)
				{
					continue;
				}
				int squared = (x - targetX) * (x - targetX) + (y - targetY) * (y - targetY);
				if (squared < best || (squared == best && distances[mapX][mapY] < bestLength))
				{
					best = squared;
					bestLength = distances[mapX][mapY];
					nearest = new int[]{x, y};
				}
			}
		}
		return nearest;
	}
}
