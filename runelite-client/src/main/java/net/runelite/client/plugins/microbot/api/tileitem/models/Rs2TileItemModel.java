/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/tileitem/models/Rs2TileItemModel.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.tileitem.models;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.IEntity;

/**
 * Snapshot of one ground item captured by {@code Rs2TileItemCache}.
 * <p>
 * Id, quantity, ownership, privacy, despawn/visible ticks, item name and tile
 * locations are captured on the client thread. Lootability is upstream's rule:
 * any ownership except {@link TileItem#OWNERSHIP_OTHER}, i.e. items owned by you,
 * your group, or nobody. Ground-item visibility (the cache only lists items the
 * client has loaded on a tile) is decided by the client, not by this model.
 * <p>
 * Adaptation: the model does not implement {@link TileItem}; its world view is
 * the world view the item was scanned from (upstream always reports the top-level
 * view). Pickup/interaction, despawn-tick, GE/alch value and ItemManager helpers
 * are not ported.
 */
public class Rs2TileItemModel implements IEntity
{
	private final Tile tile;
	private final TileItem tileItem;
	private final WorldView worldView;
	private final int id;
	private final int quantity;
	private final int ownership;
	private final boolean isPrivate;
	private final int despawnTime;
	private final int visibleTime;
	private final String name;
	private final boolean stackable;
	private final boolean noted;
	private final WorldPoint worldLocation;
	private final LocalPoint localLocation;

	/** Captures {@code tileItem} on {@code tile}; must be called on the client thread. */
	public Rs2TileItemModel(Client client, WorldView worldView, Tile tile, TileItem tileItem)
	{
		this.tile = tile;
		this.tileItem = tileItem;
		this.worldView = worldView;
		this.id = tileItem.getId();
		this.quantity = tileItem.getQuantity();
		this.ownership = tileItem.getOwnership();
		this.isPrivate = tileItem.isPrivate();
		this.despawnTime = tileItem.getDespawnTime();
		this.visibleTime = tileItem.getVisibleTime();
		ItemComposition composition = client.getItemDefinition(id);
		this.name = composition == null ? null : composition.getName();
		this.stackable = composition != null && composition.isStackable();
		this.noted = composition != null && composition.getNote() == 799;
		this.worldLocation = tile.getWorldLocation();
		this.localLocation = tile.getLocalLocation();
	}

	/** The live tile. Read it only on the client thread. */
	public Tile getTile()
	{
		return tile;
	}

	/** The live tile item. Read it only on the client thread. */
	public TileItem getTileItem()
	{
		return tileItem;
	}

	@Override
	public int getId()
	{
		return id;
	}

	public int getQuantity()
	{
		return quantity;
	}

	public int getOwnership()
	{
		return ownership;
	}

	public boolean isPrivate()
	{
		return isPrivate;
	}

	public int getDespawnTime()
	{
		return despawnTime;
	}

	public int getVisibleTime()
	{
		return visibleTime;
	}

	/** Item definition name, as upstream {@code ItemComposition.getName()}. */
	@Override
	public String getName()
	{
		return name;
	}

	public boolean isStackable()
	{
		return stackable;
	}

	/** Upstream rule: noted when the composition's note template is 799. */
	public boolean isNoted()
	{
		return noted;
	}

	/** Upstream rule: everything except items owned by another player. */
	public boolean isLootAble()
	{
		return !(ownership == TileItem.OWNERSHIP_OTHER);
	}

	public boolean isOwned()
	{
		return ownership == TileItem.OWNERSHIP_SELF;
	}

	/**
	 * As upstream: {@code "Take"}. Mobile dispatches only {@code Take} on ground items; other
	 * actions return {@code false}. A blank action means {@code Take}.
	 */
	@Override
	public boolean click(String action)
	{
		String resolved = action == null ? null : action.trim().isEmpty() ? "Take" : action;
		return resolved != null
			&& net.runelite.client.plugins.microbot.mobile.MobileActions.groundItem(id, worldLocation, resolved);
	}

	@Override
	public boolean click()
	{
		return click("");
	}

	/** As upstream: picks this item up ({@code Take}). */
	public boolean pickup()
	{
		return click("Take");
	}

	@Override
	public WorldPoint getWorldLocation()
	{
		return worldLocation;
	}

	@Override
	public LocalPoint getLocalLocation()
	{
		return localLocation;
	}

	@Override
	public WorldView getWorldView()
	{
		return worldView;
	}

	// ---- api batch 2 (Claude 1, user-authorised; Claude 2's file): upstream reads ----

	private <T> T definition(java.util.function.Function<ItemComposition, T> read, T fallback)
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
		{
			ItemComposition composition = net.runelite.client.plugins.microbot.Microbot.getClient().getItemDefinition(id);
			return composition == null ? fallback : read.apply(composition);
		}, fallback);
	}

	/** As upstream: the item definition's store price times the quantity (upstream's name for it). */
	public int getTotalGeValue()
	{
		return definition(composition -> composition.getPrice() * quantity, 0);
	}

	/** As upstream: the market price (item manager) times the quantity. */
	public long getTotalValue()
	{
		return definition(composition ->
		{
			net.runelite.client.game.ItemManager items = net.runelite.client.plugins.microbot.Microbot.getItemManager();
			return items == null ? 0L : items.getItemPrice(composition.getId()) * quantity;
		}, 0L);
	}

	/** As upstream: the item can be traded. */
	public boolean isTradeable()
	{
		return definition(ItemComposition::isTradeable, false);
	}

	/** As upstream: a members' item. */
	public boolean isMembers()
	{
		return definition(ItemComposition::isMembers, false);
	}

	/** As upstream: the market price is above the high-alchemy value (60% of the store price). */
	public boolean isProfitableToHighAlch()
	{
		return definition(composition ->
		{
			net.runelite.client.game.ItemManager items = net.runelite.client.plugins.microbot.Microbot.getItemManager();
			return items != null && items.getItemPrice(composition.getId()) > composition.getPrice() * 60 / 100;
		}, false);
	}

	/** As upstream: it despawns within {@code ticks} game ticks. */
	public boolean willDespawnWithin(int ticks)
	{
		return despawnTime - currentTick() <= ticks;
	}

	/** As upstream: its despawn tick has passed (false when it has none). */
	public boolean isDespawned()
	{
		return despawnTime != -1 && despawnTime <= currentTick();
	}

	private static int currentTick()
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(
			() -> net.runelite.client.plugins.microbot.Microbot.getClient().getTickCount(), 0);
	}
}
