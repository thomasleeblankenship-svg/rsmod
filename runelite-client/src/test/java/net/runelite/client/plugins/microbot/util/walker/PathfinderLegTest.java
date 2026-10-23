package net.runelite.client.plugins.microbot.util.walker;

import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.MobilePathfinder;
import net.runelite.client.plugins.microbot.shortestpath.PathResult;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.Test;
import org.mockito.MockedStatic;
import static net.runelite.client.plugins.microbot.shortestpath.PathResultFixtures.result;
import static net.runelite.client.plugins.microbot.shortestpath.PathResultFixtures.transport;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/** How the walker turns a pathfinder route into its next leg. */
public class PathfinderLegTest
{
	private static final WorldPoint A = new WorldPoint(3200, 3200, 0);
	private static final WorldPoint B = new WorldPoint(3201, 3200, 0);
	private static final WorldPoint DOOR = new WorldPoint(3202, 3200, 0);
	private static final WorldPoint INSIDE = new WorldPoint(3203, 3200, 0);
	private static final WorldPoint GOAL = new WorldPoint(3204, 3200, 0);
	private static final List<WorldPoint> TILES = List.of(A, B, DOOR, INSIDE, GOAL);

	private static Rs2Walker.WalkLeg legFor(PathResult route)
	{
		try (MockedStatic<MobilePathfinder> pathfinder = mockStatic(MobilePathfinder.class))
		{
			pathfinder.when(() -> MobilePathfinder.find(any(), any())).thenReturn(route);
			return Rs2Walker.pathfinderLeg(A, GOAL, 0);
		}
	}

	@Test
	public void aRouteWithoutTransportsIsOneWalkingLeg()
	{
		Rs2Walker.WalkLeg leg = legFor(result(PathResult.Status.FOUND, TILES, Map.of()));
		assertNotNull(leg);
		assertNull(leg.stairs);
		assertEquals(GOAL, leg.destination);
		assertEquals(TILES, leg.route);
		assertTrue(leg.fromPathfinder);
	}

	@Test
	public void theFirstTransportEndsTheLegAndMustLandWithinOneTile()
	{
		Rs2Walker.WalkLeg leg = legFor(result(PathResult.Status.FOUND, TILES,
			Map.of(2, transport(DOOR, INSIDE, TransportType.TRANSPORT, "Open", "Door", 1530))));
		assertNotNull(leg);
		assertEquals(DOOR, leg.destination);
		assertEquals(List.of(A, B, DOOR), leg.route);
		assertEquals("Open", leg.stairs.action);
		assertEquals(1530, leg.stairs.objectId);
		assertEquals(1, leg.stairs.arrivalRadius);
		assertFalse("two tiles past the door is not arrival", leg.stairs.observedArrival(GOAL.dx(1)));
		assertTrue(leg.stairs.observedArrival(INSIDE));
	}

	@Test
	public void routesNeedingATransportTheWalkerCannotExecuteFallBack()
	{
		assertNull(legFor(result(PathResult.Status.FOUND, TILES,
			Map.of(1, transport(B, GOAL, TransportType.BOAT, "Travel", "Sailor", -1)))));
	}

	@Test
	public void incompleteRoutesFallBack()
	{
		assertNull(legFor(result(PathResult.Status.PARTIAL, TILES, Map.of())));
		assertNull(legFor(result(PathResult.Status.TIMED_OUT, TILES, Map.of())));
	}
}
