package net.runelite.client.plugins.microbot.util.antiban;

import net.runelite.client.plugins.microbot.util.math.Rs2Random;

/** Compatibility pacing subset. Desktop fatigue/mouse profiles are not reproduced. */
public final class Rs2Antiban
{
	public static final SetupTemplates antibanSetupTemplates = new SetupTemplates();
	private Rs2Antiban() { }
	public static void resetAntibanSettings()
	{
		Rs2AntibanSettings.actionCooldownActive = false;
		Rs2AntibanSettings.dynamicActivity = false;
		Rs2AntibanSettings.dynamicIntensity = false;
		Rs2AntibanSettings.cooldownMeanMillis = 400;
		Rs2AntibanSettings.cooldownDeviationMillis = 200;
	}
	public static void actionCooldown()
	{
		Rs2AntibanSettings.actionCooldownActive = true;
		try { Rs2Random.waitEx(Rs2AntibanSettings.cooldownMeanMillis, Rs2AntibanSettings.cooldownDeviationMillis); }
		finally { Rs2AntibanSettings.actionCooldownActive = false; }
	}
	public static final class SetupTemplates
	{
		public void applyWoodcuttingSetup()
		{
			Rs2AntibanSettings.cooldownMeanMillis = 600;
			Rs2AntibanSettings.cooldownDeviationMillis = 150;
		}
	}
}
