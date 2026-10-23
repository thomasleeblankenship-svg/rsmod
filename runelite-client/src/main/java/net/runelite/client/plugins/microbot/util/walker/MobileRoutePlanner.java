package net.runelite.client.plugins.microbot.util.walker;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.WorldPoint;

/** Collision-aware walking paths; transports are deliberately not inferred from a straight line. */
final class MobileRoutePlanner
{
	private static final int[][] DIRECTIONS = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
	private static final int[] OUT = {CollisionDataFlag.BLOCK_MOVEMENT_NORTH,
		CollisionDataFlag.BLOCK_MOVEMENT_EAST, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH,
		CollisionDataFlag.BLOCK_MOVEMENT_WEST};
	private static final int[] IN = {CollisionDataFlag.BLOCK_MOVEMENT_SOUTH,
		CollisionDataFlag.BLOCK_MOVEMENT_WEST, CollisionDataFlag.BLOCK_MOVEMENT_NORTH,
		CollisionDataFlag.BLOCK_MOVEMENT_EAST};
	private static final int MAX_SEARCH_NODES = 150_000;
	private static final long MAX_SEARCH_NANOS = 2_000_000_000L;

	interface Edges
	{
		boolean canStep(int x, int y, int plane, int direction);
	}

	static final class Scene
	{
		final int baseX;
		final int baseY;
		final int plane;
		final int[][] flags;
		final WorldPoint player;
		final boolean instance;

		Scene(int baseX, int baseY, int plane, int[][] flags, WorldPoint player, boolean instance)
		{
			this.baseX = baseX;
			this.baseY = baseY;
			this.plane = plane;
			this.flags = flags;
			this.player = player;
			this.instance = instance;
		}

		boolean contains(WorldPoint point)
		{
			return point != null && point.getPlane() == plane && contains(point.getX(), point.getY());
		}

		boolean contains(int x, int y)
		{
			x -= baseX;
			y -= baseY;
			return x >= 0 && x < flags.length && flags[x] != null && y >= 0 && y < flags[x].length;
		}

		boolean canStep(int x, int y, int z, int direction)
		{
			int nextX = x + DIRECTIONS[direction][0];
			int nextY = y + DIRECTIONS[direction][1];
			if (z != plane || !contains(x, y) || !contains(nextX, nextY))
			{
				return false;
			}
			int from = flags[x - baseX][y - baseY];
			int to = flags[nextX - baseX][nextY - baseY];
			return (from & (CollisionDataFlag.BLOCK_MOVEMENT_FULL | OUT[direction])) == 0
				&& (to & (CollisionDataFlag.BLOCK_MOVEMENT_FULL | IN[direction])) == 0;
		}
	}

	private static final class Node
	{
		final int position;
		final int cost;
		final int score;

		Node(int position, int cost, int score)
		{
			this.position = position;
			this.cost = cost;
			this.score = score;
		}
	}

	/** Packaged upstream edge bits: north/east per tile; missing regions are impassable. */
	static final class WorldEdges implements Edges
	{
		private final Map<Integer, BitSet> regions;

		WorldEdges(Map<Integer, BitSet> regions)
		{
			this.regions = regions;
		}

		private boolean edge(int x, int y, int plane, int flag)
		{
			if (x < 0 || y < 0 || plane < 0 || plane > 3)
			{
				return false;
			}
			BitSet bits = regions.get((x >> 6) | ((y >> 6) << 16));
			int index = (plane * 4096 + (y & 63) * 64 + (x & 63)) * 2 + flag;
			return bits != null && bits.get(index);
		}

		@Override
		public boolean canStep(int x, int y, int plane, int direction)
		{
			switch (direction)
			{
				case 0: return edge(x, y, plane, 0);
				case 1: return edge(x, y, plane, 1);
				case 2: return edge(x, y - 1, plane, 0);
				case 3: return edge(x - 1, y, plane, 1);
				default: return false;
			}
		}
	}

	private static final class WorldHolder
	{
		static final WorldEdges EDGES = loadWorldEdges();
	}

	static WorldEdges worldEdges()
	{
		return WorldHolder.EDGES;
	}

