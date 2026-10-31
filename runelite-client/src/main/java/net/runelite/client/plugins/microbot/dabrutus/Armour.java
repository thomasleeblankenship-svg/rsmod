package net.runelite.client.plugins.microbot.dabrutus;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemID;

/**
 * Armour by Defence level (user 2026-10-03): Iron, then Mithril at 20, Adamant at 30, Rune at 40. Each slot lists the
 * items worn, best first; the first usable one is what the GE buys. Rune platebody needs Dragon Slayer I: with the quest
 * done it is bought and worn (over a worn Rune chainbody, user 2026-10-09), without it the Rune tier takes the chainbody.
 */
final class Armour
{
	private Armour()
	{
	}

	static final class Piece
	{
		final String name;
		final int id;
		/** Needs Dragon Slayer I (Rune platebody). */
		final boolean dragonSlayer;

		Piece(String name, int id)
		{
			this(name, id, false);
		}

		Piece(String name, int id, boolean dragonSlayer)
		{
			this.name = name;
			this.id = id;
			this.dragonSlayer = dragonSlayer;
		}

		/** Wearable with this quest state. */
		boolean usable(boolean dragonSlayerDone)
		{
			return !dragonSlayer || dragonSlayerDone;
		}
	}

	static final class Tier
	{
		final String metal;
		final int defence;
		final Piece[][] slots;

		Tier(String metal, int defence, Piece[]... slots)
		{
			this.metal = metal;
			this.defence = defence;
			this.slots = slots;
		}
	}

	/** Slot order of {@link Tier#slots}. */
	static final EquipmentInventorySlot[] SLOTS = {EquipmentInventorySlot.HEAD, EquipmentInventorySlot.BODY,
		EquipmentInventorySlot.LEGS, EquipmentInventorySlot.SHIELD};

	/** Best first. */
	static final Tier[] TIERS = {
		new Tier("Rune", 40,
			new Piece[]{new Piece("Rune full helm", ItemID.RUNE_FULL_HELM)},
			new Piece[]{new Piece("Rune platebody", ItemID.RUNE_PLATEBODY, true), new Piece("Rune chainbody", ItemID.RUNE_CHAINBODY)},
			new Piece[]{new Piece("Rune platelegs", ItemID.RUNE_PLATELEGS), new Piece("Rune plateskirt", ItemID.RUNE_PLATESKIRT)},
			new Piece[]{new Piece("Rune kiteshield", ItemID.RUNE_KITESHIELD)}),
		new Tier("Adamant", 30,
			new Piece[]{new Piece("Adamant full helm", ItemID.ADAMANT_FULL_HELM)},
			new Piece[]{new Piece("Adamant platebody", ItemID.ADAMANT_PLATEBODY), new Piece("Adamant chainbody", ItemID.ADAMANT_CHAINBODY)},
			new Piece[]{new Piece("Adamant platelegs", ItemID.ADAMANT_PLATELEGS), new Piece("Adamant plateskirt", ItemID.ADAMANT_PLATESKIRT)},
			new Piece[]{new Piece("Adamant kiteshield", ItemID.ADAMANT_KITESHIELD)}),
		new Tier("Mithril", 20,
			new Piece[]{new Piece("Mithril full helm", ItemID.MITHRIL_FULL_HELM)},
			new Piece[]{new Piece("Mithril platebody", ItemID.MITHRIL_PLATEBODY), new Piece("Mithril chainbody", ItemID.MITHRIL_CHAINBODY)},
			new Piece[]{new Piece("Mithril platelegs", ItemID.MITHRIL_PLATELEGS), new Piece("Mithril plateskirt", ItemID.MITHRIL_PLATESKIRT)},
			new Piece[]{new Piece("Mithril kiteshield", ItemID.MITHRIL_KITESHIELD)}),
		new Tier("Iron", 1,
			new Piece[]{new Piece("Iron full helm", ItemID.IRON_FULL_HELM)},
			new Piece[]{new Piece("Iron platebody", ItemID.IRON_PLATEBODY), new Piece("Iron chainbody", ItemID.IRON_CHAINBODY)},
			new Piece[]{new Piece("Iron platelegs", ItemID.IRON_PLATELEGS), new Piece("Iron plateskirt", ItemID.IRON_PLATESKIRT)},
			new Piece[]{new Piece("Iron kiteshield", ItemID.IRON_KITESHIELD)}),
	};

	/**
	 * F2P extras with no Defence requirement (user 2026-10-03): Amulet of strength, Leather gloves, Leather boots. Each
	 * slot lists what counts as worn, best first; the first one is what the GE buys.
	 */
	static final EquipmentInventorySlot[] EXTRA_SLOTS = {EquipmentInventorySlot.AMULET, EquipmentInventorySlot.GLOVES,
		EquipmentInventorySlot.BOOTS};
	static final Piece[][] EXTRAS = {
		{new Piece("Amulet of strength", ItemID.AMULET_OF_STRENGTH)},
		{new Piece("Leather gloves", ItemID.LEATHER_GLOVES)},
		{new Piece("Leather boots", ItemID.LEATHER_BOOTS)},
	};

	/** True when {@code name} is one of the extras for slot {@code i} (or better). */
	static boolean extraWorn(int i, String name)
	{
		if (name == null)
		{
			return false;
		}
		for (Piece p : EXTRAS[i])
		{
			if (p.name.equalsIgnoreCase(name))
			{
				return true;
			}
		}
		return false;
	}

	/** The best tier this Defence level can wear. */
	static Tier target(int defence)
	{
		for (Tier t : TIERS)
		{
			if (defence >= t.defence)
			{
				return t;
			}
		}
		return TIERS[TIERS.length - 1];
	}

	/** Rank of an item name: 4 Rune ... 1 Iron, 0 anything else (or nothing). */
	static int rank(String name)
	{
		if (name == null)
		{
			return 0;
		}
		for (int i = 0; i < TIERS.length; i++)
		{
			for (Piece[] slot : TIERS[i].slots)
			{
				for (Piece p : slot)
				{
					if (p.name.equalsIgnoreCase(name))
					{
						return TIERS.length - i;
					}
				}
			}
		}
		return 0;
	}

	/**
	 * Score of an item name within its slot: the tier ({@link #rank(String)}) first, then its place in the slot's list
	 * (a platebody over the chainbody of the same metal). 0 for anything else.
	 */
	static int score(String name)
	{
		if (name == null)
		{
			return 0;
		}
		for (int i = 0; i < TIERS.length; i++)
		{
			for (Piece[] slot : TIERS[i].slots)
			{
				for (int j = 0; j < slot.length; j++)
				{
					if (slot[j].name.equalsIgnoreCase(name))
					{
						return (TIERS.length - i) * 10 + (slot.length - j);
					}
				}
			}
		}
		return 0;
	}

	/** The best piece of {@code slot} this quest state allows, or null. */
	static Piece best(Piece[] slot, boolean dragonSlayerDone)
	{
		for (Piece p : slot)
		{
			if (p.usable(dragonSlayerDone))
			{
				return p;
			}
		}
		return null;
	}

	static int rank(Tier t)
	{
		for (int i = 0; i < TIERS.length; i++)
		{
			if (TIERS[i] == t)
			{
				return TIERS.length - i;
			}
		}
		return 0;
	}
}
