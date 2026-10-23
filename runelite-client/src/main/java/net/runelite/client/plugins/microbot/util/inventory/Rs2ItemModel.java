/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/inventory/Rs2ItemModel.java
 * (constructor, getters, initializeFromComposition). BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.inventory;

import net.runelite.api.Item;
import net.runelite.api.ItemComposition;

/**
 * Immutable snapshot of one occupied inventory slot.
 * <p>
 * Built on the client thread from the {@link Item} and its {@link ItemComposition}.
 * Upstream semantics: {@link #getName()} falls back to {@code "Unknown Item"},
 * noted means note template 799, and a noted item's tradeability is its
 * unnoted variant's. Adaptation: read-only subset; quantity is not settable,
 * and equipment actions, sub-ops and lazy composition loading are not ported.
 */
public class Rs2ItemModel
{
	private final int id;
	private final int quantity;
	private final int slot;
	private final String name;
	private final String[] inventoryActions;
	private final boolean isStackable;
	private final boolean isNoted;
	private final boolean isTradeable;

	/**
	 * @param linkedComposition definition of the linked (unnoted) id, used only
	 *                          for a noted item's tradeability; may be {@code null}
	 */
	public Rs2ItemModel(Item item, ItemComposition itemComposition, ItemComposition linkedComposition, int slot)
	{
		this.id = item.getId();
		this.quantity = item.getQuantity();
		this.slot = slot;
		this.name = itemComposition == null ? null : itemComposition.getName();
		this.inventoryActions = itemComposition == null ? new String[0] : itemComposition.getInventoryActions();
		this.isStackable = itemComposition != null && itemComposition.isStackable();
		this.isNoted = itemComposition != null && itemComposition.getNote() == 799;
		if (isNoted)
		{
			this.isTradeable = linkedComposition != null && linkedComposition.isTradeable();
		}
		else
		{
			this.isTradeable = itemComposition != null && itemComposition.isTradeable();
		}
	}

	public int getId()
	{
		return id;
	}

	/** Stack size in this slot (1 for unstacked items). */
	public int getQuantity()
	{
		return quantity;
	}

	public int getSlot()
	{
		return slot;
	}

	public String getName()
	{
		return name != null ? name : "Unknown Item";
	}

	public String[] getInventoryActions()
	{
		return inventoryActions;
	}

	public boolean isStackable()
	{
		return isStackable;
	}

	public boolean isNoted()
	{
		return isNoted;
	}

	public boolean isTradeable()
	{
		return isTradeable;
	}
}
