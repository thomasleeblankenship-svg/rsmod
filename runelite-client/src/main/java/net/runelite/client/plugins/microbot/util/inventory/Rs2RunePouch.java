package net.runelite.client.plugins.microbot.util.inventory;

import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.api.Varbits;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;

/** Reads the carried pouch's real per-slot varbits; never uses a stale bank pouch. */
public final class Rs2RunePouch
{
	private static final int[] TYPES = {Varbits.RUNE_POUCH_RUNE1, Varbits.RUNE_POUCH_RUNE2,
		Varbits.RUNE_POUCH_RUNE3, Varbits.RUNE_POUCH_RUNE4, Varbits.RUNE_POUCH_RUNE5, Varbits.RUNE_POUCH_RUNE6};
	private static final int[] AMOUNTS = {Varbits.RUNE_POUCH_AMOUNT1, Varbits.RUNE_POUCH_AMOUNT2,
		Varbits.RUNE_POUCH_AMOUNT3, Varbits.RUNE_POUCH_AMOUNT4, Varbits.RUNE_POUCH_AMOUNT5, Varbits.RUNE_POUCH_AMOUNT6};
	// Values are the server's rune-pouch enum, indexed from one (see upstream Runes).
	private static final int[] RUNES = {0, ItemID.AIRRUNE, ItemID.WATERRUNE, ItemID.EARTHRUNE, ItemID.FIRERUNE,
		ItemID.MINDRUNE, ItemID.CHAOSRUNE, ItemID.DEATHRUNE, ItemID.BLOODRUNE, ItemID.COSMICRUNE,
		ItemID.NATURERUNE, ItemID.LAWRUNE, ItemID.BODYRUNE, ItemID.SOULRUNE, ItemID.ASTRALRUNE,
		ItemID.MISTRUNE, ItemID.MUDRUNE, ItemID.DUSTRUNE, ItemID.LAVARUNE, ItemID.STEAMRUNE,
		ItemID.SMOKERUNE, ItemID.WRATHRUNE, ItemID.SUNFIRERUNE};
	/** Every rune pouch item: standard, upgraded, large, divine and large divine. */
	public static final int[] POUCH_IDS = {ItemID.BH_RUNE_POUCH, ItemID.BR_RUNE_REPLACEMENT,
		ItemID.BH_RUNE_POUCH_TROUVER, ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER};
	private Rs2RunePouch() { }
	public static int getQuantity(int itemId)
	{
		return MobileActions.read(() ->
		{
			ItemContainer inventory = Microbot.getClient().getItemContainer(InventoryID.INVENTORY);
			if (inventory == null || inventory.getItems() == null) { return 0; }
			int slots = 0;
			for (Item item : inventory.getItems())
			{
				if (item == null || item.getId() < 0) { continue; }
				if (item.getId() == ItemID.BH_RUNE_POUCH || item.getId() == ItemID.BH_RUNE_POUCH_TROUVER)
				{
					slots = Math.max(slots, 3);
				}
				if (item.getId() == ItemID.DIVINE_RUNE_POUCH || item.getId() == ItemID.DIVINE_RUNE_POUCH_TROUVER)
				{
					slots = Math.max(slots, 4);
				}
			}
			if (slots == 0) { return 0; }
			int result = 0;
			for (int i = 0; i < slots; i++)
			{
				int type = Microbot.getClient().getVarbitValue(TYPES[i]);
				if (type > 0 && type < RUNES.length && RUNES[type] == itemId)
				{
					result += Math.max(0, Microbot.getClient().getVarbitValue(AMOUNTS[i]));
				}
			}
			return result;
		}, 0);
	}

	/** As upstream: the pouch holds at least one {@code itemId} rune. */
	public static boolean contains(int itemId)
	{
		return contains(itemId, 1);
	}

	public static boolean contains(int itemId, boolean allowCombinationRunes)
	{
		return contains(itemId, 1, allowCombinationRunes);
	}

	public static boolean contains(int itemId, int amt)
	{
		return contains(itemId, amt, false);
	}

