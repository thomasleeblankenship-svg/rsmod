package net.runelite.client.plugins.microbot.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsStackCompareID;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util action batch W: small leftovers across classes. */
public class UtilActionBatchWTest
{
	private static InventorySetupsItem row(int id, String name, boolean fuzzy)
	{
		return new InventorySetupsItem(id, name, 1, fuzzy, InventorySetupsStackCompareID.Standard, false, -1);
	}

	private static Rs2ItemModel carried(int id, String name)
	{
		Rs2ItemModel item = mock(Rs2ItemModel.class);
		when(item.getId()).thenReturn(id);
		when(item.getName()).thenReturn(name);
		return item;
	}

	@Test
	public void itemsTheSetupDoesNotListAreFound()
	{
		InventorySetup setup = new InventorySetup(new ArrayList<>(List.of(row(379, "Lobster", false), row(-1, "", false),
			row(0, "rune pouch", true))), new ArrayList<>(), null, null, null, new HashMap<>(),
			"test", "", null, false, null, false, false, 4, false, -1);
		Rs2ItemModel lobster = carried(379, "Lobster");
		Rs2ItemModel pouch = carried(12791, "Divine rune pouch");
		Rs2ItemModel bones = carried(526, "Bones");
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
			MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class))
		{
			inventory.when(Rs2Inventory::all).thenReturn(List.of(lobster, pouch, bones));
			walker.when(() -> Rs2Walker.isTeleportItem(anyInt())).thenReturn(false);
			assertEquals(List.of(bones), new Rs2InventorySetup(setup, null).getItemsNotInInventorySetup(false));
		}
	}

	@Test
	public void teleportItemsComeFromTheTransportData()
	{
		assertTrue(Rs2Walker.isTeleportItem(ItemID.DRAMEN_STAFF));
		assertFalse(Rs2Walker.isTeleportItem(ItemID.BONES));
	}

	@Test
	public void prayersOutsideTheKeepSetAreTurnedOff()
	{
		try (MockedStatic<Rs2Prayer> prayer = mockStatic(Rs2Prayer.class, CALLS_REAL_METHODS))
		{
			prayer.when(Rs2Prayer::getActivePrayers).thenReturn(List.of(Rs2PrayerEnum.PROTECT_MELEE, Rs2PrayerEnum.PIETY));
			prayer.when(() -> Rs2Prayer.toggle(any(Rs2PrayerEnum.class), anyBoolean())).thenReturn(true);
			Rs2Prayer.disableAllPrayersExcept(new Rs2PrayerEnum[]{Rs2PrayerEnum.PIETY});
			prayer.verify(() -> Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false));
			prayer.verify(() -> Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, false), never());
		}
	}

	@Test
	public void shortMonsterWalksAvoidTheWilderness()
	{
		try (MockedStatic<Rs2NpcManager> manager = mockStatic(Rs2NpcManager.class))
		{
			manager.when(() -> Rs2NpcManager.getClosestLocation(anyString(), anyInt(), anyBoolean())).thenReturn(null);
			assertFalse(Rs2Npc.walkToNearestMonster("Green dragon"));
			manager.verify(() -> Rs2NpcManager.getClosestLocation("Green dragon", 1, true));
		}
		assertFalse(Rs2Npc.attack((net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel) null));
	}

	@Test
	public void tabShortcutsSwitchToTheirTab()
	{
		try (MockedStatic<Rs2Tab> tabs = mockStatic(Rs2Tab.class, CALLS_REAL_METHODS))
		{
			tabs.when(() -> Rs2Tab.switchTo(any(InterfaceTab.class))).thenReturn(true);
			assertTrue(Rs2Tab.switchToQuestTab());
			assertTrue(Rs2Tab.switchToGroupingTab());
			tabs.verify(() -> Rs2Tab.switchTo(InterfaceTab.QUESTS));
			tabs.verify(() -> Rs2Tab.switchTo(InterfaceTab.CHAT));
		}
	}
}
