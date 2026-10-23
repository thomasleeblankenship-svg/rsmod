package net.runelite.client.plugins.microbot.util.antiban;

import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.antiban.enums.Category;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;

/**
 * Compatibility pacing subset. Desktop fatigue/mouse profiles are not reproduced.
 * <p>
 * Upstream's anti-ban entry points (activities, play styles, micro breaks, mouse simulation,
 * setup templates) are accepted so unchanged scripts compile, and do nothing: anti-detection
 * behaviour is not ported. {@link #actionCooldown()} is a no-op too: upstream's is a chance-based
 * anti-ban pause, and a fixed wait after every action only slowed scripts down (phone report,
 * 2026-10-23: slow woodcutting between actions).
 */
public final class Rs2Antiban
{
	public static final SetupTemplates antibanSetupTemplates = new SetupTemplates();
	private static volatile Activity activity;
	private static volatile ActivityIntensity activityIntensity;
	private static volatile Category category;
	private static volatile PlayStyle playStyle;
	private Rs2Antiban() { }

	// Upstream anti-ban entry points, accepted and inert on mobile (see Rs2AntibanSettings).

	/** Recorded only; mobile adjusts no timing from it. */
	public static void setActivity(Activity value)
	{
		activity = value;
		if (value != null)
		{
			activityIntensity = value.getActivityIntensity();
			category = value.getCategory();
		}
	}

	/** Recorded only. */
	public static void setActivityIntensity(ActivityIntensity value)
	{
		activityIntensity = value;
	}

	/** Recorded only. */
	public static void setPlayStyle(PlayStyle value)
	{
		playStyle = value;
	}

	public static Activity getActivity() { return activity; }
	public static ActivityIntensity getActivityIntensity() { return activityIntensity; }
	public static Category getCategory() { return category; }
	public static PlayStyle getPlayStyle() { return playStyle; }

	/** Always {@code false}: mobile takes no anti-ban micro breaks. */
	public static boolean takeMicroBreakByChance() { return false; }

	/** No-op: mobile does not simulate mouse movement. */
	public static void moveMouseOffScreen() { }

	/** No-op: mobile does not simulate mouse movement. */
	public static void moveMouseOffScreen(double chance) { }

	/** No-op: mobile does not simulate mouse movement. */
	public static void moveMouseRandomly() { }

	/** No-op on mobile. */
	public static void activateAntiban() { }

	/** No-op on mobile. */
	public static void deactivateAntiban() { }

	/** Upstream overload; same as {@link #resetAntibanSettings()}. */
	public static void resetAntibanSettings(boolean forceReset)
	{
		resetAntibanSettings();
	}

	/** No-op: mobile draws no anti-ban overlay. */
	public static void renderAntibanOverlayComponents(net.runelite.client.ui.overlay.components.PanelComponent panelComponent) { }
	public static void resetAntibanSettings()
	{
		Rs2AntibanSettings.actionCooldownActive = false;
		Rs2AntibanSettings.dynamicActivity = false;
		Rs2AntibanSettings.dynamicIntensity = false;
		Rs2AntibanSettings.cooldownMeanMillis = 400;
		Rs2AntibanSettings.cooldownDeviationMillis = 200;
	}
	/** No-op on mobile (see the class comment); {@link Rs2AntibanSettings#actionCooldownActive} stays false. */
	public static void actionCooldown()
	{
	}
	public static final class SetupTemplates
	{
		// Other upstream templates: accepted and inert on mobile.
		public void applyCombatSetup() { }
		public void applyThievingSetup() { }
		public void applyCraftingSetup() { }
		public void applyRunecraftingSetup() { }
		public void applyMiningSetup() { }
		public void applyHunterSetup() { }
		public void applyFiremakingSetup() { }
		public void applyFishingSetup() { }
		public void applyCookingSetup() { }
		public void applyFletchingSetup() { }
		public void applyHerbloreSetup() { }
		public void applySmithingSetup() { }
		public void applyAgilitySetup() { }
		public void applyGeneralBasicSetup() { }

		public void applyWoodcuttingSetup()
		{
			Rs2AntibanSettings.cooldownMeanMillis = 600;
			Rs2AntibanSettings.cooldownDeviationMillis = 150;
		}
	}
}
