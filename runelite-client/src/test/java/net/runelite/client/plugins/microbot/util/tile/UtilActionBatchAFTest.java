package net.runelite.client.plugins.microbot.util.tile;

import java.util.List;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Util action batch AF: upstream's scene path search and its checkpoints. */
public class UtilActionBatchAFTest
{
	private static int[][] open()
	{
		return new int[TileReachability.FLAG_DATA_SIZE][TileReachability.FLAG_DATA_SIZE];
	}

	private static void assertTile(int x, int y, int[] tile)
	{
		assertArrayEquals(new int[]{x, y}, tile);
	}

	@Test
	public void openGroundIsWalkedStraight()
	{
		List<int[]> path = TileReachability.scenePath(open(), 10, 10, 15, 10);
		assertEquals(6, path.size());
		assertTile(10, 10, path.get(0));
		assertTile(15, 10, path.get(5));
		List<int[]> checkpoints = TileReachability.checkpoints(path);
		assertEquals(1, checkpoints.size());
		assertTile(15, 10, checkpoints.get(0));
	}

	@Test
	public void aWallIsWalkedAroundAndItsCornersAreCheckpoints()
	{
		int[][] flags = open();
		for (int y = 5; y <= 15; y++)
		{
			flags[12][y] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		}
		List<int[]> path = TileReachability.scenePath(flags, 10, 10, 14, 10);
		assertTile(14, 10, path.get(path.size() - 1));
		assertTrue(path.stream().noneMatch(tile -> tile[0] == 12 && tile[1] >= 5 && tile[1] <= 15));
		List<int[]> checkpoints = TileReachability.checkpoints(path);
		assertTrue(checkpoints.size() > 1);
		assertTile(14, 10, checkpoints.get(checkpoints.size() - 1));
	}

	@Test
	public void anUnreachableTargetEndsAtTheNearestReachedTile()
	{
		int[][] flags = open();
		flags[20][20] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		List<int[]> path = TileReachability.scenePath(flags, 15, 20, 20, 20);
		assertTile(19, 20, path.get(path.size() - 1));
		assertNull(TileReachability.scenePath(null, 1, 1, 2, 2));
		assertNull(TileReachability.checkpoints(null));
	}

	@Test
	public void checkpointsStopAtTwentyFive()
	{
		int[][] flags = open();
		// A comb of walls forces a zig-zag with many turns.
		for (int x = 2; x < 90; x += 2)
		{
			for (int y = 0; y < 103; y++)
			{
				flags[x][(x / 2) % 2 == 0 ? y : y + 1] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
			}
		}
		List<int[]> path = TileReachability.scenePath(flags, 1, 50, 61, 50);
		assertNotNull(path);
		assertEquals(25, TileReachability.checkpoints(path).size());
	}

	@Test
	public void noPathWithoutAClientOrOnOtherFloors()
	{
		assertNull(Rs2Tile.pathTo(null, null));
		assertFalse(Rs2Tile.hoverOverTile(null));
		assertFalse(Rs2Tile.areSurroundingTilesWalkable(null, 1, 1));
		Rs2WorldPoint point = new Rs2WorldPoint(3200, 3200, 0);
		assertNull(point.pathTo(new WorldPoint(3201, 3200, 1)));
		assertEquals(Integer.MAX_VALUE, point.distanceToPath(null));
	}
}
