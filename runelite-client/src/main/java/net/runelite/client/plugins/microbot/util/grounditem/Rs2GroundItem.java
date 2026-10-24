package net.runelite.client.plugins.microbot.util.grounditem;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Ground-item pickup through the live scene (by name, by untradeability or by value), each
 * confirmed by an inventory delta. One stack per call, as scripts call these in their loop.
 */
public final class Rs2GroundItem
{
	private Rs2GroundItem() { }

	private static final net.runelite.client.plugins.microbot.util.models.RS2Item[] NONE = new net.runelite.client.plugins.microbot.util.models.RS2Item[0];

	/**
	 * As upstream: the ground items in the loaded scene keyed by tile and item id (upstream reads the
	 * ground-items plugin's collected table; here the scene is read on the client thread). Each
	 * {@link net.runelite.client.plugins.grounditems.GroundItem} carries id, name, quantity, location,
	 * layer height, prices, tradeability, stackability and ownership.
	 */
	public static com.google.common.collect.Table<net.runelite.api.coords.WorldPoint, Integer, net.runelite.client.plugins.grounditems.GroundItem> getGroundItems()
	{
		return MobileActions.read(() ->
		{
			com.google.common.collect.Table<net.runelite.api.coords.WorldPoint, Integer, net.runelite.client.plugins.grounditems.GroundItem> table =
				com.google.common.collect.HashBasedTable.create();
			net.runelite.api.WorldView view = Microbot.getClient().getTopLevelWorldView();
			net.runelite.api.Tile[][][] tiles = view == null || view.getScene() == null ? null : view.getScene().getTiles();
			net.runelite.client.game.ItemManager prices = Microbot.getItemManager();
			if (tiles == null || view.getPlane() < 0 || view.getPlane() >= tiles.length)
			{
				return table;
			}
			for (net.runelite.api.Tile[] column : tiles[view.getPlane()])
			{
				for (net.runelite.api.Tile tile : column)
				{
					List<net.runelite.api.TileItem> items = tile == null ? null : tile.getGroundItems();
					if (items == null)
					{
						continue;
					}
					for (net.runelite.api.TileItem item : items)
					{
						net.runelite.api.ItemComposition definition = item == null ? null : Microbot.getClient().getItemDefinition(item.getId());
						if (definition == null)
						{
							continue;
						}
						net.runelite.client.plugins.grounditems.GroundItem existing = table.get(tile.getWorldLocation(), item.getId());
						if (existing != null)
						{
							existing.setQuantity(existing.getQuantity() + item.getQuantity());
							continue;
						}
						table.put(tile.getWorldLocation(), item.getId(), net.runelite.client.plugins.grounditems.GroundItem.builder()
							.id(item.getId()).itemId(definition.getId()).name(definition.getName()).quantity(item.getQuantity())
							.location(tile.getWorldLocation()).itemLayer(tile.getItemLayer())
							.height(tile.getItemLayer() == null ? 0 : tile.getItemLayer().getHeight())
							.haPrice(definition.getHaPrice()).gePrice(prices == null ? 0 : prices.getItemPrice(item.getId()))
							.tradeable(definition.isTradeable()).stackable(definition.isStackable()).ownership(item.getOwnership())
							.isPrivate(item.isPrivate()).build());
					}
				}
			}
			return table;
		}, com.google.common.collect.HashBasedTable.create());
	}

	/** As upstream: every ground item within {@code range} tiles of the player, nearest first. */
	@Deprecated
	public static net.runelite.client.plugins.microbot.util.models.RS2Item[] getAll(int range)
	{
		return getAllFromWorldPoint(range, Rs2Player.getWorldLocation());
	}

	/**
	 * As upstream: every ground item in the square of {@code range} tiles around {@code worldPoint},
	 * nearest to the player first; one client-thread read.
	 */
	@Deprecated
	public static net.runelite.client.plugins.microbot.util.models.RS2Item[] getAllFromWorldPoint(int range, net.runelite.api.coords.WorldPoint worldPoint)
	{
		if (worldPoint == null)
		{
			return NONE;
		}
		return MobileActions.read(() ->
		{
			net.runelite.api.Player player = Microbot.getClient().getLocalPlayer();
			net.runelite.api.coords.LocalPoint from = player == null ? null : player.getLocalLocation();
			if (from == null)
			{
				return NONE;
			}
			List<net.runelite.client.plugins.microbot.util.models.RS2Item> found = new java.util.ArrayList<>();
			for (int x = worldPoint.getX() - range; x <= worldPoint.getX() + range; x++)
			{
				for (int y = worldPoint.getY() - range; y <= worldPoint.getY() + range; y++)
				{
					found.addAll(Arrays.asList(getAllAt(x, y)));
				}
			}
			found.sort(java.util.Comparator.comparingInt(item -> item.getTile().getLocalLocation().distanceTo(from)));
			return found.toArray(NONE);
		}, NONE);
	}

