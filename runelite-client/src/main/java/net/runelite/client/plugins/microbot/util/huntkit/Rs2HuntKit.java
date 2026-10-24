/*
 * Adapted from chsami/Microbot (2.6.38),
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/huntkit/Rs2HuntKit.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.huntkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * The huntsman's kit: its interface, contents, deposits and withdrawals, with upstream's names.
 * <p>
 * Mobile adaptation: the contents are read live from the kit container on the client thread (no
 * event cache), and every option is one script action on the slot's own option name (the kit grid
 * for withdrawals, the kit's side inventory for deposits), so slots need not be scrolled into view.
 * A withdrawal or deposit returns true once the option was dispatched, as upstream.
 */
public final class Rs2HuntKit
{
	public static final int INTERFACE_ROOT_PACKED_ID = InterfaceID.HuntsmansKit.UNIVERSE;
	public static final int ITEM_CONTAINER_PACKED_ID = InterfaceID.HuntsmansKit.ITEMS;
	public static final int KIT_SIDE_INVENTORY_PACKED_ID = InterfaceID.HuntsmansKitSide.ITEMS;
	public static final int KIT_ITEM_CONTAINER_ID = InventoryID.HUNTSMANS_KIT;
	public static final int KIT_ITEM_ID = ItemID.HUNTSMANS_KIT;
	public static final String KIT_NAME = "Huntsman's kit";
	public static final int CAPACITY = 32;

	private static final int OPEN_TIMEOUT_MILLIS = 3_500;
	private static final int ACTION_TIMEOUT_MILLIS = 2_000;

	private Rs2HuntKit()
	{
	}

	/** Upstream fed its cache from this event; mobile reads the container live, so nothing is stored. */
	public static void updateLocalKit(ItemContainerChanged event)
	{
		// Contents are read on demand from the kit container.
	}

	// ---- interface ----

	/** As upstream: the kit interface is showing. */
	public static boolean isOpen()
	{
		return Rs2Widget.isWidgetVisible(INTERFACE_ROOT_PACKED_ID);
	}

	/** As upstream: "View" on the carried kit without waiting (true when open or the click went out). */
	public static boolean requestOpenView()
	{
		return isOpen() || Rs2Inventory.hasItem(KIT_ITEM_ID) && Rs2Inventory.interact(KIT_ITEM_ID, "View");
	}

	/** As upstream: "View" on the carried kit, then waits for its interface. */
	public static boolean openView()
	{
		if (isOpen()) return true;
		return Rs2Inventory.hasItem(KIT_ITEM_ID) && Rs2Inventory.interact(KIT_ITEM_ID, "View")
			&& Global.sleepUntil(Rs2HuntKit::isOpen, OPEN_TIMEOUT_MILLIS);
	}

	/** As upstream: "Fill" on the carried kit (only with its interface closed). */
	public static boolean fill()
	{
		return !isOpen() && Rs2Inventory.interact(KIT_ITEM_ID, "Fill");
	}

	/** As upstream: "Empty" on the carried kit (only with its interface closed). */
	public static boolean emptyKit()
	{
		return !isOpen() && Rs2Inventory.interact(KIT_ITEM_ID, "Empty");
	}

	/** As upstream: the interface's Fill button. */
	public static boolean fillWidget()
	{
		return isOpen() && MobileActions.widget(InterfaceID.HuntsmansKit.FILL, -1, "Fill");
	}

	/** As upstream: the interface's Empty (the Fill icon's option, else the Empty button). */
	public static boolean emptyWidget()
	{
		return isOpen() && (MobileActions.widget(InterfaceID.HuntsmansKit.FILL, -1, "Empty")
			|| MobileActions.widget(InterfaceID.HuntsmansKit.EMPTY, -1, "Empty"));
	}

	/** As upstream: the frame's Close, then waits for the interface to go. */
	public static boolean close()
	{
		if (!isOpen()) return true;
		return MobileActions.widget(InterfaceID.HuntsmansKit.FRAME, -1, "Close")
			&& Global.sleepUntil(() -> !isOpen(), OPEN_TIMEOUT_MILLIS);
	}

