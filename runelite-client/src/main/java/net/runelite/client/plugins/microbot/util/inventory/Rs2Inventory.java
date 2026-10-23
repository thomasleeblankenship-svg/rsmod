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
import java.util.concurrent.ThreadLocalRandom;
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
	public static boolean contains(int id) { return hasItem(id); }
	public static boolean contains(String name) { return hasItem(name); }
	public static boolean isFull() { return count() >= 28; }
	public static int emptySlotCount() { return Math.max(0, 28 - count()); }
	public static List<Rs2ItemModel> getList(Predicate<Rs2ItemModel> predicate) { return items(predicate).collect(Collectors.toList()); }
	public static Rs2ItemModel get(int id) { return items(i -> i.getId() == id).findFirst().orElse(null); }
	public static Rs2ItemModel get(String name) { return items(i -> matches(i.getName(), name, false)).findFirst().orElse(null); }

	private static boolean matches(String candidate, String search, boolean exact)
	{
		if (candidate == null || search == null || search.trim().isEmpty()) return false;
		return exact ? candidate.equalsIgnoreCase(search.trim())
			: candidate.toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT));
	}

	public static boolean interact(int id, String action)
	{
		Rs2ItemModel item = get(id);
		return item != null && MobileActions.inventory(item.getSlot(), item.getId(), action);
	}

	public static boolean use(String name)
	{
		Rs2ItemModel item = get(name);
		return item != null && MobileActions.inventory(item.getSlot(), item.getId(), "Use");
	}

	public static boolean use(int id) { return interact(id, "Use"); }

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

	public static boolean combine(int firstId, int secondId)
	{
		Rs2ItemModel first = get(firstId);
		Rs2ItemModel second = get(secondId);
		return first != null && second != null && first.getSlot() != second.getSlot()
			&& MobileActions.itemOnItem(first.getSlot(), second.getSlot());
	}

	public static boolean useItemOnObject(int itemId, int objectId)
	{
		Rs2ItemModel item = get(itemId);
		return item != null && MobileActions.itemOnObject(item.getSlot(), objectId);
	}

	/** Orders a copied list; never mutates a published inventory snapshot. */
	static List<Rs2ItemModel> ordered(List<Rs2ItemModel> source, InteractOrder order)
	{
		List<Rs2ItemModel> copy = new ArrayList<>(source);
		if (order == InteractOrder.RANDOM)
		{
			Collections.shuffle(copy, ThreadLocalRandom.current());
			return copy;
		}
		copy.sort(Comparator.comparingInt(i -> orderKey(i.getSlot(), order)));
		return copy;
	}

	private static int orderKey(int slot, InteractOrder order)
	{
		int row = slot / 4;
		int column = slot % 4;
		if (order == InteractOrder.COLUMN) return column * 7 + row;
		if (order == InteractOrder.EFFICIENT_COLUMN) return column * 7 + (column % 2 == 0 ? row : 6 - row);
		if (order == InteractOrder.EFFICIENT_ROW || order == InteractOrder.ZIGZAG)
			return row * 4 + (row % 2 == 0 ? column : 3 - column);
		return slot;
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

	public static boolean dropAllExcept(boolean exact, InteractOrder order, String... names)
	{
		dropFailure = "";
		Predicate<Rs2ItemModel> removable = i -> !isProtectedAxe(i.getId(), i.getName())
			&& Arrays.stream(names).noneMatch(n -> matches(i.getName(), n, exact));
		// A full reset never completes merely because the first drop freed one slot.
		// Allow logs awarded by an already-running chop while the batch is draining.
		final int maxDrops = 56;
		for (int dropped = 0; dropped <= maxDrops; dropped++)
		{
			List<Rs2ItemModel> live = refreshInventory();
			if (live == null) { dropFailure = "Inventory could not be read"; return false; }
			List<Rs2ItemModel> targets = ordered(live.stream().filter(removable).collect(Collectors.toList()), order);
			if (dropped == 0) { LOG.info("Mobile inventory reset: {} eligible items; axes protected", targets.size()); }
			if (targets.isEmpty()) { LOG.info("Mobile inventory reset complete: {} drops", dropped); return true; }
			if (dropped == maxDrops) { dropFailure = "Inventory kept refilling during the drop batch"; }
			if (dropped == maxDrops || !dropSlot(targets.get(0)))
			{
				LOG.warn("Mobile inventory reset failed after {} drops, slot {} item {}: {}",
					dropped, targets.get(0).getSlot(), targets.get(0).getId(), dropFailure);
				return false;
			}
		}
		return false;
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

}
