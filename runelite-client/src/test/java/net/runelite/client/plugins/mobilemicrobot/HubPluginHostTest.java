package net.runelite.client.plugins.mobilemicrobot;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.headless.JavaBridge;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.hubtest.HubTestPlugins;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Hub plugin hosting: explicit start, one at a time, fail closed. No game, mocked nothing but the plugin stopper. */
public class HubPluginHostTest
{
	static
	{
		// JavaBridge captures this flag on first load; set it before any test reaches the bridge.
		System.setProperty("runelite.bridge", "true");
	}

	private final List<Plugin> stopped = new ArrayList<>();
	private final HubPluginHost host = new HubPluginHost(stopped::add);
	private final Plugin a = new HubTestPlugins.HubA();
	private final Plugin b = new HubTestPlugins.HubB();

	@Before
	public void setUp()
	{
		JavaBridge.setScriptInputSuspended(false);
	}

	@After
	public void tearDown()
	{
		host.end(false);
		JavaBridge.setScriptInputSuspended(false);
	}

	@Test
	public void recognisesOnlyExternalPluginsInTheMicrobotPackage()
	{
		assertTrue(HubPluginHost.isHubPlugin(a));
		assertFalse(HubPluginHost.isHubPlugin(new HubTestPlugins.NotExternal()));
		assertFalse(HubPluginHost.isHubPlugin(new MobileMicrobotPlugin()));
		assertFalse(HubPluginHost.isHubPlugin(null));
		assertEquals("Hub A", HubPluginHost.name(a));
	}

	@Test
	public void startedPluginGetsItsOwnActiveSession()
	{
		String status = host.started(a, true);
		assertTrue(status, status.startsWith("Running"));
		assertSame(a, host.activePlugin());
		assertTrue(MobileActions.currentPermission() != null && MobileActions.currentPermission().isActive());
		assertTrue(stopped.isEmpty());
	}

	@Test
	public void notReadyRuntimeTurnsThePluginStraightBackOff()
	{
		host.started(a, false);
		assertEquals(List.of(a), stopped);
		assertFalse(host.isRunning());
	}

	@Test
	public void secondHubPluginReplacesTheFirstOneAtATime()
	{
		host.started(a, true);
		host.started(b, true);
		assertEquals(List.of(a), stopped);
		assertSame(b, host.activePlugin());
	}

	@Test
	public void disablingEndsTheSessionWithoutTurningOffAgain()
	{
		host.started(a, true);
		var session = MobileActions.currentPermission();
		host.stopped(a);
		assertFalse(host.isRunning());
		assertFalse(session.isActive());
		assertNull(MobileActions.currentPermission());
		assertTrue(stopped.isEmpty());
	}

	@Test
	public void backgroundingTurnsThePluginOffAndResumeDoesNotRestartIt()
	{
		host.started(a, true);
		JavaBridge.setScriptInputSuspended(true);
		JavaBridge.setScriptInputSuspended(false);
		assertTrue(host.checkSession());
		assertEquals(List.of(a), stopped);
		assertFalse(host.isRunning());
		assertFalse(host.checkSession());
	}

	@Test
	public void manualTapDoesNotEndTheHubPlugin()
	{
		host.started(a, true);
		JavaBridge.onMove(0, 0);
		assertFalse(host.checkSession());
		assertTrue(host.isRunning());
	}

	@Test
	public void endTurnsOffAndIsIdempotent()
	{
		host.started(a, true);
		assertTrue(host.end(true));
		assertFalse(host.end(true));
		assertEquals(List.of(a), stopped);
	}
}
