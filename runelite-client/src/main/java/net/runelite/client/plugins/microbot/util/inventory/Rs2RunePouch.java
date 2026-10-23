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
}
