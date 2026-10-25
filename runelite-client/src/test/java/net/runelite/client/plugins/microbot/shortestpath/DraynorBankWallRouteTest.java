package net.runelite.client.plugins.microbot.shortestpath;

import static org.junit.Assert.assertTrue;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/**
 * Draynor bank (user report: the bank walk stopped behind the bank and never got inside). From the south side
 * (the willows side) the bank tile is within the 8-tile straight-line "near" check, but the walking route goes
 * round to the entrance and is much longer; {@code Rs2Bank.isNearBankOnFoot} relies on that difference. From
 * the entrance side the route stays close to the straight line.
 */
public class DraynorBankWallRouteTest
{
	private static final WorldPoint BANK = new WorldPoint(3093, 3245, 0);
	private static final int SLACK = 4;

	private static int routeTiles(WorldPoint from)
	{
		PathResult result = MobilePathfinder.search(MobileRouteTest.offlineConfig(MobileTransportPolicy.DEFAULT), from, BANK);
		assertTrue(from + " -> " + result.getStatus(), result.isFound());
		return result.getTiles().size();
	}

	@Test
	public void behindTheSouthWallIsADetour()
	{
		WorldPoint south = new WorldPoint(3092, 3238, 0);
		assertTrue("straight-line near", south.distanceTo(BANK) <= 8);
		assertTrue(routeTiles(south) - south.distanceTo(BANK) > SLACK);
	}

	@Test
	public void outsideTheEastWallIsADetour()
	{
		WorldPoint east = new WorldPoint(3098, 3242, 0);
		assertTrue(routeTiles(east) - east.distanceTo(BANK) > SLACK);
	}

	@Test
	public void entranceSideAndInsideAreNear()
	{
		for (WorldPoint near : new WorldPoint[]{new WorldPoint(3090, 3248, 0), new WorldPoint(3086, 3250, 0), new WorldPoint(3094, 3242, 0)})
		{
			assertTrue(near.toString(), routeTiles(near) - near.distanceTo(BANK) <= SLACK);
		}
	}
}
