package net.runelite.client.plugins.mobilemicrobot;

import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

/** Instance-scoped, client-thread-only subset of inventory reads. */
public final class Rs2Inventory
{
	private final MobileScriptContext context;

	Rs2Inventory(MobileScriptContext context)
	{
		this.context = context;
	}

	public int occupiedSlots()
	{
		ItemContainer inv = context.client().getItemContainer(InventoryID.INVENTORY);
		if (inv == null || inv.getItems() == null)
		{
			return 0;
		}
		int occupied = 0;
		for (Item item : inv.getItems())
		{
			if (item != null && item.getId() >= 0)
			{
				occupied++;
			}
		}
		return occupied;
	}

	public int freeSlots()
	{
		return Math.max(0, 28 - occupiedSlots());
	}

	public boolean isFull()
	{
		return occupiedSlots() >= 28;
	}

	public int count(int itemId)
	{
		ItemContainer inv = context.client().getItemContainer(InventoryID.INVENTORY);
		if (inv == null || inv.getItems() == null)
		{
			return 0;
		}
		int total = 0;
		for (Item item : inv.getItems())
		{
			if (item != null && item.getId() == itemId)
			{
				total += item.getQuantity();
			}
		}
		return total;
	}

	public boolean contains(int itemId)
	{
		return count(itemId) > 0;
	}
}
