package net.runelite.client.plugins.microbot.util.antiban;

import net.runelite.api.AnimationID;
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

	// ---- Util action batch AM (Claude 1): activity state ----
	// Upstream's AntibanPlugin counts idle ticks only while anti-ban is on; mobile never turns it on,
	// so this tracker runs on its own (fed by MobileMicrobotPlugin). It reads game state only.

	/** Upstream's limits: idle after more than 1 tick without animation; cooking and mining linger 3 s. */
	private static final int IDLE_TIMEOUT_TICKS = 1;
	private static final long ACTIVITY_LINGER_MILLIS = 3_000;
	private static volatile int idleTicks;
	private static volatile long lastCookingMillis = Long.MIN_VALUE / 2;
	private static volatile long lastMiningMillis = Long.MIN_VALUE / 2;

	private static final String[] COOKING_PREFIXES = {"You successfully cook", "You successfully bake", "You successfully fry",
		"You manage to cook", "You roast a", "You spit-roast", "You cook", "Eventually the Jubbly", "You half-cook",
		"The undead meat is now cooked", "The undead chicken is now cooked", "You successfully scramble", "You dry a piece of meat",
		"You accidentally burn", "Unfortunately the Jubbly", "You accidentally spoil"};

	/** As upstream: more than one game tick has passed without an animation. */
	public static boolean isIdle()
	{
		return idleTicks > IDLE_TIMEOUT_TICKS;
	}

	/** As upstream: a cooking chat message (spam channel). */
	public static boolean checkForCookingEvent(net.runelite.api.events.ChatMessage event)
	{
		if (event == null || event.getType() != net.runelite.api.ChatMessageType.SPAM || event.getMessage() == null)
		{
			return false;
		}
		String message = event.getMessage();
		return java.util.Arrays.stream(COOKING_PREFIXES).anyMatch(message::startsWith)
			|| "You burn the mushroom in the fire.".equals(message);
	}

	/** Upstream AntibanPlugin's cooking check: a cooking animation, or a cooking message within 3 s. */
	static boolean isCookingActivity()
	{
		int animation = net.runelite.client.plugins.microbot.util.player.Rs2Player.getAnimation();
		return animation == AnimationID.COOKING_FIRE || animation == AnimationID.COOKING_RANGE || animation == AnimationID.COOKING_WINE
			|| System.currentTimeMillis() - lastCookingMillis < ACTIVITY_LINGER_MILLIS;
	}

	/** Upstream AntibanPlugin's mining check: a mining animation now or within 3 s. */
	static boolean isMiningActivity()
	{
		return isMining() || System.currentTimeMillis() - lastMiningMillis < ACTIVITY_LINGER_MILLIS;
	}

	/** Called every game tick (by MobileMicrobotPlugin): counts idle ticks and notes mining. */
	public static void onGameTick()
	{
		if (net.runelite.client.plugins.microbot.util.player.Rs2Player.isAnimating())
		{
			idleTicks = 0;
		}
		else
		{
			idleTicks++;
		}
		if (isMining())
		{
			lastMiningMillis = System.currentTimeMillis();
		}
	}

	/** Called for every chat message (by MobileMicrobotPlugin): notes cooking. */
	public static void onChatMessage(net.runelite.api.events.ChatMessage event)
	{
		if (checkForCookingEvent(event))
		{
			lastCookingMillis = System.currentTimeMillis();
		}
	}

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
		AnimationID.MINING_BRONZE_PICKAXE, AnimationID.MINING_MOTHERLODE_BRONZE,
		AnimationID.MINING_CRASHEDSTAR_BRONZE, AnimationID.MINING_IRON_PICKAXE,
		AnimationID.MINING_MOTHERLODE_IRON, AnimationID.MINING_CRASHEDSTAR_IRON,
		AnimationID.MINING_STEEL_PICKAXE, AnimationID.MINING_MOTHERLODE_STEEL,
		AnimationID.MINING_CRASHEDSTAR_STEEL, AnimationID.MINING_BLACK_PICKAXE,
		AnimationID.MINING_MOTHERLODE_BLACK, AnimationID.MINING_CRASHEDSTAR_BLACK,
		AnimationID.MINING_MITHRIL_PICKAXE, AnimationID.MINING_MOTHERLODE_MITHRIL,
		AnimationID.MINING_CRASHEDSTAR_MITHRIL, AnimationID.MINING_ADAMANT_PICKAXE,
		AnimationID.MINING_MOTHERLODE_ADAMANT, AnimationID.MINING_CRASHEDSTAR_ADAMANT,
		AnimationID.MINING_RUNE_PICKAXE, AnimationID.MINING_MOTHERLODE_RUNE,
		AnimationID.MINING_CRASHEDSTAR_RUNE, AnimationID.MINING_GILDED_PICKAXE,
		AnimationID.MINING_MOTHERLODE_GILDED, AnimationID.MINING_CRASHEDSTAR_GILDED,
		AnimationID.MINING_DRAGON_PICKAXE, AnimationID.MINING_MOTHERLODE_DRAGON,
		AnimationID.MINING_CRASHEDSTAR_DRAGON, AnimationID.MINING_DRAGON_PICKAXE_OR,
		AnimationID.MINING_MOTHERLODE_DRAGON_OR, AnimationID.MINING_CRASHEDSTAR_DRAGON_OR,
		AnimationID.MINING_DRAGON_PICKAXE_OR_TRAILBLAZER, AnimationID.MINING_MOTHERLODE_DRAGON_OR_TRAILBLAZER,
		AnimationID.MINING_CRASHEDSTAR_DRAGON_OR_TRAILBLAZER, AnimationID.MINING_DRAGON_PICKAXE_UPGRADED,
		AnimationID.MINING_MOTHERLODE_DRAGON_UPGRADED, AnimationID.MINING_CRASHEDSTAR_DRAGON_UPGRADED,
		AnimationID.MINING_INFERNAL_PICKAXE, AnimationID.MINING_MOTHERLODE_INFERNAL,
		AnimationID.MINING_CRASHEDSTAR_INFERNAL, AnimationID.MINING_3A_PICKAXE,
		AnimationID.MINING_MOTHERLODE_3A, AnimationID.MINING_CRASHEDSTAR_3A,
		AnimationID.MINING_CRYSTAL_PICKAXE, AnimationID.MINING_MOTHERLODE_CRYSTAL,
		AnimationID.MINING_CRASHEDSTAR_CRYSTAL, AnimationID.MINING_TRAILBLAZER_PICKAXE,
		AnimationID.MINING_TRAILBLAZER_PICKAXE_2, AnimationID.MINING_TRAILBLAZER_PICKAXE_3,
		AnimationID.MINING_MOTHERLODE_TRAILBLAZER
	);

	/** As upstream: every one- and two-handed axe woodcutting animation. */
	private static final java.util.Set<Integer> WOODCUTTING_ANIMS = com.google.common.collect.ImmutableSet.of(
		AnimationID.WOODCUTTING_BRONZE, AnimationID.WOODCUTTING_IRON, AnimationID.WOODCUTTING_STEEL,
		AnimationID.WOODCUTTING_BLACK, AnimationID.WOODCUTTING_MITHRIL, AnimationID.WOODCUTTING_ADAMANT,
		AnimationID.WOODCUTTING_RUNE, AnimationID.WOODCUTTING_GILDED, AnimationID.WOODCUTTING_DRAGON,
		AnimationID.WOODCUTTING_DRAGON_OR, AnimationID.WOODCUTTING_INFERNAL,
		AnimationID.WOODCUTTING_3A_AXE, AnimationID.WOODCUTTING_CRYSTAL,
		AnimationID.WOODCUTTING_TRAILBLAZER, AnimationID.WOODCUTTING_2H_BRONZE,
		AnimationID.WOODCUTTING_2H_IRON, AnimationID.WOODCUTTING_2H_STEEL,
		AnimationID.WOODCUTTING_2H_BLACK, AnimationID.WOODCUTTING_2H_MITHRIL,
		AnimationID.WOODCUTTING_2H_ADAMANT, AnimationID.WOODCUTTING_2H_RUNE,
		AnimationID.WOODCUTTING_2H_DRAGON, AnimationID.WOODCUTTING_2H_CRYSTAL,
		AnimationID.WOODCUTTING_2H_CRYSTAL_INACTIVE, AnimationID.WOODCUTTING_2H_3A
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
