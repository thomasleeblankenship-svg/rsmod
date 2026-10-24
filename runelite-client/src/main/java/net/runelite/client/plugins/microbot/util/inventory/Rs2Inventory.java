/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/inventory/Rs2Inventory.java
 * (storeInventoryItemsInMemory, items, all, count overloads). BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;

/**
 * Inventory snapshots and cancellation-aware mobile actions with upstream slot-count semantics.
 * <p>
 * The item list is an immutable snapshot rebuilt on the client thread from
 * {@link ItemContainerChanged} for {@link InventoryID#INV}. If it is empty while
 * logged in, {@link #items()} loads it once from the container on the client
 * thread (upstream fallback). {@code count(...)} counts occupied slots, not
 * stack quantities: 1,000 coins in one slot count as 1.
 * <p>
 * Actions use MobileActions and never synthesize desktop input. Batch operations
 * wait for observed container changes, stop on failed actions, and refuse to block
 * the client thread. The snapshot is cleared when the runtime is disabled.
 */
public final class Rs2Inventory
{
	private static volatile List<Rs2ItemModel> inventoryItems = Collections.emptyList();
	private static volatile String dropFailure = "";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Rs2Inventory.class);
	private static final java.util.regex.Pattern AXE_NAME = java.util.regex.Pattern.compile("(?i)(^|[^a-z])axe([^a-z]|$)");

	private Rs2Inventory()
	{
	}

	/** Event hook (client thread); routed by {@code MicrobotReadServices}. */
	public static void storeInventoryItemsInMemory(ItemContainerChanged e)
	{
		MicrobotReadServices services = MicrobotReadServices.current();
		if (services == null || e.getContainerId() != InventoryID.INV)
		{
			return;
		}
		ItemContainer itemContainer = e.getItemContainer();
		if (itemContainer == null)
		{
			return;
		}
		inventoryItems = snapshot(services.getClient(), itemContainer);
	}

	/** Forgets the snapshot (logout, hop, connection loss, provider uninstall). */
	public static void clear()
	{
		inventoryItems = Collections.emptyList();
	}

	private static List<Rs2ItemModel> snapshot(Client client, ItemContainer itemContainer)
	{
		List<Rs2ItemModel> items = new ArrayList<>();
		Item[] slots = itemContainer.getItems();
		for (int i = 0; i < slots.length; i++)
		{
			Item item = slots[i];
			if (item == null || item.getId() == -1)
			{
				continue;
			}
			ItemComposition composition = client.getItemDefinition(item.getId());
			ItemComposition linked = composition != null && composition.getNote() == 799
				? client.getItemDefinition(composition.getLinkedNoteId())
				: null;
			items.add(new Rs2ItemModel(item, composition, linked, i));
		}
		return Collections.unmodifiableList(items);
	}

	public static Stream<Rs2ItemModel> items()
	{
		MicrobotReadServices services = MicrobotReadServices.current();
		if (inventoryItems.isEmpty() && services != null && Microbot.isLoggedIn())
		{
			Client client = services.getClient();
			services.read(() ->
			{
				ItemContainer itemContainer = client.getItemContainer(InventoryID.INV);
				if (itemContainer != null)
				{
					inventoryItems = snapshot(client, itemContainer);
				}
				return null;
			});
		}
		return inventoryItems.stream();
	}

	public static Stream<Rs2ItemModel> items(Predicate<Rs2ItemModel> predicate)
	{
		return items().filter(predicate);
	}

	/** All occupied slots. */
	public static List<Rs2ItemModel> all()
	{
		return items().collect(Collectors.toList());
	}

	/** Number of occupied slots (upstream: {@code items().count()}), not summed quantity. */
	public static int count()
	{
		return (int) items().count();
	}

	/** Number of occupied slots matching {@code predicate}. */
	public static int count(Predicate<Rs2ItemModel> predicate)
	{
		return (int) items(predicate).count();
	}

	/** Number of occupied slots holding item {@code id}. */
	public static int count(int id)
	{
		return count(item -> item.getId() == id);
	}

	/** Number of occupied slots whose name equals (exact) or contains {@code name}, case-insensitively. */
	public static int count(String name, boolean exact)
	{
		return count(exact ? item -> item.getName().equalsIgnoreCase(name) :
			item -> item.getName().toLowerCase().contains(name.toLowerCase()));
	}

	/** Number of occupied slots whose name contains {@code name}, case-insensitively. */
	public static int count(String name)
	{
		return count(item -> item.getName().toLowerCase().contains(name.toLowerCase()));
	}

	public static boolean hasItem(int id) { return count(id) > 0; }
	public static boolean hasItem(String name) { return count(name) > 0; }
	/** Upstream: any of {@code ids} is in the inventory. */
	public static boolean contains(int... ids)
	{
		return ids != null && items(i -> Arrays.stream(ids).anyMatch(id -> id == i.getId())).findAny().isPresent();
	}

	/** Upstream default is an exact, case-insensitive name match ("Logs" does not match "Oak logs"). */
	public static boolean contains(String... names) { return contains(true, names); }

	public static boolean contains(String name, boolean exact) { return contains(exact, name); }

	/** Any of {@code names} is in the inventory, matched exactly or as a substring, case-insensitively. */
	public static boolean contains(boolean exact, String... names)
	{
		return names != null && items(i -> Arrays.stream(names).anyMatch(n -> matches(i.getName(), n, exact)))
			.findAny().isPresent();
	}
	public static boolean isFull() { return count() >= 28; }
	public static int emptySlotCount() { return Math.max(0, 28 - count()); }
	/** As upstream: the number of empty inventory slots ({@link #emptySlotCount()}). */
	public static int getEmptySlots() { return emptySlotCount(); }
	/** As upstream: a random item matching {@code filter}, or {@code null}. */
	public static Rs2ItemModel getRandom(Predicate<Rs2ItemModel> filter)
	{
		List<Rs2ItemModel> matching = items(filter).collect(Collectors.toList());
		return matching.isEmpty() ? null : matching.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(matching.size()));
	}
	public static Rs2ItemModel getRandom(int... ids) { return getRandom(item -> Arrays.stream(ids).anyMatch(id -> id == item.getId())); }
	public static Rs2ItemModel getRandom(String... names) { return getRandom(item -> Arrays.stream(names).anyMatch(name -> item.getName().equalsIgnoreCase(name))); }
	public static List<Rs2ItemModel> getList(Predicate<Rs2ItemModel> predicate) { return items(predicate).collect(Collectors.toList()); }
	/** As upstream: the last slot holding {@code id}. */
	public static Rs2ItemModel getLast(int id) { return items(i -> i.getId() == id).reduce((a, b) -> b).orElse(null); }
	/** As upstream: items with a "Scatter" option. */
	public static List<Rs2ItemModel> getAshes()
	{
		return items(i -> i.getInventoryActions() != null && Arrays.stream(i.getInventoryActions()).anyMatch("scatter"::equalsIgnoreCase))
			.collect(Collectors.toList());
	}
	/** As upstream: every item matching {@code filter}. */
	public static List<Rs2ItemModel> all(Predicate<Rs2ItemModel> filter) { return items(filter).collect(Collectors.toList()); }
	public static Rs2ItemModel get(int id) { return items(i -> i.getId() == id).findFirst().orElse(null); }
	public static Rs2ItemModel get(String name) { return items(i -> matches(i.getName(), name, false)).findFirst().orElse(null); }
	/** As upstream: the first item whose name contains any of {@code names}, ignoring case. */
	public static Rs2ItemModel get(String... names)
	{
		return names == null ? null : items(i -> Arrays.stream(names).anyMatch(n -> matches(i.getName(), n, false))).findFirst().orElse(null);
	}
	/** As upstream: the first item matching {@code predicate}. */
	public static Rs2ItemModel get(Predicate<Rs2ItemModel> predicate) { return items(predicate).findFirst().orElse(null); }

	private static boolean matches(String candidate, String search, boolean exact)
	{
		if (candidate == null || search == null || search.trim().isEmpty()) return false;
		return exact ? candidate.equalsIgnoreCase(search.trim())
			: candidate.toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT));
	}

	/**
	 * As upstream: one inventory action on {@code item}; a blank action means the item's first
	 * inventory action. {@code true} means dispatched.
	 */
	public static boolean interact(Rs2ItemModel item, String action)
	{
		if (item == null) return false;
		String resolved = action == null || action.trim().isEmpty() ? firstAction(item.getId()) : action;
		return resolved != null && MobileActions.inventory(item.getSlot(), item.getId(), resolved);
	}

	public static boolean interact(Rs2ItemModel item) { return interact(item, ""); }
	/**
	 * As upstream: drags {@code item} onto inventory {@code slot}. False for a missing item, a slot
	 * out of range or the slot it is already in, or when the inventory is not showing (it is opened
	 * first, as upstream). True once the drag has run; the caller observes the swap.
	 */
	public static boolean moveItemToSlot(Rs2ItemModel item, int slot)
	{
		if (item == null || slot < 0 || slot >= 28 || item.getSlot() == slot)
		{
			return false;
		}
		if (!MobileActions.ensureInventoryVisible() && !waitFor(MobileActions::ensureInventoryVisible, 2_000))
		{
			return false;
		}
		java.awt.Rectangle[] bounds = MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget inventory = Microbot.getClient().getWidget(net.runelite.api.widgets.WidgetInfo.INVENTORY);
			net.runelite.api.widgets.Widget[] slots = inventory == null ? null : inventory.getDynamicChildren();
			if (slots == null || slots.length <= Math.max(slot, item.getSlot()) || slots[item.getSlot()] == null || slots[slot] == null)
			{
				return null;
			}
			return new java.awt.Rectangle[]{slots[item.getSlot()].getBounds(), slots[slot].getBounds()};
		}, null);
		if (bounds == null)
		{
			return false;
		}
		Microbot.drag(bounds[0], bounds[1]);
		return true;
	}

	/**
	 * As upstream, where it hovers only when natural mouse is on: humanised input is not ported, so
	 * this never moves anything and returns false.
	 */
	public static boolean hover(Rs2ItemModel item)
	{
		return false;
	}

	/** As upstream: the first inventory action of the item in {@code slot}. */
	public static boolean slotInteract(int slot) { return slotInteract(slot, ""); }
	/** As upstream: {@code action} (or, blank, the first action) on the item in {@code slot}. */
	public static boolean slotInteract(int slot, String action) { return interact(getItemInSlot(slot), action); }
	public static boolean interact(int id) { return interact(id, ""); }
	public static boolean interact(int id, String action) { return interact(get(id), action); }
	public static boolean interact(String name) { return interact(name, "", false); }
	public static boolean interact(String name, String action) { return interact(name, action, false); }

	public static boolean interact(String name, String action, boolean exact)
	{
		return interact(items(i -> matches(i.getName(), name, exact)).findFirst().orElse(null), action);
	}

	/** As upstream: tries each id in order until one dispatches. */
	public static boolean interact(int[] ids, String action)
	{
		return Arrays.stream(ids).anyMatch(id -> interact(id, action));
	}

	public static boolean interact(String[] names, String action)
	{
		return Arrays.stream(names).anyMatch(name -> interact(name, action));
	}

	/** As upstream, the predicate-only form uses the item. */
	public static boolean interact(Predicate<Rs2ItemModel> filter) { return interact(filter, "Use"); }

	public static boolean interact(Predicate<Rs2ItemModel> filter, String action)
	{
		return interact(items(filter).findFirst().orElse(null), action);
	}

	/**
	 * As upstream: equips the first listed item present in the inventory; {@code true} when one is
	 * present (dispatched, or already worn). Mobile uses whichever of Wield, Wear or Equip the item offers.
	 */
	public static boolean wield(String... names)
	{
		String name = Arrays.stream(names).filter(Rs2Inventory::hasItem).findFirst().orElse(null);
		if (name == null) return false;
		return Rs2Equipment.isWearing(name, true) || equip(items(i -> matches(i.getName(), name, false)).findFirst().orElse(null));
	}

	public static boolean wield(int... ids)
	{
		int id = Arrays.stream(ids).filter(Rs2Inventory::hasItem).findFirst().orElse(-1);
		if (id < 0) return false;
		return Rs2Equipment.isWearing(id) || equip(get(id));
	}

	public static boolean wear(String... names) { return wield(names); }

	public static boolean equip(int... ids) { return wield(ids); }

	/** As upstream: {@code false} when the item is missing or already worn. */
	public static boolean wear(int id)
	{
		return hasItem(id) && !Rs2Equipment.isWearing(id) && equip(get(id));
	}

	/** Dispatches the item's equip action (Wield, Wear or Equip, whichever it offers). */
	public static boolean equip(Rs2ItemModel item)
	{
		if (item == null) return false;
		String action = MobileActions.read(() ->
		{
			ItemComposition definition = Microbot.getClient().getItemDefinition(item.getId());
			String[] actions = definition == null ? null : definition.getInventoryActions();
			return actions == null ? null : Arrays.stream(actions)
				.filter(a -> "Wield".equalsIgnoreCase(a) || "Wear".equalsIgnoreCase(a) || "Equip".equalsIgnoreCase(a))
				.findFirst().orElse(null);
		}, null);
		return action != null && MobileActions.inventory(item.getSlot(), item.getId(), action);
	}

	private static String firstAction(int id)
	{
		return MobileActions.read(() ->
		{
			ItemComposition definition = Microbot.getClient().getItemDefinition(id);
			String[] actions = definition == null ? null : definition.getInventoryActions();
			return actions == null ? null : Arrays.stream(actions).filter(a -> a != null && !a.trim().isEmpty()).findFirst().orElse(null);
		}, null);
	}

	public static boolean use(String name)
	{
		Rs2ItemModel item = get(name);
		return item != null && MobileActions.inventory(item.getSlot(), item.getId(), "Use");
	}

	public static boolean use(int id) { return interact(id, "Use"); }
	/** As upstream: "Use" on this item, selecting it for use on something else. */
	public static boolean use(Rs2ItemModel item) { return item != null && interact(item, "Use"); }

	public static boolean useLast(int id)
	{
		List<Rs2ItemModel> candidates = getList(i -> i.getId() == id);
		return !candidates.isEmpty() && MobileActions.inventory(candidates.get(candidates.size() - 1).getSlot(), id, "Use");
	}

	public static boolean isItemSelected()
	{
		return MobileActions.read(() -> Microbot.getClient().isWidgetSelected(), false);
	}

	public static boolean combineClosest(String primary, String secondary)
	{
		Rs2ItemModel first = get(primary);
		Rs2ItemModel second = get(secondary);
		return first != null && second != null && first.getSlot() != second.getSlot()
			&& MobileActions.itemOnItem(first.getSlot(), second.getSlot());
	}

	/**
	 * As upstream: uses the stack of {@code primaryItemId} on the stack of {@code secondaryItemId}
	 * whose slots are closest in the inventory grid (Manhattan distance over rows of four).
	 */
	public static boolean combineClosest(int primaryItemId, int secondaryItemId)
	{
		List<Rs2ItemModel> primaries = items(i -> i.getId() == primaryItemId).collect(Collectors.toList());
		List<Rs2ItemModel> secondaries = items(i -> i.getId() == secondaryItemId).collect(Collectors.toList());
		Rs2ItemModel bestFirst = null;
		Rs2ItemModel bestSecond = null;
		int best = Integer.MAX_VALUE;
		for (Rs2ItemModel first : primaries)
		{
			for (Rs2ItemModel second : secondaries)
			{
				if (first.getSlot() == second.getSlot())
				{
					continue;
				}
				int distance = Math.abs(first.getSlot() / 4 - second.getSlot() / 4) + Math.abs(first.getSlot() % 4 - second.getSlot() % 4);
				if (distance < best)
				{
					best = distance;
					bestFirst = first;
					bestSecond = second;
				}
			}
		}
		return bestFirst != null && MobileActions.itemOnItem(bestFirst.getSlot(), bestSecond.getSlot());
	}

	public static boolean combine(int firstId, int secondId)
	{
		Rs2ItemModel first = get(firstId);
		Rs2ItemModel second = get(secondId);
		return first != null && second != null && first.getSlot() != second.getSlot()
			&& MobileActions.itemOnItem(first.getSlot(), second.getSlot());
	}

	/** As upstream: uses the first item with {@code primary}'s id on another slot holding {@code secondary}'s id. */
	public static boolean combine(Rs2ItemModel primary, Rs2ItemModel secondary)
	{
		if (primary == null || secondary == null) return false;
		Rs2ItemModel first = get(primary.getId());
		Rs2ItemModel second = first == null ? null
			: items(i -> i.getId() == secondary.getId() && i.getSlot() != first.getSlot()).findFirst().orElse(null);
		return second != null && MobileActions.itemOnItem(first.getSlot(), second.getSlot());
	}

	/** As upstream: sells {@code quantity} ("1", "5", "10" or "50") of the item with this name (exact) to the open shop. */
	public static boolean sellItem(String itemName, String quantity)
	{
		return sell(items(i -> matches(i.getName(), itemName, true)).findFirst().orElse(null), quantity);
	}

	/** As upstream: sells {@code quantity} of the item with this id to the open shop. */
	public static boolean sellItem(int itemId, String quantity)
	{
		return sell(get(itemId), quantity);
	}

	/** The item's "Sell N" option on the inventory the shop shows beside its stock. */
	private static boolean sell(Rs2ItemModel item, String quantity)
	{
		String option = "Sell " + quantity;
		return item != null && waitFor(() -> MobileActions.widget(net.runelite.api.gameval.InterfaceID.Shopside.ITEMS, item.getSlot(), option), 2_000);
	}

	/** As upstream: combines the first items with these names (any order). */
	public static boolean combine(String primary, String secondary)
	{
		return combineClosest(primary, secondary);
	}

	/**
	 * As upstream: uses the unnoted item with this name (case-insensitive substring) on the nearest
	 * object with this id. False while the bank is open.
	 */
	public static boolean useUnNotedItemOnObject(String itemName, int objectId)
	{
		if (net.runelite.client.plugins.microbot.util.bank.Rs2Bank.isOpen()) return false;
		Rs2ItemModel item = items(i -> !i.isNoted() && matches(i.getName(), itemName, false)).findFirst().orElse(null);
		return item != null && MobileActions.itemOnObject(item.getSlot(), objectId);
	}

	public static boolean useItemOnObject(int itemId, int objectId)
	{
		Rs2ItemModel item = get(itemId);
		return item != null && MobileActions.itemOnObject(item.getSlot(), objectId);
	}

	/** As upstream: uses the item with this id on the nearest NPC with {@code npcId}. False while the bank is open. */
	public static boolean useItemOnNpc(int itemId, int npcId)
	{
		net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel npc = Microbot.getRs2NpcCache() == null ? null
			: Microbot.getRs2NpcCache().query().withId(npcId).nearest();
		return npc != null && useItemOnNpc(itemId, npc);
	}

	/** As upstream: uses the item with this id on {@code npc}. False while the bank is open. */
	public static boolean useItemOnNpc(int itemId, net.runelite.api.NPC npc)
	{
		if (npc == null) return false;
		Integer index = MobileActions.read(npc::getIndex, null);
		return index != null && useItemOnNpcIndex(itemId, index);
	}

	/** As upstream: uses the item with this id on {@code npc}. False while the bank is open. */
	public static boolean useItemOnNpc(int itemId, net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel npc)
	{
		return npc != null && useItemOnNpcIndex(itemId, npc.getIndex());
	}

	private static boolean useItemOnNpcIndex(int itemId, int npcIndex)
	{
		if (net.runelite.client.plugins.microbot.util.bank.Rs2Bank.isOpen()) return false;
		Rs2ItemModel item = get(itemId);
		return item != null && MobileActions.itemOnNpc(item.getSlot(), npcIndex);
	}

	/**
	 * As upstream: sorts {@code rs2Items} in place by slot into {@code interactOrder} and returns it.
	 * {@code STANDARD} and {@code RANDOM} leave the list as it is. Pass a mutable copy, never a
	 * published snapshot.
	 */
	public static List<Rs2ItemModel> calculateInteractOrder(List<Rs2ItemModel> rs2Items, InteractOrder interactOrder)
	{
		switch (interactOrder)
		{
			case EFFICIENT_ROW:
				rs2Items.sort(Comparator.comparingInt((Rs2ItemModel i) -> i.getSlot() / COLUMNS)
					.thenComparingInt(i -> (i.getSlot() / COLUMNS) % 2 == 0 ? i.getSlot() % COLUMNS : -(i.getSlot() % COLUMNS)));
				return rs2Items;
			case COLUMN:
				rs2Items.sort(Comparator.comparingInt((Rs2ItemModel i) -> i.getSlot() % COLUMNS)
					.thenComparingInt(i -> i.getSlot() / COLUMNS));
				return rs2Items;
			case EFFICIENT_COLUMN:
				rs2Items.sort(Comparator.comparingInt((Rs2ItemModel i) -> i.getSlot() % COLUMNS)
					.thenComparingInt(i -> (i.getSlot() % COLUMNS) % 2 == 0 ? i.getSlot() / COLUMNS : -(i.getSlot() / COLUMNS)));
				return rs2Items;
			case ZIGZAG:
				rs2Items.sort(Comparator.comparingInt(i -> zigzagRank(i.getSlot())));
				return rs2Items;
			case STANDARD:
			default:
				return rs2Items;
		}
	}

	private static final int COLUMNS = 4;
	private static final int[] ZIGZAG_ORDER = {
		0, 4, 1, 5, 2, 6, 3, 7,
		11, 15, 10, 14, 9, 13, 8, 12,
		16, 20, 17, 21, 18, 22, 19, 23,
		27, 26, 25, 24
	};

	private static int zigzagRank(int slot)
	{
		for (int i = 0; i < ZIGZAG_ORDER.length; i++)
		{
			if (ZIGZAG_ORDER[i] == slot)
			{
				return i;
			}
		}
		return Integer.MAX_VALUE;
	}

	/** As upstream: whether the inventory tab is the open side panel. */
	public static boolean isOpen()
	{
		return net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.getCurrentTab()
			== net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.INVENTORY;
	}

	/** As upstream: whether {@code slot} holds nothing. */
	public static boolean isSlotEmpty(int slot)
	{
		return getItemInSlot(slot) == null;
	}

	/** As upstream: the selected ("Use") widget's name without colour tags, or {@code null} when none. */
	public static String getSelectedItemName()
	{
		return MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget widget = Microbot.getClient().getSelectedWidget();
			return widget == null || widget.getName() == null ? null
				: net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper.stripColTags(widget.getName());
		}, null);
	}

	/** As upstream: the item id of the selected widget (a "Use" item), or -1 when none. */
	public static int getSelectedItemId()
	{
		return MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget widget = Microbot.getClient().getSelectedWidget();
			return widget == null ? -1 : widget.getItemId();
		}, -1);
	}

	/**
	 * Upstream's {@link #calculateInteractOrder} on a copy, so a published inventory snapshot is
	 * never sorted in place.
	 */
	static List<Rs2ItemModel> ordered(List<Rs2ItemModel> source, InteractOrder order)
	{
		return calculateInteractOrder(new ArrayList<>(source), order);
	}

	/** Tools are protected independently of the editable keep list, including the leagues tool names. */
	public static boolean isProtectedAxe(int id, String name)
	{
		return name != null && AXE_NAME.matcher(name).find()
			|| id == net.runelite.api.gameval.ItemID.TRAILBLAZER_AXE
			|| id == net.runelite.api.gameval.ItemID.LEAGUE_TRAILBLAZER_AXE
			|| id == net.runelite.api.gameval.ItemID.TRAILBLAZER_AXE_EMPTY
			|| id == net.runelite.api.gameval.ItemID.TRAILBLAZER_AXE_NO_INFERNAL;
	}

	public static String dropFailure() { return dropFailure; }

	/** Refresh after each drop: a mobile inventory can rebuild/reorder widgets between actions. */
	private static List<Rs2ItemModel> refreshInventory()
	{
		return MobileActions.read(() ->
		{
			Client c = Microbot.getClient();
			ItemContainer container = c.getItemContainer(InventoryID.INV);
			if (container == null || container.getItems() == null) { return null; }
			inventoryItems = snapshot(c, container);
			return inventoryItems;
		}, null);
	}

	/** As upstream: drops everything but items whose names contain one of {@code names}; axes stay protected. */
	public static boolean dropAllExcept(String... names)
	{
		return dropAllExcept(false, InteractOrder.STANDARD, names);
	}

	public static boolean dropAllExcept(boolean exact, InteractOrder order, String... names)
	{
		return dropWhile(i -> !isProtectedAxe(i.getId(), i.getName())
			&& Arrays.stream(names).noneMatch(n -> matches(i.getName(), n, exact)), order);
	}

	/** As upstream: drops every inventory item matching {@code predicate}; axes are not protected. */
	public static boolean dropAll(Predicate<Rs2ItemModel> predicate)
	{
		return dropAll(predicate, InteractOrder.STANDARD);
	}

	public static boolean dropAll(Predicate<Rs2ItemModel> predicate, InteractOrder order)
	{
		return dropWhile(predicate, order);
	}

	public static boolean dropAll()
	{
		return dropAll(i -> true);
	}

	public static boolean dropAll(int... ids)
	{
		return dropAll(i -> Arrays.stream(ids).anyMatch(id -> id == i.getId()));
	}

	/** As upstream: names match exactly (case-insensitive). */
	public static boolean dropAll(String... names)
	{
		return dropAll(true, names);
	}

	public static boolean dropAll(boolean exact, String... names)
	{
		return dropAll(i -> Arrays.stream(names).anyMatch(n -> matches(i.getName(), n, exact)), InteractOrder.STANDARD);
	}

	/**
	 * Mobile: drops one matching item per dispatched action, re-reading the inventory before each,
	 * until none match. Upstream fires every drop from one stale list with fixed sleeps. Returns
	 * false, with {@link #dropFailure()} set, when the inventory cannot be read or a drop is refused.
	 */
	private static boolean dropWhile(Predicate<Rs2ItemModel> removable, InteractOrder order)
	{
		dropFailure = "";
		// A full reset never completes merely because the first drop freed one slot.
		// Allow logs awarded by an already-running chop while the batch is draining.
		final int maxDrops = 56;
		for (int dropped = 0; dropped <= maxDrops; dropped++)
		{
			List<Rs2ItemModel> live = refreshInventory();
			if (live == null) { dropFailure = "Inventory could not be read"; return false; }
			List<Rs2ItemModel> targets = ordered(live.stream().filter(removable).collect(Collectors.toList()), order);
			if (dropped == 0) { LOG.info("Mobile drop batch: {} eligible items", targets.size()); }
			if (targets.isEmpty()) { LOG.info("Mobile drop batch complete: {} drops", dropped); return true; }
			if (dropped == maxDrops) { dropFailure = "Inventory kept refilling during the drop batch"; }
			if (dropped == maxDrops || !dropSlot(targets.get(0)))
			{
				LOG.warn("Mobile drop batch failed after {} drops, slot {} item {}: {}",
					dropped, targets.get(0).getSlot(), targets.get(0).getId(), dropFailure);
				return false;
			}
		}
		return false;
	}

	/** As upstream: the inventory's fast food (priority 1 in {@code Rs2Food}, for example karambwans). */
	public static List<Rs2ItemModel> getInventoryFastFood()
	{
		java.util.Set<Integer> fast = net.runelite.client.plugins.microbot.util.misc.Rs2Food.getFastFoodIds();
		return getInventoryFood().stream().filter(item -> fast.contains(item.getId())).collect(Collectors.toList());
	}

	/** As upstream: fills every essence pouch the Runecrafting level allows, in size order. */
	public static boolean fillPouches()
	{
		for (net.runelite.client.plugins.microbot.pouch.Pouch pouch : net.runelite.client.plugins.microbot.pouch.Pouch.values())
		{
			if (pouch.hasRequiredRunecraftingLevel())
			{
				pouch.fill();
			}
		}
		return true;
	}

	/** As upstream: empties every usable essence pouch; false when the inventory is full. */
	public static boolean emptyPouches()
	{
		if (isFull())
		{
			return false;
		}
		for (net.runelite.client.plugins.microbot.pouch.Pouch pouch : net.runelite.client.plugins.microbot.pouch.Pouch.values())
		{
			if (pouch.hasRequiredRunecraftingLevel())
			{
				pouch.empty();
			}
		}
		return true;
	}

	/** As upstream: "Check" on every usable essence pouch; false when the inventory is full. */
	public static boolean checkPouches()
	{
		if (isFull())
		{
			return false;
		}
		for (net.runelite.client.plugins.microbot.pouch.Pouch pouch : net.runelite.client.plugins.microbot.pouch.Pouch.values())
		{
			if (pouch.hasRequiredRunecraftingLevel())
			{
				pouch.check();
			}
		}
		return true;
	}

	/** As upstream: opens the inventory tab. */
	public static boolean open()
	{
		return net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.switchToInventoryTab();
	}

	/** As upstream: drops the first inventory item matching {@code predicate}. */
	public static boolean drop(Predicate<Rs2ItemModel> predicate)
	{
		Rs2ItemModel item = predicate == null ? null : get(predicate);
		return item != null && dropSlot(item);
	}

	/** As upstream: clears a selected item by using it again; false when nothing is selected. */
	public static boolean deselect()
	{
		return isItemSelected() && use(getSelectedItemId());
	}

	/** As upstream: "Clean" on every grimy herb, in {@code interactOrder}. */
	public static void cleanHerbs(InteractOrder interactOrder)
	{
		for (Rs2ItemModel item : calculateInteractOrder(items(i -> i.getName() != null
			&& i.getName().toLowerCase(Locale.ROOT).contains("grimy")).collect(Collectors.toList()), interactOrder))
		{
			interact(item, "Clean");
		}
	}

	/** As upstream: drops the first inventory item with this ID; {@code true} means dispatched. */
	public static boolean drop(int id)
	{
		Rs2ItemModel item = get(id);
		return item != null && dropSlot(item);
	}

	public static boolean drop(String name)
	{
		Rs2ItemModel item = get(name);
		return item != null && dropSlot(item);
	}

	public static int dropAmount(String name, int amount, InteractOrder order)
	{
		int dropped = 0;
		for (Rs2ItemModel item : ordered(getList(i -> !isProtectedAxe(i.getId(), i.getName()) && matches(i.getName(), name, false)), order))
		{
			if (dropped >= Math.max(0, amount) || !dropSlot(item)) break;
			dropped++;
		}
		return dropped;
	}

	private static boolean dropSlot(Rs2ItemModel item)
	{
		if (isProtectedAxe(item.getId(), item.getName())) { dropFailure = "Axes are protected from dropping"; return false; }
		if (!canWait() || !waitFor(MobileActions::ensureInventoryVisible, 5_000))
		{
			dropFailure = "Inventory could not be opened: " + MobileActions.inventoryFailure();
			return false;
		}
		List<Rs2ItemModel> before = refreshInventory();
		if (before == null) { dropFailure = "Inventory could not be read"; return false; }
		long quantity = before.stream().filter(i -> i.getId() == item.getId()).mapToLong(Rs2ItemModel::getQuantity).sum();
		if (!slotMatches(item) || !waitFor(() -> slotMatches(item)
			&& MobileActions.inventory(item.getSlot(), item.getId(), "Drop"), 2_000))
		{
			dropFailure = MobileActions.inventoryFailure();
			return false;
		}
		boolean changed = waitFor(() ->
		{
			List<Rs2ItemModel> now = refreshInventory();
			return now != null && now.stream().filter(i -> i.getId() == item.getId())
				.mapToLong(Rs2ItemModel::getQuantity).sum() < quantity;
		}, 3_000);
		if (!changed) { dropFailure = "Drop was sent but the inventory did not change"; }
		return changed;
	}

	private static boolean slotMatches(Rs2ItemModel expected)
	{
		return items(i -> i.getSlot() == expected.getSlot() && i.getId() == expected.getId()
			&& i.getQuantity() == expected.getQuantity()).findAny().isPresent();
	}

	private static List<String> fingerprint()
	{
		return items().map(i -> i.getSlot() + ":" + i.getId() + ":" + i.getQuantity()).collect(Collectors.toList());
	}

	public static boolean waitForInventoryChanges(int timeout)
	{
		List<String> before = fingerprint();
		return waitFor(() -> !before.equals(fingerprint()), timeout);
	}

	public static boolean waitForInventoryChanges(Runnable action, int interval, int timeout)
	{
		if (!canWait()) return false;
		List<String> before = fingerprint();
		action.run();
		return waitFor(() -> !before.equals(fingerprint()), timeout);
	}

	/** Script-thread polling only; a takeover, Stop or pause aborts the wait. */
	public static boolean waitFor(BooleanSupplier condition, int timeout)
	{
		if (!canWait()) return false;
		long deadline = System.nanoTime() + Math.max(0, timeout) * 1_000_000L;
		do
		{
			if (!MobileActions.active() || Thread.currentThread().isInterrupted()) return false;
			if (condition.getAsBoolean()) return true;
			try { Thread.sleep(40); }
			catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
		}
		while (System.nanoTime() < deadline);
		return false;
	}

	private static boolean canWait()
	{
		Client client = Microbot.getClient();
		return client != null && !client.isClientThread() && MobileActions.active();
	}

	// ---- Reads requested for the Hub plugins (Microbot API names, written for mobile) ----

	/** Total stack size of every item accepted by {@code filter}. */
	public static int itemQuantity(Predicate<Rs2ItemModel> filter)
	{
		int total = 0;
		for (Rs2ItemModel item : getList(filter))
		{
			total += item.getQuantity();
		}
		return total;
	}

	/** Total stack size of item {@code id}. */
	public static int itemQuantity(int id)
	{
		return itemQuantity(item -> item.getId() == id);
	}

	/** Total stack size of items named {@code name} (exact) or containing it, ignoring case. */
	public static int itemQuantity(String name, boolean exact)
	{
		return itemQuantity(item -> matches(item.getName(), name, exact));
	}

	/** {@link #itemQuantity(String, boolean)} with an exact name. */
	public static int itemQuantity(String name)
	{
		return itemQuantity(name, true);
	}

	/** At least {@code amount} of item {@code id}. */
	public static boolean hasItemAmount(int id, int amount)
	{
		return hasItemAmount(id, amount, false);
	}

	/** Exactly {@code amount} of item {@code id} when {@code exact}, otherwise at least that many. */
	public static boolean hasItemAmount(int id, int amount, boolean exact)
	{
		int quantity = itemQuantity(id);
		return exact ? quantity == amount : quantity >= amount;
	}

	/**
	 * At least {@code amount} of the item whose name contains {@code name}: its stack size if it
	 * stacks, otherwise the number of slots holding it. False when no such item is carried.
	 */
	public static boolean hasItemAmount(String name, int amount)
	{
		Rs2ItemModel item = get(name);
		return item != null && hasItemAmount(name, amount, item.isStackable());
	}

	/** {@link #hasItemAmount(String, int, boolean, boolean)} with a partial name. */
	public static boolean hasItemAmount(String name, int amount, boolean stackable)
	{
		return hasItemAmount(name, amount, stackable, false);
	}

	/** Compares stack size for a stackable item and occupied slots otherwise. */
	public static boolean hasItemAmount(String name, int amount, boolean stackable, boolean exact)
	{
		int have = stackable ? itemQuantity(name, exact) : count(name, exact);
		return have >= amount;
	}

	/** Whether any of {@code ids} is carried. */
	public static boolean hasItem(int... ids)
	{
		return contains(ids);
	}

	/** Whether any carried item's name contains one of {@code names}, ignoring case. */
	public static boolean hasItem(String... names)
	{
		return contains(false, names);
	}

	/** Whether any carried item matches one of {@code names}, exactly or partially. */
	public static boolean hasItem(String[] names, boolean exact)
	{
		return contains(exact, names);
	}

	/** Whether a carried item matches {@code name}, exactly or partially. */
	public static boolean hasItem(String name, boolean exact)
	{
		return contains(exact, name);
	}

	/** Whether no slot is occupied. */
	public static boolean isEmpty()
	{
		return count() == 0;
	}

	/** Whether every carried item satisfies {@code predicate}; an empty inventory qualifies. */
	public static boolean onlyContains(Predicate<Rs2ItemModel> predicate)
	{
		return items().allMatch(predicate);
	}

	/** Whether every carried item is one of {@code ids}. */
	public static boolean onlyContains(int... ids)
	{
		return onlyContains(item -> Arrays.stream(ids).anyMatch(id -> id == item.getId()));
	}

	/** Whether every carried item is named exactly (ignoring case) one of {@code names}. */
	public static boolean onlyContains(String... names)
	{
		return onlyContains(item -> Arrays.stream(names).anyMatch(name -> matches(item.getName(), name, true)));
	}

	/** Carried food (see {@link Rs2ItemModel#isFood()}). */
	public static List<Rs2ItemModel> getInventoryFood()
	{
		return getList(Rs2ItemModel::isFood);
	}

	/** Carried items that can be buried. */
	public static List<Rs2ItemModel> getBones()
	{
		return getList(item -> hasAction(item, "Bury"));
	}

	private static boolean hasAction(Rs2ItemModel item, String action)
	{
		for (String candidate : item.getInventoryActions())
		{
			if (action.equalsIgnoreCase(candidate))
			{
				return true;
			}
		}
		return false;
	}

	/** Carried essence pouches the player has the Runecraft level to use. */
	private static Stream<net.runelite.client.plugins.microbot.pouch.Pouch> usablePouches()
	{
		return Arrays.stream(net.runelite.client.plugins.microbot.pouch.Pouch.values())
			.filter(net.runelite.client.plugins.microbot.pouch.Pouch::hasPouchInInventory)
			.filter(net.runelite.client.plugins.microbot.pouch.Pouch::hasRequiredRunecraftingLevel);
	}

	/** Whether any essence pouch is carried. */
	public static boolean hasAnyPouch()
	{
		return Arrays.stream(net.runelite.client.plugins.microbot.pouch.Pouch.values())
			.anyMatch(net.runelite.client.plugins.microbot.pouch.Pouch::hasPouchInInventory);
	}

	/** Whether a carried essence pouch is degraded. */
	public static boolean hasDegradedPouch()
	{
		return Arrays.stream(net.runelite.client.plugins.microbot.pouch.Pouch.values())
			.anyMatch(pouch -> pouch.isDegraded() && pouch.hasPouchInInventory());
	}

	/** Whether a usable carried pouch's contents have not been observed yet. */
	public static boolean anyPouchUnknown()
	{
		return usablePouches().anyMatch(net.runelite.client.plugins.microbot.pouch.Pouch::isUnknown);
	}

	/** Whether a usable carried pouch still has room (Microbot's name for "can be filled"). */
	public static boolean anyPouchEmpty()
	{
		return usablePouches().anyMatch(pouch -> pouch.getRemaining() > 0);
	}

	/** Whether a usable carried pouch holds essence (Microbot's name for "can be emptied"). */
	public static boolean anyPouchFull()
	{
		return usablePouches().anyMatch(pouch -> pouch.getHolding() > 0);
	}

	/** Whether every usable carried pouch is full. */
	public static boolean allPouchesFull()
	{
		return usablePouches().allMatch(pouch -> pouch.getRemaining() <= 0);
	}

	/** Whether every usable carried pouch holds nothing. */
	public static boolean allPouchesEmpty()
	{
		return usablePouches().allMatch(pouch -> pouch.getHolding() <= 0);
	}

	/** Free essence space across the usable carried pouches. */
	public static int getRemainingCapacityInPouches()
	{
		return usablePouches().mapToInt(net.runelite.client.plugins.microbot.pouch.Pouch::getRemaining).sum();
	}

	/** Whether any kind of rune pouch is carried. */
	public static boolean hasRunePouch()
	{
		return hasItem(Rs2RunePouch.POUCH_IDS);
	}

	private static final int[] INVENTORY_VIEWS = {
		net.runelite.api.widgets.ComponentID.INVENTORY_CONTAINER,
		net.runelite.api.widgets.ComponentID.BANK_INVENTORY_ITEM_CONTAINER,
		net.runelite.api.gameval.InterfaceID.INVENTORY_NOOPS << 16,
		net.runelite.api.gameval.InterfaceID.Shopside.ITEMS,
		net.runelite.api.widgets.ComponentID.GRAND_EXCHANGE_INVENTORY_INVENTORY_ITEM_CONTAINER,
		net.runelite.api.widgets.ComponentID.DEPOSIT_BOX_INVENTORY_ITEM_CONTAINER,
	};

	/**
	 * The item-container widget currently showing the inventory: the side panel, or the inventory
	 * shown beside the bank, bank PIN, shop, Grand Exchange or deposit box. {@code null} when none
	 * is visible. A live handle; dereference it on the client thread.
	 */
	public static net.runelite.api.widgets.Widget getInventory()
	{
		return MobileActions.read(() ->
		{
			for (int id : INVENTORY_VIEWS)
			{
				net.runelite.api.widgets.Widget view = Microbot.getClient().getWidget(id);
				if (view != null && !view.isHidden() && view.getDynamicChildren() != null)
				{
					return view;
				}
			}
			return null;
		}, null);
	}

	/** As upstream: inventory items with a "Drink" action. */
	public static List<Rs2ItemModel> getPotions()
	{
		return items(item -> Arrays.stream(item.getInventoryActions()).anyMatch("drink"::equalsIgnoreCase))
			.collect(Collectors.toList());
	}

	/** As upstream: drinkable items named {@code potionName}, ignoring the dose suffix such as "(3)". */
	public static List<Rs2ItemModel> getFilteredPotionItemsInInventory(String potionName)
	{
		return getFilteredPotionItemsInInventory(Collections.singletonList(potionName));
	}

	/** As upstream: drinkable items whose name without the dose suffix equals one of {@code potionNames}. */
	public static List<Rs2ItemModel> getFilteredPotionItemsInInventory(List<String> potionNames)
	{
		return getPotionsInInventory(potionNames.toArray(String[]::new)).collect(Collectors.toList());
	}

	private static final java.util.regex.Pattern POTION_USES = java.util.regex.Pattern.compile("^(.*?)(?:\\(\\d+\\))?$");

	private static Stream<Rs2ItemModel> getPotionsInInventory(String... potionNames)
	{
		return getPotions().stream().filter(item ->
		{
			java.util.regex.Matcher matcher = POTION_USES.matcher(item.getName());
			return matcher.matches() && Arrays.stream(potionNames).anyMatch(name -> name.equalsIgnoreCase(matcher.group(1).trim()));
		});
	}

	/** As upstream: any item accepted by {@code predicate}. */
	public static boolean contains(Predicate<Rs2ItemModel> predicate)
	{
		return items().anyMatch(predicate);
	}

	/** As upstream: inventory size, 28 slots. */
	public static int capacity()
	{
		return 28;
	}

	/** The item in {@code slot}, or {@code null} when it is empty. */
	public static Rs2ItemModel getItemInSlot(int slot)
	{
		return items(i -> i.getSlot() == slot).findFirst().orElse(null);
	}

	/** As upstream: {@code slot} holds one of {@code ids}. */
	public static boolean slotContains(int slot, int... ids)
	{
		Rs2ItemModel item = getItemInSlot(slot);
		return item != null && ids != null && Arrays.stream(ids).anyMatch(id -> id == item.getId());
	}

	/** As upstream: {@code slot} holds an item named exactly (case-insensitively) one of {@code names}. */
	public static boolean slotContains(int slot, String... names)
	{
		Rs2ItemModel item = getItemInSlot(slot);
		return item != null && names != null && Arrays.stream(names).anyMatch(name -> name.equalsIgnoreCase(item.getName()));
	}

	/** As upstream: the first noted item matching {@code name} (exact, or a substring), case-insensitively. */
	public static Rs2ItemModel getNotedItem(String name, boolean exact)
	{
		return items(Rs2ItemModel::isNoted).filter(exact ? item -> item.getName().equalsIgnoreCase(name)
			: item -> item.getName().toLowerCase().contains(name.toLowerCase())).findFirst().orElse(null);
	}

	public static boolean hasNotedItem(String name)
	{
		return getNotedItem(name, false) != null;
	}

	public static boolean hasNotedItem(String name, boolean exact)
	{
		return getNotedItem(name, exact) != null;
	}

	/** As upstream: the first unnoted item matching {@code name} (exact, or a substring), case-insensitively. */
	public static Rs2ItemModel getUnNotedItem(String name, boolean exact)
	{
		return items(exact ? item -> item.getName().equalsIgnoreCase(name) && !item.isNoted()
			: item -> item.getName().toLowerCase().contains(name.toLowerCase()) && !item.isNoted()).findFirst().orElse(null);
	}

	public static boolean hasUnNotedItem(String name)
	{
		return getUnNotedItem(name, false) != null;
	}

	public static boolean hasUnNotedItem(String name, boolean exact)
	{
		return getUnNotedItem(name, exact) != null;
	}
}
