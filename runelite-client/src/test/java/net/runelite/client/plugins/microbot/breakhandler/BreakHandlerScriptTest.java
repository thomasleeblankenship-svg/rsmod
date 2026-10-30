package net.runelite.client.plugins.microbot.breakhandler;

import net.runelite.api.Client;
import net.runelite.client.headless.HeadlessLogin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class BreakHandlerScriptTest
{
	private MockedStatic<Microbot> microbot;
	private MockedStatic<Rs2Player> player;
	private MockedStatic<Rs2Walker> walker;
	private MockedStatic<LoginManager> login;
	private MockedStatic<HeadlessLogin> headless;
	private boolean loggedIn = true;
	private boolean inCombat;
	private boolean scheduled = true;
	private boolean logout = true;
	private boolean running = true;
	private boolean held;
	private long now = 1_000_000;

	private final BreakHandlerScript.Settings settings = new BreakHandlerScript.Settings()
	{
		@Override
		public boolean scheduledBreaks()
		{
			return scheduled;
		}

		@Override
		public int playMinutesMin()
		{
			return 1;
		}

		@Override
		public int playMinutesMax()
		{
			return 1;
		}

		@Override
		public int breakMinutesMin()
		{
			return 2;
		}

		@Override
		public int breakMinutesMax()
		{
			return 2;
		}

		@Override
		public boolean logoutOnBreak()
		{
			return logout;
		}

		@Override
		public boolean onlyMicroBreaks()
		{
			return false;
		}

		@Override
		public boolean scriptRunning()
		{
			return running;
		}

		@Override
		public boolean loginHeld()
		{
			return held;
		}
	};

	@Before
	public void setUp()
	{
		BreakHandlerScript.resetForTest();
		Rs2AntibanSettings.microBreakActive = false;
		Rs2AntibanSettings.microBreakDurationLow = 1;
		Rs2AntibanSettings.microBreakDurationHigh = 1;
		Microbot.pauseAllScripts.set(false);
		Client client = mock(Client.class);
		when(client.getWorld()).thenReturn(420);
		microbot = mockStatic(Microbot.class);
		microbot.when(Microbot::isLoggedIn).thenAnswer(call -> loggedIn);
		microbot.when(Microbot::getClient).thenReturn(client);
		player = mockStatic(Rs2Player.class);
		player.when(Rs2Player::isInCombat).thenAnswer(call -> inCombat);
		player.when(Rs2Player::logout).thenAnswer(call ->
		{
			loggedIn = false;
			return null;
		});
		walker = mockStatic(Rs2Walker.class);
		login = mockStatic(LoginManager.class);
		login.when(() -> LoginManager.login(anyInt())).thenAnswer(call ->
		{
			loggedIn = true;
			return true;
		});
		headless = mockStatic(HeadlessLogin.class);
		BreakHandlerScript.start(settings, now);
	}

	@After
	public void tearDown()
	{
		microbot.close();
		player.close();
		walker.close();
		login.close();
		headless.close();
		BreakHandlerScript.resetForTest();
		Rs2AntibanSettings.microBreakActive = false;
		Microbot.pauseAllScripts.set(false);
	}

	private void advance(long millis)
	{
		for (long t = 0; t < millis; t += 600)
		{
			now += 600;
			BreakHandlerScript.tick(now);
		}
	}

	@Test
	public void aScheduledBreakLogsOutPausesAndLogsBackInToTheSameWorld()
	{
		assertEquals(60, BreakHandlerScript.breakIn);
		advance(59_000);
		assertFalse(BreakHandlerScript.isBreakActive());
		advance(4_000);
		assertTrue(BreakHandlerScript.isBreakActive());
		assertTrue(Microbot.pauseAllScripts.get());
		walker.verify(() -> Rs2Walker.clearWalkingRoute(anyString()));
		player.verify(Rs2Player::logout, times(1));
		assertEquals(BreakHandlerState.LOGGED_OUT, BreakHandlerScript.getCurrentState());

		advance(121_000);
		login.verify(() -> LoginManager.login(420));
		advance(1_200);
		assertFalse(BreakHandlerScript.isBreakActive());
		assertFalse(Microbot.pauseAllScripts.get());
		assertEquals(1, BreakHandlerScript.totalBreaks);
		assertTrue("the next play time is scheduled", BreakHandlerScript.breakIn > 50);
	}

	@Test
	public void playTimeOnlyCountsWhileLoggedInWithAScript()
	{
		running = false;
		advance(30_000);
		loggedIn = false;
		running = true;
		advance(30_000);
		assertEquals(60, BreakHandlerScript.breakIn);
	}

	@Test
	public void theLockAndCombatHoldABreakOff()
	{
		BreakHandlerScript.setLockState(true);
		advance(70_000);
		assertFalse(BreakHandlerScript.isBreakActive());
		BreakHandlerScript.setLockState(false);
		inCombat = true;
		advance(1_200);
		assertEquals(BreakHandlerState.BREAK_REQUESTED, BreakHandlerScript.getCurrentState());
		assertFalse(Microbot.pauseAllScripts.get());
		advance(BreakHandlerScript.SAFE_WAIT_MILLIS + 1_200);
		assertFalse("skipped after waiting too long", BreakHandlerScript.isBreakActive());
	}

	@Test
	public void aMicroBreakPausesInGameEvenWithScheduledBreaksOff()
	{
		scheduled = false;
		BreakHandlerScript.settingsChanged();
		assertEquals(-1, BreakHandlerScript.breakIn);
		Rs2AntibanSettings.microBreakActive = true;
		advance(1_800);
		assertEquals(BreakHandlerState.INGAME_BREAK_ACTIVE, BreakHandlerScript.getCurrentState());
		assertTrue(Microbot.pauseAllScripts.get());
		player.verify(Rs2Player::logout, never());
		advance(62_000);
		assertFalse(BreakHandlerScript.isBreakActive());
		assertFalse(Rs2AntibanSettings.microBreakActive);
		assertFalse(Microbot.pauseAllScripts.get());
	}

	@Test
	public void stoppingTheScriptEndsTheBreakWithoutLoggingIn()
	{
		advance(64_000);
		assertEquals(BreakHandlerState.LOGGED_OUT, BreakHandlerScript.getCurrentState());
		running = false;
		advance(600);
		assertFalse(BreakHandlerScript.isBreakActive());
		assertFalse(Microbot.pauseAllScripts.get());
		login.verify(() -> LoginManager.login(anyInt()), never());
	}

	@Test
	public void aTerminalLogoutDuringTheBreakIsNotUndone()
	{
		advance(64_000);
		assertEquals(BreakHandlerState.LOGGED_OUT, BreakHandlerScript.getCurrentState());
		held = true;
		advance(121_000);
		login.verify(() -> LoginManager.login(anyInt()), never());
		assertFalse(BreakHandlerScript.isBreakActive());
		assertFalse(Microbot.pauseAllScripts.get());
	}
}
