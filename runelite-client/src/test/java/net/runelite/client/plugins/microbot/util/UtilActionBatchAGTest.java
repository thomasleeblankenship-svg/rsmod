package net.runelite.client.plugins.microbot.util;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.misc.Predicates;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util action batch AG: inventory slot helpers, quick spell check, Enter release, distinct predicate. */
public class UtilActionBatchAGTest
{
	private static Rs2ItemModel item(int id, String name, int slot, int quantity, boolean stackable)
	{
		Rs2ItemModel item = mock(Rs2ItemModel.class);
		when(item.getId()).thenReturn(id);
		when(item.getName()).thenReturn(name);
		when(item.getSlot()).thenReturn(slot);
		when(item.getQuantity()).thenReturn(quantity);
		when(item.isStackable()).thenReturn(stackable);
		return item;
	}

	@Test
	public void slotHelpersFindItemsBySlot()
	{
		Rs2ItemModel lobster = item(379, "Lobster", 3, 1, false);
		Rs2ItemModel coins = item(995, "Coins", 7, 500, true);
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class, CALLS_REAL_METHODS))
		{
			inventory.when(Rs2Inventory::items).thenAnswer(invocation -> Stream.of(lobster, coins));
			inventory.when(() -> Rs2Inventory.items(any())).thenAnswer(invocation ->
				Stream.of(lobster, coins).filter(invocation.getArgument(0)));
			assertEquals(3, Rs2Inventory.slot(379));
			assertEquals(7, Rs2Inventory.slot("coins"));
			assertEquals(-1, Rs2Inventory.slot("Coin"));
			assertEquals(-1, Rs2Inventory.slot(1));
			assertEquals(2, Rs2Inventory.fullSlotCount());
			assertEquals(List.of(coins), Rs2Inventory.except(i -> i.getId() == 379));
			assertTrue(Rs2Inventory.slotNameContains(3, "LOB"));
			assertFalse(Rs2Inventory.slotNameContains(4, "lob"));
		}
	}

	@Test
	public void quickCastCheckComparesTheSpellIcon()
	{
		Widget spellbook = mock(Widget.class);
		Widget icon = mock(Widget.class);
		try (MockedStatic<Rs2Tab> tabs = mockStatic(Rs2Tab.class);
			MockedStatic<Rs2Widget> widgets = mockStatic(Rs2Widget.class);
			MockedStatic<MobileActions> actions = mockStatic(MobileActions.class))
		{
			tabs.when(() -> Rs2Tab.switchTo(InterfaceTab.MAGIC)).thenReturn(true);
			widgets.when(() -> Rs2Widget.getWidget(218, 3)).thenReturn(spellbook);
			widgets.when(() -> Rs2Widget.findWidget(eq("Wind Strike"), anyList())).thenReturn(icon);
			actions.when(() -> MobileActions.read(any(), any())).thenReturn(MagicAction.WIND_STRIKE.getSprite());
			assertTrue(Rs2Magic.quickCanCast(MagicAction.WIND_STRIKE));
			assertTrue(Rs2Magic.quickCanCast("wind strike"));
			actions.when(() -> MobileActions.read(any(), any())).thenReturn(-1);
			assertFalse(Rs2Magic.quickCanCast(MagicAction.WIND_STRIKE));
			assertFalse(Rs2Magic.quickCanCast((String) null));
		}
	}

	@Test
	public void resetEnterReleasesTheKey()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class))
		{
			Rs2Keyboard.resetEnter();
			actions.verify(() -> MobileActions.keyRelease(KeyEvent.VK_ENTER));
		}
	}

	@Test
	public void distinctByPropertyKeepsTheFirstOfEach()
	{
		List<String> words = Stream.of("apple", "avocado", "banana", "blueberry", "cherry")
			.filter(Predicates.distinctByProperty(word -> word.charAt(0)))
			.collect(Collectors.toList());
		assertEquals(List.of("apple", "banana", "cherry"), words);
	}
}
