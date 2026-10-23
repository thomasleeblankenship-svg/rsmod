package net.runelite.client.plugins.microbot.util.walker;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class LumbridgeStairsTest
{
	@Test
	public void groundToBankPlansAnObservedFirstFloorLanding()
	{
		WorldPoint player = new WorldPoint(3206, 3207, 0);
		WorldPoint bank = new WorldPoint(3209, 3220, 2);
		List<LumbridgeStairs.Step> candidates = LumbridgeStairs.candidates(player, bank);
		assertEquals(2, candidates.size());
		LumbridgeStairs.Step stairs = candidates.get(0);
		assertEquals(new WorldPoint(3206, 3208, 0), stairs.origin);
		assertEquals(new WorldPoint(3205, 3209, 1), stairs.arrival);
		assertEquals(56230, stairs.objectId);
		assertEquals("Climb-up", stairs.action);
		assertFalse(stairs.observedArrival(player));
		assertFalse(stairs.observedArrival(new WorldPoint(3206, 3208, 0)));
		assertTrue(stairs.observedArrival(new WorldPoint(3205, 3209, 1)));
		assertFalse(stairs.observedArrival(new WorldPoint(3000, 3000, 1)));
	}

	@Test
	public void firstFloorContinuesToBankOnSecondFloor()
	{
		LumbridgeStairs.Step stairs = LumbridgeStairs.candidates(new WorldPoint(3205, 3209, 1),
			new WorldPoint(3209, 3220, 2)).get(0);
		assertEquals(16672, stairs.objectId);
		assertEquals("Climb-up", stairs.action);
		assertEquals(2, stairs.arrival.getPlane());
		assertTrue(stairs.observedArrival(new WorldPoint(3205, 3209, 2)));
	}

	@Test
	public void bankToGroundPlansBothDownstairsTransitions()
	{
		WorldPoint ground = new WorldPoint(3200, 3240, 0);
		LumbridgeStairs.Step upper = LumbridgeStairs.candidates(new WorldPoint(3209, 3220, 2), ground).get(0);
		assertEquals(56231, upper.objectId);
		assertEquals("Climb-down", upper.action);
		assertEquals(1, upper.arrival.getPlane());
		LumbridgeStairs.Step lower = LumbridgeStairs.candidates(upper.arrival, ground).get(0);
		assertEquals(16672, lower.objectId);
		assertEquals("Climb-down", lower.action);
		assertEquals(0, lower.arrival.getPlane());
	}

	@Test
	public void nearbyNorthStaircaseIsPreferredOverSouthStaircase()
	{
		LumbridgeStairs.Step stairs = LumbridgeStairs.candidates(new WorldPoint(3204, 3230, 0),
			new WorldPoint(3209, 3220, 2)).get(0);
		assertEquals(new WorldPoint(3205, 3228, 0), stairs.origin);
		assertEquals(new WorldPoint(3206, 3229, 1), stairs.arrival);
	}

	@Test
	public void unsupportedUpstairsDestinationsAndPlaneThreeAreRefused()
	{
		WorldPoint ground = new WorldPoint(3200, 3200, 0);
		assertFalse(LumbridgeStairs.supported(ground, new WorldPoint(3000, 3000, 2)));
		assertTrue(LumbridgeStairs.candidates(ground, new WorldPoint(3000, 3000, 2)).isEmpty());
		assertFalse(LumbridgeStairs.supported(new WorldPoint(3000, 3000, 1), ground));
		assertFalse(LumbridgeStairs.supported(ground, new WorldPoint(3209, 3220, 3)));
		assertFalse(LumbridgeStairs.supported(ground, ground));
	}
}
