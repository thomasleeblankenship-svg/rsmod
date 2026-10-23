/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/inventory/Rs2Inventory.java
 * (storeInventoryItemsInMemory, items, all, count overloads). BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.inventory;

import java.util.ArrayList;
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
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;

/**
 * Read-only view of the local player's inventory with upstream semantics.
 * <p>
 * The item list is an immutable snapshot rebuilt on the client thread from
 * {@link ItemContainerChanged} for {@link InventoryID#INV}. If it is empty while
 * logged in, {@link #items()} loads it once from the container on the client
 * thread (upstream fallback). {@code count(...)} counts occupied slots, not
 * stack quantities: 1,000 coins in one slot count as 1.
 * <p>
 * Adaptation: only the read surface needed by the first upstream script is
 * ported (no interactions, selection, dropping or banking). The snapshot is
 * cleared on logout, hop and connection loss; upstream keeps it.
 */
public final class Rs2Inventory
{
	private static volatile List<Rs2ItemModel> inventoryItems = Collections.emptyList();

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
			if (item.getId() == -1)
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
		if (inventoryItems.isEmpty() && services != null && services.isLoggedIn())
		{
			Client client = services.getClient();
			services.getClientThreadCalls().call(() ->
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
}
