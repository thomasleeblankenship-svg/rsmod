package net.runelite.client.plugins.microbot.util.shop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.runelite.api.NPCComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Shop actions and stock reads with chsami/Microbot's {@code util/shop/Rs2Shop} names (at
 * {@code a2114401bbbc35e9762db9e8aa666d70ffcd24d8}, BSD 2-Clause), written for mobile.
 * <p>
 * Stock is read from the shop interface itself (each slot widget's item and quantity, on the
 * client thread) rather than from a cached item container, so it is always the open shop's.
 * Opening is "Trade" on the nearest NPC that offers it; buying is the slot's "Buy N" option. Each
 * is one exact menu dispatch through {@link MobileActions}; {@code true} means dispatched.
 */
public final class Rs2Shop
{
	/** The shop's stock: one child widget per slot (upstream {@code SHOP_INVENTORY_ITEM_CONTAINER}). */
	private static final int STOCK = InterfaceID.Shopmain.ITEMS;
	/** As upstream: the close button is child 11 of the shop frame. */
	private static final int CLOSE_PARENT = InterfaceID.Shopmain.FRAME;
	private static final int CLOSE_CHILD = 11;
	/** As upstream: a shop holds at most 40 slots. */
	private static final int SHOP_SLOTS = 40;
	/** As upstream: the quantities a shop sells at once, largest first. */
	private static final int[] BUY_QUANTITIES = {50, 10, 5, 1};

	private Rs2Shop()
	{
	}

	/** One shop slot as the interface shows it. */
	static final class Stock
	{
		final int slot;
		final int id;
		final int quantity;
		final String name;

		Stock(int slot, int id, int quantity, String name)
		{
			this.slot = slot;
			this.id = id;
			this.quantity = quantity;
			this.name = name == null ? "" : name;
		}
	}

	// ---- Util action batch AH (Claude 1): change waits ----

	/**
	 * As upstream's container-event hook. Mobile reads the stock from the shop interface each time
	 * (see the class comment), so there is no copy to update: this does nothing.
	 */
	public static void storeShopItemsInMemory(net.runelite.api.events.ItemContainerChanged e, int id)
	{
		// Stock is read live from the shop interface; nothing is cached.
	}

	/** As upstream: waits (Global's default timeout) until the shop's stock differs from now; false on timeout. */
	public static boolean waitForShopChanges()
	{
		String before = stockSignature();
		return Global.sleepUntil(() -> !stockSignature().equals(before));
	}

	private static String stockSignature()
	{
		StringBuilder signature = new StringBuilder();
		for (Stock item : stock())
		{
			signature.append(item.slot).append(':').append(item.id).append('x').append(item.quantity).append(';');
		}
		return signature.toString();
	}

	/** As upstream: the shop interface is showing. */
	public static boolean isOpen()
	{
		return MobileActions.read(() ->
		{
			Widget stock = Microbot.getClient().getWidget(STOCK);
			return stock != null && !stock.isHidden();
		}, false);
	}

	/** As upstream: presses the shop's close button and waits for it to close. */
	public static void closeShop()
	{
		Microbot.status = "Closing Shop";
		if (!isOpen())
		{
			return;
		}
		if (Global.sleepUntil(() -> MobileActions.widget(CLOSE_PARENT, CLOSE_CHILD, ""), 2_000))
		{
			Global.sleepUntil(() -> !isOpen(), 3_000);
		}
	}

	/** As upstream: "Trade" on the nearest NPC with this name (exact, or containing it) that offers it. */
	public static boolean openShop(String npcName, boolean exact)
	{
		Microbot.status = "Opening Shop";
		if (isOpen())
		{
			return true;
		}
		Rs2NpcModel npc = nearestShopNpc(npcName, exact);
		return npc != null && Global.sleepUntil(() -> MobileActions.npcAt(npc.getId(), npc.getIndex(), "Trade"), 2_000)
			&& Global.sleepUntil(Rs2Shop::isOpen, 5_000);
	}

	public static boolean openShop(String npcName)
	{
		return openShop(npcName, false);
	}

	/**
	 * As upstream: the nearest NPC with this name (exact, or containing it, ignoring case) offering
	 * "Trade", as upstream's legacy {@code util/npc/Rs2NpcModel}.
	 */
	public static net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel getNearestShopNpc(String npcName, boolean exact)
	{
		Rs2NpcModel npc = nearestShopNpc(npcName, exact);
		return npc == null || npc.getNpc() == null ? null : new net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel(npc.getNpc());
	}

