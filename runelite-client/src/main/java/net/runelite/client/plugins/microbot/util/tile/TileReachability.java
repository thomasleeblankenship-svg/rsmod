package net.runelite.client.plugins.microbot.util.tile;

import java.util.ArrayDeque;
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
}
