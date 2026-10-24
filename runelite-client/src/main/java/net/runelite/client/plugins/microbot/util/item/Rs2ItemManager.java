package net.runelite.client.plugins.microbot.util.item;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.http.api.item.ItemPrice;

/**
 * Upstream 2.6.38's item lookups by name and id, over RuneLite's {@link ItemManager}. Every
 * item-manager call runs on the client thread; results fall back to -1, {@code null} or empty.
 */
public class Rs2ItemManager
{
	/**
	 * As upstream: the id for {@code itemName}: a banked or carried item of that exact name first,
	 * then the item manager's exact match; with {@code fuzzy}, the search result held in the largest
	 * quantity (inventory plus bank). -1 when nothing matches.
	 */
	public static int getItemIdByName(String itemName, boolean fuzzy)
	{
		if (itemName == null || itemName.trim().isEmpty())
		{
			return -1;
		}
		if (Rs2Bank.hasBankItem(itemName, true))
		{
			return Rs2Bank.getBankItem(itemName, true).getId();
		}
		if (Rs2Inventory.hasItem(itemName, true))
		{
			return Rs2Inventory.get(itemName).getId();
		}
		String name = itemName.trim();
		List<ItemPrice> results = search(name);
		if (results.isEmpty())
		{
			return -1;
		}
		Map<Integer, Integer> counts = new HashMap<>();
		for (ItemPrice item : results)
		{
			if (item.getName().equalsIgnoreCase(name))
			{
				return item.getId();
			}
			counts.put(item.getId(), Rs2Inventory.itemQuantity(item.getId()) + Rs2Bank.count(item.getId()));
		}
		if (!fuzzy)
		{
			return -1;
		}
		return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(results.get(0).getId());
	}

	/** As upstream: item manager search results for {@code query}; empty for a blank query. */
	public List<ItemPrice> searchItem(String query)
	{
		return query == null || query.trim().isEmpty() ? Collections.emptyList() : search(query);
	}

	/** As upstream: the first search result's id for {@code itemName}, or -1. */
	public int getItemId(String itemName)
	{
		List<ItemPrice> items = searchItem(itemName);
		return items.isEmpty() ? -1 : items.get(0).getId();
	}

	/** As upstream: the item's definition, or {@code null}. */
	public ItemComposition getItemComposition(int itemId)
	{
		return MobileActions.read(() ->
		{
			ItemManager manager = Microbot.getItemManager();
			return manager == null ? null : manager.getItemComposition(itemId);
		}, null);
	}

	/** As upstream: the definition's store value, or -1. */
	public int getPrice(int itemId)
	{
		ItemComposition composition = getItemComposition(itemId);
		return composition == null ? -1 : composition.getPrice();
	}

	/** As upstream: the GE offer price of the first search result for {@code itemName}, or -1. */
	public int getGEPrice(String itemName)
	{
		int itemId = getItemId(itemName);
		return itemId == -1 ? -1 : Rs2GrandExchange.getOfferPrice(itemId);
	}

	/** As upstream: the GE offer price for {@code itemId}. */
	public int getGEPrice(int itemId)
	{
		return Rs2GrandExchange.getOfferPrice(itemId);
	}

	private static List<ItemPrice> search(String query)
	{
		return MobileActions.read(() ->
		{
			ItemManager manager = Microbot.getItemManager();
			return manager == null ? Collections.<ItemPrice>emptyList() : manager.search(query);
		}, Collections.emptyList());
	}
}
