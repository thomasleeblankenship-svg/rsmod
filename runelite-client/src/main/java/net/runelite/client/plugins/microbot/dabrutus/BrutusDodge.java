package net.runelite.client.plugins.microbot.dabrutus;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Dodge geometry for Brutus's two specials, on scene coordinates (the same inside his instance). Pure, so it is unit
 * tested ({@code BrutusDodgeTest}).
 *
 * <p>From the wiki (Brutus, Brutus/Strategies) and the three reference scripts (KSP Mad Cow, Niri Brutus, osrsx-brutus):
 * <ul>
 *   <li><b>Charge</b> ("*growls*"): a 3-tick warning, then he dashes about 4 tiles straight at the player, facing the way
 *   he charges. Anything in the 3-wide lane he sweeps is hit (up to 19, ignores prayer). Two tiles sideways clears it.</li>
 *   <li><b>Slam</b> ("*snorts*"): a 4-tick warning, then 1-3 slams on the tiles next to him on the player's side: a 3x1
 *   row when the player is next to an edge, an L when the player is at a corner. One tile back clears it.</li>
 * </ul>
 * Brutus is 3x3. Players walk through NPCs, so his footprint only stops us from standing in it, not from passing.
 */
final class BrutusDodge
{
	/** Brutus's size in tiles. */
	static final int SIZE = 3;
	/** Tiles the charge lane is drawn past his footprint: a 4-tile dash plus one tile of margin. */
	static final int CHARGE_LENGTH = 5;
	/** How far from his footprint the slam can land on the player's side (wiki: 1; 2 is margin). */
	static final int SLAM_DEPTH = 2;
	/** Steps a dodge may take: 3 ticks running (2 tiles a tick) minus about a tick of reaction. */
	static final int MAX_CHARGE_STEPS = 4;
	static final int MAX_SLAM_STEPS = 6;

	/** Which way a step between two tiles is possible (collision). */
	interface Walls
	{
		/** True when the player can step from (x, y) to the cardinal neighbour (x + dx, y + dy). */
		boolean canStep(int x, int y, int dx, int dy);
	}

	private BrutusDodge()
	{
	}

	static long key(int x, int y)
	{
		return ((long) x << 32) | (y & 0xffffffffL);
	}

	static int kx(long key)
	{
		return (int) (key >> 32);
	}

	static int ky(long key)
	{
		return (int) key;
	}

	/** Unit vector of an NPC orientation (0 south, 512 west, 1024 north, 1536 east; 8 directions). */
	static int[] facing(int orientation)
	{
		int normalized = ((orientation % 2048) + 2048) % 2048;
		switch (((normalized + 128) / 256) & 7)
		{
			case 0:
				return new int[]{0, -1};
			case 1:
				return new int[]{-1, -1};
			case 2:
				return new int[]{-1, 0};
			case 3:
				return new int[]{-1, 1};
			case 4:
				return new int[]{0, 1};
			case 5:
				return new int[]{1, 1};
			case 6:
				return new int[]{1, 0};
			default:
				return new int[]{1, -1};
		}
	}

	/** Direction (8-way) from the footprint at (minX, minY) towards the player; {0,0} when the player is inside it. */
	static int[] toward(int minX, int minY, int px, int py)
	{
		return new int[]{Integer.signum(offset(px, minX)), Integer.signum(offset(py, minY))};
	}

	/** Distance of {@code p} outside the span [min, min + SIZE - 1] (negative below, 0 inside, positive above). */
	static int offset(int p, int min)
	{
		int max = min + SIZE - 1;
		return p < min ? p - min : p > max ? p - max : 0;
	}

	/** Chebyshev distance of (x, y) from the footprint; 0 inside it. */
	static int ring(int minX, int minY, int x, int y)
	{
		return Math.max(Math.abs(offset(x, minX)), Math.abs(offset(y, minY)));
	}

	/** Tiles the charge can hit: his footprint swept {@link #CHARGE_LENGTH} tiles along each direction given. */
	static Set<Long> chargeDanger(int minX, int minY, int[]... dirs)
	{
		Set<Long> out = new HashSet<>();
		for (int[] d : dirs)
		{
			if (d == null || (d[0] == 0 && d[1] == 0))
			{
				continue;
			}
			for (int k = 0; k <= CHARGE_LENGTH; k++)
			{
				for (int x = 0; x < SIZE; x++)
				{
					for (int y = 0; y < SIZE; y++)
					{
						out.add(key(minX + x + k * d[0], minY + y + k * d[1]));
					}
				}
			}
		}
		return out;
	}