	public static boolean contains(int itemId, int amt, boolean allowCombinationRunes)
	{
		return contains(net.runelite.client.plugins.microbot.util.magic.Runes.byItemId(itemId), amt, allowCombinationRunes);
	}

	/** As upstream: the pouch holds at least one {@code rune}. */
	public static boolean contains(net.runelite.client.plugins.microbot.util.magic.Runes rune)
	{
		return contains(rune, 1);
	}

	public static boolean contains(net.runelite.client.plugins.microbot.util.magic.Runes rune, boolean allowCombinationRunes)
	{
		return contains(rune, 1, allowCombinationRunes);
	}

	public static boolean contains(net.runelite.client.plugins.microbot.util.magic.Runes rune, int amt)
	{
		return contains(rune, amt, false);
	}

	public static boolean contains(net.runelite.client.plugins.microbot.util.magic.Runes rune, int amt, boolean allowCombinationRunes)
	{
		return rune != null && contains(java.util.Map.of(rune, amt), allowCombinationRunes);
	}

	public static boolean contains(java.util.Map<net.runelite.client.plugins.microbot.util.magic.Runes, Integer> requiredRunes)
	{
		return contains(requiredRunes, false);
	}

	/**
	 * As upstream: the pouch holds every required rune and amount. With {@code allowCombinationRunes}
	 * a combination rune (dust, mist, ...) may stand in for a base rune. Adaptation: in that mode the
	 * exact rune counts first; upstream's combination check only ever counts combination runes.
	 */
	public static boolean contains(java.util.Map<net.runelite.client.plugins.microbot.util.magic.Runes, Integer> requiredRunes, boolean allowCombinationRunes)
	{
		if (requiredRunes == null)
		{
			return false;
		}
		java.util.Map<net.runelite.client.plugins.microbot.util.magic.Runes, Integer> available = new java.util.HashMap<>(getRunes());
		for (java.util.Map.Entry<net.runelite.client.plugins.microbot.util.magic.Runes, Integer> required : requiredRunes.entrySet())
		{
			for (int remaining = required.getValue(); remaining > 0; remaining--)
			{
				if (!take(available, required.getKey(), allowCombinationRunes))
				{
					return false;
				}
			}
		}
		return true;
	}

	private static boolean take(java.util.Map<net.runelite.client.plugins.microbot.util.magic.Runes, Integer> available,
		net.runelite.client.plugins.microbot.util.magic.Runes required, boolean allowCombinationRunes)
	{
		if (available.getOrDefault(required, 0) > 0)
		{
			available.merge(required, -1, Integer::sum);
			return true;
		}
		if (!allowCombinationRunes)
		{
			return false;
		}
		for (java.util.Map.Entry<net.runelite.client.plugins.microbot.util.magic.Runes, Integer> entry : available.entrySet())
		{
			if (entry.getValue() > 0 && entry.getKey() != required && entry.getKey().providesRune(required))
			{
				entry.setValue(entry.getValue() - 1);
				return true;
			}
		}
		return false;
	}

	/**
	 * Runes held in the carried rune pouch, keyed by {@link net.runelite.client.plugins.microbot.util.magic.Runes}
	 * (empty without a pouch). Same pouch detection and slot varbits as {@link #getQuantity(int)}.
	 */
	public static java.util.Map<net.runelite.client.plugins.microbot.util.magic.Runes, Integer> getRunes()
	{
		java.util.Map<net.runelite.client.plugins.microbot.util.magic.Runes, Integer> runes = new java.util.HashMap<>();
		for (int i = 1; i < RUNES.length; i++)
		{
			int quantity = getQuantity(RUNES[i]);
			net.runelite.client.plugins.microbot.util.magic.Runes rune =
				net.runelite.client.plugins.microbot.util.magic.Runes.byItemId(RUNES[i]);
			if (quantity > 0 && rune != null)
			{
				runes.put(rune, quantity);
			}
		}
		return runes;
	}
}
