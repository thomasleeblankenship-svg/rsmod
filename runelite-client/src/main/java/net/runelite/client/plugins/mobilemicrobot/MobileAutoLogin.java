package net.runelite.client.plugins.mobilemicrobot;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.headless.HeadlessLogin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs the Jagex account back in whenever the client sits on the login screen, as upstream's
 * {@code accountselector/AutoLoginScript} (chsami/Microbot {@code a2114401}): retries back off 5, 15, 60
 * and 300 s with jitter, a too-many-attempts lockout pauses it, a banned account stops it, and a
 * break-handler break or a script's terminal logout ({@code AutoLoginSuppressionRequest}) holds it off.
 * <p>
 * Runs on its own thread every 600 ms, because the client does not tick on the login screen. A
 * login is a Play Now through {@link LoginManager}; {@link HeadlessLogin} recovers a disconnect by
 * itself, so this only steps in once that machine is idle or has given up.
 */
final class MobileAutoLogin
{
	private static final Logger LOG = LoggerFactory.getLogger(MobileAutoLogin.class);
	/** Upstream: the login screen's "account disabled" sub-state. */
	static final int BANNED_LOGIN_INDEX = 14;
	static final int[] BACKOFF_SEQUENCE_SECONDS = {5, 15, 60, 300};
	/** After this many attempts in a row, rest as upstream's extended sleep before trying again. */
	static final int MAX_ATTEMPTS_BEFORE_REST = 8;
	static final long REST_MILLIS = TimeUnit.MINUTES.toMillis(15);

	private final BooleanSupplier enabled;
	private final IntSupplier world;
	private final IntSupplier loginIndex;
	private final LongSupplier clock;
	private final net.runelite.client.plugins.microbot.accountselector.AutoLoginSuppression suppression =
		new net.runelite.client.plugins.microbot.accountselector.AutoLoginSuppression();
	private ScheduledFuture<?> future;
	private int attempts;
	private long nextAttemptAt;
	private long restUntil;
	private volatile boolean banned;
	private volatile String status = "";

	MobileAutoLogin(BooleanSupplier enabled, IntSupplier world, IntSupplier loginIndex, LongSupplier clock)
	{
		this.enabled = enabled;
		this.world = world;
		this.loginIndex = loginIndex;
		this.clock = clock;
	}

	/** As upstream: the base step for {@code attemptsSoFar}, never below {@code configuredBaseSeconds}, with ±30 % jitter. */
	static int computeRetryBackoffSeconds(int attemptsSoFar, int configuredBaseSeconds)
	{
		int idx = Math.min(Math.max(attemptsSoFar - 1, 0), BACKOFF_SEQUENCE_SECONDS.length - 1);
		int scheduleStep = BACKOFF_SEQUENCE_SECONDS[idx];
		int floor = Math.max(1, configuredBaseSeconds);
		int step = Math.max(scheduleStep, floor);
		double jitter = 0.7 + ThreadLocalRandom.current().nextDouble() * 0.6;
		long jittered = Math.round(step * jitter);
		return (int) Math.max(floor, jittered);
	}

	synchronized void start(ScheduledExecutorService executor)
	{
		stop();
		future = executor.scheduleWithFixedDelay(this::safeTick, 600, 600, TimeUnit.MILLISECONDS);
	}

	synchronized void stop()
	{
		if (future != null)
		{
			future.cancel(true);
			future = null;
		}
		reset();
	}

	/** A script's terminal logout: do not log back in until a script starts again or the user logs in. */
	void suppress()
	{
		suppression.request();
	}

	void renewSuppression()
	{
		suppression.renew();
	}

	void holdLoggedOut()
	{
		suppression.holdLoggedOut(LoginManager.getGameState());
	}

	/** A script start opts back in (upstream: {@code ScriptStarted}); also clears a ban latch the user has dealt with. */
	void resume()
	{
		suppression.clear();
		banned = false;
	}

	void observe(GameState state)
	{
		suppression.observe(state);
		if (state == GameState.LOGGED_IN)
		{
			reset();
		}
	}

	/** True when a logout should only pause the running script: auto-login is on and will sign back in. */
	boolean keepsScriptsThroughLogout()
	{
		return enabled.getAsBoolean() && !banned && !suppression.isSuppressed();
	}

	/** A terminal logout or a disabled account: nothing should log the account back in. */
	boolean loginHeld()
	{
		return banned || suppression.isSuppressed();
	}

	String status()
	{
		return status;
	}

	private void safeTick()
	{
		try
		{
			tick();
		}
		catch (RuntimeException exception)
		{
			LOG.warn("Auto-login check failed", exception);
		}
	}

	synchronized void tick()
	{
		if (!enabled.getAsBoolean() || banned)
		{
			reset();
			return;
		}
		if (suppression.isSuppressed() || BreakHandlerScript.isBreakActive())
		{
			reset();
			return;
		}
		GameState state = LoginManager.getGameState();
		if (state != GameState.LOGIN_SCREEN)
		{
			if (state == GameState.LOGGED_IN)
			{
				reset();
			}
			return;
		}
		if (loginIndex.getAsInt() == BANNED_LOGIN_INDEX)
		{
			banned = true;
			status = "Auto-login stopped: the account is disabled";
			LOG.warn(status);
			return;
		}
		if (LoginManager.checkLoginLockout())
		{
			status = "Auto-login waiting: too many login attempts";
			return;
		}
		long now = clock.getAsLong();
		if (now < restUntil || now < nextAttemptAt)
		{
			return;
		}
		// HeadlessLogin is still driving a login (a disconnect's Try again, or our last Play Now).
		if (HeadlessLogin.isLoginInProgress() && !HeadlessLogin.isLoginGivenUp())
		{
			return;
		}
		if (attempts >= MAX_ATTEMPTS_BEFORE_REST)
		{
			attempts = 0;
			restUntil = now + REST_MILLIS;
			status = "Auto-login resting after " + MAX_ATTEMPTS_BEFORE_REST + " failed attempts";
			LOG.warn(status);
			return;
		}
		applyWorld();
		attempts++;
		boolean started = LoginManager.login();
		nextAttemptAt = now + TimeUnit.SECONDS.toMillis(computeRetryBackoffSeconds(attempts, 5));
		status = started ? "Auto-login: logging in (attempt " + attempts + ")" : "Auto-login: login not started";
		LOG.info(status);
	}

	/** The configured world becomes the profile's selected world, which {@link LoginManager#login()} uses. */
	private void applyWorld()
	{
		ConfigProfile profile = LoginManager.getActiveProfile();
		if (profile != null)
		{
			int selected = world.getAsInt();
			profile.setSelectedWorld(selected == 0 ? null : selected);
		}
	}

	private void reset()
	{
		attempts = 0;
		nextAttemptAt = 0;
		restUntil = 0;
		status = "";
	}
}
