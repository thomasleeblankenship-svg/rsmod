package net.runelite.client.plugins.microbot.util.equipment;

import java.util.Locale;
import java.util.function.Predicate;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;

/** Equipment reads are performed on the client thread, never on the script thread. */
public final class Rs2Equipment
{
	private Rs2Equipment() { }

	public static boolean isWearing(int itemId)
	{
		return any(item -> item.getId() == itemId);
	}

	public static boolean isWearing(String name)
	{
		if (name == null || name.trim().isEmpty()) return false;
		String needle = name.toLowerCase(Locale.ROOT);
		return any(item ->
		{
			ItemComposition definition = Microbot.getClient().getItemDefinition(item.getId());
			return definition != null && definition.getName() != null
				&& definition.getName().toLowerCase(Locale.ROOT).contains(needle);
		});
	}

	public static boolean hasWoodcuttingAxe()
	{
		return any(item ->
		{
			ItemComposition definition = Microbot.getClient().getItemDefinition(item.getId());
			return net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.isProtectedAxe(
				item.getId(), definition == null ? null : definition.getName());
		});
	}

	private static boolean any(Predicate<Item> predicate)
	{
		return MobileActions.read(() ->
		{
			ItemContainer equipment = Microbot.getClient().getItemContainer(InventoryID.EQUIPMENT);
			if (equipment == null || equipment.getItems() == null) return false;
			for (Item item : equipment.getItems())
			{
				if (item != null && item.getId() >= 0 && predicate.test(item)) return true;
			}
			return false;
		}, false);
	}
}
