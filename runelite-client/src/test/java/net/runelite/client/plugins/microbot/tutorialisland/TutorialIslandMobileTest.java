package net.runelite.client.plugins.microbot.tutorialisland;

import org.junit.Test;
import static org.junit.Assert.*;

/** Tutorial Island mobile port: the progress varbit maps to upstream's stages. */
public class TutorialIslandMobileTest
{
	@Test
	public void progressMapsToUpstreamStages()
	{
		TutorialIslandScript.Status keep = TutorialIslandScript.Status.NAME;
		assertEquals(TutorialIslandScript.Status.GETTING_STARTED, TutorialIslandScript.statusFor(0, keep));
		assertEquals(TutorialIslandScript.Status.SURVIVAL_GUIDE, TutorialIslandScript.statusFor(10, keep));
		assertEquals(TutorialIslandScript.Status.COOKING_GUIDE, TutorialIslandScript.statusFor(199, keep));
		assertEquals(TutorialIslandScript.Status.QUEST_GUIDE, TutorialIslandScript.statusFor(250, keep));
		assertEquals("251-259 keep the current stage, as upstream", keep, TutorialIslandScript.statusFor(255, keep));
		assertEquals(TutorialIslandScript.Status.MINING_GUIDE, TutorialIslandScript.statusFor(360, keep));
		assertEquals(TutorialIslandScript.Status.COMBAT_GUIDE, TutorialIslandScript.statusFor(361, keep));
		assertEquals(TutorialIslandScript.Status.BANKER_GUIDE, TutorialIslandScript.statusFor(531, keep));
		assertEquals(TutorialIslandScript.Status.PRAYER_GUIDE, TutorialIslandScript.statusFor(600, keep));
		assertEquals(TutorialIslandScript.Status.MAGE_GUIDE, TutorialIslandScript.statusFor(680, keep));
		assertEquals(TutorialIslandScript.Status.FINISHED, TutorialIslandScript.statusFor(1000, keep));
	}
}
