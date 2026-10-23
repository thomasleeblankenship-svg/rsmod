package net.runelite.client.plugins.microbot.util.antiban;

/** Compatibility state for uploaded script pacing, not an anti-detection system. */
public final class Rs2AntibanSettings
{
	private Rs2AntibanSettings() { }
	public static volatile boolean actionCooldownActive;
	public static volatile boolean dynamicActivity;
	public static volatile boolean dynamicIntensity;
	public static volatile int cooldownMeanMillis = 400;
	public static volatile int cooldownDeviationMillis = 200;
}
