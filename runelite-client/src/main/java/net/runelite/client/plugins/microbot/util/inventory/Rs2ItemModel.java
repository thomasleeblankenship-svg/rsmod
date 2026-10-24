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
	private final int unNotedId;

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
		this.unNotedId = unNotedId(item.getId(), itemComposition, isNoted);
	}

	/**
	 * Upstream's {@code getUnNotedId}: a noted item's linked id (itself without one); an unnoted
	 * item keeps its id unless it is stackable with a linked id.
	 */
	private static int unNotedId(int id, ItemComposition composition, boolean noted)
	{
		if (composition == null)
		{
			return id;
		}
		int linked = composition.getLinkedNoteId();
		if (noted)
		{
			return linked != -1 ? linked : id;
		}
		return !composition.isStackable() || linked == -1 ? id : linked;
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

	/** As upstream: GE price times quantity from the item manager, 0 when it cannot be read. */
	public long getPrice()
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
		{
			net.runelite.client.game.ItemManager items = net.runelite.client.plugins.microbot.Microbot.getItemManager();
			return items == null ? 0L : items.getItemPrice(id) * quantity;
		}, 0L);
	}

	/** As upstream: items whose name equals ({@code exact}) or contains one of {@code names}, ignoring case. */
	public static java.util.function.Predicate<Rs2ItemModel> matches(boolean exact, String... names)
	{
		return item -> names != null && java.util.Arrays.stream(names).filter(java.util.Objects::nonNull).anyMatch(name -> exact
			? item.getName().equalsIgnoreCase(name) : item.getName().toLowerCase().contains(name.toLowerCase()));
	}

	/** As upstream: items with one of {@code ids}. */
	public static java.util.function.Predicate<Rs2ItemModel> matches(int... ids)
	{
		return item -> ids != null && java.util.Arrays.stream(ids).anyMatch(id -> id == item.getId());
	}

	/** As upstream: items in one of the equipment {@code slots} (by slot index). */
	public static java.util.function.Predicate<Rs2ItemModel> matches(net.runelite.api.EquipmentInventorySlot... slots)
	{
		return item -> slots != null && java.util.Arrays.stream(slots).filter(java.util.Objects::nonNull)
			.anyMatch(slot -> slot.getSlotIdx() == item.getSlot());
	}

	/** The unnoted variant's id (see the constructor), read when the model was built. */
	public int getUnNotedId()
	{
		return unNotedId;
	}

	/**
	 * Whether this is food: an unnoted item that can be eaten, or a jug of wine. Rock cakes have an
	 * Eat option but only hurt, so they are excluded.
	 */
	public boolean isFood()
	{
		String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
		if (isNoted || lower.contains("rock cake"))
		{
			return false;
		}
		for (String action : getInventoryActions())
		{
			if ("Eat".equalsIgnoreCase(action))
			{
				return true;
			}
		}
		return lower.contains("jug of wine");
	}
}
