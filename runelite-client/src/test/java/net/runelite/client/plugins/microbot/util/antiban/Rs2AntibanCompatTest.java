package net.runelite.client.plugins.microbot.util.antiban;

import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/** The upstream anti-ban entry points compile and stay inert: they record labels and never act. */
public class Rs2AntibanCompatTest
{
	@After
	public void tearDown()
	{
		Rs2Antiban.setActivity(null);
		Rs2Antiban.setActivityIntensity(null);
		Rs2Antiban.setPlayStyle(null);
	}

	@Test
	public void setActivityRecordsUpstreamIntensityAndCategory()
	{
		Activity activity = Activity.values()[0];
		Rs2Antiban.setActivity(activity);
		assertSame(activity, Rs2Antiban.getActivity());
		assertSame(activity.getActivityIntensity(), Rs2Antiban.getActivityIntensity());
		assertSame(activity.getCategory(), Rs2Antiban.getCategory());
	}

	@Test
	public void entryPointsAreInert()
	{
		Rs2Antiban.setPlayStyle(PlayStyle.values()[0]);
		Rs2Antiban.antibanSetupTemplates.applyWoodcuttingSetup();
		Rs2Antiban.activateAntiban();
		Rs2Antiban.moveMouseRandomly();
		Rs2Antiban.moveMouseOffScreen(1.0);
		assertFalse(Rs2Antiban.takeMicroBreakByChance());
		assertFalse(Rs2AntibanSettings.microBreakActive);
	}
}