	/** As upstream: {@link #close()}. */
	public static boolean closeView()
	{
		return close();
	}

	/** Mobile options do not need the slot in view, so there is nothing to scroll; true while open. */
	public static boolean scrollKitToSlot(int slotId)
	{
		return isOpen() && slotId >= 0 && slotId < CAPACITY;
	}

	// ---- contents (read live on the client thread) ----

	/** As upstream: the kit's item container, or null. */
	public static ItemContainer kitItemContainer()
	{
		return MobileActions.read(() -> Microbot.getClient().getItemContainer(KIT_ITEM_CONTAINER_ID), null);
	}

	/** As upstream: the kit's items (placeholders left out), in slot order. */
	public static List<Rs2ItemModel> kitItems()
	{
		return MobileActions.read(() ->
		{
			ItemContainer container = Microbot.getClient().getItemContainer(KIT_ITEM_CONTAINER_ID);
			Item[] items = container == null ? null : container.getItems();
			List<Rs2ItemModel> built = new ArrayList<>();
			for (int slot = 0; items != null && slot < Math.min(items.length, CAPACITY); slot++)
			{
				Item item = items[slot];
				if (item == null || item.getId() <= 0) continue;
				ItemComposition composition = Microbot.getClient().getItemDefinition(item.getId());
				if (composition == null || composition.getPlaceholderTemplateId() > 0) continue;
				built.add(new Rs2ItemModel(item, composition, null, slot));
			}
			return Collections.unmodifiableList(built);
		}, Collections.emptyList());
	}

	public static Stream<Rs2ItemModel> stream()
	{
		return kitItems().stream();
	}

	public static Stream<Rs2ItemModel> items(Predicate<Rs2ItemModel> predicate)
	{
		return predicate == null ? Stream.empty() : stream().filter(predicate);
	}

	public static List<Rs2ItemModel> all()
	{
		return kitItems();
	}

	public static List<Rs2ItemModel> all(Predicate<Rs2ItemModel> filter)
	{
		return items(filter).collect(java.util.stream.Collectors.toList());
	}

	public static int capacity()
	{
		return CAPACITY;
	}

	public static int emptySlotCount()
	{
		return CAPACITY - kitItems().size();
	}

	public static Rs2ItemModel get(Predicate<Rs2ItemModel> predicate)
	{
		return items(predicate).findFirst().orElse(null);
	}

	public static Rs2ItemModel findKitItem(int id)
	{
		return get(x -> x.getId() == id);
	}

	/** As upstream: exact (case-insensitive) or, by containment, the shortest matching name. */
	public static Rs2ItemModel findKitItem(String name, boolean exact)
	{
		if (name == null) return null;
		if (exact) return get(x -> name.equalsIgnoreCase(x.getName()));
		String needle = name.toLowerCase(Locale.ROOT);
		return items(x -> x.getName() != null && x.getName().toLowerCase(Locale.ROOT).contains(needle))
			.min(java.util.Comparator.comparingInt(x -> x.getName().length())).orElse(null);
	}

	public static Rs2ItemModel findKitItem(String name)
	{
		return findKitItem(name, false);
	}

	public static Rs2ItemModel findKitItem(String name, boolean exact, int minQuantity)
	{
		Rs2ItemModel item = findKitItem(name, exact);
		return item != null && item.getQuantity() >= minQuantity ? item : null;
	}

	public static boolean hasKitItem(int id)
	{
		return findKitItem(id) != null;
	}

	public static boolean hasKitItem(int id, int amount)
	{
		return count(id) >= amount;
	}

	public static boolean hasKitItem(String name)
	{
		return findKitItem(name, false) != null;
	}

	public static boolean hasKitItem(String name, boolean exact)
	{
		return findKitItem(name, exact) != null;
	}

	public static boolean hasKitItem(String name, int amount)
	{
		return hasKitItem(name, amount, false);
	}

