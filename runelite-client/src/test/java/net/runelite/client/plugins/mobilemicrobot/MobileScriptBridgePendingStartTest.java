package net.runelite.client.plugins.mobilemicrobot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.After;
import org.junit.Test;

/**
 * The Microbot screen's Start while the Microbot Mobile plugin is off: the request is queued, the caller is told
 * to turn the plugin on, and the plugin runs it once attached (only while it is fresh, and not after Stop).
 */
public class MobileScriptBridgePendingStartTest
{
	private final MobileMicrobotPlugin plugin = mock(MobileMicrobotPlugin.class);

	@After
	public void tearDown()
	{
		MobileScriptBridge.attach(plugin);
		MobileScriptBridge.detach(plugin);
	}

	@Test
	public void startWhileOffIsQueuedAndRunsOnAttach()
	{
		assertFalse(MobileScriptBridge.start("F2P_FISHING"));
		assertTrue(MobileScriptBridge.statusJson().contains("Turning on Microbot Mobile"));
		MobileScriptBridge.attach(plugin);
		MobileScriptBridge.startPending();
		verify(plugin).requestStart("F2P_FISHING");
	}

	@Test
	public void startWhileOnGoesStraightToThePlugin()
	{
		MobileScriptBridge.attach(plugin);
		assertTrue(MobileScriptBridge.start("F2P_COOKING"));
		verify(plugin).requestStart("F2P_COOKING");
	}

	@Test
	public void staleQueuedStartIsDropped()
	{
		MobileScriptBridge.start("F2P_FISHING");
		MobileScriptBridge.agePendingStartForTest(MobileScriptBridge.PENDING_START_MILLIS + 1);
		MobileScriptBridge.attach(plugin);
		MobileScriptBridge.startPending();
		verify(plugin, never()).requestStart("F2P_FISHING");
	}

	@Test
	public void stopCancelsAQueuedStart()
	{
		MobileScriptBridge.start("F2P_FISHING");
		MobileScriptBridge.stop();
		MobileScriptBridge.attach(plugin);
		MobileScriptBridge.startPending();
		verify(plugin, never()).requestStart("F2P_FISHING");
	}

	@Test
	public void offStatusSaysStartTurnsItOn()
	{
		MobileScriptBridge.attach(plugin);
		MobileScriptBridge.detach(plugin);
		assertTrue(MobileScriptBridge.statusJson().contains(MobileScriptBridge.OFF_STATUS));
		assertEquals(false, MobileScriptBridge.statusJson().contains("\"ready\":true"));
	}
}
