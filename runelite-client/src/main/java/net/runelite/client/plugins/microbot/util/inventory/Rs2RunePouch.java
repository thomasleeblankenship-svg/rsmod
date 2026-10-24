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

	/** As upstream: the carried pouch holds no runes (true without a pouch). */
	public static boolean isEmpty()
	{
		return getRunes().isEmpty();
	}

	// ---- Util action batch T (Claude 1): the pouch's bank interface ----

	/** The rune pouch panel in the bank side interface (upstream's validated children of 15). */
	private static final int BANK_SIDE = net.runelite.api.gameval.InterfaceID.BANKSIDE;
	private static final int POUCH_ROOT = 19;
	private static final int POUCH_DEPOSIT_ALL = 20;
	private static final int POUCH_CLOSE = 22;

	/** As upstream: the rune pouch panel is showing (bank side, 15:19). */
	public static boolean isRunePouchOpen()
	{
		return net.runelite.client.plugins.microbot.util.widget.Rs2Widget.isWidgetVisible(BANK_SIDE, POUCH_ROOT);
	}

	/** As upstream: "Configure" on the carried pouch, then waits for its panel. */
	public static boolean openRunePouch()
	{
		if (isRunePouchOpen()) return true;
		return Rs2Inventory.interact(POUCH_IDS, "Configure")
			&& net.runelite.client.plugins.microbot.util.Global.sleepUntil(Rs2RunePouch::isRunePouchOpen, 3_000);
	}

	/** As upstream: closes the pouch panel and waits for it to go. */
	public static boolean closeRunePouch()
	{
		if (!isRunePouchOpen()) return true;
		return net.runelite.client.plugins.microbot.util.widget.Rs2Widget.clickWidget(BANK_SIDE, POUCH_CLOSE)
			&& net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> !isRunePouchOpen(), 3_000);
	}

	/** As upstream: with the bank open, empties the pouch into the bank through its panel. */
	public static boolean depositAll()
	{
		if (getRunes().isEmpty()) return true;
		if (!net.runelite.client.plugins.microbot.util.bank.Rs2Bank.isOpen() || !Rs2Inventory.hasRunePouch()) return false;
		return openRunePouch()
			&& net.runelite.client.plugins.microbot.util.widget.Rs2Widget.clickWidget(BANK_SIDE, POUCH_DEPOSIT_ALL)
			&& net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> getRunes().isEmpty(), 3_000);
	}

	/** As upstream: true when the pouch already holds these runes exactly (no combination runes), else loads them. */
	public static boolean load(java.util.Map<net.runelite.client.plugins.microbot.util.magic.Runes, Integer> requiredRunes)
	{
		return contains(requiredRunes, false) || coreLoad(requiredRunes);
	}

	/** As upstream: like {@link #load}, each rune's fuzzy flag allowing combination runes. */
	public static boolean loadFromInventorySetup(
		java.util.Map<net.runelite.client.plugins.microbot.util.magic.Runes, net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem> inventorySetupRunes)
	{
		boolean present = inventorySetupRunes.entrySet().stream()
			.allMatch(e -> contains(e.getKey(), e.getValue().getQuantity(), e.getValue().isFuzzy()));
		return present || coreLoad(inventorySetupRunes.entrySet().stream()
			.collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, e -> e.getValue().getQuantity())));
	}

	/**
	 * Upstream's core without the saved loadouts: with the bank open and the pouch panel open,
	 * empties the pouch, withdraws each rune into it, and closes the panel. Mobile adaptation:
	 * saved pouch loadouts are not tried first. True when every rune is in the pouch.
	 */
	private static boolean coreLoad(java.util.Map<net.runelite.client.plugins.microbot.util.magic.Runes, Integer> requiredRunes)
	{
		if (!net.runelite.client.plugins.microbot.util.bank.Rs2Bank.isOpen() || !Rs2Inventory.hasRunePouch()
			|| !openRunePouch() || !depositAll())
		{
			return false;
		}
		for (java.util.Map.Entry<net.runelite.client.plugins.microbot.util.magic.Runes, Integer> entry : requiredRunes.entrySet())
		{
			if (!net.runelite.client.plugins.microbot.util.bank.Rs2Bank.withdrawX(entry.getKey().getItemId(), entry.getValue()))
			{
				return false;
			}
			net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> contains(entry.getKey(), entry.getValue()), 3_000);
		}
		boolean loaded = requiredRunes.entrySet().stream().allMatch(e -> contains(e.getKey(), e.getValue()));
		return closeRunePouch() && loaded;
	}
}