	public static boolean hasKitItem(String name, int amount, boolean exact)
	{
		return findKitItem(name, exact, amount) != null;
	}

	/** As upstream: every one of these IDs is in the kit. */
	public static boolean hasKitItem(int... ids)
	{
		return ids != null && ids.length > 0 && Arrays.stream(ids).allMatch(Rs2HuntKit::hasKitItem);
	}

	/** As upstream: every one of these names (by containment) is in the kit. */
	public static boolean hasKitItem(Collection<String> names)
	{
		return names != null && !names.isEmpty() && names.stream().allMatch(Rs2HuntKit::hasKitItem);
	}

	public static int count(Predicate<Rs2ItemModel> predicate)
	{
		return (int) items(predicate).count();
	}

	public static int count(int id)
	{
		Rs2ItemModel item = findKitItem(id);
		return item == null ? 0 : item.getQuantity();
	}

	public static int count(String name, boolean exact)
	{
		Rs2ItemModel item = findKitItem(name, exact);
		return item == null ? 0 : item.getQuantity();
	}

	public static boolean contains(Predicate<Rs2ItemModel> predicate)
	{
		return predicate != null && stream().anyMatch(predicate);
	}

	public static boolean contains(int... ids)
	{
		return ids != null && contains(item -> Arrays.stream(ids).anyMatch(id -> id == item.getId()));
	}

	/** As upstream: exact (case-insensitive) whole-name matches. */
	public static boolean contains(String... names)
	{
		return contains(true, names);
	}

	public static boolean contains(boolean exact, String... names)
	{
		return names != null && contains(item -> item.getName() != null && Arrays.stream(names).anyMatch(n -> n != null
			&& (exact ? item.getName().equalsIgnoreCase(n) : item.getName().toLowerCase(Locale.ROOT).contains(n.toLowerCase(Locale.ROOT)))));
	}

	public static boolean contains(String name, boolean exact)
	{
		return contains(exact, name);
	}

	public static boolean containsAll(int... ids)
	{
		return ids != null && Arrays.stream(ids).allMatch(Rs2HuntKit::hasKitItem);
	}

	public static boolean containsAll(String... names)
	{
		return names != null && Arrays.stream(names).allMatch(n -> hasKitItem(n, true));
	}

	// ---- slot options ----

	/** As upstream: the kit-grid option with this 1-based index on the item's slot. */
	public static void invokeMenu(int identifier, Rs2ItemModel rs2Item)
	{
		String option = optionAt(ITEM_CONTAINER_PACKED_ID, rs2Item == null ? -1 : rs2Item.getSlot(), identifier);
		if (option != null)
		{
			MobileActions.widget(ITEM_CONTAINER_PACKED_ID, rs2Item.getSlot(), option);
		}
	}

	private static String optionAt(int container, int slot, int identifier)
	{
		return MobileActions.read(() ->
		{
			Widget parent = Microbot.getClient().getWidget(container);
			Widget item = parent == null || slot < 0 ? null : parent.getChild(slot);
			String[] actions = item == null ? null : item.getActions();
			return actions == null || identifier < 1 || identifier > actions.length ? null : actions[identifier - 1];
		}, null);
	}

	/** One option on a slot, dispatched as one script action. */
	private static boolean option(int container, int slot, String option)
	{
		return Global.sleepUntil(() -> MobileActions.widget(container, slot, option), ACTION_TIMEOUT_MILLIS);
	}

	/** Types {@code amount} into the "Enter amount" prompt. */
	private static boolean enterAmount(int amount)
	{
		return Global.sleepUntil(() -> MobileActions.read(() ->
				Microbot.getClient().getVarcIntValue(net.runelite.api.VarClientInt.INPUT_TYPE) != 0, false), 5_000)
			&& Rs2Keyboard.typeString(String.valueOf(amount)) && Rs2Keyboard.enter();
	}

	// ---- deposits (the kit's side inventory) ----

