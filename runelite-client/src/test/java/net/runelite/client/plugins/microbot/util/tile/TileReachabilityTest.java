package net.runelite.client.plugins.microbot.util.tile;

import net.runelite.api.CollisionDataFlag;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Upstream 2.6.38's scene reachability rules in {@link TileReachability}, over hand-built collision flags. */
public class TileReachabilityTest
{
	private static int[][] open()
	{
		return new int[TileReachability.FLAG_DATA_SIZE][TileReachability.FLAG_DATA_SIZE];
	}

	@Test
	public void floodStopsAtFullTilesAndRingedAreas()
	{
		int[][] flags = open();
		// A closed ring of full tiles around (50, 50).
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				if (dx != 0 || dy != 0)
				{
					flags[50 + dx][50 + dy] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
				}
			}
		}
		boolean[][] reached = TileReachability.reachableFrom(flags, 10, 10);
		assertTrue(reached[90][90]);
		assertFalse("inside the ring", reached[50][50]);
		assertFalse("full tile", reached[49][50]);
		assertFalse("start off-scene reaches nothing", TileReachability.reachableFrom(flags, -1, 0)[0][0]);
	}

	@Test
	public void stepRuleBlocksWallsAndCutCorners()
	{
		int[][] flags = open();
		assertTrue(TileReachability.isStepAllowed(flags, 10, 10, 1, 1));
		flags[10][10] = CollisionDataFlag.BLOCK_MOVEMENT_EAST;
		assertFalse("east wall", TileReachability.isStepAllowed(flags, 10, 10, 1, 0));
		assertFalse("diagonal needs the east side open", TileReachability.isStepAllowed(flags, 10, 10, 1, 1));
		assertTrue(TileReachability.isStepAllowed(flags, 10, 10, 0, 1));
		flags[10][10] = 0;
		flags[11][10] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		assertFalse("cuts through a full tile", TileReachability.isStepAllowed(flags, 10, 10, 1, 1));
		assertTrue(TileReachability.isStepAllowed(flags, 10, 10, 0, 0));
	}

	@Test
	public void objectsAreUsableFromAReachedNeighbourUnlessAWallFacesAway()
	{
		int[][] flags = open();
		boolean[][] reached = new boolean[TileReachability.FLAG_DATA_SIZE][TileReachability.FLAG_DATA_SIZE];
		reached[19][20] = true;
		flags[20][20] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		assertTrue("west neighbour reached", TileReachability.canInteractFromReachableTile(flags, reached, 20, 20, 0));
		flags[20][20] |= CollisionDataFlag.BLOCK_MOVEMENT_WEST;
		assertFalse("blocked on the west face", TileReachability.canInteractFromReachableTile(flags, reached, 20, 20, 0));
		assertTrue("a west wall faces the reached tile", TileReachability.canInteractFromReachableTile(flags, reached, 20, 20, 1));
		assertTrue(TileReachability.canInteractWithFootprintFromReachableTile(open(), reached, 20, 20, 21, 21));
		assertFalse(TileReachability.canInteractWithFootprintFromReachableTile(open(), reached, 30, 30, 31, 31));
		assertFalse(TileReachability.canInteractFromReachableTile(flags, reached, -1, 0, 0));
	}
}