	/**
	 * Tiles the slam can hit for a player at (px, py): the band up to {@link #SLAM_DEPTH} deep along each edge the player is
	 * past (one edge: the 3x1 row; a corner: both edges, the L), corners included.
	 */
	static Set<Long> slamDanger(int minX, int minY, int px, int py)
	{
		Set<Long> out = new HashSet<>();
		int sx = Integer.signum(offset(px, minX));
		int sy = Integer.signum(offset(py, minY));
		int maxX = minX + SIZE - 1;
		int maxY = minY + SIZE - 1;
		if (sx == 0 && sy == 0)
		{
			// Under him: every side is "his side".
			for (int x = minX - SLAM_DEPTH; x <= maxX + SLAM_DEPTH; x++)
			{
				for (int y = minY - SLAM_DEPTH; y <= maxY + SLAM_DEPTH; y++)
				{
					out.add(key(x, y));
				}
			}
			return out;
		}
		for (int depth = 1; depth <= SLAM_DEPTH; depth++)
		{
			if (sx != 0)
			{
				int x = sx < 0 ? minX - depth : maxX + depth;
				for (int y = minY - SLAM_DEPTH; y <= maxY + SLAM_DEPTH; y++)
				{
					out.add(key(x, y));
				}
			}
			if (sy != 0)
			{
				int y = sy < 0 ? minY - depth : maxY + depth;
				for (int x = minX - SLAM_DEPTH; x <= maxX + SLAM_DEPTH; x++)
				{
					out.add(key(x, y));
				}
			}
		}
		return out;
	}

	/** True when (x, y) is next to an edge of the footprint (not a corner): a melee attack works from there. */
	static boolean attackable(int minX, int minY, int x, int y)
	{
		int ox = offset(x, minX);
		int oy = offset(y, minY);
		return (Math.abs(ox) == 1 && oy == 0) || (ox == 0 && Math.abs(oy) == 1);
	}

	/**
	 * Steps from (px, py) to every tile within {@code maxSteps}, 8-way like the game (a diagonal needs both of its
	 * cardinal steps open).
	 */
	static Map<Long, Integer> reach(int px, int py, int maxSteps, Walls walls)
	{
		Map<Long, Integer> dist = new HashMap<>();
		ArrayDeque<long[]> queue = new ArrayDeque<>();
		dist.put(key(px, py), 0);
		queue.add(new long[]{px, py, 0});
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
		while (!queue.isEmpty())
		{
			long[] c = queue.poll();
			int x = (int) c[0];
			int y = (int) c[1];
			int d = (int) c[2];
			if (d >= maxSteps)
			{
				continue;
			}
			for (int[] dir : dirs)
			{
				boolean ok;
				if (dir[0] != 0 && dir[1] != 0)
				{
					ok = walls.canStep(x, y, dir[0], 0) && walls.canStep(x + dir[0], y, 0, dir[1])
						&& walls.canStep(x, y, 0, dir[1]) && walls.canStep(x, y + dir[1], dir[0], 0);
				}
				else
				{
					ok = walls.canStep(x, y, dir[0], dir[1]);
				}
				long k = key(x + dir[0], y + dir[1]);
				if (ok && !dist.containsKey(k))
				{
					dist.put(k, d + 1);
					queue.add(new long[]{x + dir[0], y + dir[1], d + 1});
				}
			}
		}
		return dist;
	}

	/**
	 * The tile to dodge to: reachable within {@code maxSteps}, not in {@code danger}, not under him, and (for a slam)
	 * at least {@code minRing} from his footprint. Fewest steps wins; ties go to a tile we can attack from
	 * ({@code preferAttackable}), then to the one closest to him. Null when there is none; {px, py} itself when the
	 * player is already safe.
	 */
	static int[] pick(int px, int py, int minX, int minY, Set<Long> danger, int minRing, int maxSteps,
		boolean preferAttackable, Walls walls)
	{
		if (!danger.contains(key(px, py)) && ring(minX, minY, px, py) >= Math.max(1, minRing))
		{
			return new int[]{px, py};
		}
		Map<Long, Integer> dist = reach(px, py, maxSteps, walls);
		long best = 0;
		int bestScore = Integer.MAX_VALUE;
		boolean found = false;
		for (Map.Entry<Long, Integer> e : dist.entrySet())
		{
			long k = e.getKey();
			int x = kx(k);
			int y = ky(k);
			int r = ring(minX, minY, x, y);
			if (danger.contains(k) || r < Math.max(1, minRing))
			{
				continue;
			}
			int score = e.getValue() * 100 + r * 10;
			if (preferAttackable && attackable(minX, minY, x, y))
			{
				score -= 50;
			}
			// Stable tie-break so the choice does not flicker between equal tiles.
			score = score * 4 + (x > px ? 0 : 1) + (y > py ? 0 : 2);
			if (score < bestScore)
			{
				bestScore = score;
				best = k;
				found = true;
			}
		}
		return found ? new int[]{kx(best), ky(best)} : null;
	}
}