	/** The first carried item named {@code name}, exactly (ignoring case) or containing it. */
	private static Rs2ItemModel carried(String name, boolean exact)
	{
		if (name == null) return null;
		String needle = name.toLowerCase(Locale.ROOT);
		return Rs2Inventory.items(i -> i.getName() != null
			&& (exact ? i.getName().equalsIgnoreCase(name) : i.getName().toLowerCase(Locale.ROOT).contains(needle))).findFirst().orElse(null);
	}

	private static boolean deposit(Rs2ItemModel item, String label)
	{
		return item != null && isOpen() && Rs2Inventory.hasItem(item.getId()) && option(KIT_SIDE_INVENTORY_PACKED_ID, item.getSlot(), label);
	}

	public static boolean depositOne(int id)
	{
		return deposit(Rs2Inventory.get(id), "Deposit-1");
	}

	public static boolean depositOne(String name, boolean exact)
	{
		return deposit(carried(name, exact), "Deposit-1");
	}

	public static boolean depositOne(String name)
	{
		return depositOne(name, false);
	}

	public static boolean depositFive(int id)
	{
		return deposit(Rs2Inventory.get(id), "Deposit-5");
	}

	public static boolean depositFive(String name, boolean exact)
	{
		return deposit(carried(name, exact), "Deposit-5");
	}

	public static boolean depositFive(String name)
	{
		return depositFive(name, false);
	}

	private static boolean depositX(Rs2ItemModel item, int amount)
	{
		if (amount <= 0) return item != null && isOpen();
		return deposit(item, "Deposit-X") && enterAmount(amount);
	}

	public static boolean depositX(int id, int amount)
	{
		return depositX(Rs2Inventory.get(id), amount);
	}

	public static boolean depositX(String name, int amount, boolean exact)
	{
		return depositX(carried(name, exact), amount);
	}

	public static boolean depositX(String name, int amount)
	{
		return depositX(name, amount, false);
	}

	public static boolean depositAll(int id)
	{
		return deposit(Rs2Inventory.get(id), "Deposit-All");
	}

	public static boolean depositAll(String name, boolean exact)
	{
		return deposit(carried(name, exact), "Deposit-All");
	}

	public static boolean depositAll(String name)
	{
		return depositAll(name, false);
	}

	/** As upstream: deposits every matching stack, re-checking the inventory each pass (bounded). */
	public static boolean depositAll(Predicate<Rs2ItemModel> predicate)
	{
		if (predicate == null) return false;
		boolean result = false;
		int maxPasses = Math.max(40, Rs2Inventory.capacity() * 2);
		for (int pass = 0; pass < maxPasses && MobileActions.active(); pass++)
		{
			Rs2ItemModel item = Rs2Inventory.items(predicate).findFirst().orElse(null);
			if (item == null) break;
			int id = item.getId();
			if (!deposit(item, "Deposit-All") || !Rs2Inventory.waitFor(() -> !Rs2Inventory.hasItem(id), ACTION_TIMEOUT_MILLIS))
			{
				return false;
			}
			result = true;
		}
		return result;
	}

	public static boolean depositAllExcept(Predicate<Rs2ItemModel> predicate)
	{
		return predicate != null && depositAll(predicate.negate());
	}

	public static boolean depositAllExcept(Integer... ids)
	{
		return ids != null && depositAll(x -> Arrays.stream(ids).noneMatch(id -> id != null && id == x.getId()));
	}

	/** As upstream: keeps items whose names contain any of {@code names}. */
	public static boolean depositAllExcept(String... names)
	{
		return depositAllExcept(false, names);
	}

	public static boolean depositAllExcept(Collection<String> names)
	{
		return names != null && depositAllExcept(names.toArray(new String[0]));
	}

	/** As upstream: keeps each named item, fuzzily when its value is true. */
	public static boolean depositAllExcept(Map<String, Boolean> itemsToExclude)
	{
		if (itemsToExclude == null) return false;
		return depositAll(item -> item.getName() != null && itemsToExclude.entrySet().stream().noneMatch(e -> e.getKey() != null
			&& (Boolean.TRUE.equals(e.getValue()) ? item.getName().toLowerCase(Locale.ROOT).contains(e.getKey().toLowerCase(Locale.ROOT))
				: item.getName().equalsIgnoreCase(e.getKey()))));
	}

