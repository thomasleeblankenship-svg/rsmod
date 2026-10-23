package net.runelite.client.plugins.microbot.api;

import java.util.EnumSet;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

/** Real read-service/client-thread hops; transport reachability is the actual walker implementation. */
public class LumbridgeBankSelectionTest
{
	private ReadApiFixture fixture;

	@Before
	public void setUp() throws Exception
	{
		fixture = new ReadApiFixture();
		when(fixture.client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
	}

	@After
	public void tearDown() throws Exception { fixture.close(); }

	private void place(int x, int y, int plane)
	{
		WorldPoint position = new WorldPoint(x, y, plane);
		doAnswer(call -> fixture.onClientThread(position)).when(fixture.localPlayer).getWorldLocation();
	}

	@Test
	public void groundFloorNearLumbridgeSelectsUpstairsBankOverDraynor()
	{
		place(3205, 3205, 0);
		assertEquals(BankLocation.LUMBRIDGE_TOP, Rs2Bank.getNearestBank());
	}

	@Test
	public void firstFloorUsesKnownCastleStairs()
	{
		place(3205, 3209, 1);
		assertEquals(BankLocation.LUMBRIDGE_TOP, Rs2Bank.getNearestBank());
	}

	@Test
	public void upstairsAlreadySelectsLocalBank()
	{
		place(3208, 3220, 2);
		assertEquals(BankLocation.LUMBRIDGE_TOP, Rs2Bank.getNearestBank());
	}

	@Test
	public void draynorStillSelectsItsCloserSameFloorBank()
	{
		place(3093, 3245, 0);
		assertEquals(BankLocation.DRAYNOR_VILLAGE, Rs2Bank.getNearestBank());
	}

	@Test
	public void ordinaryWorldCannotSelectPvpOnlyLumbridgeFrontBank()
	{
		place(3221, 3217, 0);
		assertEquals(BankLocation.LUMBRIDGE_TOP, Rs2Bank.getNearestBank());
	}

	@Test
	public void pvpWorldKeepsExplicitFrontBankAccessRequirement()
	{
		when(fixture.client.getWorldType()).thenReturn(EnumSet.of(WorldType.PVP));
		place(3221, 3217, 0);
		assertEquals(BankLocation.LUMBRIDGE_FRONT, Rs2Bank.getNearestBank());
	}
	@Test
	public void faladorEastAndWestChooseTheLocalAutomaticBank()
	{
		place(3014, 3355, 0);
		assertEquals(BankLocation.FALADOR_EAST, Rs2Bank.getNearestBank());
		place(2945, 3369, 0);
		assertEquals(BankLocation.FALADOR_WEST, Rs2Bank.getNearestBank());
	}

	@Test
	public void varrockEastAndWestChooseTheLocalAutomaticBank()
	{
		place(3253, 3422, 0);
		assertEquals(BankLocation.VARROCK_EAST, Rs2Bank.getNearestBank());
		place(3183, 3441, 0);
		assertEquals(BankLocation.VARROCK_WEST, Rs2Bank.getNearestBank());
	}
}
