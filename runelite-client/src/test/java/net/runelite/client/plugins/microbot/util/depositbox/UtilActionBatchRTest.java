package net.runelite.client.plugins.microbot.util.depositbox;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util action batch R: deposit box single deposits, buttons and walking. */
public class UtilActionBatchRTest
{
	@Test
	public void depositOneIsTheSlotsDepositOneOption()
	{
		Rs2ItemModel ore = mock(Rs2ItemModel.class);
		when(ore.getId()).thenReturn(440);
		when(ore.getSlot()).thenReturn(3);
		when(ore.getQuantity()).thenReturn(1);
		try (MockedStatic<Rs2DepositBox> box = mockStatic(Rs2DepositBox.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
			MockedStatic<MobileActions> actions = mockStatic(MobileActions.class);
			MockedStatic<Global> global = mockStatic(Global.class))
		{
			box.when(Rs2DepositBox::isOpen).thenReturn(true);
			inventory.when(() -> Rs2Inventory.get(440)).thenReturn(ore);
			inventory.when(() -> Rs2Inventory.hasItem(440)).thenReturn(true);
			// Before the deposit the ore is carried; the wait then sees it gone.
			inventory.when(() -> Rs2Inventory.items(any())).thenAnswer(a -> Stream.of(ore)).thenAnswer(a -> Stream.empty());
			actions.when(() -> MobileActions.widget(anyInt(), anyInt(), anyString())).thenReturn(true);
			global.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt()))
				.thenAnswer(a -> ((BooleanSupplier) a.getArgument(0)).getAsBoolean());

			assertTrue(Rs2DepositBox.depositOne(440));
			actions.verify(() -> MobileActions.widget(InterfaceID.BankDepositbox.INVENTORY, 3, "Deposit-1"));
			assertTrue(Rs2DepositBox.depositEquipment());
			actions.verify(() -> MobileActions.widget(InterfaceID.BankDepositbox.DEPOSIT_WORN, -1, ""));
			assertFalse("no amount", Rs2DepositBox.depositX(ore, 0));
		}
	}

	@Test
	public void theWildernessBoxIsNeverChosenOrWalkedTo()
	{
		assertTrue(DepositBoxLocation.MAGE_ARENA.isInWilderness());
		assertFalse(DepositBoxLocation.EDGEVILLE.isInWilderness());
		try (MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class))
		{
			assertFalse(Rs2DepositBox.walkToDepositBox(DepositBoxLocation.MAGE_ARENA));
			assertFalse(Rs2DepositBox.walkToAndUseDepositBox(DepositBoxLocation.MAGE_ARENA));
			walker.verifyNoInteractions();
		}
		assertNull(Rs2DepositBox.getNearestDepositBox(null, 10));
	}

	@Test
	public void aFullInventoryGoesToTheBoxFirst()
	{
		WorldPoint spot = new WorldPoint(3200, 3200, 0);
		try (MockedStatic<Rs2DepositBox> box = mockStatic(Rs2DepositBox.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
			MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class))
		{
			inventory.when(Rs2Inventory::getEmptySlots).thenReturn(0);
			box.when(Rs2DepositBox::walkToAndUseDepositBox).thenReturn(true);
			box.when(() -> Rs2DepositBox.depositAll(anyList(), anyBoolean())).thenReturn(true);
			assertFalse(Rs2DepositBox.bankItemsAndWalkBackToOriginalPosition(List.of("ore"), spot));
			box.verify(() -> Rs2DepositBox.depositAll(List.of("ore"), false));
		}
	}
}
