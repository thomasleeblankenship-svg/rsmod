package net.runelite.client.plugins.microbot.util.antiban;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mockStatic;

/** The upstream anti-ban runs as upstream: cooldowns pause and release scripts, micro-breaks are raised, the switch stops it. */
public class Rs2AntibanCompatTest
{
	@Before
	public void setUp()
	{
		Rs2Antiban.resetAntibanSettings(true);
		AntibanPlugin.setMasterEnabled(true);
		Microbot.pauseAllScripts.set(false);
	}

	@After
	public void tearDown()
	{
		Rs2Antiban.resetAntibanSettings(true);
		Rs2Antiban.setPlayStyle(null);
		AntibanPlugin.setMasterEnabled(true);
		Microbot.pauseAllScripts.set(false);
		Rs2Antiban.TIMEOUT = 0;
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
	public void anActionCooldownPausesScriptsUntilItCountsDown()
	{
		Rs2AntibanSettings.usePlayStyle = true;
		Rs2AntibanSettings.universalAntiban = true;
		Rs2AntibanSettings.actionCooldownChance = 1.0;
		Rs2AntibanSettings.moveMouseRandomly = false;
		Rs2AntibanSettings.moveMouseOffScreen = false;
		Rs2Antiban.setPlayStyle(PlayStyle.values()[0]);
		Rs2Antiban.actionCooldown();
		assertTrue(Rs2AntibanSettings.actionCooldownActive);
		assertTrue(Microbot.pauseAllScripts.get());

		try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class))
		{
			for (int i = 0; i < 1_000 && Rs2AntibanSettings.actionCooldownActive; i++)
			{
				AntibanPlugin.performActionBreak();
			}
		}
		assertFalse(Rs2AntibanSettings.actionCooldownActive);
		assertFalse("scripts resume once the cooldown ends", Microbot.pauseAllScripts.get());
	}

	@Test
	public void aMicroBreakIsRaisedOnlyWhenEnabled()
	{
		Rs2AntibanSettings.moveMouseOffScreen = false;
		Rs2AntibanSettings.microBreakChance = 1.0;
		Rs2AntibanSettings.takeMicroBreaks = false;
		assertFalse(Rs2Antiban.takeMicroBreakByChance());
		Rs2AntibanSettings.takeMicroBreaks = true;
		assertTrue(Rs2Antiban.takeMicroBreakByChance());
		assertTrue(Rs2AntibanSettings.microBreakActive);
	}

	@Test
	public void theMasterSwitchStopsEveryActionAndReleasesAPause()
	{
		Rs2AntibanSettings.usePlayStyle = true;
		Rs2AntibanSettings.universalAntiban = true;
		Rs2AntibanSettings.actionCooldownChance = 1.0;
		Rs2AntibanSettings.moveMouseOffScreen = false;
		Rs2AntibanSettings.moveMouseRandomly = false;
		Rs2Antiban.setPlayStyle(PlayStyle.values()[0]);
		Rs2Antiban.actionCooldown();
		assertTrue(Microbot.pauseAllScripts.get());

		AntibanPlugin.setMasterEnabled(false);
		assertFalse(Rs2AntibanSettings.actionCooldownActive);
		assertFalse(Microbot.pauseAllScripts.get());

		Rs2AntibanSettings.takeMicroBreaks = true;
		Rs2AntibanSettings.microBreakChance = 1.0;
		assertFalse(Rs2Antiban.takeMicroBreakByChance());
		Rs2Antiban.actionCooldown();
		assertFalse(Rs2AntibanSettings.actionCooldownActive);
		Rs2Antiban.moveMouseRandomly();
		Rs2Antiban.moveMouseOffScreen(100.0);
	}

	@Test
	public void outOfRangeMicroBreakDurationsFallBackToTheDefaults()
	{
		Rs2AntibanSettings.microBreakDurationLow = 0;
		Rs2AntibanSettings.microBreakDurationHigh = 99;
		AntibanPlugin.validateAndSetBreakDurations();
		assertEquals(AntibanPlugin.MICRO_BREAK_DURATION_LOW_DEFAULT, Rs2AntibanSettings.microBreakDurationLow);
		assertEquals(AntibanPlugin.MICRO_BREAK_DURATION_HIGH_DEFAULT, Rs2AntibanSettings.microBreakDurationHigh);
	}
}
