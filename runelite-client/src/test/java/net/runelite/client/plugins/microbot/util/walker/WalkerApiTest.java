package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Upstream walker API surface on mobile: single-tap walks, state-reporting walks, route clearing. */
public class WalkerApiTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final WorldPoint POINT = new WorldPoint(3210, 3220, 0);

	@After
	public void tearDown()
	{
		Rs2Walker.setTarget(null);
	}

	@Test
	public void walkMiniMapIsOneMinimapTap()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.walkMinimap(POINT)).thenReturn(true);
			actions.clearInvocations();
			assertTrue(Rs2Walker.walkMiniMap(POINT));
			assertTrue("the zoom argument is accepted and ignored", Rs2Walker.walkMiniMap(POINT, 2.0));
			actions.verify(() -> MobileActions.walkMinimap(POINT), times(2));
			assertFalse(Rs2Walker.walkMiniMap(null));
		}
	}

	@Test
	public void walkCanvasReturnsThePointOnlyWhenTheWalkWasDispatched()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.walk(POINT)).thenReturn(true);
			assertEquals(POINT, Rs2Walker.walkCanvas(POINT));
			actions.when(() -> MobileActions.walk(POINT)).thenReturn(false);
			assertNull(Rs2Walker.walkCanvas(POINT));
		}
	}

	@Test
	public void walkWithStateReportsArrivalCancellationAndNoRoute()
	{
		try (MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class, CALLS_REAL_METHODS);
			MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(MobileActions::active).thenReturn(true);
			walker.when(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt())).thenAnswer(call ->
			{
				Rs2Walker.setTarget(call.getArgument(0)); // a walk claims one generation, as the real one does
				return true;
			});
			assertEquals(WalkerState.ARRIVED, Rs2Walker.walkWithState(POINT, 2));

			walker.when(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt())).thenAnswer(call ->
			{
				Rs2Walker.setTarget(call.getArgument(0));
				return false;
			});
			assertEquals("a failed walk nobody cancelled has no route", WalkerState.UNREACHABLE, Rs2Walker.walkWithState(POINT));

			walker.when(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt())).thenAnswer(call ->
			{
				Rs2Walker.setTarget(call.getArgument(0));
				Rs2Walker.clearWalkingRoute("test: another caller cancels");
				return false;
			});
			assertEquals(WalkerState.EXIT, Rs2Walker.walkWithState(POINT, 2));
			assertEquals(WalkerState.EXIT, Rs2Walker.walkWithState(null, 2));
		}
	}

	@Test
	public void aConditionThatAlreadyHoldsArrivesWithoutWalking()
	{
		try (MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class, CALLS_REAL_METHODS))
		{
			assertEquals(WalkerState.ARRIVED, Rs2Walker.walkWithStateUntil(POINT, 2, () -> true));
			walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never());
		}
	}

	@Test
	public void clearWalkingRouteForgetsTheCurrentTarget()
	{
		Rs2Walker.setTarget(POINT);
		assertEquals(POINT, Rs2Walker.getCurrentTarget());
		Rs2Walker.clearWalkingRoute(null);
		assertNull(Rs2Walker.getCurrentTarget());
	}
}
