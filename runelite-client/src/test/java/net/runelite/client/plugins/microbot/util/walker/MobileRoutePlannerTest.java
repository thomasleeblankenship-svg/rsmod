package net.runelite.client.plugins.microbot.util.walker;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class MobileRoutePlannerTest
{
	private static WorldPoint point(int x, int y)
	{
		return new WorldPoint(x, y, 0);
	}

	private static MobileRoutePlanner.Scene scene(int[][] flags, WorldPoint start)
	{
		return new MobileRoutePlanner.Scene(0, 0, 0, flags, start, false);
	}

	@Test
	public void pathGoesAroundBlockedTileInsteadOfCuttingThrough()
	{
		int[][] flags = new int[5][5];
		flags[2][2] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		MobileRoutePlanner.Scene scene = scene(flags, point(1, 2));
		List<WorldPoint> path = MobileRoutePlanner.route(point(1, 2), point(3, 2), 0, scene::canStep);
		assertEquals(point(1, 2), path.get(0));
		assertEquals(point(3, 2), path.get(path.size() - 1));
		assertFalse(path.contains(point(2, 2)));
		assertEquals(5, path.size());
	}

	@Test
	public void wallsBlockBothSidesOfAnEdge()
	{
		int[][] flags = new int[2][1];
		flags[1][0] = CollisionDataFlag.BLOCK_MOVEMENT_WEST;
		MobileRoutePlanner.Scene scene = scene(flags, point(0, 0));
		assertFalse(scene.canStep(0, 0, 0, 1));
		assertFalse(scene.canStep(1, 0, 0, 3));
		assertTrue(MobileRoutePlanner.route(point(0, 0), point(1, 0), 0, scene::canStep).isEmpty());
	}

	@Test
	public void cornerCuttingAndPlaneChangesAreRefused()
	{
		int[][] flags = new int[2][2];
		flags[0][1] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		flags[1][0] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		MobileRoutePlanner.Scene scene = scene(flags, point(0, 0));
		assertTrue(MobileRoutePlanner.route(point(0, 0), point(1, 1), 0, scene::canStep).isEmpty());
		assertTrue(MobileRoutePlanner.route(point(0, 0), new WorldPoint(0, 0, 1), 0, scene::canStep).isEmpty());
	}

	@Test
	public void radiusArrivalReturnsReachedTileRatherThanBlockedGoal()
	{
		int[][] flags = new int[5][1];
		flags[4][0] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		MobileRoutePlanner.Scene scene = scene(flags, point(0, 0));
		List<WorldPoint> path = MobileRoutePlanner.route(point(0, 0), point(4, 0), 1, scene::canStep);
		assertEquals(point(3, 0), path.get(path.size() - 1));
	}

	@Test
	public void reachableTilesExcludeWallsAndRespectRadius()
	{
		int[][] flags = new int[5][5];
		flags[2][3] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		List<WorldPoint> reachable = MobileRoutePlanner.reachable(scene(flags, point(2, 2)), 1);
		assertFalse(reachable.contains(point(2, 3)));
		assertTrue(reachable.contains(point(1, 2)));
		assertFalse(reachable.contains(point(0, 2)));
		assertEquals(8, reachable.size());
	}

	@Test
	public void staticMapDecoderCrossesRegionBoundaryAndFailsForMissingEdges()
	{
		Map<Integer, BitSet> regions = new HashMap<>();
		BitSet west = new BitSet();
		west.set(((10 * 64 + 63) * 2) + 1);
		regions.put(0, west);
		MobileRoutePlanner.WorldEdges edges = new MobileRoutePlanner.WorldEdges(regions);
		assertTrue(edges.canStep(63, 10, 0, 1));
		assertTrue(edges.canStep(64, 10, 0, 3));
		assertFalse(edges.canStep(64, 10, 0, 1));
		assertFalse(edges.canStep(63, 10, 1, 1));
		List<WorldPoint> path = MobileRoutePlanner.route(point(63, 10), point(64, 10), 0, edges);
		assertEquals(2, path.size());
	}

	@Test
	public void packagedMapLoadsAndKnownWalkingEdgesAreBidirectional()
	{
		MobileRoutePlanner.WorldEdges edges = MobileRoutePlanner.worldEdges();
		for (int x = 3200; x < 3264; x++)
		{
			for (int y = 3200; y < 3264; y++)
			{
				if (edges.canStep(x, y, 0, 0))
				{
					assertTrue(edges.canStep(x, y + 1, 0, 2));
					assertEquals(2, MobileRoutePlanner.route(point(x, y), point(x, y + 1), 0, edges).size());
					return;
				}
			}
		}
		fail("Packaged Lumbridge collision region must include walking edges");
	}

	@Test
	public void cancelledSearchDoesNotExploreLargeGraph()
	{
		Thread.currentThread().interrupt();
		try
		{
			assertTrue(MobileRoutePlanner.route(point(1, 1), point(3000, 3000), 0,
				(x, y, plane, direction) -> x >= 0 && y >= 0).isEmpty());
		}
		finally
		{
			Thread.interrupted();
		}
	}
}
