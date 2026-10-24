/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/inventory/Rs2ItemModel.java
 * (constructor, getters, initializeFromComposition). BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.inventory;

import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import java.util.List;

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
	private final ItemComposition itemComposition;
	private final String[][] subops;
	private final int haPrice;
	private final int linkedNoteId;
	private final List<String> equipmentActions;

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
		this.itemComposition = itemComposition;
		this.subops = itemComposition == null ? null : itemComposition.getSubops();
		this.haPrice = itemComposition == null ? 0 : itemComposition.getHaPrice();
		this.linkedNoteId = itemComposition == null ? -1 : itemComposition.getLinkedNoteId();
		this.equipmentActions = equipmentActions(itemComposition);
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

	/** Upstream's worn-item option params (OC_ITEM_OP1 to OC_ITEM_OP8). */
	private static final int[] WEARABLE_ACTION_PARAMS = {
		net.runelite.api.ParamID.OC_ITEM_OP1, net.runelite.api.ParamID.OC_ITEM_OP2, net.runelite.api.ParamID.OC_ITEM_OP3,
		net.runelite.api.ParamID.OC_ITEM_OP4, net.runelite.api.ParamID.OC_ITEM_OP5, net.runelite.api.ParamID.OC_ITEM_OP6,
		net.runelite.api.ParamID.OC_ITEM_OP7, net.runelite.api.ParamID.OC_ITEM_OP8,
	};

	private static List<String> equipmentActions(ItemComposition composition)
	{
		if (composition == null)
		{
			return java.util.Collections.emptyList();
		}
		List<String> actions = new java.util.ArrayList<>();
		for (int param : WEARABLE_ACTION_PARAMS)
		{
			String value = composition.getStringValue(param);
			actions.add(value == null ? "" : value);
		}
		return java.util.Collections.unmodifiableList(actions);
	}

	/** As upstream: the definition this model was built from ({@code null} when unknown). */
	public ItemComposition getItemComposition()
	{
		return itemComposition;
	}

	/** As upstream: the definition's sub-option menus per inventory option. */
	public String[][] getSubops()
	{
		return subops;
	}

	/** As upstream: the high alchemy value. */
	public int getHaPrice()
	{
		return haPrice;
	}

	/** As upstream: the definition's linked note id (-1 without one). */
	public int getLinkedId()
	{
		return linkedNoteId;
	}

	/** As upstream: the noted variant's id (this id when it has none or is already a stackable note). */
	public int getNotedId()
	{
		if (itemComposition == null)
		{
			return id;
		}
		if (!isNoted)
		{
			return linkedNoteId != -1 ? linkedNoteId : id;
		}
		return notedId(itemComposition);
	}

	/** As upstream: the noted variant of {@code itemId} (definition read on the client thread); -1 when unknown. */
	public static int getNotedId(int itemId)
	{
		ItemComposition composition = net.runelite.client.plugins.microbot.mobile.MobileActions.read(
			() -> net.runelite.client.plugins.microbot.Microbot.getClient().getItemDefinition(itemId), null);
		return notedId(composition);
	}

	private static int notedId(ItemComposition composition)
	{
		if (composition == null)
		{
			return -1;
		}
		boolean noted = composition.getNote() == 799;
		int linked = composition.getLinkedNoteId();
		return (noted && composition.isStackable()) || linked == -1 ? composition.getId() : linked;
	}

	/** As upstream: the worn-item options (8 entries, "" when unset). */
	public List<String> getEquipmentActions()
	{
		return equipmentActions;
	}

	/** As upstream: the first inventory option containing {@code partOfAction} (case-sensitive on the option, as upstream). */
	public String getAction(String partOfAction)
	{
		return java.util.Arrays.stream(getInventoryActions()).filter(java.util.Objects::nonNull)
			.filter(action -> action.toLowerCase().contains(partOfAction)).findFirst().orElse(null);
	}

	/** As upstream: the inventory option matching the earliest keyword in {@code actions}, ignoring case. */
	public String getActionFromList(List<String> actions)
	{
		return java.util.Arrays.stream(getInventoryActions())
			.filter(action -> action != null && actions.stream().anyMatch(k -> action.toLowerCase().contains(k.toLowerCase())))
			.min(java.util.Comparator.comparingInt(action -> actions.stream()
				.filter(k -> action.toLowerCase().contains(k.toLowerCase())).mapToInt(actions::indexOf).findFirst().orElse(Integer.MAX_VALUE)))
			.orElse(null);
	}

	/** As upstream: the inventory option and sub-option index whose sub-option contains {@code action}, ignoring case. */
	public java.util.Map.Entry<String, Integer> getIndexOfSubAction(String action)
	{
		if (action == null || subops == null)
		{
			return null;
		}
		String lower = action.toLowerCase();
		for (int i = 0; i < subops.length && i < inventoryActions.length; i++)
		{
			for (int j = 0; subops[i] != null && j < subops[i].length; j++)
			{
				if (subops[i][j] != null && subops[i][j].toLowerCase().contains(lower))
				{
					return java.util.Map.entry(inventoryActions[i], j);
				}
			}
		}
		return null;
	}

	/** As upstream: high alchemy beats the per-item GE price after a nature rune, and the item is tradeable. */
	public boolean isHaProfitable()
	{
		long nature = net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
		{
			net.runelite.client.game.ItemManager items = net.runelite.client.plugins.microbot.Microbot.getItemManager();
			return items == null ? 0L : items.getItemPrice(net.runelite.api.gameval.ItemID.NATURERUNE);
		}, 0L);
		return quantity > 0 && haPrice - nature > getPrice() / quantity && isTradeable;
	}

	/** As upstream: models are equal when their item ids are. */
	@Override
	public boolean equals(Object obj)
	{
		return obj == this || (obj != null && obj.getClass() == getClass() && ((Rs2ItemModel) obj).id == id);
	}

	@Override
	public int hashCode()
	{
		return 31 + id;
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

	/** As upstream: the unnoted variant of {@code itemId} (definition read on the client thread); -1 when unknown. */
	public static int getUnNotedId(int itemId)
	{
		ItemComposition composition = net.runelite.client.plugins.microbot.mobile.MobileActions.read(
			() -> net.runelite.client.plugins.microbot.Microbot.getClient().getItemDefinition(itemId), null);
		return composition == null ? -1 : unNotedId(composition.getId(), composition, composition.getNote() == 799);
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