	/** As upstream: the ground items on the tile at world ({@code x}, {@code y}) on the current floor. */
	@Deprecated
	public static net.runelite.client.plugins.microbot.util.models.RS2Item[] getAllAt(int x, int y)
	{
		return MobileActions.read(() ->
		{
			net.runelite.api.Tile tile = net.runelite.client.plugins.microbot.util.tile.Rs2Tile.getTile(x, y);
			List<net.runelite.api.TileItem> items = tile == null ? null : tile.getGroundItems();
			if (items == null)
			{
				return NONE;
			}
			return items.stream().filter(java.util.Objects::nonNull)
				.map(item -> new net.runelite.client.plugins.microbot.util.models.RS2Item(Microbot.getClient().getItemDefinition(item.getId()), tile, item))
				.toArray(net.runelite.client.plugins.microbot.util.models.RS2Item[]::new);
		}, NONE);
	}
	public static boolean lootItemsBasedOnNames(LootingParameters parameters)
	{
		return parameters != null && lootMatching(parameters, item -> matchesName(item, parameters));
	}

	/** As upstream: untradeable stacks other than coins, within the parameters' range and ownership rule. */
	public static boolean lootUntradables(LootingParameters parameters)
	{
		return parameters != null && lootMatching(parameters, item -> item.getId() != net.runelite.api.ItemID.COINS_995
			&& !isTradeable(item.getId()));
	}

	/**
	 * As upstream: the first stack within {@code range} tiles whose GE price times quantity is at
	 * least {@code value}. Without an item manager nothing is worth looting.
	 */
	public static boolean lootItemBasedOnValue(int value, int range)
	{
		net.runelite.client.game.ItemManager prices = Microbot.getItemManager();
		return prices != null && lootMatching(new LootingParameters(range, 1, 1, 0, false, false),
			item -> prices.getItemPrice(item.getId()) * item.getQuantity() >= value);
	}

	/**
	 * As upstream: the first stack, within the parameters' range and rules, whose total GE value is
	 * above {@code minValue} and whose value per item is below {@code maxValue}, skipping ignored
	 * names. Without an item manager nothing is worth looting.
	 */
	public static boolean lootItemBasedOnValue(LootingParameters params)
	{
		net.runelite.client.game.ItemManager prices = Microbot.getItemManager();
		if (params == null || prices == null)
		{
			return false;
		}
		java.util.Set<String> ignored = new java.util.HashSet<>();
		for (String name : params.getIgnoredNames())
		{
			if (name != null)
			{
				ignored.add(name.trim().toLowerCase(Locale.ROOT));
			}
		}
		return lootMatching(params, item ->
		{
			if (item.getName() != null && ignored.contains(item.getName().trim().toLowerCase(Locale.ROOT)))
			{
				return false;
			}
			int quantity = Math.max(1, item.getQuantity());
			long total = prices.getItemPrice(item.getId()) * quantity;
			return total > params.getMinValue() && total / quantity < params.getMaxValue();
		});
	}

	/** As upstream: coin stacks, within the parameters' range and rules. */
	public static boolean lootCoins(LootingParameters params)
	{
		return params != null && lootMatching(params, item -> item.getId() == net.runelite.api.ItemID.COINS_995);
	}

	/** As upstream: picks up the first stack of {@code itemId} within 10 tiles. */
	public static boolean pickup(int itemId)
	{
		return lootMatching(new LootingParameters(10, 1, 1, 0, false, false), item -> item.getId() == itemId);
	}

	/** As upstream: {@link #lootItemBasedOnValue(int, int)} within 14 tiles. */
	public static boolean lootAtGePrice(int minGePrice)
	{
		return lootItemBasedOnValue(minGePrice, 14);
	}

	/**
	 * Picks up the first stack that passes the parameters' quantity, lootability and ownership rules
	 * and {@code filter}, which runs on the client thread (it may read item definitions or prices).
	 */
	private static boolean lootMatching(LootingParameters parameters, java.util.function.Predicate<Rs2TileItemModel> filter)
	{
		if (!MobileActions.active() || Microbot.getRs2TileItemCache() == null
			|| 28 - Rs2Inventory.count() < parameters.getMinInvSlots()) { return false; }
		List<Rs2TileItemModel> nearby = Microbot.getRs2TileItemCache().query().within(parameters.getRange())
			.where(item -> lootable(item, parameters)).toList();
		List<Rs2TileItemModel> candidates = MobileActions.read(() -> nearby.stream().filter(filter)
			.collect(java.util.stream.Collectors.toList()), java.util.Collections.emptyList());
		if (candidates.size() < parameters.getMinItems()) { return false; }
		for (Rs2TileItemModel item : candidates)
		{
			int before = inventoryQuantity(item.getId());
			if (MobileActions.groundItem(item.getId(), item.getWorldLocation(), "Take"))
			{
				return Rs2Player.waitFor(() -> inventoryQuantity(item.getId()) > before, 5000);
			}
		}
		return false;
	}
	/** As upstream: picks up the first stack named {@code lootItem} (ignoring case) within {@code range} tiles. */
	public static boolean loot(String lootItem, int range)
	{
		return loot(lootItem, 1, range);
	}

