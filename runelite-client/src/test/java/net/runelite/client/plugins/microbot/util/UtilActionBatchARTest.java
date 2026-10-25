package net.runelite.client.plugins.microbot.util;

import java.util.List;
import java.util.concurrent.Callable;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Batch AR: bank route reads. */
public class UtilActionBatchARTest
{
	/** Runs client-thread reads inline, so a free-to-play bank's requirements pass without a client. */
	private static MockedStatic<MobileActions> inlineReads()
	{
		MockedStatic<MobileActions> actions = mockStatic(MobileActions.class);
		actions.when(() -> MobileActions.read(any(), any())).thenAnswer(invocation -> ((Callable<?>) invocation.getArgument(0)).call());
		return actions;
	}

	@Test
	public void aBankInsideTheSearchRadiusWins()
	{
		WorldPoint nearDraynor = BankLocation.DRAYNOR_VILLAGE.getWorldPoint().dx(3);
		try (MockedStatic<MobileActions> actions = inlineReads())
		{
			assertEquals(BankLocation.DRAYNOR_VILLAGE, Rs2Bank.getNearestBank(nearDraynor, 10));
		}
		assertNull(Rs2Bank.getNearestBank(null, 10));
	}

	@Test
	public void routesCarryThePathAndAreNullWithoutOne()
	{
		WorldPoint start = BankLocation.DRAYNOR_VILLAGE.getWorldPoint().dx(3);
		try (MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
			MockedStatic<MobileActions> actions = inlineReads())
		{
			walker.when(() -> Rs2Walker.getWalkPath(any(WorldPoint.class), any(WorldPoint.class)))
				.thenReturn(List.of(start, BankLocation.DRAYNOR_VILLAGE.getWorldPoint()));
			Rs2Bank.NearestBankRoute route = Rs2Bank.getNearestBankRoute(start);
			assertEquals(BankLocation.DRAYNOR_VILLAGE, route.getBank());
			assertEquals(2, route.getPath().size());
			assertEquals(2, Rs2Bank.getPathToNearestBank(start).size());
			walker.when(() -> Rs2Walker.getWalkPath(any(WorldPoint.class), any(WorldPoint.class))).thenReturn(List.of());
			assertNull(Rs2Bank.getNearestBankRoute(start));
			assertTrue(Rs2Bank.getPathToNearestBank(start).isEmpty());
		}
	}
}
