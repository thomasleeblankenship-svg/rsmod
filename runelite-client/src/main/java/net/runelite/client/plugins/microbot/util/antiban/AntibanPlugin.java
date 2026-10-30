package net.runelite.client.plugins.microbot.util.antiban;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.AnimationID;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.StatChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.antiban.enums.CombatSkills;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Upstream {@code AntibanPlugin} (chsami/Microbot {@code a2114401}, BSD 2-Clause) as a static engine
 * the mobile plugin drives: it forwards the game tick, stat, chat and game-state events here. The
 * logic is upstream's: action cooldowns count down only while the activity is not busy, a repeated
 * XP drop in the same skill may start a cooldown or a micro-break, the activity and its intensity
 * follow the skill being trained, and play-style switching follows the attention span.
 * <p>
 * Mobile adaptations: there is no Swing panel or overlay; a master switch ({@link #setMasterEnabled},
 * the plugin's "Anti-ban" setting) turns every anti-ban action off and releases any pause it holds;
 * and micro-breaks do not need the break-handler plugin (the mobile break handler always runs them).
 */
public final class AntibanPlugin
{
	private static final int COOK_TIMEOUT = 3;
	private static final int MINING_TIMEOUT = 3;
	private static final int IDLE_TIMEOUT = 1;
	public static final int MICRO_BREAK_DURATION_LOW_DEFAULT = 3;
	public static final int MICRO_BREAK_DURATION_HIGH_DEFAULT = 15;
	private static final int MICRO_BREAK_DURATION_LOW_MIN = 1;
	private static final int MICRO_BREAK_DURATION_LOW_MAX = 10;
	private static final int MICRO_BREAK_DURATION_HIGH_MIN = 1;
	private static final int MICRO_BREAK_DURATION_HIGH_MAX = 30;

	public static int ticksSinceLogin;
	private static volatile boolean masterEnabled = true;
	private static Instant lastCookingAction = Instant.MIN;
	private static Instant lastMiningAction = Instant.MIN;
	private static int idleTicks;
	private static final Map<Skill, Integer> SKILL_EXP = new EnumMap<>(Skill.class);
	private static boolean ready;
	private static Skill lastSkillChanged;
	private static boolean lastMicroBreakActive;

	private AntibanPlugin()
	{
	}

	/** Upstream {@code startUp}: fresh settings from the profile, durations validated. */
	public static synchronized void start()
	{
		Rs2Antiban.setActivityIntensity(ActivityIntensity.EXTREME);
		Rs2AntibanSettings.reset();
		Rs2AntibanSettings.loadFromProfile();
		validateAndSetBreakDurations();
		resetTracking();
	}

	/** Upstream {@code shutDown}: releases any pause the anti-ban holds. */
	public static synchronized void stop()
	{
		endCooldownAndBreak();
		resetTracking();
	}

	/** The mobile master switch; turning it off ends a cooldown or micro-break and resumes scripts. */
	public static synchronized void setMasterEnabled(boolean enabled)
	{
		if (masterEnabled && !enabled)
		{
			endCooldownAndBreak();
		}
		masterEnabled = enabled;
	}

	public static boolean isMasterEnabled()
	{
		return masterEnabled;
	}

	public static boolean isCooking()
	{
		int animation = Rs2Player.getAnimation();
		return animation == AnimationID.COOKING_FIRE
			|| animation == AnimationID.COOKING_RANGE
			|| animation == AnimationID.COOKING_WINE
			|| Duration.between(lastCookingAction, Instant.now()).getSeconds() < COOK_TIMEOUT;
	}

	public static boolean isMining()
	{
		return Rs2Antiban.isMining()
			|| Duration.between(lastMiningAction, Instant.now()).getSeconds() < MINING_TIMEOUT;
	}

	public static boolean isIdle()
	{
		return idleTicks > IDLE_TIMEOUT;
	}

	/** As upstream: idle for too long, which usually means the script is stuck. */
	public static boolean isIdleTooLong(int timeout)
	{
		return idleTicks > timeout && !Rs2AntibanSettings.actionCooldownActive && !Rs2AntibanSettings.takeMicroBreaks;
	}

	public static void performActionBreak()
	{
		if (Rs2AntibanSettings.actionCooldownActive)
		{
			if (Rs2Antiban.getTIMEOUT() > 0)
			{
				if (Rs2Antiban.getCategory() == null || !Rs2Antiban.getCategory().isBusy())
				{
					Rs2Antiban.TIMEOUT--;
				}
			}
			else
			{
				Rs2AntibanSettings.actionCooldownActive = false;
				if (Rs2AntibanSettings.universalAntiban && !Rs2AntibanSettings.microBreakActive && !BreakHandlerScript.isBreakActive())
				{
					Microbot.pauseAllScripts.compareAndSet(true, false);
				}
			}
		}
	}

	public static void onChatMessage(ChatMessage event)
	{
		if (Rs2Antiban.checkForCookingEvent(event))
		{
			lastCookingAction = Instant.now();
		}
	}

	public static synchronized void onGameStateChanged(GameState state)
	{
		switch (state)
		{
			case LOGIN_SCREEN:
				if (Rs2AntibanSettings.actionCooldownActive)
				{
					Rs2Antiban.TIMEOUT = 0;
					Rs2AntibanSettings.actionCooldownActive = false;
				}
				// fall through
			case LOGGING_IN:
			case HOPPING:
				ready = true;
				break;
			case LOGGED_IN:
				if (ready)
				{
					ticksSinceLogin = 0;
					ready = false;
				}
				break;
			default:
				break;
		}
	}

	public static synchronized void onGameTick()
	{
		ticksSinceLogin++;

		if (!Rs2Player.isAnimating())
		{
			idleTicks++;
		}
		else
		{
			if (Rs2AntibanSettings.simulateFatigue)
			{
				ticksSinceLogin -= idleTicks;
			}
			idleTicks = 0;
		}

		if (Rs2Antiban.isMining())
		{
			lastMiningAction = Instant.now();
		}

		if (!masterEnabled || !Rs2AntibanSettings.antibanEnabled)
		{
			return;
		}

		// Upstream: when a micro-break ends, release the pause so scripts resume.
		if (lastMicroBreakActive && !Rs2AntibanSettings.microBreakActive && !Rs2AntibanSettings.actionCooldownActive
			&& !BreakHandlerScript.isBreakActive())
		{
			Microbot.pauseAllScripts.compareAndSet(true, false);
		}
		lastMicroBreakActive = Rs2AntibanSettings.microBreakActive;

		if (Rs2AntibanSettings.actionCooldownActive)
		{
			performActionBreak();
		}

		if (Rs2AntibanSettings.usePlayStyle && Rs2Antiban.getPlayStyle() != null
			&& Rs2AntibanSettings.simulateAttentionSpan && Rs2AntibanSettings.profileSwitching
			&& Rs2Antiban.getPlayStyle().shouldSwitchProfileBasedOnAttention())
		{
			Rs2Antiban.setPlayStyle(Rs2Antiban.getPlayStyle().switchProfile());
			Rs2Antiban.getPlayStyle().resetPlayStyle();
		}
	}

	public static synchronized void onStatChanged(StatChanged statChanged)
	{
		if (!masterEnabled || !Rs2AntibanSettings.antibanEnabled)
		{
			return;
		}

		final Skill skill = statChanged.getSkill();
		final int exp = statChanged.getXp();
		final Integer previous = SKILL_EXP.put(skill, exp);

		if (lastSkillChanged != null && (lastSkillChanged.equals(skill)
			|| (CombatSkills.isCombatSkill(lastSkillChanged) && CombatSkills.isCombatSkill(skill))))
		{
			if (Rs2AntibanSettings.universalAntiban && !Rs2AntibanSettings.actionCooldownActive && Rs2Antiban.getActivity() != null)
			{
				Rs2Antiban.actionCooldown();
				Rs2Antiban.takeMicroBreakByChance();
			}
			if (Rs2Antiban.getActivity() == null)
			{
				updateAntibanSettings(skill);
			}
			return;
		}

		lastSkillChanged = skill;

		if (previous == null || previous >= exp)
		{
			return;
		}

		updateAntibanSettings(skill);
	}

	private static void updateAntibanSettings(Skill skill)
	{
		final ActivityIntensity activityIntensity = ActivityIntensity.fromSkill(skill);
		final Activity activity = Activity.fromSkill(skill);

		if (activity != null && Rs2AntibanSettings.dynamicActivity)
		{
			Rs2Antiban.setActivity(activity);
			if (Rs2AntibanSettings.devDebug)
			{
				Microbot.log("Activity changed, new activity: " + activity);
			}
			if (Rs2AntibanSettings.universalAntiban)
			{
				Rs2Antiban.actionCooldown();
				Rs2Antiban.takeMicroBreakByChance();
			}
		}

		if (activityIntensity != null && Rs2AntibanSettings.dynamicIntensity)
		{
			Rs2Antiban.setActivityIntensity(activityIntensity);
			if (Rs2AntibanSettings.devDebug)
			{
				Microbot.log("Activity changed, new activity intensity: " + activityIntensity);
			}
		}
	}

	/** As upstream: out-of-range micro-break durations (minutes) fall back to the defaults. */
	public static void validateAndSetBreakDurations()
	{
		if (Rs2AntibanSettings.microBreakDurationLow < MICRO_BREAK_DURATION_LOW_MIN
			|| Rs2AntibanSettings.microBreakDurationLow > MICRO_BREAK_DURATION_LOW_MAX)
		{
			Rs2AntibanSettings.microBreakDurationLow = MICRO_BREAK_DURATION_LOW_DEFAULT;
		}
		if (Rs2AntibanSettings.microBreakDurationHigh < MICRO_BREAK_DURATION_HIGH_MIN
			|| Rs2AntibanSettings.microBreakDurationHigh > MICRO_BREAK_DURATION_HIGH_MAX)
		{
			Rs2AntibanSettings.microBreakDurationHigh = MICRO_BREAK_DURATION_HIGH_DEFAULT;
		}
		if (Rs2AntibanSettings.microBreakDurationLow > Rs2AntibanSettings.microBreakDurationHigh)
		{
			Rs2AntibanSettings.microBreakDurationLow = MICRO_BREAK_DURATION_LOW_DEFAULT;
			Rs2AntibanSettings.microBreakDurationHigh = MICRO_BREAK_DURATION_HIGH_DEFAULT;
		}
	}

	/** Ends a cooldown, and a micro-break not yet taken; a break the break handler is taking runs to its end. */
	private static void endCooldownAndBreak()
	{
		boolean held = Rs2AntibanSettings.actionCooldownActive;
		Rs2Antiban.TIMEOUT = 0;
		Rs2AntibanSettings.actionCooldownActive = false;
		if (BreakHandlerScript.isBreakActive())
		{
			return;
		}
		Rs2AntibanSettings.microBreakActive = false;
		if (held)
		{
			Microbot.pauseAllScripts.compareAndSet(true, false);
		}
	}

	private static void resetTracking()
	{
		idleTicks = 0;
		lastSkillChanged = null;
		lastMicroBreakActive = false;
		SKILL_EXP.clear();
		lastCookingAction = Instant.MIN;
		lastMiningAction = Instant.MIN;
	}
}
