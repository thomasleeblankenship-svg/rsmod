package net.runelite.client.plugins.microbot.dabrutus;

import java.util.function.IntUnaryOperator;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;

/**
 * F2P magic for {@code combatMode=MAGIC} (user 2026-10-09): a Staff of air (unlimited air runes) and earth, fire and water
 * runes, with mind runes for the strike spells or chaos runes for the bolts depending on the Magic level. The best spell
 * the level and the runes allow is autocast; when one element runs out it falls back to a lower spell. Blasts (death
 * runes) are only used when the "Use blast spells" setting is on, from Magic 41. The staff is one-handed, so the Mooleta
 * or a kiteshield still fits.
 */
final class MagicGear
{
	private MagicGear()
	{
	}

	static final int STAFF_OF_AIR = 1381;
	static final int WATER_RUNE = 555;
	static final int EARTH_RUNE = 557;
	static final int FIRE_RUNE = 554;
	static final int MIND_RUNE = 558;
	static final int CHAOS_RUNE = 562;
	static final int DEATH_RUNE = 560;
	/** Every rune the mode carries (air runes stay for the cowbell anyway). */
	static final int[] RUNES = {WATER_RUNE, EARTH_RUNE, FIRE_RUNE, MIND_RUNE, CHAOS_RUNE, DEATH_RUNE};
	/** Element runes a GE restock buys together (user 2026-10-09). */
	static final int[] ELEMENTS = {WATER_RUNE, EARTH_RUNE, FIRE_RUNE};
	/** Each element's GE batch, by the coins (see {@link GeBuyer#byGold}; user 2026-10-09: 1500-6500). */
	static final int ELEMENTS_MIN = 1500, ELEMENTS_MAX = 6500;

	/** Element runes to buy: a whole batch for {@code coins} when bank and bag hold less than one, else none. */
	static int elementBuy(long have, long coins)
	{
		return elementBatch(have, GeBuyer.byGold(coins, ELEMENTS_MIN, ELEMENTS_MAX));
	}

	/** Element runes to buy: the whole {@code batch} when bank and bag hold less than one, else none. */
	static int elementBatch(long have, int batch)
	{
		return have < batch ? batch : 0;
	}

	/**
	 * F2P mage gear (user 2026-10-09), no level requirements, best first per slot; the first one is what the GE buys.
	 * Gloves, boots and the shield slot keep what the melee/extras logic wears (the staff is one-handed).
	 */
	static final EquipmentInventorySlot[] SLOTS = {EquipmentInventorySlot.HEAD, EquipmentInventorySlot.BODY,
		EquipmentInventorySlot.LEGS, EquipmentInventorySlot.AMULET};
	static final RangedGear.Piece[][] ARMOUR = {
		{piece("Blue wizard hat", 579), piece("Wizard hat", 1017)},
		{piece("Blue wizard robe", 577), piece("Zamorak monk top", 1035)},
		{piece("Zamorak monk bottom", 1033)},
		{piece("Amulet of magic", 1727)},
	};

	private static RangedGear.Piece piece(String name, int id)
	{
		return new RangedGear.Piece(name, id, 1, 1, -1, false);
	}

	/** One autocast spell: level, and runes per cast besides air (the staff gives those). */
	static final class Cast
	{
		final Rs2CombatSpells spell;
		final String name;
		final int level;
		/** Element rune id, or -1 (wind spells need only air). */
		final int element;
		final int elementPerCast;
		/** Mind rune (strikes), chaos rune (bolts) or death rune (blasts), one per cast. */
		final int catalytic;

		Cast(Rs2CombatSpells spell, String name, int level, int element, int elementPerCast, int catalytic)
		{
			this.spell = spell;
			this.name = name;
			this.level = level;
			this.element = element;
			this.elementPerCast = elementPerCast;
			this.catalytic = catalytic;
		}

		/** Casts these rune counts allow. */
		int casts(IntUnaryOperator count)
		{
			int n = count.applyAsInt(catalytic);
			if (element > 0)
			{
				n = Math.min(n, count.applyAsInt(element) / elementPerCast);
			}
			return Math.max(0, n);
		}
	}

