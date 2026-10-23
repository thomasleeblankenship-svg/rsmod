package net.runelite.client.plugins.microbot.api;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.headless.JavaBridge;
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real cross-thread action dispatch and cancellation, with mocked engine boundaries. */
public class MobileActionsTest
{
	private ReadApiFixture fixture;
	private ScriptInputSession session;

	@Before
	public void setUp() throws Exception
	{
		JavaBridge.setScriptInputSuspended(false);
		fixture = new ReadApiFixture();
		session = new ScriptInputSession();
		MobileActions.bind(session);
	}

	@After
	public void tearDown() throws Exception
	{
		MobileActions.unbind(session);
		JavaBridge.setScriptInputSuspended(false);
		fixture.close();
	}

	@Test
	public void walkConfinesEngineDispatchToClientThreadAndRateLimitsPerTick()
	{
		doAnswer(call -> { assertTrue(fixture.client.isClientThread()); return null; })
			.when(fixture.client).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), anyString(), anyString());
		assertTrue(MobileActions.walk(new WorldPoint(3208, 3208, 0)));
		assertFalse(MobileActions.walk(new WorldPoint(3209, 3209, 0)));
		fixture.tick++;
		assertTrue(MobileActions.walk(new WorldPoint(3209, 3209, 0)));
		verify(fixture.client, times(2)).menuAction(anyInt(), anyInt(), eq(MenuAction.WALK), anyInt(), anyInt(), anyString(), anyString());
	}

	@Test
	public void cancelledAndBackgroundSessionsCannotDispatchAfterResume()
	{
		JavaBridge.setScriptInputSuspended(true);
		ScriptInputSession background = new ScriptInputSession();
		JavaBridge.setScriptInputSuspended(false);
		assertFalse(MobileActions.walk(new WorldPoint(3208, 3208, 0)));
		MobileActions.bind(background);
		assertFalse(MobileActions.walk(new WorldPoint(3208, 3208, 0)));
		verify(fixture.client, never()).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), anyString(), anyString());
	}

	@Test
	public void openNativeMenuAndWrongPlaneRefuseWithoutConsumingTickBudget()
	{
		when(fixture.client.isMenuOpen()).thenReturn(true);
		assertFalse(MobileActions.walk(new WorldPoint(3208, 3208, 0)));
		when(fixture.client.isMenuOpen()).thenReturn(false);
		assertFalse(MobileActions.walk(new WorldPoint(3208, 3208, 1)));
		assertTrue(MobileActions.walk(new WorldPoint(3208, 3208, 0)));
	}

	@Test
	public void replacedSessionDoesNotReactivateOldPermission()
	{
		ScriptInputSession next = new ScriptInputSession();
		MobileActions.bind(next);
		assertFalse(session.isActive());
		MobileActions.unbind(session);
		assertTrue(MobileActions.active());
		MobileActions.unbind(next);
		assertFalse(MobileActions.active());
	}

	@Test
	public void queuedActionIsCancelledWhenInputIsRevokedBeforeClientRunsIt() throws Exception
	{
		fixture.clientThreadStalled = true;
		java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
		try
		{
			java.util.concurrent.Future<Boolean> request = worker.submit(() -> MobileActions.walk(new WorldPoint(3208, 3208, 0)));
			session.close();
			assertFalse(request.get(3, java.util.concurrent.TimeUnit.SECONDS));
			verify(fixture.client, never()).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), anyString(), anyString());
		}
		finally { worker.shutdownNow(); }
	}
}
