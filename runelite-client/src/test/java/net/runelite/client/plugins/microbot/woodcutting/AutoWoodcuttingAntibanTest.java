package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The mobile woodcutting port runs the whole anti-ban set, unless the player overrides script settings. */
public class AutoWoodcuttingAntibanTest
{
	@After
	public void tearDown()
	{
		// reset() leaves the player's override alone, so clear it here.
		Rs2AntibanSettings.overwriteScriptSettings = false;
		Rs2Antiban.resetAntibanSettings(true);
	}

	@Test
	public void theExtrasEnableBreaksMouseAndCameraOnTopOfTheTemplate()
	{
		Rs2Antiban.resetAntibanSettings(true);
		Rs2AntibanSettings.overwriteScriptSettings = false;
		Rs2Antiban.antibanSetupTemplates.applyWoodcuttingSetup();
		AutoWoodcuttingScript.applyMobileAntibanExtras();
		assertTrue(Rs2AntibanSettings.usePlayStyle);
		assertTrue(Rs2AntibanSettings.takeMicroBreaks);
		assertTrue(Rs2AntibanSettings.moveMouseRandomly);
		assertTrue(Rs2AntibanSettings.moveMouseOffScreen);
		assertTrue(Rs2AntibanSettings.moveCameraRandomly);
	}

	@Test
	public void aPlayersOverrideKeepsTheirSettings()
	{
		Rs2Antiban.resetAntibanSettings(true);
		Rs2AntibanSettings.overwriteScriptSettings = true;
		AutoWoodcuttingScript.applyMobileAntibanExtras();
		assertFalse(Rs2AntibanSettings.takeMicroBreaks);
		assertFalse(Rs2AntibanSettings.moveCameraRandomly);
	}
}
