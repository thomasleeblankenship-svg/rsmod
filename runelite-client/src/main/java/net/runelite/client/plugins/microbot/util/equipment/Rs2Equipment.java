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

/**
 * Equipment reads are performed on the client thread, never on the script thread. Actions (the
 * block at the end) dispatch one exact option on the worn-equipment tab through {@link MobileActions}.
 */
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

	/** As upstream: equipped items whose name equals ({@code exact}) or contains {@code name}. */
	public static java.util.stream.Stream<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> all(String name, boolean exact)
	{
		return all(net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel.matches(exact, name));
	}

	/** As upstream: the first equipped item, or {@code null}. */
	public static net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel get()
	{
		return all().findFirst().orElse(null);
	}

	/** As upstream: the first equipped item named {@code name}. */
	public static net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel get(String name, boolean exact)
	{
		return all(name, exact).findFirst().orElse(null);
	}

	/** As upstream: anything is equipped. */
	public static boolean isWearing()
	{
		return get() != null;
	}

	/** As upstream: every one of {@code names} is worn in {@code searchSlots}. */
	public static boolean isWearing(String[] names, boolean exact, EquipmentInventorySlot[] searchSlots)
	{
		java.util.Set<Integer> slots = java.util.Arrays.stream(searchSlots).map(EquipmentInventorySlot::getSlotIdx).collect(java.util.stream.Collectors.toSet());
		java.util.List<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> worn =
			all(item -> slots.contains(item.getSlot())).collect(java.util.stream.Collectors.toList());
		return java.util.Arrays.stream(names).allMatch(name -> worn.stream().anyMatch(
			net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel.matches(exact, name)));
	}

	/** As upstream: as above, searching {@code slots} or, when not {@code areSearchSlots}, every other slot. */
	public static boolean isWearing(String[] names, boolean exact, EquipmentInventorySlot[] slots, boolean areSearchSlots)
	{
		java.util.Set<EquipmentInventorySlot> given = java.util.EnumSet.noneOf(EquipmentInventorySlot.class);
		given.addAll(java.util.Arrays.asList(slots));
		EquipmentInventorySlot[] search = areSearchSlots ? slots : java.util.Arrays.stream(EquipmentInventorySlot.values())
			.filter(slot -> !given.contains(slot)).toArray(EquipmentInventorySlot[]::new);
		return isWearing(names, exact, search);
	}

	/** As upstream: equipped items in any of {@code slots}. */
	public static java.util.stream.Stream<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> all(EquipmentInventorySlot... slots)
	{
		return all(net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel.matches(slots));
	}

	/** As upstream: equipped items with any of {@code ids}. */
	public static java.util.stream.Stream<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> all(int... ids)
	{
		return all(net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel.matches(ids));
	}

	/** As upstream: equipped items matching any of {@code names}. */
	public static java.util.stream.Stream<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> all(String[] names, boolean exact)
	{
		return all(net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel.matches(exact, names));
	}

	/** As upstream: equipped items whose name contains any of {@code names}. */
	public static java.util.stream.Stream<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> all(String... names)
	{
		return all(names, false);
	}

	/** As upstream: the first equipped item matching any of {@code names}. */
	public static net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel get(String[] names, boolean exact)
	{
		return all(names, exact).findFirst().orElse(null);
	}

	/** As upstream: the first equipped item whose name contains any of {@code names}. */
	public static net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel get(String... names)
	{
		return all(names).findFirst().orElse(null);
	}

	/** As upstream: any of {@code names} is worn. */
	public static boolean isWearing(String[] names, boolean exact)
	{
		return get(names, exact) != null;
	}

	/** As upstream: any equipped name contains one of {@code names}. */
	public static boolean isWearing(String... names)
	{
		return get(names) != null;
	}

	/** As upstream: every one of {@code names} is worn in {@code searchSlots} (contains match). */
	public static boolean isWearing(String[] names, EquipmentInventorySlot[] searchSlots)
	{
		return isWearing(names, false, searchSlots);
	}

	/** As upstream: equipped items matching {@code predicate}. */
	public static java.util.stream.Stream<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> all(
		Predicate<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> predicate)
	{
		return all().filter(predicate);
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

	// Actions (Claude 1)

	/** Worn-equipment widgets by equipment slot index (head 0 ... ammo 13; 6, 8 and 11 are unused). */
	private static final java.util.Map<Integer, Integer> WORN_SLOT_WIDGETS = java.util.Map.ofEntries(
		java.util.Map.entry(0, net.runelite.api.gameval.InterfaceID.Wornitems.SLOT0),
		java.util.Map.entry(1, net.runelite.api.gameval.InterfaceID.Wornitems.SLOT1),
		java.util.Map.entry(2, net.runelite.api.gameval.InterfaceID.Wornitems.SLOT2),
		java.util.Map.entry(3, net.runelite.api.gameval.InterfaceID.Wornitems.SLOT3),
		java.util.Map.entry(4, net.runelite.api.gameval.InterfaceID.Wornitems.SLOT4),
		java.util.Map.entry(5, net.runelite.api.gameval.InterfaceID.Wornitems.SLOT5),
		java.util.Map.entry(7, net.runelite.api.gameval.InterfaceID.Wornitems.SLOT7),
		java.util.Map.entry(9, net.runelite.api.gameval.InterfaceID.Wornitems.SLOT9),
		java.util.Map.entry(10, net.runelite.api.gameval.InterfaceID.Wornitems.SLOT10),
		java.util.Map.entry(12, net.runelite.api.gameval.InterfaceID.Wornitems.SLOT12),
		java.util.Map.entry(13, net.runelite.api.gameval.InterfaceID.Wornitems.SLOT13));

	/** The worn-equipment widget for an equipment slot index, or {@code null} for an unused index. */
	public static Integer wornSlotWidget(int slot)
	{
		return WORN_SLOT_WIDGETS.get(slot);
	}

	/**
	 * As upstream {@code invokeMenu}: opens the equipment tab and dispatches the worn item's option
	 * whose text contains {@code action} (case-insensitive), so "remove" picks "Remove". {@code true}
	 * means dispatched, not that the game acted on it.
	 */
	private static boolean interact(net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel item, String action)
	{
		if (item == null || action == null || action.isEmpty())
		{
			return false;
		}
		Integer widget = wornSlotWidget(item.getSlot());
		if (widget == null)
		{
			return false;
		}
		String wanted = action.toLowerCase(Locale.ROOT);
		String option = MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget slot = Microbot.getClient().getWidget(widget);
			return slot == null || slot.getActions() == null ? null : java.util.Arrays.stream(slot.getActions())
				.filter(candidate -> candidate != null && candidate.toLowerCase(Locale.ROOT).contains(wanted))
				.findFirst().orElse(null);
		}, null);
		return option != null
			&& net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.switchTo(net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.EQUIPMENT)
			&& net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> MobileActions.widget(widget, -1, option), 2_000);
	}

	/** As upstream: the first worn item accepted by {@code predicate}. */
	public static boolean interact(Predicate<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> predicate, String action)
	{
		return interact(get(predicate), action);
	}

	/** As upstream: {@code action} on the worn item whose name contains {@code name} (ignoring case). */
	public static boolean interact(String name, String action)
	{
		return interact(name, action, false);
	}

	/** As upstream: {@code action} on the worn item named {@code name}, exactly or by containment (ignoring case). */
	public static boolean interact(String name, String action, boolean exact)
	{
		if (name == null)
		{
			return false;
		}
		String wanted = name.toLowerCase(Locale.ROOT);
		return interact(item -> item.getName() != null && (exact ? item.getName().equalsIgnoreCase(name)
			: item.getName().toLowerCase(Locale.ROOT).contains(wanted)), action);
	}

	/** As upstream: the first of {@code names} worn takes {@code action}. */
	public static boolean interact(String[] names, String action, boolean exact)
	{
		if (names == null)
		{
			return false;
		}
		for (String name : names)
		{
			if (interact(name, action, exact))
			{
				return true;
			}
		}
		return false;
	}

	/** As upstream: {@link #interact(String[], String, boolean)} by containment. */
	public static boolean interact(String[] names, String action)
	{
		return interact(names, action, false);
	}

	/** As upstream: the item in {@code slot}. */
	public static boolean interact(EquipmentInventorySlot slot, String action)
	{
		return interact(get(slot), action);
	}

	/** As upstream: the first of {@code slots} whose item takes the action. */
	public static boolean interact(EquipmentInventorySlot[] slots, String action)
	{
		if (slots == null) return false;
		for (EquipmentInventorySlot slot : slots)
		{
			if (interact(slot, action)) return true;
		}
		return false;
	}

	/** As upstream: the worn item with this id. */
	public static boolean interact(int id, String action)
	{
		return interact(get(id), action);
	}

	/** As upstream: the first of {@code ids} that is worn and takes the action. */
	public static boolean interact(int[] ids, String action)
	{
		if (ids == null) return false;
		for (int id : ids)
		{
			if (interact(id, action)) return true;
		}
		return false;
	}

	/** As upstream: "Remove" on the first worn item accepted by {@code predicate}. */
	public static boolean unEquip(Predicate<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> predicate)
	{
		return interact(get(predicate), "remove");
	}

	/** As upstream: "Remove" on the item in the first of {@code slots} that holds one. */
	public static boolean unEquip(EquipmentInventorySlot... slots)
	{
		return interact(get(slots), "remove");
	}

	/** As upstream: "Remove" on the first worn item with one of {@code ids}. */
	public static boolean unEquip(int... ids)
	{
		return interact(get(ids), "remove");
	}

	/** As upstream: removes the worn item named {@code name}, exactly or containing it. */
	public static boolean unEquip(String name, boolean exact)
	{
		return unEquip(new String[]{name}, exact);
	}

	/** As upstream: removes the first worn item named one of {@code names}, exactly or containing it. */
	public static boolean unEquip(String[] names, boolean exact)
	{
		if (names == null) return false;
		return unEquip(item -> item.getName() != null && java.util.Arrays.stream(names).filter(java.util.Objects::nonNull)
			.anyMatch(n -> exact ? item.getName().equalsIgnoreCase(n)
				: item.getName().toLowerCase(Locale.ROOT).contains(n.toLowerCase(Locale.ROOT))));
	}

	/** As upstream: removes the first worn item whose name contains one of {@code names}. */
	public static boolean unEquip(String... names)
	{
		return unEquip(names, false);
	}
}
