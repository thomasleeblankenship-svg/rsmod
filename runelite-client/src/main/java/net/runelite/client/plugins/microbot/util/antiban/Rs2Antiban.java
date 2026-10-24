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
	// ---- Read batch 16f (upstream 2.6.38) ----

	/** As upstream: every pickaxe mining animation (normal, Motherlode and crashed-star variants). */
	public static final com.google.common.collect.ImmutableSet<Integer> MINING_ANIMATION_IDS = com.google.common.collect.ImmutableSet.of(
		net.runelite.api.AnimationID.MINING_BRONZE_PICKAXE, net.runelite.api.AnimationID.MINING_MOTHERLODE_BRONZE,
		net.runelite.api.AnimationID.MINING_CRASHEDSTAR_BRONZE, net.runelite.api.AnimationID.MINING_IRON_PICKAXE,
		net.runelite.api.AnimationID.MINING_MOTHERLODE_IRON, net.runelite.api.AnimationID.MINING_CRASHEDSTAR_IRON,
		net.runelite.api.AnimationID.MINING_STEEL_PICKAXE, net.runelite.api.AnimationID.MINING_MOTHERLODE_STEEL,
		net.runelite.api.AnimationID.MINING_CRASHEDSTAR_STEEL, net.runelite.api.AnimationID.MINING_BLACK_PICKAXE,
		net.runelite.api.AnimationID.MINING_MOTHERLODE_BLACK, net.runelite.api.AnimationID.MINING_CRASHEDSTAR_BLACK,
		net.runelite.api.AnimationID.MINING_MITHRIL_PICKAXE, net.runelite.api.AnimationID.MINING_MOTHERLODE_MITHRIL,
		net.runelite.api.AnimationID.MINING_CRASHEDSTAR_MITHRIL, net.runelite.api.AnimationID.MINING_ADAMANT_PICKAXE,
		net.runelite.api.AnimationID.MINING_MOTHERLODE_ADAMANT, net.runelite.api.AnimationID.MINING_CRASHEDSTAR_ADAMANT,
		net.runelite.api.AnimationID.MINING_RUNE_PICKAXE, net.runelite.api.AnimationID.MINING_MOTHERLODE_RUNE,
		net.runelite.api.AnimationID.MINING_CRASHEDSTAR_RUNE, net.runelite.api.AnimationID.MINING_GILDED_PICKAXE,
		net.runelite.api.AnimationID.MINING_MOTHERLODE_GILDED, net.runelite.api.AnimationID.MINING_CRASHEDSTAR_GILDED,
		net.runelite.api.AnimationID.MINING_DRAGON_PICKAXE, net.runelite.api.AnimationID.MINING_MOTHERLODE_DRAGON,
		net.runelite.api.AnimationID.MINING_CRASHEDSTAR_DRAGON, net.runelite.api.AnimationID.MINING_DRAGON_PICKAXE_OR,
		net.runelite.api.AnimationID.MINING_MOTHERLODE_DRAGON_OR, net.runelite.api.AnimationID.MINING_CRASHEDSTAR_DRAGON_OR,
		net.runelite.api.AnimationID.MINING_DRAGON_PICKAXE_OR_TRAILBLAZER, net.runelite.api.AnimationID.MINING_MOTHERLODE_DRAGON_OR_TRAILBLAZER,
		net.runelite.api.AnimationID.MINING_CRASHEDSTAR_DRAGON_OR_TRAILBLAZER, net.runelite.api.AnimationID.MINING_DRAGON_PICKAXE_UPGRADED,
		net.runelite.api.AnimationID.MINING_MOTHERLODE_DRAGON_UPGRADED, net.runelite.api.AnimationID.MINING_CRASHEDSTAR_DRAGON_UPGRADED,
		net.runelite.api.AnimationID.MINING_INFERNAL_PICKAXE, net.runelite.api.AnimationID.MINING_MOTHERLODE_INFERNAL,
		net.runelite.api.AnimationID.MINING_CRASHEDSTAR_INFERNAL, net.runelite.api.AnimationID.MINING_3A_PICKAXE,
		net.runelite.api.AnimationID.MINING_MOTHERLODE_3A, net.runelite.api.AnimationID.MINING_CRASHEDSTAR_3A,
		net.runelite.api.AnimationID.MINING_CRYSTAL_PICKAXE, net.runelite.api.AnimationID.MINING_MOTHERLODE_CRYSTAL,
		net.runelite.api.AnimationID.MINING_CRASHEDSTAR_CRYSTAL, net.runelite.api.AnimationID.MINING_TRAILBLAZER_PICKAXE,
		net.runelite.api.AnimationID.MINING_TRAILBLAZER_PICKAXE_2, net.runelite.api.AnimationID.MINING_TRAILBLAZER_PICKAXE_3,
		net.runelite.api.AnimationID.MINING_MOTHERLODE_TRAILBLAZER
	);

	/** As upstream: every one- and two-handed axe woodcutting animation. */
	private static final java.util.Set<Integer> WOODCUTTING_ANIMS = com.google.common.collect.ImmutableSet.of(
		net.runelite.api.AnimationID.WOODCUTTING_BRONZE, net.runelite.api.AnimationID.WOODCUTTING_IRON, net.runelite.api.AnimationID.WOODCUTTING_STEEL,
		net.runelite.api.AnimationID.WOODCUTTING_BLACK, net.runelite.api.AnimationID.WOODCUTTING_MITHRIL, net.runelite.api.AnimationID.WOODCUTTING_ADAMANT,
		net.runelite.api.AnimationID.WOODCUTTING_RUNE, net.runelite.api.AnimationID.WOODCUTTING_GILDED, net.runelite.api.AnimationID.WOODCUTTING_DRAGON,
		net.runelite.api.AnimationID.WOODCUTTING_DRAGON_OR, net.runelite.api.AnimationID.WOODCUTTING_INFERNAL,
		net.runelite.api.AnimationID.WOODCUTTING_3A_AXE, net.runelite.api.AnimationID.WOODCUTTING_CRYSTAL,
		net.runelite.api.AnimationID.WOODCUTTING_TRAILBLAZER, net.runelite.api.AnimationID.WOODCUTTING_2H_BRONZE,
		net.runelite.api.AnimationID.WOODCUTTING_2H_IRON, net.runelite.api.AnimationID.WOODCUTTING_2H_STEEL,
		net.runelite.api.AnimationID.WOODCUTTING_2H_BLACK, net.runelite.api.AnimationID.WOODCUTTING_2H_MITHRIL,
		net.runelite.api.AnimationID.WOODCUTTING_2H_ADAMANT, net.runelite.api.AnimationID.WOODCUTTING_2H_RUNE,
		net.runelite.api.AnimationID.WOODCUTTING_2H_DRAGON, net.runelite.api.AnimationID.WOODCUTTING_2H_CRYSTAL,
		net.runelite.api.AnimationID.WOODCUTTING_2H_CRYSTAL_INACTIVE, net.runelite.api.AnimationID.WOODCUTTING_2H_3A
	);

	/** As upstream: the local player is playing a woodcutting animation. */
	public static boolean isWoodcutting()
	{
		return WOODCUTTING_ANIMS.contains(net.runelite.client.plugins.microbot.util.player.Rs2Player.getAnimation());
	}

	/** As upstream: the local player is playing a mining animation. */
	public static boolean isMining()
	{
		return MINING_ANIMATION_IDS.contains(net.runelite.client.plugins.microbot.util.player.Rs2Player.getAnimation());
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
		public void applyFarmingSetup() { }
		public void applyFishingSetup() { }
		public void applyCookingSetup() { }
		public void applyFletchingSetup() { }
		public void applyHerbloreSetup() { }
		public void applySmithingSetup() { }
		public void applyAgilitySetup() { }
		public void applyGeneralBasicSetup() { }
		public void applyUniversalAntibanSetup() { }

		public void applyWoodcuttingSetup()
		{
			Rs2AntibanSettings.cooldownMeanMillis = 600;
			Rs2AntibanSettings.cooldownDeviationMillis = 150;
		}
	}
}