	public static boolean depositAllExcept(boolean exact, String... names)
	{
		if (names == null) return false;
		return depositAll(item -> item.getName() == null || Arrays.stream(names).filter(java.util.Objects::nonNull).noneMatch(n -> exact
			? item.getName().equalsIgnoreCase(n) : item.getName().toLowerCase(Locale.ROOT).contains(n.toLowerCase(Locale.ROOT))));
	}

	// ---- withdrawals (the kit grid) ----

	private static boolean withdraw(Rs2ItemModel item, String label)
	{
		return item != null && isOpen() && option(ITEM_CONTAINER_PACKED_ID, item.getSlot(), label);
	}

	public static boolean withdrawOne(Rs2ItemModel rs2Item)
	{
		return !Rs2Inventory.isFull() && withdraw(rs2Item, "Withdraw-1");
	}

	public static boolean withdrawOne(int id)
	{
		return withdrawOne(findKitItem(id));
	}

	public static boolean withdrawOne(String name, boolean exact)
	{
		return withdrawOne(findKitItem(name, exact));
	}

	public static boolean withdrawOne(String name)
	{
		return withdrawOne(name, false);
	}

	/** As upstream: nothing to do when already carried and {@code checkInv}. */
	public static boolean withdrawOne(boolean checkInv, int id)
	{
		return checkInv && Rs2Inventory.hasItem(id) || withdrawOne(id);
	}

	public static boolean withdrawItem(int id)
	{
		return withdrawOne(id);
	}

	public static boolean withdrawItem(String name)
	{
		return withdrawOne(name);
	}

	public static boolean withdrawItem(boolean checkInv, int id)
	{
		return withdrawOne(checkInv, id);
	}

	public static boolean withdrawItem(boolean checkInv, String name)
	{
		return checkInv && Rs2Inventory.hasItem(name) || withdrawOne(name);
	}

	public static boolean withdrawItem(boolean checkInv, String name, boolean exact)
	{
		return checkInv && Rs2Inventory.hasItem(name, exact) || withdrawOne(name, exact);
	}

	public static boolean withdrawAll(Rs2ItemModel rs2Item)
	{
		return rs2Item != null && (!Rs2Inventory.isFull() || rs2Item.isStackable() && Rs2Inventory.hasItem(rs2Item.getId()))
			&& withdraw(rs2Item, "Withdraw-All");
	}

	public static boolean withdrawAll(int id)
	{
		return withdrawAll(findKitItem(id));
	}

	public static boolean withdrawAll(String name, boolean exact)
	{
		return withdrawAll(findKitItem(name, exact));
	}

	public static boolean withdrawAll(String name)
	{
		return withdrawAll(name, false);
	}

	private static boolean withdrawAllButOne(Rs2ItemModel rs2Item)
	{
		if (rs2Item == null || !isOpen()) return false;
		return Rs2Inventory.isFull() || withdraw(rs2Item, "Withdraw-All-but-one");
	}

	public static boolean withdrawAllButOne(int id)
	{
		return withdrawAllButOne(findKitItem(id));
	}

	public static boolean withdrawAllButOne(String name)
	{
		return withdrawAllButOne(name, false);
	}

	public static boolean withdrawAllButOne(String name, boolean exact)
	{
		return withdrawAllButOne(findKitItem(name, exact));
	}

	public static boolean withdrawFive(Rs2ItemModel rs2Item)
	{
		return !Rs2Inventory.isFull() && withdraw(rs2Item, "Withdraw-5");
	}

	public static boolean withdrawFive(int id)
	{
		return withdrawFive(findKitItem(id));
	}

	public static boolean withdrawFive(String name)
	{
		return withdrawFive(name, false);
	}

	public static boolean withdrawFive(String name, boolean exact)
	{
		return withdrawFive(findKitItem(name, exact));
	}