	private static Rs2NpcModel nearestShopNpc(String npcName, boolean exact)
	{
		if (npcName == null || Microbot.getRs2NpcCache() == null)
		{
			return null;
		}
		String wanted = npcName.toLowerCase(Locale.ROOT);
		List<Rs2NpcModel> named = Microbot.getRs2NpcCache().query().where(npc -> npc.getName() != null
			&& (exact ? npc.getName().equalsIgnoreCase(npcName) : npc.getName().toLowerCase(Locale.ROOT).contains(wanted))).toList();
		List<Rs2NpcModel> traders = MobileActions.read(() ->
		{
			List<Rs2NpcModel> result = new ArrayList<>();
			for (Rs2NpcModel npc : named)
			{
				if (offersTrade(npc.getNpc().getComposition()) || offersTrade(npc.getNpc().getTransformedComposition()))
				{
					result.add(npc);
				}
			}
			return result;
		}, Collections.emptyList());
		WorldPoint here = Rs2Player.getWorldLocation();
		return traders.stream()
			.min(Comparator.comparingInt(npc -> here == null || npc.getWorldLocation() == null ? Integer.MAX_VALUE
				: npc.getWorldLocation().distanceTo(here)))
			.orElse(null);
	}

	public static net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel getNearestShopNpc(String npcName)
	{
		return getNearestShopNpc(npcName, false);
	}

	private static boolean offersTrade(NPCComposition composition)
	{
		return composition != null && composition.getActions() != null
			&& Arrays.stream(composition.getActions()).anyMatch("Trade"::equals);
	}

	/** As upstream: buys {@code quantity} ("1", "5", "10" or "50") of the first in-stock slot with this name. */
	public static boolean buyItem(String itemName, String quantity)
	{
		Microbot.status = "Buying " + quantity + " " + itemName;
		return itemName != null && buy(stock().stream()
			.filter(item -> item.quantity > 0 && item.name.equalsIgnoreCase(itemName)).findFirst().orElse(null), quantity);
	}

	/** As upstream: buys {@code quantity} of the first in-stock slot with this item id. */
	public static boolean buyItem(int itemId, String quantity)
	{
		Microbot.status = "Buying " + quantity + " item with ID " + itemId;
		return buy(stock().stream().filter(item -> item.quantity > 0 && item.id == itemId).findFirst().orElse(null), quantity);
	}

	private static boolean buy(Stock item, String quantity)
	{
		String option = "Buy " + quantity;
		return item != null && Global.sleepUntil(() -> MobileActions.widget(STOCK, item.slot, option), 2_000);
	}

	/**
	 * As upstream: buys {@code desiredQuantity} in as few purchases as possible (50, 10, 5, 1).
	 * Mobile: between purchases it waits for the inventory to change (at most 1.8 s) rather than a
	 * random 0.9 s.
	 */
	public static void buyItemOptimally(String itemName, int desiredQuantity)
	{
		int remaining = desiredQuantity;
		for (int allowed : BUY_QUANTITIES)
		{
			while (remaining >= allowed)
			{
				int before = Rs2Inventory.count(itemName);
				if (!buyItem(itemName, String.valueOf(allowed)))
				{
					return;
				}
				remaining -= allowed;
				Global.sleepUntil(() -> Rs2Inventory.count(itemName) != before, 1_800);
			}
		}
	}

	/** As upstream: every one of the shop's 40 slots holds an item. */
	public static boolean isFull()
	{
		List<Stock> items = stock();
		return items.size() >= SHOP_SLOTS && items.stream().noneMatch(item -> item.id == -1);
	}

	/** As upstream: an item with this name is in stock (quantity above 0). */
	public static boolean hasStock(String itemName)
	{
		return hasMinimumStock(itemName, 1);
	}

	/** As upstream: an item with this id is in stock (quantity above 0). */
	public static boolean hasStock(int itemId)
	{
		return hasMinimumStock(itemId, 1);
	}

	/** As upstream: an item with this name has at least {@code minimumQuantity} in stock. */
	public static boolean hasMinimumStock(String itemName, int minimumQuantity)
	{
		return itemName != null && stock().stream()
			.anyMatch(item -> item.name.equalsIgnoreCase(itemName) && item.quantity >= minimumQuantity);
	}

	/** As upstream: an item with this id has at least {@code minimumQuantity} in stock. */
	public static boolean hasMinimumStock(int itemId, int minimumQuantity)
	{
		return stock().stream().anyMatch(item -> item.id == itemId && item.quantity >= minimumQuantity);
	}

	/** As upstream: the shop slot of the first item named {@code itemName} (any case), or -1. */
	public static int getSlot(String itemName)
	{
		return stock().stream().filter(item -> item.name.equalsIgnoreCase(itemName)).mapToInt(item -> item.slot).findFirst().orElse(-1);
	}

	/** The open shop's slots, read on the client thread; empty when no shop is open. */
	static List<Stock> stock()
	{
		return MobileActions.read(() ->
		{
			Widget container = Microbot.getClient().getWidget(STOCK);
			Widget[] slots = container == null || container.isHidden() ? null : container.getDynamicChildren();
			List<Stock> result = new ArrayList<>();
			for (int i = 0; slots != null && i < slots.length; i++)
			{
				Widget slot = slots[i];
				if (slot == null)
				{
					continue;
				}
				int id = slot.getItemId();
				String name = null;
				if (id >= 0)
				{
					net.runelite.api.ItemComposition definition = Microbot.getClient().getItemDefinition(id);
					name = definition == null ? null : definition.getName();
				}
				result.add(new Stock(i, id, slot.getItemQuantity(), name));
			}
			return result;
		}, Collections.emptyList());
	}
}
