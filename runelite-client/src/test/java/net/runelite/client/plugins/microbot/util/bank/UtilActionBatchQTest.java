package net.runelite.client.plugins.microbot.util.bank;

import java.util.function.BooleanSupplier;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util action batch Q: withdraw-as, equipment deposits, rune pouch and the collection box. */
public class UtilActionBatchQTest
{
	@Test
	public void wornItemsAreUnequippedThenDeposited()
	{
		Rs2ItemModel helm = mock(Rs2ItemModel.class);
		when(helm.getId()).thenReturn(1153);
		try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class);
			MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class))
		{
			bank.when(Rs2Bank::isOpen).thenReturn(true);
			bank.when(() -> Rs2Bank.depositOne(anyInt())).thenReturn(true);
			equipment.when(() -> Rs2Equipment.get(EquipmentInventorySlot.HEAD)).thenReturn(helm);
			equipment.when(() -> Rs2Equipment.unEquip(1153)).thenReturn(true);
			inventory.when(Rs2Inventory::emptySlotCount).thenReturn(5);
			inventory.when(() -> Rs2Inventory.waitFor(any(BooleanSupplier.class), anyInt())).thenReturn(true);
			bank.clearInvocations();

			assertTrue(Rs2Bank.depositEquippedItem(EquipmentInventorySlot.HEAD));
			equipment.verify(() -> Rs2Equipment.unEquip(1153));
			bank.verify(() -> Rs2Bank.depositOne(1153));

			equipment.clearInvocations();
			inventory.when(Rs2Inventory::emptySlotCount).thenReturn(0);
			assertFalse("no room: nothing is moved", Rs2Bank.depositEquippedItems(EquipmentInventorySlot.HEAD));
			equipment.verify(() -> Rs2Equipment.unEquip(anyInt()), never());
			assertTrue("nothing worn: nothing to do", Rs2Bank.depositEquippedItems(EquipmentInventorySlot.CAPE));
		}
	}

	@Test
	public void withdrawAsAndRunePouchDelegate()
	{
		try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class))
		{
			bank.when(Rs2Bank::setWithdrawAsNote).thenReturn(true);
			bank.when(Rs2Bank::setWithdrawAsItem).thenReturn(false);
			assertTrue(Rs2Bank.setWithdrawAs(true));
			assertFalse(Rs2Bank.setWithdrawAs(false));

			int pouch = Rs2RunePouch.POUCH_IDS[0];
			inventory.when(() -> Rs2Inventory.hasItem(pouch)).thenReturn(true);
			bank.when(() -> Rs2Bank.depositOne(pouch)).thenReturn(true);
			assertTrue(Rs2Bank.depositRunePouch());

			bank.when(() -> Rs2Bank.withdrawOne(anyInt())).thenReturn(true, false);
			assertFalse("one failed", Rs2Bank.withdrawItems(1, 2));
		}
	}

	@Test
	public void theCollectionBoxButtonsNeedItOpen()
	{
		try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class))
		{
			bank.when(Rs2Bank::openCollectionBox).thenReturn(true);
			widget.when(() -> Rs2Widget.clickWidget(anyInt())).thenReturn(true);
			assertTrue(Rs2Bank.bankAllCollectionBoxItems());
			widget.verify(() -> Rs2Widget.clickWidget((402 << 16) | 4));
			assertTrue(Rs2Bank.inventoryAllCollectionBoxItems());
			widget.verify(() -> Rs2Widget.clickWidget((402 << 16) | 3));

			bank.when(Rs2Bank::openCollectionBox).thenReturn(false);
			widget.clearInvocations();
			assertFalse(Rs2Bank.bankAllCollectionBoxItems());
			widget.verify(() -> Rs2Widget.clickWidget(anyInt()), never());
			assertTrue("already closed", Rs2Bank.closeCollectionBox());
		}
	}
}
