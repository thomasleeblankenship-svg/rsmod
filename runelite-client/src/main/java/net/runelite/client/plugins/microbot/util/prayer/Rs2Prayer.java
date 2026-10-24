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

	// ---- Toggles (Claude 1). Mobile: the prayer tab is opened first, because script actions
	// only reach visible widgets; upstream sends the menu action to the hidden button. ----

	/** As upstream: flips {@code prayer} once; does nothing when out of prayer points. */
	public static void toggle(Rs2PrayerEnum prayer)
	{
		if (prayer != null && !isOutOfPrayer()) { clickPrayer(prayer); }
	}

	/** As upstream: true once {@code prayer} is in the requested state (waits up to 10 seconds). */
	public static boolean toggle(Rs2PrayerEnum prayer, boolean on)
	{
		if (prayer == null || isOutOfPrayer()) { return false; }
		if (isPrayerActive(prayer) == on) { return true; }
		return clickPrayer(prayer)
			&& net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> isPrayerActive(prayer) == on, 10_000);
	}

	/** {@code withMouse} has no mobile meaning (every toggle is a script action); same as {@link #toggle(Rs2PrayerEnum, boolean)}. */
	public static boolean toggle(Rs2PrayerEnum prayer, boolean on, boolean withMouse)
	{
		return toggle(prayer, on);
	}

	private static boolean clickPrayer(Rs2PrayerEnum prayer)
	{
		return net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.switchTo(net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.PRAYER)
			&& net.runelite.client.plugins.microbot.util.Global.sleepUntil(() ->
				net.runelite.client.plugins.microbot.mobile.MobileActions.widget(prayer.getIndex(), -1, ""), 2_000);
	}

	/** As upstream: true once quick prayers are in the requested state; the minimap orb, no tab needed. */
	public static boolean toggleQuickPrayer(boolean on)
	{
		if (isOutOfPrayer()) { return false; }
		if (isQuickPrayerEnabled() == on) { return true; }
		return net.runelite.client.plugins.microbot.util.Global.sleepUntil(() ->
				net.runelite.client.plugins.microbot.mobile.MobileActions.widget(net.runelite.api.gameval.InterfaceID.Orbs.PRAYERBUTTON, -1, ""), 2_000)
			&& net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> isQuickPrayerEnabled() == on, 10_000);
	}

	/** As upstream: flips quick prayers, unless out of prayer or none are selected. */
	public static void toggleQuickPrayer()
	{
		if (isOutOfPrayer() || !hasAnyQuickPrayers())
		{
			return;
		}
		toggleQuickPrayer(!isQuickPrayerEnabled());
	}

	public static boolean toggleQuickPrayer(boolean on, boolean withMouse)
	{
		return toggleQuickPrayer(on);
	}

	/** As upstream: turns every active prayer off. */
	public static void disableAllPrayers()
	{
		for (Rs2PrayerEnum prayer : getActivePrayers()) { toggle(prayer, false); }
	}

	public static void disableAllPrayers(boolean withMouse)
	{
		disableAllPrayers();
	}

	/** As upstream: turns off every active prayer not in {@code prayersToKeep}. */
	public static void disableAllPrayersExcept(Rs2PrayerEnum[] prayersToKeep)
	{
		java.util.Set<Rs2PrayerEnum> keep = new java.util.HashSet<>(java.util.Arrays.asList(prayersToKeep));
		for (Rs2PrayerEnum prayer : getActivePrayers())
		{
			if (!keep.contains(prayer))
			{
				toggle(prayer, false);
			}
		}
	}

	/** As upstream; mobile has one way to toggle, so {@code withMouse} changes nothing. */
	public static void disableAllPrayersExcept(Rs2PrayerEnum[] prayersToKeep, boolean withMouse)
	{
		disableAllPrayersExcept(prayersToKeep);
	}

	/** As upstream; {@code withMouse} changes nothing on mobile. */
	public static void enablePrayers(Rs2PrayerEnum[] prayers, boolean withMouse)
	{
		enablePrayers(prayers);
	}

	/** As upstream: turns each listed prayer on. */
	public static void enablePrayers(Rs2PrayerEnum[] prayers)
	{
		for (Rs2PrayerEnum prayer : prayers) { toggle(prayer, true); }
	}

	/** As upstream: turns {@code prayer} on unless it is already the active protection prayer. */
	public static void swapOverHeadPrayer(Rs2PrayerEnum prayer)
	{
		if (getActiveProtectionPrayer() != prayer) { toggle(prayer, true); }
	}

	/** As upstream: {@code prayer} is one of the selected quick prayers. */
	/**
	 * As upstream: opens the quick-prayer setup from the prayer orb, unticks every set quick prayer
	 * not in {@code prayers}, ticks the missing ones, and presses Done. False when the orb is hidden
	 * or the setup did not open.
	 */
	public static boolean setQuickPrayers(Rs2PrayerEnum[] prayers)
	{
		int orb = net.runelite.api.gameval.InterfaceID.Orbs.PRAYERBUTTON;
		int buttons = net.runelite.api.gameval.InterfaceID.Quickprayer.BUTTONS;
		if (prayers == null || !net.runelite.client.plugins.microbot.util.widget.Rs2Widget.isWidgetVisible(orb)
			|| !net.runelite.client.plugins.microbot.util.Global.sleepUntil(
				() -> net.runelite.client.plugins.microbot.mobile.MobileActions.widget(orb, -1, "Setup"), 2_000)
			|| !net.runelite.client.plugins.microbot.util.Global.sleepUntil(
				() -> net.runelite.client.plugins.microbot.util.widget.Rs2Widget.isWidgetVisible(buttons), 3_000))
		{
			return false;
		}
		List<Rs2PrayerEnum> wanted = java.util.Arrays.asList(prayers);
		for (Rs2PrayerEnum prayer : Rs2PrayerEnum.values())
		{
			if (isQuickPrayerSet(prayer) != wanted.contains(prayer))
			{
				net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> net.runelite.client.plugins.microbot.mobile.MobileActions
					.widget(buttons, prayer.getQuickPrayerIndex(), "Toggle"), 2_000);
			}
		}
		return net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> net.runelite.client.plugins.microbot.mobile.MobileActions
			.widget(net.runelite.api.gameval.InterfaceID.Quickprayer.CLOSE, -1, "Done"), 2_000);
	}

	public static boolean isQuickPrayerSet(Rs2PrayerEnum prayer)
	{
		return (Microbot.getVarbitValue(VarbitID.QUICKPRAYER_SELECTED) & (1 << prayer.getQuickPrayerIndex())) != 0;
	}

	/** As upstream: any quick prayer is selected. */
	public static boolean hasAnyQuickPrayers()
	{
		return Microbot.getVarbitValue(VarbitID.QUICKPRAYER_SELECTED) > 0;
	}

	/** As upstream: Sharp Eye, Hawk Eye, Eagle Eye or Rigour is on. */
	public static boolean isRangePrayerActive()
	{
		return anyActive(Rs2PrayerEnum.SHARP_EYE, Rs2PrayerEnum.HAWK_EYE, Rs2PrayerEnum.EAGLE_EYE, Rs2PrayerEnum.RIGOUR);
	}

	/** As upstream: Mystic Will, Mystic Lore, Mystic Might or Augury is on. */
	public static boolean isMagePrayerActive()
	{
		return anyActive(Rs2PrayerEnum.MYSTIC_WILL, Rs2PrayerEnum.MYSTIC_LORE, Rs2PrayerEnum.MYSTIC_MIGHT, Rs2PrayerEnum.AUGURY);
	}

	/** As upstream: Piety, Chivalry, Ultimate Strength or Superhuman Strength is on. */
	public static boolean isMeleePrayerActive()
	{
		return anyActive(Rs2PrayerEnum.PIETY, Rs2PrayerEnum.CHIVALRY, Rs2PrayerEnum.ULTIMATE_STRENGTH, Rs2PrayerEnum.SUPERHUMAN_STRENGTH);
	}

	private static boolean anyActive(Rs2PrayerEnum... prayers)
	{
		for (Rs2PrayerEnum prayer : prayers)
		{
			if (isPrayerActive(prayer))
			{
				return true;
			}
		}
		return false;
	}

	/** As upstream: King's Ransom's Knight Waves training is done and Prayer 70 / Defence 70 are reached. */
	public static boolean isPietyUnlocked()
	{
		return knightWavesDone() && realLevel(Skill.PRAYER) >= 70 && realLevel(Skill.DEFENCE) >= 70;
	}

	/** As upstream: Knight Waves training is done and Prayer 60 / Defence 65 are reached. */
	public static boolean isChivalryUnlocked()
	{
		return knightWavesDone() && realLevel(Skill.PRAYER) >= 60 && realLevel(Skill.DEFENCE) >= 65;
	}

	private static boolean knightWavesDone()
	{
		return Microbot.getVarbitValue(VarbitID.KR_KNIGHTWAVES_STATE) == 8;
	}

	private static int realLevel(Skill skill)
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() -> Microbot.getClient().getRealSkillLevel(skill), 0);
	}
}
