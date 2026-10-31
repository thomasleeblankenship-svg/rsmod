package net.runelite.client.plugins.microbot.dabrutus;

import net.runelite.api.EquipmentInventorySlot;

/**
 * F2P ranged gear for {@code combatMode=ranged}, best first (wiki). Shortbows only: they fire faster than the longbows
 * with the same arrows. Every bow is two-handed, so ranged mode never wears a shield (or the Mooleta).
 */
final class RangedGear
{
	private RangedGear()
	{
	}

	/** This account's upgrade timing (0.6.0); {@link GearLag#NONE} until the setup window's Start. */
	static volatile GearLag lag = GearLag.NONE;

	/** One item and the levels it needs. {@code arrowTier} (bows only): the best arrow it fires, see {@link #ARROWS}. */
	static final class Piece
	{
		final String name;
		final int id;
		final int ranged;
		final int defence;
		final int arrowTier;
		/** Needs Dragon Slayer I (Green d'hide body). */
		final boolean dragonSlayer;

		Piece(String name, int id, int ranged, int defence, int arrowTier, boolean dragonSlayer)
		{
			this.name = name;
			this.id = id;
			this.ranged = ranged;
			this.defence = defence;
			this.arrowTier = arrowTier;
			this.dragonSlayer = dragonSlayer;
		}

		boolean usable(int rangedLevel, int defenceLevel, boolean dragonSlayerDone)
		{
			return ranged <= rangedLevel && defence <= defenceLevel && (!dragonSlayer || dragonSlayerDone);
		}
	}

	private static Piece bow(String name, int id, int ranged, int arrowTier)
	{
		return new Piece(name, id, ranged, 1, arrowTier, false);
	}

	private static Piece gear(String name, int id, int ranged, int defence)
	{
		return new Piece(name, id, ranged, defence, -1, false);
	}

	/** Shortbows, best first; arrowTier indexes {@link #ARROWS} from the worst (0 bronze) up. */
	static final Piece[] BOWS = {
		bow("Maple shortbow", 853, 30, 4),
		bow("Willow shortbow", 849, 20, 3),
		bow("Oak shortbow", 843, 5, 2),
		bow("Shortbow", 841, 1, 1),
	};

	/** Arrows, worst first (the index is the tier); ranged = level to fire them. */
	static final Piece[] ARROWS = {
		gear("Bronze arrow", 882, 1, 1),
		gear("Iron arrow", 884, 1, 1),
		gear("Steel arrow", 886, 5, 1),
		gear("Mithril arrow", 888, 20, 1),
		gear("Adamant arrow", 890, 30, 1),
	};

	/** Armour slots, and what each one wears, best first; the first usable one is what the GE buys. */
	static final EquipmentInventorySlot[] SLOTS = {EquipmentInventorySlot.HEAD, EquipmentInventorySlot.BODY, EquipmentInventorySlot.LEGS,
		EquipmentInventorySlot.GLOVES, EquipmentInventorySlot.BOOTS, EquipmentInventorySlot.AMULET};
	static final Piece[][] ARMOUR = {
		{gear("Coif", 1169, 20, 1)},
		{new Piece("Green d'hide body", 1135, 40, 40, -1, true), gear("Studded body", 1133, 20, 20),
			gear("Hardleather body", 1131, 1, 10), gear("Leather body", 1129, 1, 1)},
		{gear("Green d'hide chaps", 1099, 40, 1), gear("Studded chaps", 1097, 20, 1), gear("Leather chaps", 1095, 1, 1)},
		{gear("Green d'hide vambraces", 1065, 40, 1), gear("Leather vambraces", 1063, 1, 1)},
		{gear("Leather boots", 1061, 1, 1)},
		{gear("Amulet of power", 1731, 1, 1), gear("Amulet of accuracy", 1478, 1, 1)},
	};

	/** Rank of an item in one slot's list (best first): higher is better, 0 when it is not on the list (or nothing). */
	static int rank(Piece[] list, String name)
	{
		if (name == null)
		{
			return 0;
		}
		for (int k = 0; k < list.length; k++)
		{
			if (list[k].name.equalsIgnoreCase(name))
			{
				return list.length - k;
			}
		}
		return 0;
	}

	/** Rank of a worn item in slot {@code i}: higher is better, 0 when it is not on the list (or nothing). */
	static int rank(int i, String name)
	{
		if (name == null)
		{
			return 0;
		}
		Piece[] list = ARMOUR[i];
		for (int k = 0; k < list.length; k++)
		{
			if (list[k].name.equalsIgnoreCase(name))
			{
				return list.length - k;
			}
		}
		return 0;
	}

	static int rank(int i, Piece p)
	{
		return rank(i, p.name);
	}

	/** Best bow these levels can use, once this account's wait for it ({@link #lag}) is over. */
	static Piece bestBow(int rangedLevel)
	{
		GearLag l = lag;
		for (int i = 0; i < BOWS.length; i++)
		{
			if (l.usedFrom(GearLag.Kind.BOW, i, BOWS[i].ranged, i == BOWS.length - 1) <= rangedLevel)
			{
				return BOWS[i];
			}
		}
		return BOWS[BOWS.length - 1];
	}

	static Piece bow(String name)
	{
		for (Piece b : BOWS)
		{
			if (b.name.equalsIgnoreCase(name))
			{
				return b;
			}
		}
		return null;
	}

	/** Tier of an arrow id (see {@link #ARROWS}), -1 when it is not one of them. */
	static int arrowTier(int id)
	{
		for (int i = 0; i < ARROWS.length; i++)
		{
			if (ARROWS[i].id == id)
			{
				return i;
			}
		}
		return -1;
	}

	static boolean isArrow(int id)
	{
		return arrowTier(id) >= 0;
	}

	/**
	 * Best arrow tier a bow fires at this Ranged level, once this account's wait for it ({@link #lag}) is over. The wait
	 * is separate from the bow's, so a new bow can keep the old arrows for a while.
	 */
	static int bestArrowTier(Piece bow, int rangedLevel)
	{
		int max = bow == null ? 1 : bow.arrowTier;
		GearLag l = lag;
		for (int t = Math.min(max, ARROWS.length - 1); t >= 0; t--)
		{
			if (l.usedFrom(GearLag.Kind.ARROW, t, ARROWS[t].ranged, t == 0) <= rangedLevel)
			{
				return t;
			}
		}
		return 0;
	}
}
