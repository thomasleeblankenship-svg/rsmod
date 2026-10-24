package net.runelite.client.plugins.microbot.util.inventory;

import java.util.function.Predicate;
import java.util.stream.Stream;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.configs.SpecialAttackConfigs;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.item.Rs2ExplorersRing;
import net.runelite.client.plugins.microbot.util.misc.SpecialAttackWeaponEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util-gap action batch F: special-attack settings, gem bag, explorer's ring, pouches, rune pouch. */
public class HubActionBatchFTest
{
	@Test
	public void specialAttackSettingsAreSharedAndReset()
	{
		SpecialAttackConfigs configs = Microbot.getSpecialAttackConfigs();
		assertSame(configs, Microbot.getSpecialAttackConfigs());
		configs.setSpecialAttack(true).setMinimumSpecEnergy(500).setSpecialAttackWeapon(SpecialAttackWeaponEnum.ABYSSAL_DAGGER);
		assertTrue(configs.isUseSpecialAttack());
		assertEquals(500, configs.getMinimumSpecEnergy());
		configs.reset();
		assertFalse(configs.isUseSpecialAttack());
		assertEquals(-1, configs.getMinimumSpecEnergy());
		assertNull(configs.getSpecialAttackWeapon());
		assertFalse("not logged in: no special", configs.useSpecWeapon());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void withoutTheItemsNothingIsSent()
	{
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class, CALLS_REAL_METHODS))
		{
			inventory.when(() -> Rs2Inventory.items(any(Predicate.class))).thenAnswer(call -> Stream.empty());
			inventory.when(() -> Rs2Inventory.interact(any(Rs2ItemModel.class), anyString())).thenReturn(true);
			bank.when(() -> Rs2Bank.hasItem(anyInt())).thenReturn(false);
			inventory.clearInvocations();
			assertFalse("no gem bag", Rs2Bank.emptyGemBag());
			assertFalse("no rune pouch in the bank", Rs2Bank.withdrawRunePouch());
			inventory.verify(() -> Rs2Inventory.interact(any(Rs2ItemModel.class), anyString()), never());

			inventory.when(Rs2Inventory::isFull).thenReturn(true);
			assertFalse("full: nowhere to empty pouches into", Rs2Inventory.emptyPouches());
			assertFalse(Rs2Inventory.checkPouches());
		}
	}

	@Test
	public void theRingInterfaceClosesOnlyWhenOpenAndTheInventoryTabOpens()
	{
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Tab> tabs = mockStatic(Rs2Tab.class, CALLS_REAL_METHODS))
		{
			microbot.when(() -> Microbot.getVarbitValue(anyInt())).thenReturn(0);
			assertTrue("not open: nothing to close", Rs2ExplorersRing.closeInterface());
			tabs.when(Rs2Tab::switchToInventoryTab).thenReturn(true);
			tabs.clearInvocations();
			assertTrue(Rs2Inventory.open());
			tabs.verify(Rs2Tab::switchToInventoryTab);
		}
		assertFalse("no item", Rs2ExplorersRing.highAlch(null));
	}
}
