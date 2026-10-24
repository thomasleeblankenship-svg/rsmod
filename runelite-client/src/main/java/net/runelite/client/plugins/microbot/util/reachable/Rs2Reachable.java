package net.runelite.client.plugins.microbot.util.reachable;

import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Tiles reachable on foot from a start tile over the loaded scene's collision map (upstream
 * Microbot 2.6.38 {@code Rs2Reachable}: 4-direction flood fill, cached per game tick). RuneMobile
 * adaptations: fastutil is not on the classpath, so the result is a {@link PackedTiles} set of
 * {@link WorldPointUtil}-packed points with the same {@code contains}/{@code forEach(int)} use; the
 * tick cache is also keyed by the start tile; {@link #isReachable} floods from the player (upstream
 * floods from the target itself, which always contains it).
 */
public final class Rs2Reachable
{
	private static final int REGION_SIZE = 104;
	private static volatile Cached cached;

	private Rs2Reachable()
	{
	}

	/** An immutable set of packed world points. */
	public static final class PackedTiles
	{
		private final int[] sorted;

		PackedTiles(int[] packed)
		{
			this.sorted = packed.clone();
			Arrays.sort(this.sorted);
		}

		public boolean contains(int packed)
		{
			return Arrays.binarySearch(sorted, packed) >= 0;
		}

		public boolean contains(WorldPoint point)
		{
			return point != null && contains(WorldPointUtil.packWorldPoint(point));
		}

		public void forEach(IntConsumer action)
		{
			for (int packed : sorted)
			{
				action.accept(packed);
			}
		}

		public IntStream stream()
		{
			return Arrays.stream(sorted);
		}

		public int size()
		{
			return sorted.length;
		}

		public boolean isEmpty()
		{
			return sorted.length == 0;
		}
	}

	private static final class Cached
	{
		final int tick;
		final WorldPoint start;
		final PackedTiles tiles;

		Cached(int tick, WorldPoint start, PackedTiles tiles)
		{
			this.tick = tick;
			this.start = start;
			this.tiles = tiles;
		}
	}

	/** Whether the player can walk to {@code target} within the loaded scene. */
	public static boolean isReachable(WorldPoint target)
	{
		return target != null && getReachableTiles(Rs2Player.getWorldLocation()).contains(target);
	}

	/** The tiles reachable from {@code start} on its floor; empty outside the scene. One client-thread read. */
	public static PackedTiles getReachableTiles(WorldPoint start)
	{
		int tick = MobileActions.read(() -> Microbot.getClient().getTickCount(), -1);
		Cached last = cached;
		if (last != null && last.tick == tick && java.util.Objects.equals(last.start, start))
		{
			return last.tiles;
		}
		PackedTiles tiles = MobileActions.read(() -> flood(Microbot.getClient(), start), new PackedTiles(new int[0]));
		cached = new Cached(tick, start, tiles);
		return tiles;
	}

	private static PackedTiles flood(Client client, WorldPoint start)
	{
		WorldView view = client.getTopLevelWorldView();
		CollisionData[] maps = view == null ? null : view.getCollisionMaps();
		int plane = view == null ? -1 : view.getPlane();
		if (start == null || maps == null || plane < 0 || plane >= maps.length || maps[plane] == null || start.getPlane() != plane)
		{
			return new PackedTiles(new int[0]);
		}
		int[][] flags = maps[plane].getFlags();
		int size = Math.min(REGION_SIZE, flags.length);
		int sx = start.getX() - view.getBaseX();
		int sy = start.getY() - view.getBaseY();
		if (sx < 0 || sy < 0 || sx >= size || sy >= size)
		{
			return new PackedTiles(new int[0]);
		}
		boolean[][] visited = new boolean[size][size];
		int[] queue = new int[size * size];
		int head = 0;
		int tail = 0;
		queue[tail++] = (sx << 16) | sy;
		visited[sx][sy] = true;
		while (head < tail)
		{
			int key = queue[head++];
			int x = key >> 16;
			int y = key & 0xFFFF;
			int here = flags[x][y];
			tail = step(flags, visited, queue, tail, x, y - 1, (here & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0);
			tail = step(flags, visited, queue, tail, x, y + 1, (here & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0);
			tail = step(flags, visited, queue, tail, x - 1, y, (here & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0);
			tail = step(flags, visited, queue, tail, x + 1, y, (here & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0);
		}
		int[] packed = new int[tail];
		for (int i = 0; i < tail; i++)
		{
			packed[i] = WorldPointUtil.packWorldPoint(view.getBaseX() + (queue[i] >> 16), view.getBaseY() + (queue[i] & 0xFFFF), plane);
		}
		return new PackedTiles(packed);
	}

	private static int step(int[][] flags, boolean[][] visited, int[] queue, int tail, int x, int y, boolean open)
	{
		if (!open || x < 0 || y < 0 || x >= visited.length || y >= visited.length || visited[x][y]
			|| (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0)
		{
			return tail;
		}
		visited[x][y] = true;
		queue[tail] = (x << 16) | y;
		return tail + 1;
	}
}
