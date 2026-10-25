package net.runelite.client.plugins.microbot.util.bank;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Bank "near" needs a walking route close to the straight line, so a player behind a bank wall keeps walking. */
public class BankOnFootProximityTest
{
	@Test
	public void routeCloseToTheStraightLineIsNear()
	{
		assertTrue(Rs2Bank.nearOnFoot(1, 1));
		assertTrue(Rs2Bank.nearOnFoot(8, 7));
		// Lumbridge top floor, round the booths: 10 steps for 6 straight.
		assertTrue(Rs2Bank.nearOnFoot(10, 6));
	}

	@Test
	public void detourAroundAWallIsNotNear()
	{
		// Draynor: south side 20 steps for 7 straight; outside the east wall 12 for 5.
		assertFalse(Rs2Bank.nearOnFoot(20, 7));
		assertFalse(Rs2Bank.nearOnFoot(12, 5));
	}

	@Test
	public void noRouteKeepsTheStraightLineAnswer()
	{
		assertTrue(Rs2Bank.nearOnFoot(Integer.MAX_VALUE, 7));
	}
}