	/** As upstream: nothing to do when at least five are carried and {@code checkInv}. */
	public static boolean withdrawFive(boolean checkInv, String name, boolean exact)
	{
		return checkInv && Rs2Inventory.itemQuantity(name, exact) >= 5 || withdrawFive(name, exact);
	}

	/** As upstream: "Withdraw-X" and the amount typed at the prompt. */
	public static boolean withdrawX(Rs2ItemModel rs2Item, int amount)
	{
		if (rs2Item == null || !isOpen()) return false;
		if (amount <= 0) return true;
		if (Rs2Inventory.isFull() && !Rs2Inventory.hasItem(rs2Item.getId()) && !rs2Item.isStackable()) return false;
		return withdraw(rs2Item, "Withdraw-X") && enterAmount(amount);
	}

	public static boolean withdrawX(int id, int amount)
	{
		return withdrawX(findKitItem(id), amount);
	}

	public static boolean withdrawX(String name, int amount, boolean exact)
	{
		return withdrawX(findKitItem(name, exact), amount);
	}

	public static boolean withdrawX(String name, int amount)
	{
		return withdrawX(name, amount, false);
	}

	public static boolean withdrawX(boolean checkInv, int id, int amount)
	{
		return checkInv && Rs2Inventory.itemQuantity(id) >= amount || withdrawX(id, amount);
	}

	public static boolean withdrawX(boolean checkInv, String name, int amount)
	{
		return withdrawX(checkInv, name, amount, false);
	}

	public static boolean withdrawX(boolean checkInv, String name, int amount, boolean exact)
	{
		return checkInv && Rs2Inventory.itemQuantity(name, exact) >= amount || withdrawX(name, amount, exact);
	}

	/**
	 * As upstream: withdraws what is missing for {@code requiredAmount} carried: "Withdraw-X" for a
	 * stackable (or one), else one at a time (bounded). True once enough is carried.
	 */
	public static boolean withdrawDeficit(int id, int requiredAmount)
	{
		int deficit = requiredAmount - Rs2Inventory.itemQuantity(id);
		if (deficit <= 0) return true;
		Rs2ItemModel inKit = findKitItem(id);
		if (inKit == null || inKit.getQuantity() < deficit) return false;
		if (inKit.isStackable() || deficit == 1)
		{
			return withdrawX(inKit, deficit);
		}
		for (int safety = 0; safety < 40 && Rs2Inventory.itemQuantity(id) < requiredAmount; safety++)
		{
			Rs2ItemModel next = findKitItem(id);
			if (Rs2Inventory.isFull() || next == null || !withdrawOne(next)) break;
			Rs2Inventory.waitForInventoryChanges(1200);
		}
		return Rs2Inventory.itemQuantity(id) >= requiredAmount;
	}

	public static boolean withdrawDeficit(String name, int requiredAmount, boolean exact)
	{
		int deficit = requiredAmount - Rs2Inventory.itemQuantity(name, exact);
		if (deficit <= 0) return true;
		return hasKitItem(name, deficit, exact) && withdrawX(findKitItem(name, exact), deficit);
	}

	public static boolean withdrawDeficit(String name, int requiredAmount)
	{
		return withdrawDeficit(name, requiredAmount, false);
	}

	/** As upstream: "Withdraw-All" on each stackable in the kit, at most {@code maxOps} times. */
	public static boolean withdrawAllStackables(int maxOps)
	{
		if (!isOpen()) return false;
		for (int ops = 0, pass = 0; ops < maxOps && pass < maxOps * 8; pass++)
		{
			Rs2ItemModel stackable = stream().filter(Rs2ItemModel::isStackable).findFirst().orElse(null);
			if (stackable == null) break;
			if (Rs2Inventory.isFull() && !Rs2Inventory.hasItem(stackable.getId())) return false;
			int before = count(stackable.getId());
			if (!withdrawAll(stackable)) return false;
			Global.sleepUntil(() -> count(stackable.getId()) < before, ACTION_TIMEOUT_MILLIS);
			ops++;
		}
		return true;
	}
}
