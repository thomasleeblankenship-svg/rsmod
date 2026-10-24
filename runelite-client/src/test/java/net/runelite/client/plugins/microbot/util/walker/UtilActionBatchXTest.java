package net.runelite.client.plugins.microbot.util.walker;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util action batch X: walker public API. */
public class UtilActionBatchXTest
{
	private static final WorldPoint A = new WorldPoint(3200, 3200, 0);
	private static final WorldPoint B = new WorldPoint(3300, 3300, 0);

	@Test
	public void restartingPathfindingReportsWhetherARouteExists()
	{
		try (MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class, CALLS_REAL_METHODS))
		{
			walker.when(() -> Rs2Walker.getWalkPath(A, B)).thenReturn(List.of(A, B));
			walker.when(() -> Rs2Walker.getWalkPath(B, A)).thenReturn(List.of());
			assertTrue(Rs2Walker.restartPathfinding(A, B));
			assertFalse(Rs2Walker.restartPathfinding(B, A));
			assertTrue(Rs2Walker.restartPathfinding(A, Set.of(B)));
			assertFalse(Rs2Walker.restartPathfinding(null, B));
		}
	}

	@Test
	public void noMissingTransportItemsMeansNoBankTrip()
	{
		try (MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class))
		{
			walker.when(() -> Rs2Walker.getTransportsForDestination(B, true)).thenReturn(List.of());
			walker.when(() -> Rs2Walker.getMissingTransportItemIdsWithQuantities(anyList())).thenReturn(Map.of());
			walker.when(() -> Rs2Walker.walkWithState(B, 3)).thenReturn(WalkerState.ARRIVED);
			assertTrue(Rs2Walker.walkWithBankedTransports(B, 3, true));
			bank.verifyNoInteractions();
		}
	}

	@Test
	public void forcedBankingWithdrawsTheMissingItemsFirst()
	{
		try (MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class))
		{
			walker.when(() -> Rs2Walker.getTransportsForDestination(B, true)).thenReturn(List.of());
			walker.when(() -> Rs2Walker.getMissingTransportItemIdsWithQuantities(anyList())).thenReturn(Map.of(8007, 1));
			walker.when(() -> Rs2Walker.walkWithState(B, 3)).thenReturn(WalkerState.ARRIVED);
			bank.when(Rs2Bank::isOpen).thenReturn(true);
			bank.when(() -> Rs2Bank.hasItem(8007)).thenReturn(true);
			assertEquals(WalkerState.ARRIVED, Rs2Walker.walkWithBankedTransportsAndState(B, 3, true));
			bank.verify(() -> Rs2Bank.withdrawX(8007, 1));
			bank.verify(Rs2Bank::closeBank);
		}
		assertEquals(WalkerState.EXIT, Rs2Walker.walkWithBankedTransportsAndState(null, 3, false));
	}
}