	private static WorldEdges loadWorldEdges()
	{
		Map<Integer, BitSet> regions = new HashMap<>();
		InputStream resource = MobileRoutePlanner.class.getResourceAsStream("collision-map.zip");
		if (resource == null)
		{
			throw new IllegalStateException("Packaged mobile walking collision map is missing");
		}
		try (ZipInputStream zip = new ZipInputStream(resource))
		{
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null)
			{
				String[] parts = entry.getName().split("_");
				if (entry.isDirectory() || parts.length != 2)
				{
					throw new IOException("Unexpected collision map entry: " + entry.getName());
				}
				int x = Integer.parseInt(parts[0]);
				int y = Integer.parseInt(parts[1]);
				regions.put(x | (y << 16), BitSet.valueOf(zip.readAllBytes()));
			}
		}
		catch (IOException | NumberFormatException exception)
		{
			throw new IllegalStateException("Unable to load walking collision map", exception);
		}
		return new WorldEdges(Collections.unmodifiableMap(regions));
	}

	/** Cardinal edges avoid diagonal corner cutting and respect directional walls in both directions. */
	static List<WorldPoint> route(WorldPoint start, WorldPoint goal, int radius, Edges edges)
	{
		if (start == null || goal == null || start.getPlane() != goal.getPlane())
		{
			return Collections.emptyList();
		}
		int plane = start.getPlane();
		int startKey = pack(start.getX(), start.getY());
		PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(n -> n.score));
		Map<Integer, Integer> cost = new HashMap<>();
		Map<Integer, Integer> parent = new HashMap<>();
		cost.put(startKey, 0);
		open.add(new Node(startKey, 0, heuristic(start.getX(), start.getY(), goal)));
		long deadline = System.nanoTime() + MAX_SEARCH_NANOS;
		int expanded = 0;
		while (!open.isEmpty() && expanded++ < MAX_SEARCH_NODES)
		{
			if ((expanded & 255) == 0 && (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline))
			{
				return Collections.emptyList();
			}
			Node current = open.poll();
			if (current.cost != cost.getOrDefault(current.position, Integer.MAX_VALUE))
			{
				continue;
			}
			int x = x(current.position);
			int y = y(current.position);
			if (Math.max(Math.abs(x - goal.getX()), Math.abs(y - goal.getY())) <= radius)
			{
				return reconstruct(current.position, parent, plane);
			}
			for (int direction = 0; direction < DIRECTIONS.length; direction++)
			{
				if (!edges.canStep(x, y, plane, direction))
				{
					continue;
				}
				int nx = x + DIRECTIONS[direction][0];
				int ny = y + DIRECTIONS[direction][1];
				if (nx < 0 || nx > 65535 || ny < 0 || ny > 65535)
				{
					continue;
				}
				int next = pack(nx, ny);
				int nextCost = current.cost + 1;
				if (nextCost < cost.getOrDefault(next, Integer.MAX_VALUE))
				{
					cost.put(next, nextCost);
					parent.put(next, current.position);
					open.add(new Node(next, nextCost, nextCost + heuristic(nx, ny, goal)));
				}
			}
		}
		return Collections.emptyList();
	}

	static List<WorldPoint> reachable(Scene scene, int radius)
	{
		if (scene == null || scene.player == null || !scene.contains(scene.player))
		{
			return Collections.emptyList();
		}
		int bounded = Math.max(0, Math.min(32, radius));
		Map<Integer, WorldPoint> visited = new HashMap<>();
		ArrayDeque<WorldPoint> queue = new ArrayDeque<>();
		queue.add(scene.player);
		visited.put(pack(scene.player.getX(), scene.player.getY()), scene.player);
		while (!queue.isEmpty())
		{
			WorldPoint point = queue.removeFirst();
			for (int direction = 0; direction < DIRECTIONS.length; direction++)
			{
				if (!scene.canStep(point.getX(), point.getY(), point.getPlane(), direction))
				{
					continue;
				}
				int nx = point.getX() + DIRECTIONS[direction][0];
				int ny = point.getY() + DIRECTIONS[direction][1];
				int key = pack(nx, ny);
				WorldPoint next = new WorldPoint(nx, ny, point.getPlane());
				if (scene.player.distanceTo(next) <= bounded && !visited.containsKey(key))
				{
					visited.put(key, next);
					queue.addLast(next);
				}
			}
		}
		return new ArrayList<>(visited.values());
	}

	private static int heuristic(int x, int y, WorldPoint goal)
	{
		return Math.abs(x - goal.getX()) + Math.abs(y - goal.getY());
	}

	private static List<WorldPoint> reconstruct(int position, Map<Integer, Integer> parent, int plane)
	{
		List<WorldPoint> route = new ArrayList<>();
		Integer current = position;
		while (current != null)
		{
			route.add(new WorldPoint(x(current), y(current), plane));
			current = parent.get(current);
		}
		Collections.reverse(route);
		return route;
	}

	private static int pack(int x, int y)
	{
		return (x & 0xffff) | (y << 16);
	}

	private static int x(int packed)
	{
		return packed & 0xffff;
	}

	private static int y(int packed)
	{
		return (packed >>> 16) & 0xffff;
	}
}
