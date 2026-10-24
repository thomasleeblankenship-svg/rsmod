package net.runelite.client.plugins.microbot.util.inventory;

import java.util.Map;
import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util action batch T: the rune pouch's bank panel. */
public class UtilActionBatchTTest
{
	@Test
	public void anEmptyPouchNeedsNoDeposit()
	{
		try (MockedStatic<Rs2RunePouch> pouch = mockStatic(Rs2RunePouch.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class))
		{
			pouch.when(Rs2RunePouch::getRunes).thenReturn(Map.of());
			assertTrue(Rs2RunePouch.depositAll());
			widget.verify(() -> Rs2Widget.clickWidget(anyInt(), anyInt()), never());
		}
	}

	@Test
	public void depositingOpensThePanelAndPressesDepositAll()
	{
		try (MockedStatic<Rs2RunePouch> pouch = mockStatic(Rs2RunePouch.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class);
			MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
			MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
			MockedStatic<Global> global = mockStatic(Global.class))
		{
			pouch.when(Rs2RunePouch::getRunes).thenReturn(Map.of(Runes.AIR, 100), Map.of());
			pouch.when(Rs2RunePouch::isRunePouchOpen).thenReturn(true);
			bank.when(Rs2Bank::isOpen).thenReturn(true);
			inventory.when(Rs2Inventory::hasRunePouch).thenReturn(true);
			widget.when(() -> Rs2Widget.clickWidget(anyInt(), anyInt())).thenReturn(true);
			global.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt()))
				.thenAnswer(a -> ((BooleanSupplier) a.getArgument(0)).getAsBoolean());
			assertTrue(Rs2RunePouch.depositAll());
			widget.verify(() -> Rs2Widget.clickWidget(net.runelite.api.gameval.InterfaceID.BANKSIDE, 20));
		}
	}

	@Test
	public void loadingIsSkippedWhenThePouchAlreadyHoldsTheRunes()
	{
		try (MockedStatic<Rs2RunePouch> pouch = mockStatic(Rs2RunePouch.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class))
		{
			pouch.when(() -> Rs2RunePouch.contains(anyMap(), eq(false))).thenReturn(true);
			assertTrue(Rs2RunePouch.load(Map.of(Runes.FIRE, 50)));
			bank.verify(() -> Rs2Bank.withdrawX(anyInt(), anyInt()), never());
			pouch.when(() -> Rs2RunePouch.contains(anyMap(), eq(false))).thenReturn(false);
			bank.when(Rs2Bank::isOpen).thenReturn(false);
			assertFalse("needs the bank", Rs2RunePouch.load(Map.of(Runes.FIRE, 50)));
		}
	}
}