	/** As upstream: {@link #loot(String, int)}. */
	public static boolean pickup(String lootItem, int range)
	{
		return loot(lootItem, 1, range);
	}

	/** As upstream: {@link #loot(String, int)}. */
	public static boolean take(String lootItem, int range)
	{
		return loot(lootItem, 1, range);
	}

	/**
	 * As upstream: picks up the first stack named {@code lootItem} of at least {@code minQuantity}
	 * within {@code range} tiles; nothing when the inventory is full and holds none of it already.
	 * Confirmed by the inventory, as every pickup here.
	 */
	public static boolean loot(String lootItem, int minQuantity, int range)
	{
		if (lootItem == null || (Rs2Inventory.isFull() && !Rs2Inventory.hasItem(lootItem)))
		{
			return false;
		}
		return lootMatching(new LootingParameters(range, 1, minQuantity, 0, false, false),
			item -> lootItem.equalsIgnoreCase(item.getName()));
	}

	/**
	 * As upstream: {@code action} on the nearest stack of {@code itemId} within {@code range} tiles.
	 * With a spell or item selected (Telekinetic Grab: "Cast") it targets the stack; otherwise it is
	 * the stack's own option. One script action; the caller observes the result.
	 */
	public static boolean interact(int itemId, String action, int range)
	{
		if (Microbot.getRs2TileItemCache() == null)
		{
			return false;
		}
		Rs2TileItemModel item = Microbot.getRs2TileItemCache().query().within(range).withId(itemId).nearest();
		if (item == null)
		{
			return false;
		}
		boolean selected = MobileActions.read(() -> Microbot.getClient().isWidgetSelected(), false);
		return selected ? MobileActions.targetGroundItem(itemId, item.getWorldLocation(), action)
			: MobileActions.groundItem(itemId, item.getWorldLocation(), action);
	}

	/** As upstream: an item with {@code id} lies within {@code range} tiles of the player. */
	public static boolean exists(int id, int range)
	{
		return Microbot.getRs2TileItemCache() != null
			&& Microbot.getRs2TileItemCache().query().within(range).withId(id).first() != null;
	}

	/** As upstream: an item named {@code itemName} (ignoring case) lies within {@code range} tiles of the player. */
	public static boolean exists(String itemName, int range)
	{
		return itemName != null && Microbot.getRs2TileItemCache() != null
			&& Microbot.getRs2TileItemCache().query().within(range)
			.where(item -> itemName.equalsIgnoreCase(item.getName())).first() != null;
	}
	/**
	 * As upstream: a ground stack within {@code range} tiles of the player whose GE price times
	 * quantity is at least {@code value}. Prices are read on the client thread; without an item
	 * manager every stack counts as 0.
	 */
	public static boolean isItemBasedOnValueOnGround(int value, int range)
	{
		if (Microbot.getRs2TileItemCache() == null)
		{
			return false;
		}
		List<Rs2TileItemModel> items = Microbot.getRs2TileItemCache().query().within(range).toList();
		net.runelite.client.game.ItemManager prices = Microbot.getItemManager();
		if (items.isEmpty() || prices == null)
		{
			return false;
		}
		return MobileActions.read(() -> items.stream()
			.anyMatch(item -> prices.getItemPrice(item.getId()) * item.getQuantity() >= value), false);
	}
	static boolean accepts(Rs2TileItemModel item, LootingParameters parameters)
	{
		return lootable(item, parameters) && matchesName(item, parameters);
	}

	private static boolean lootable(Rs2TileItemModel item, LootingParameters parameters)
	{
		return item.getQuantity() >= parameters.getMinQuantity() && item.isLootAble()
			&& (!parameters.isAntiLureProtection() || item.isOwned());
	}

	private static boolean matchesName(Rs2TileItemModel item, LootingParameters parameters)
	{
		if (item.getName() == null) { return false; }
		String name = item.getName().toLowerCase(Locale.ROOT);
		return Arrays.stream(parameters.getNames()).filter(java.util.Objects::nonNull)
			.anyMatch(part -> name.contains(part.toLowerCase(Locale.ROOT)));
	}

	/** Client thread only (called inside {@link #lootMatching}'s read). */
	private static boolean isTradeable(int itemId)
	{
		net.runelite.api.ItemComposition definition = Microbot.getClient().getItemDefinition(itemId);
		return definition == null || definition.isTradeable();
	}
	private static int inventoryQuantity(int itemId)
	{
		return MobileActions.read(() ->
		{
			net.runelite.api.ItemContainer inventory = Microbot.getClient().getItemContainer(net.runelite.api.InventoryID.INVENTORY);
			if (inventory == null || inventory.getItems() == null) { return 0; }
			int quantity = 0;
			for (net.runelite.api.Item item : inventory.getItems())
			{
				if (item != null && item.getId() == itemId) { quantity += item.getQuantity(); }
			}
			return quantity;
		}, 0);
	}
}
