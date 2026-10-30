package net.runelite.client.plugins.mobilemicrobot;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.GameState;
import net.runelite.client.headless.HeadlessLogin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

public class MobileAutoLoginTest
{
	private final AtomicBoolean enabled = new AtomicBoolean(true);
	private final AtomicInteger loginIndex = new AtomicInteger(10);
	private final AtomicLong now = new AtomicLong(1_000_000);
	private MockedStatic<LoginManager> login;
	private MockedStatic<HeadlessLogin> headless;
	private MockedStatic<BreakHandlerScript> breaks;
	private GameState state = GameState.LOGIN_SCREEN;
	private boolean onBreak;
	private MobileAutoLogin autoLogin;

	@Before
	public void setUp()
	{
		login = mockStatic(LoginManager.class);
		login.when(LoginManager::getGameState).thenAnswer(call -> state);
		login.when(LoginManager::checkLoginLockout).thenReturn(false);
		login.when(LoginManager::login).thenReturn(true);
		headless = mockStatic(HeadlessLogin.class);
		headless.when(HeadlessLogin::isLoginInProgress).thenReturn(false);
		breaks = mockStatic(BreakHandlerScript.class);
		breaks.when(BreakHandlerScript::isBreakActive).thenAnswer(call -> onBreak);
		autoLogin = new MobileAutoLogin(enabled::get, () -> 0, loginIndex::get, now::get);
	}

	@After
	public void tearDown()
	{
		login.close();
		headless.close();
		breaks.close();
	}

	@Test
	public void logsInOnTheLoginScreenAndBacksOffBetweenAttempts()
	{
		autoLogin.tick();
		login.verify(LoginManager::login, times(1));
		autoLogin.tick();
		now.addAndGet(2_000);
		autoLogin.tick();
		login.verify(LoginManager::login, times(1));
		now.addAndGet(20_000);
		autoLogin.tick();
		login.verify(LoginManager::login, times(2));
	}

	@Test
	public void waitsWhileHeadlessLoginIsStillDrivingALogin()
	{
		headless.when(HeadlessLogin::isLoginInProgress).thenReturn(true);
		autoLogin.tick();
		login.verify(LoginManager::login, never());
		headless.when(HeadlessLogin::isLoginGivenUp).thenReturn(true);
		autoLogin.tick();
		login.verify(LoginManager::login, times(1));
	}

	@Test
	public void doesNothingWhenOffLoggedInOnABreakOrLockedOut()
	{
		enabled.set(false);
		autoLogin.tick();
		enabled.set(true);
		state = GameState.LOGGED_IN;
		autoLogin.tick();
		state = GameState.LOGIN_SCREEN;
		onBreak = true;
		autoLogin.tick();
		onBreak = false;
		login.when(LoginManager::checkLoginLockout).thenReturn(true);
		autoLogin.tick();
		login.verify(LoginManager::login, never());
	}

	@Test
	public void aDisabledAccountStopsAutoLogin()
	{
		loginIndex.set(MobileAutoLogin.BANNED_LOGIN_INDEX);
		autoLogin.tick();
		loginIndex.set(10);
		now.addAndGet(600_000);
		autoLogin.tick();
		login.verify(LoginManager::login, never());
		assertFalse(autoLogin.keepsScriptsThroughLogout());
	}

	@Test
	public void aTerminalLogoutHoldsAutoLoginOffUntilAScriptStarts()
	{
		autoLogin.suppress();
		autoLogin.observe(GameState.LOGIN_SCREEN);
		assertFalse(autoLogin.keepsScriptsThroughLogout());
		autoLogin.tick();
		login.verify(LoginManager::login, never());

		autoLogin.resume();
		assertTrue(autoLogin.keepsScriptsThroughLogout());
		autoLogin.tick();
		login.verify(LoginManager::login, times(1));
	}

	@Test
	public void restsAfterRepeatedFailures()
	{
		for (int i = 0; i < MobileAutoLogin.MAX_ATTEMPTS_BEFORE_REST; i++)
		{
			autoLogin.tick();
			now.addAndGet(600_000);
		}
		login.verify(LoginManager::login, times(MobileAutoLogin.MAX_ATTEMPTS_BEFORE_REST));
		autoLogin.tick();
		now.addAndGet(MobileAutoLogin.REST_MILLIS / 2);
		autoLogin.tick();
		login.verify(LoginManager::login, times(MobileAutoLogin.MAX_ATTEMPTS_BEFORE_REST));
		now.addAndGet(MobileAutoLogin.REST_MILLIS);
		autoLogin.tick();
		login.verify(LoginManager::login, times(MobileAutoLogin.MAX_ATTEMPTS_BEFORE_REST + 1));
	}
}
