package net.runelite.client.plugins.microbot.shortestpath;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/** Upstream walker path reads over the real collision map; no game client. */
public class WalkerPathReadsTest
{
	private static final WorldPoint DRAYNOR_BANK = new WorldPoint(3092, 3245, 0);
	/** Walled pocket south of Draynor bank (see MobileRouteTest's collision probe). */
	private static final WorldPoint DRAYNOR_ENCLOSED = new WorldPoint(3088, 3235, 0);

	private static PathfinderConfig config;
	private static CollisionMap map;

	@BeforeClass
	public static void load()
	{
		config = MobileRouteTest.offlineConfig(MobileTransportPolicy.DEFAULT);
		map = config.getMap();
	}

	@Test
	public void totalTilesNeedThePathToEndNearTheDestination()
	{
		WorldPoint a = new WorldPoint(3200, 3200, 0);
		List<WorldPoint> path = List.of(a, a.dx(1), a.dx(2));
		assertEquals(3, WalkerPathReads.getTotalTilesFromPath(path, a.dx(9)));
		assertEquals(Integer.MAX_VALUE, WalkerPathReads.getTotalTilesFromPath(path, a.dx(10)));
		assertEquals("other floor", Integer.MAX_VALUE,
			WalkerPathReads.getTotalTilesFromPath(path, new WorldPoint(3202, 3200, 1)));
		assertEquals(Integer.MAX_VALUE, WalkerPathReads.getTotalTilesFromPath(List.of(), a));
		assertEquals(Integer.MAX_VALUE, WalkerPathReads.getTotalTiles(a, null));
	}

	@Test
	public void collisionMapWalkabilityNeedsAMappedOpenTile()
	{
		assertTrue(WalkerPathReads.isWalkableInCollisionMap(map, DRAYNOR_BANK));
		assertFalse("unmapped region", WalkerPathReads.isWalkableInCollisionMap(map, new WorldPoint(0, 0, 0)));
		assertFalse(WalkerPathReads.isWalkableInCollisionMap(map, null));
	}

	@Test
	public void closestIndexPrefersStepsOverStraightLineDistance()
	{
		PathResult east = MobilePathfinder.search(config, DRAYNOR_BANK, new WorldPoint(3092, 3260, 0));
		List<WorldPoint> route = east.getTiles();
		assertTrue(east.isFound());
		WorldPoint open = route.get(Math.min(route.size() - 1, 14));
		assertTrue("reachable within the step budget", route.indexOf(open) <= WalkerPathReads.CLOSEST_INDEX_STEP_BUDGET);
		assertTrue("the pocket is nearer in a straight line",
			DRAYNOR_BANK.distanceTo(DRAYNOR_ENCLOSED) < DRAYNOR_BANK.distanceTo(open));

		assertEquals(1, WalkerPathReads.getClosestTileIndex(List.of(DRAYNOR_ENCLOSED, open), DRAYNOR_BANK, map));
		assertEquals("without a map: straight line", 0,
			WalkerPathReads.getClosestTileIndex(List.of(DRAYNOR_ENCLOSED, open), DRAYNOR_BANK, null));
		assertEquals("standing on the path", 3, WalkerPathReads.getClosestTileIndex(route, route.get(3), map));
		assertEquals(-1, WalkerPathReads.getClosestTileIndex(List.of(), DRAYNOR_BANK, map));
		assertEquals(-1, WalkerPathReads.getClosestTileIndex(route, null, map));
	}
}
