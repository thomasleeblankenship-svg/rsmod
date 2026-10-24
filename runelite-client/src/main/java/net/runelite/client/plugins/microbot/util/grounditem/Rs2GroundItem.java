package net.runelite.client.plugins.microbot.util.grounditem;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/** Named ground-item pickup through the live scene, confirmed by an inventory delta. */
public final class Rs2GroundItem
{
	private Rs2GroundItem() { }
	public static boolean lootItemsBasedOnNames(LootingParameters parameters)
	{
		if (parameters == null || !MobileActions.active() || Microbot.getRs2TileItemCache() == null
			|| 28 - Rs2Inventory.count() < parameters.getMinInvSlots()) { return false; }
		List<Rs2TileItemModel> candidates = Microbot.getRs2TileItemCache().query().within(parameters.getRange())
			.where(item -> accepts(item, parameters)).toList();
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
			.anyMatch(item -> (long) prices.getItemPrice(item.getId()) * item.getQuantity() >= value), false);
	}
	static boolean accepts(Rs2TileItemModel item, LootingParameters parameters)
	{
		if (item.getQuantity() < parameters.getMinQuantity() || !item.isLootAble()
			|| (parameters.isAntiLureProtection() && !item.isOwned()) || item.getName() == null) { return false; }
		String name = item.getName().toLowerCase(Locale.ROOT);
		return Arrays.stream(parameters.getNames()).filter(java.util.Objects::nonNull)
			.anyMatch(part -> name.contains(part.toLowerCase(Locale.ROOT)));
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
