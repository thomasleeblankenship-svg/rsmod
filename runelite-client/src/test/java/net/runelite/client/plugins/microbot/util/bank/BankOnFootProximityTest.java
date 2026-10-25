package net.runelite.client.plugins.microbot.util.bank;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Bank "near" needs a walking route not much longer than the straight line, so a player behind a bank wall keeps walking. */
public class BankOnFootProximityTest
{
	@Test
	public void routeCloseToTheStraightLineIsNear()
	{
		assertTrue(Rs2Bank.nearOnFoot(2, 1));
		assertTrue(Rs2Bank.nearOnFoot(9, 7));
		assertTrue(Rs2Bank.nearOnFoot(11, 7));
	}

	@Test
	public void detourAroundAWallIsNotNear()
	{
		// Draynor: south side 7 straight, 17-21 walking; outside the east wall 5 straight, 11-15 walking.
		assertFalse(Rs2Bank.nearOnFoot(17, 7));
		assertFalse(Rs2Bank.nearOnFoot(11, 5));
	}

	@Test
	public void noRouteKeepsTheStraightLineAnswer()
	{
		assertTrue(Rs2Bank.nearOnFoot(Integer.MAX_VALUE, 7));
	}
}
