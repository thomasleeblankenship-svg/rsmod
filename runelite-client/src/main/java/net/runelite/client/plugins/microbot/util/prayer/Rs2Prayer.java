package net.runelite.client.plugins.microbot.util.prayer;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Prayer state with Microbot's method names, read from varbits (through the player-state cache)
 * and skill levels. Reads only; toggling is a separate action. Written for RuneMobile.
 */
public final class Rs2Prayer
{
	private static final Rs2PrayerEnum[] PROTECTION = {
		Rs2PrayerEnum.PROTECT_MAGIC, Rs2PrayerEnum.PROTECT_RANGE, Rs2PrayerEnum.PROTECT_MELEE};

	private Rs2Prayer()
	{
	}

	public static boolean isPrayerActive(Rs2PrayerEnum prayer)
	{
		return prayer != null && Microbot.getVarbitValue(prayer.getVarbit()) == 1;
	}

	/** Whether the quick-prayer orb is on. */
	public static boolean isQuickPrayerEnabled()
	{
		return Microbot.getVarbitValue(VarbitID.QUICKPRAYER_ACTIVE) == 1;
	}

	/** Every active standard prayer, in enum order. */
	public static List<Rs2PrayerEnum> getActivePrayers()
	{
		List<Rs2PrayerEnum> active = new ArrayList<>();
		for (Rs2PrayerEnum prayer : Rs2PrayerEnum.values())
		{
			if (isPrayerActive(prayer))
			{
				active.add(prayer);
			}
		}
		return active;
	}

	/** The active protection prayer (magic, missiles or melee), or {@code null}. */
	public static Rs2PrayerEnum getActiveProtectionPrayer()
	{
		for (Rs2PrayerEnum prayer : PROTECTION)
		{
			if (isPrayerActive(prayer))
			{
				return prayer;
			}
		}
		return null;
	}

	/** Current prayer points (the boosted Prayer level). */
	public static int getPrayerPoints()
	{
		return Rs2Player.getBoostedSkillLevel(Skill.PRAYER);
	}

	public static boolean isOutOfPrayer()
	{
		return getPrayerPoints() <= 0;
	}

	public static boolean isRigourUnlocked()
	{
		return Microbot.getVarbitValue(VarbitID.PRAYER_RIGOUR_UNLOCKED) == 1;
	}

	public static boolean isAuguryUnlocked()
	{
		return Microbot.getVarbitValue(VarbitID.PRAYER_AUGURY_UNLOCKED) == 1;
	}

	public static boolean isPreserveUnlocked()
	{
		return Microbot.getVarbitValue(VarbitID.PRAYER_PRESERVE_UNLOCKED) == 1;
	}

	public static boolean isDeadeyeUnlocked()
	{
		return Microbot.getVarbitValue(VarbitID.PRAYER_DEADEYE_UNLOCKED) == 1;
	}

	public static boolean isMysticVigourUnlocked()
	{
		return Microbot.getVarbitValue(VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED) == 1;
	}

	/**
	 * Strongest melee offence prayer the player's Prayer level allows, or {@code null}. Chivalry and
	 * Piety also need the Knight Waves training, which is not checked.
	 */
	public static Rs2PrayerEnum getBestMeleePrayer()
	{
		int level = prayerLevel();
		return best(level, Rs2PrayerEnum.PIETY, Rs2PrayerEnum.CHIVALRY, Rs2PrayerEnum.ULTIMATE_STRENGTH,
			Rs2PrayerEnum.SUPERHUMAN_STRENGTH, Rs2PrayerEnum.BURST_STRENGTH);
	}

	/** Strongest ranged prayer available (Rigour and Deadeye only once unlocked), or {@code null}. */
	public static Rs2PrayerEnum getBestRangePrayer()
	{
		int level = prayerLevel();
		if (level >= Rs2PrayerEnum.RIGOUR.getLevel() && isRigourUnlocked())
		{
			return Rs2PrayerEnum.RIGOUR;
		}
		if (level >= Rs2PrayerEnum.DEAD_EYE.getLevel() && isDeadeyeUnlocked())
		{
			return Rs2PrayerEnum.DEAD_EYE;
		}
		return best(level, Rs2PrayerEnum.EAGLE_EYE, Rs2PrayerEnum.HAWK_EYE, Rs2PrayerEnum.SHARP_EYE);
	}

	/** Strongest magic prayer available (Augury and Mystic Vigour only once unlocked), or {@code null}. */
	public static Rs2PrayerEnum getBestMagePrayer()
	{
		int level = prayerLevel();
		if (level >= Rs2PrayerEnum.AUGURY.getLevel() && isAuguryUnlocked())
		{
			return Rs2PrayerEnum.AUGURY;
		}
		if (level >= Rs2PrayerEnum.MYSTIC_VIGOUR.getLevel() && isMysticVigourUnlocked())
		{
			return Rs2PrayerEnum.MYSTIC_VIGOUR;
		}
		return best(level, Rs2PrayerEnum.MYSTIC_MIGHT, Rs2PrayerEnum.MYSTIC_LORE, Rs2PrayerEnum.MYSTIC_WILL);
	}

	private static int prayerLevel()
	{
		return Rs2Player.getRealSkillLevel(Skill.PRAYER);
	}

	private static Rs2PrayerEnum best(int level, Rs2PrayerEnum... strongestFirst)
	{
		for (Rs2PrayerEnum prayer : strongestFirst)
		{
			if (level >= prayer.getLevel())
			{
				return prayer;
			}
		}
		return null;
	}
}
