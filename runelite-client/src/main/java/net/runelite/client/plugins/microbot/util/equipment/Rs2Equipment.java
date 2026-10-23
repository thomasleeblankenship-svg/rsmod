package net.runelite.client.plugins.microbot.util.equipment;

import java.util.Locale;
import java.util.function.Predicate;
import net.runelite.api.EquipmentInventorySlot;
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

	/** As upstream: exact is case-insensitive equality, otherwise a case-insensitive substring. */
	public static boolean isWearing(String name, boolean exact)
	{
		if (!exact) return isWearing(name);
		if (name == null || name.trim().isEmpty()) return false;
		return any(item ->
		{
			ItemComposition definition = Microbot.getClient().getItemDefinition(item.getId());
			return definition != null && name.equalsIgnoreCase(definition.getName());
		});
	}

	/** As upstream: true when any of the given slots holds an item. Read on the client thread. */
	public static boolean isWearing(EquipmentInventorySlot... slots)
	{
		if (slots == null || slots.length == 0) return false;
		return MobileActions.read(() ->
		{
			ItemContainer equipment = Microbot.getClient().getItemContainer(InventoryID.EQUIPMENT);
			if (equipment == null) return false;
			for (EquipmentInventorySlot slot : slots)
			{
				Item item = slot == null ? null : equipment.getItem(slot.getSlotIdx());
				if (item != null && item.getId() >= 0) return true;
			}
			return false;
		}, false);
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

	/** The first equipped item accepted by {@code predicate}, or {@code null}. */
	public static net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel get(
		Predicate<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> predicate)
	{
		return MobileActions.read(() ->
		{
			ItemContainer equipment = Microbot.getClient().getItemContainer(InventoryID.EQUIPMENT);
			Item[] items = equipment == null ? null : equipment.getItems();
			for (int slot = 0; items != null && slot < items.length; slot++)
			{
				net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel model = model(items[slot], slot);
				if (model != null && predicate.test(model))
				{
					return model;
				}
			}
			return null;
		}, null);
	}

	/** The item worn in the first of {@code slots} that holds one, or {@code null}. */
	public static net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel get(EquipmentInventorySlot... slots)
	{
		return MobileActions.read(() ->
		{
			ItemContainer equipment = Microbot.getClient().getItemContainer(InventoryID.EQUIPMENT);
			Item[] items = equipment == null ? null : equipment.getItems();
			for (EquipmentInventorySlot slot : slots)
			{
				int index = slot.getSlotIdx();
				if (items != null && index < items.length)
				{
					net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel model = model(items[index], index);
					if (model != null)
					{
						return model;
					}
				}
			}
			return null;
		}, null);
	}

	/** As upstream: every equipped item, read once on the client thread. */
	public static java.util.stream.Stream<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> all()
	{
		java.util.List<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> worn = MobileActions.read(() ->
		{
			ItemContainer equipment = Microbot.getClient().getItemContainer(InventoryID.EQUIPMENT);
			Item[] items = equipment == null ? null : equipment.getItems();
			java.util.List<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> result = new java.util.ArrayList<>();
			for (int slot = 0; items != null && slot < items.length; slot++)
			{
				net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel model = model(items[slot], slot);
				if (model != null)
				{
					result.add(model);
				}
			}
			return result;
		}, java.util.Collections.emptyList());
		return worn.stream();
	}

	/** As upstream: the first equipped item whose id is one of {@code ids}, or {@code null}. */
	public static net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel get(int... ids)
	{
		if (ids == null || ids.length == 0) return null;
		return get(item -> java.util.Arrays.stream(ids).anyMatch(id -> id == item.getId()));
	}

	/** As upstream: any of {@code ids} is equipped. */
	public static boolean isWearing(int... ids)
	{
		return get(ids) != null;
	}

	private static net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel model(Item item, int slot)
	{
		if (item == null || item.getId() < 0 || item.getQuantity() <= 0)
		{
			return null;
		}
		return new net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel(
			item, Microbot.getClient().getItemDefinition(item.getId()), null, slot);
	}

	/** As upstream: every equipped item as a list. */
	public static java.util.List<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> items()
	{
		return all().collect(java.util.stream.Collectors.toList());
	}
}
