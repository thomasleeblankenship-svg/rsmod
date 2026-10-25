package net.runelite.client.plugins.microbot.shortestpath;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import org.junit.Test;

/**
 * Rs2Bank's "near on foot" rule on the real collision map. Draynor (user report: the bank walk stopped behind
 * the bank and never got inside): the south and east sides are within the 8-tile straight-line check but the
 * route goes round to the entrance, so the script must walk on. Lumbridge top floor: the route round the
 * booths is longer than the straight line but is no wall, so it stays near.
 */
public class DraynorBankWallRouteTest
{
	private static final WorldPoint DRAYNOR = BankLocation.DRAYNOR_VILLAGE.getWorldPoint();

	private static boolean nearOnFoot(WorldPoint from, WorldPoint bank)
	{
		PathResult result = MobilePathfinder.search(MobileRouteTest.offlineConfig(MobileTransportPolicy.DEFAULT), from, bank);
		assertTrue(from + " -> " + result.getStatus(), result.isFound());
		return Rs2Bank.nearOnFoot(result.getTiles().size() - 1, from.distanceTo(bank));
	}

	@Test
	public void behindDraynorsSouthWallIsNotNear()
	{
		WorldPoint south = new WorldPoint(3092, 3238, 0);
		assertTrue("straight-line near", south.distanceTo(DRAYNOR) <= 8);
		assertFalse(nearOnFoot(south, DRAYNOR));
	}

	@Test
	public void outsideDraynorsEastWallIsNotNear()
	{
		assertFalse(nearOnFoot(new WorldPoint(3098, 3242, 0), DRAYNOR));
	}

	@Test
	public void draynorEntranceSideAndInsideAreNear()
	{
		for (WorldPoint near : new WorldPoint[]{new WorldPoint(3090, 3248, 0), new WorldPoint(3086, 3250, 0), new WorldPoint(3094, 3242, 0)})
		{
			assertTrue(near.toString(), nearOnFoot(near, DRAYNOR));
		}
	}

	@Test
	public void lumbridgeTopFloorRoundTheBoothsIsNear()
	{
		assertTrue(nearOnFoot(new WorldPoint(3207, 3226, 2), BankLocation.LUMBRIDGE_TOP.getWorldPoint()));
	}
}
