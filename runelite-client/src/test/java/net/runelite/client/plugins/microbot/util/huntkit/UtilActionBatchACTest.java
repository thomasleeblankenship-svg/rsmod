package net.runelite.client.plugins.microbot.util.huntkit;

import java.util.List;
import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util action batch AC: the huntsman's kit. */
public class UtilActionBatchACTest
{
	private static Rs2ItemModel item(int id, String name, int quantity, int slot, boolean stackable)
	{
		Rs2ItemModel model = mock(Rs2ItemModel.class);
		when(model.getId()).thenReturn(id);
		when(model.getName()).thenReturn(name);
		when(model.getQuantity()).thenReturn(quantity);
		when(model.getSlot()).thenReturn(slot);
		when(model.isStackable()).thenReturn(stackable);
		return model;
	}

	@Test
	public void kitReadsMatchByNameAndAmount()
	{
		Rs2ItemModel snares = item(10006, "Bird snare", 4, 0, false);
		Rs2ItemModel boxes = item(10008, "Box trap", 2, 1, false);
		try (MockedStatic<Rs2HuntKit> kit = mockStatic(Rs2HuntKit.class, CALLS_REAL_METHODS))
		{
			kit.when(Rs2HuntKit::kitItems).thenReturn(List.of(snares, boxes));
			assertTrue(Rs2HuntKit.contains("bird snare"));
			assertFalse("exact by default", Rs2HuntKit.contains("snare"));
			assertTrue(Rs2HuntKit.contains(false, "snare"));
			assertTrue(Rs2HuntKit.hasKitItem("box", 2));
			assertFalse(Rs2HuntKit.hasKitItem("box", 3));
			assertEquals(30, Rs2HuntKit.emptySlotCount());
			assertTrue(Rs2HuntKit.containsAll(10006, 10008));
		}
	}

	@Test
	public void withdrawalsUseTheGridSlotsOption()
	{
		Rs2ItemModel boxes = item(10008, "Box trap", 2, 1, false);
		try (MockedStatic<Rs2HuntKit> kit = mockStatic(Rs2HuntKit.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
			MockedStatic<MobileActions> actions = mockStatic(MobileActions.class);
			MockedStatic<Global> global = mockStatic(Global.class))
		{
			kit.when(Rs2HuntKit::kitItems).thenReturn(List.of(boxes));
			kit.when(Rs2HuntKit::isOpen).thenReturn(true);
			inventory.when(Rs2Inventory::isFull).thenReturn(false);
			actions.when(() -> MobileActions.widget(anyInt(), anyInt(), anyString())).thenReturn(true);
			global.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt()))
				.thenAnswer(a -> ((BooleanSupplier) a.getArgument(0)).getAsBoolean());
			assertTrue(Rs2HuntKit.withdrawOne("box trap", true));
			actions.verify(() -> MobileActions.widget(Rs2HuntKit.ITEM_CONTAINER_PACKED_ID, 1, "Withdraw-1"));
			assertFalse("not in the kit", Rs2HuntKit.withdrawOne(1));
		}
	}

	@Test
	public void theCarriedKitIsOpenedWithView()
	{
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
			MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class))
		{
			widget.when(() -> Rs2Widget.isWidgetVisible(anyInt())).thenReturn(false);
			inventory.when(() -> Rs2Inventory.hasItem(Rs2HuntKit.KIT_ITEM_ID)).thenReturn(true);
			inventory.when(() -> Rs2Inventory.interact(anyInt(), anyString())).thenReturn(true);
			assertTrue(Rs2HuntKit.requestOpenView());
			inventory.verify(() -> Rs2Inventory.interact(Rs2HuntKit.KIT_ITEM_ID, "View"));
			assertTrue(Rs2HuntKit.fill());
			inventory.verify(() -> Rs2Inventory.interact(Rs2HuntKit.KIT_ITEM_ID, "Fill"));
		}
	}
}
