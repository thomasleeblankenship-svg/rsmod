package net.runelite.client.plugins.microbot.util.antiban;

/** Compatibility state for uploaded script pacing, not an anti-detection system. */
public final class Rs2AntibanSettings
{
	// Upstream anti-ban switches (chsami/Microbot a2114401, upstream defaults). Mobile accepts and
	// ignores them: no fatigue, attention, mistake, play-style, mouse or micro-break simulation is
	// ported, because those exist to evade detection. Kept so unchanged scripts compile.
	public static volatile boolean microBreakActive = false;
	public static volatile boolean antibanEnabled = true;
	public static volatile boolean usePlayStyle = false;
	public static volatile boolean randomIntervals = false;
	public static volatile boolean simulateFatigue = false;
	public static volatile boolean simulateAttentionSpan = false;
	public static volatile boolean behavioralVariability = false;
	public static volatile boolean nonLinearIntervals = false;
	public static volatile boolean profileSwitching = false;
	public static volatile boolean timeOfDayAdjust = false;
	public static volatile boolean simulateMistakes = false;
	public static volatile boolean naturalMouse = true;
	public static volatile boolean moveMouseOffScreen = false;
	public static volatile boolean moveMouseRandomly = false;
	public static volatile boolean contextualVariability = false;
	public static volatile boolean devDebug = false;
	public static volatile boolean overwriteScriptSettings = false;
	public static volatile boolean takeMicroBreaks = false;
	public static volatile boolean playSchedule = false;
	public static volatile boolean universalAntiban = false;
	public static volatile int microBreakDurationLow = 3;
	public static volatile int microBreakDurationHigh = 15;
	public static volatile double actionCooldownChance = 0.1;
	public static volatile double microBreakChance = 0.1;
	public static volatile double moveMouseRandomlyChance = 0.1;
	public static volatile double moveMouseOffScreenChance = 0.1;

	private Rs2AntibanSettings() { }
	public static volatile boolean actionCooldownActive;
	public static volatile boolean dynamicActivity;
	public static volatile boolean dynamicIntensity;
	public static volatile int cooldownMeanMillis = 400;
	public static volatile int cooldownDeviationMillis = 200;

	/** As upstream, but inert: anti-ban settings are not loaded from a profile (they have no effect here). */
	public static void loadFromProfile()
	{
	}
}