	/** Best first (wiki: air runes per cast are covered by the staff). */
	static final Cast[] SPELLS = {
		new Cast(Rs2CombatSpells.FIRE_BLAST, "Fire Blast", 59, FIRE_RUNE, 5, DEATH_RUNE),
		new Cast(Rs2CombatSpells.EARTH_BLAST, "Earth Blast", 53, EARTH_RUNE, 4, DEATH_RUNE),
		new Cast(Rs2CombatSpells.WATER_BLAST, "Water Blast", 47, WATER_RUNE, 3, DEATH_RUNE),
		new Cast(Rs2CombatSpells.WIND_BLAST, "Wind Blast", 41, -1, 0, DEATH_RUNE),
		new Cast(Rs2CombatSpells.FIRE_BOLT, "Fire Bolt", 35, FIRE_RUNE, 4, CHAOS_RUNE),
		new Cast(Rs2CombatSpells.EARTH_BOLT, "Earth Bolt", 29, EARTH_RUNE, 3, CHAOS_RUNE),
		new Cast(Rs2CombatSpells.WATER_BOLT, "Water Bolt", 23, WATER_RUNE, 2, CHAOS_RUNE),
		new Cast(Rs2CombatSpells.WIND_BOLT, "Wind Bolt", 17, -1, 0, CHAOS_RUNE),
		new Cast(Rs2CombatSpells.FIRE_STRIKE, "Fire Strike", 13, FIRE_RUNE, 3, MIND_RUNE),
		new Cast(Rs2CombatSpells.EARTH_STRIKE, "Earth Strike", 9, EARTH_RUNE, 2, MIND_RUNE),
		new Cast(Rs2CombatSpells.WATER_STRIKE, "Water Strike", 5, WATER_RUNE, 1, MIND_RUNE),
		new Cast(Rs2CombatSpells.WIND_STRIKE, "Wind Strike", 1, -1, 0, MIND_RUNE),
	};

	/** This account's upgrade timing (0.6.0); {@link GearLag#NONE} until the setup window's Start. */
	static volatile GearLag lag = GearLag.NONE;

	/**
	 * The catalytic rune of the spell {@link #best} picks: mind runes below Magic 17, chaos runes from 17 (the bolts),
	 * death runes from 41 when blasts are on; later when this account waits for the bolts or blasts.
	 */
	static int catalytic(int magicLevel, boolean blasts)
	{
		return best(magicLevel, blasts).catalytic;
	}

	/** A spell this level may use: known (and this account's wait for it over), and a blast only when blasts are on. */
	static boolean allowed(Cast c, int magicLevel, boolean blasts)
	{
		int i = index(c);
		return lag.usedFrom(GearLag.Kind.SPELL, i, c.level, i == SPELLS.length - 1) <= magicLevel
			&& (blasts || c.catalytic != DEATH_RUNE);
	}

	private static int index(Cast c)
	{
		for (int i = 0; i < SPELLS.length; i++)
		{
			if (SPELLS[i] == c)
			{
				return i;
			}
		}
		return 0;
	}

	/** The best spell for the level, runes or not (what to stock and buy for). */
	static Cast best(int magicLevel, boolean blasts)
	{
		for (Cast c : SPELLS)
		{
			if (allowed(c, magicLevel, blasts))
			{
				return c;
			}
		}
		return SPELLS[SPELLS.length - 1];
	}

	/** The best spell the level and these runes allow, or null when none can be cast. */
	static Cast castable(int magicLevel, boolean blasts, IntUnaryOperator count)
	{
		for (Cast c : SPELLS)
		{
			if (allowed(c, magicLevel, blasts) && c.casts(count) > 0)
			{
				return c;
			}
		}
		return null;
	}

	static String runeName(int id)
	{
		switch (id)
		{
			case WATER_RUNE: return "Water rune";
			case EARTH_RUNE: return "Earth rune";
			case FIRE_RUNE: return "Fire rune";
			case MIND_RUNE: return "Mind rune";
			case CHAOS_RUNE: return "Chaos rune";
			case DEATH_RUNE: return "Death rune";
			default: return "rune " + id;
		}
	}
}
