package net.runelite.client.plugins.microbot.util.skills.fletching;

import java.util.function.BooleanSupplier;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util action batch U: upstream's fletching helpers. */
public class UtilActionBatchUTest
{
	@Test
	public void theKnifeIsUsedOnTheMaterial()
	{
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class))
		{
			inventory.when(() -> Rs2Inventory.hasItem("knife")).thenReturn(true);
			inventory.when(() -> Rs2Inventory.hasItem(ItemID.KNIFE)).thenReturn(true);
			inventory.when(() -> Rs2Inventory.hasItem(ItemID.YEW_LOGS)).thenReturn(true);
			inventory.when(() -> Rs2Inventory.combine(anyInt(), anyInt())).thenReturn(true);
			assertTrue(Rs2Fletching.startFletching(ItemID.YEW_LOGS));
			inventory.verify(() -> Rs2Inventory.combine(ItemID.KNIFE, ItemID.YEW_LOGS));

			inventory.when(() -> Rs2Inventory.hasItem("knife")).thenReturn(false);
			assertFalse("no knife", Rs2Fletching.startFletching(ItemID.YEW_LOGS));
		}
	}

	@Test
	public void dartsAreMadeThroughTheMakeInterface()
	{
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
			MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class))
		{
			inventory.when(() -> Rs2Inventory.combine("mithril dart tip", "feather")).thenReturn(true);
			inventory.when(() -> Rs2Inventory.waitFor(any(BooleanSupplier.class), anyInt()))
				.thenAnswer(a -> ((BooleanSupplier) a.getArgument(0)).getAsBoolean());
			inventory.when(() -> Rs2Inventory.hasItem("mithril dart tip")).thenReturn(false);
			widget.when(Rs2Widget::isProductionWidgetOpen).thenReturn(true);
			widget.when(() -> Rs2Widget.handleProcessingInterface("Dart")).thenReturn(true);

			assertTrue(Rs2Fletching.makeDarts("mithril dart tip", "All"));
			widget.verify(() -> Rs2Widget.enableQuantityOption("All"));
			widget.verify(() -> Rs2Widget.handleProcessingInterface("Dart"));
		}
	}

	@Test
	public void combineItemsAcceptsNamesOrIds()
	{
		Rs2ItemModel a = mock(Rs2ItemModel.class);
		Rs2ItemModel b = mock(Rs2ItemModel.class);
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class))
		{
			inventory.when(() -> Rs2Inventory.get("bow string")).thenReturn(a);
			inventory.when(() -> Rs2Inventory.get(70)).thenReturn(b);
			inventory.when(() -> Rs2Inventory.combine(a, b)).thenReturn(true);
			assertTrue(Rs2Fletching.combineItems((Object) "bow string", (Object) 70));
			assertFalse(Rs2Fletching.combineItems((Object) "bow string", (Object) 1.5));
		}
	}
}
