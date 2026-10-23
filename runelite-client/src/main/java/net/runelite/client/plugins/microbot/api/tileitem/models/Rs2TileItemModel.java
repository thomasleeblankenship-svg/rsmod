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
}
