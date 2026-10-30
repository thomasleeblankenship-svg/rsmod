package net.runelite.client.plugins.microbot.breakhandler;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.headless.HeadlessLogin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The break handler, after upstream's {@code breakhandler/BreakHandlerScript} (chsami/Microbot
 * {@code a2114401}, BSD 2-Clause), with its static API and states ({@link BreakHandlerState}).
 * <p>
 * Two kinds of break. A <em>micro-break</em> starts when the anti-ban raises
 * {@code Rs2AntibanSettings.microBreakActive} (a script that enables micro-breaks); it pauses the
 * scripts in game for {@code microBreakDurationLow}..{@code High} minutes. A <em>scheduled
 * break</em>, when turned on in the plugin settings, comes after a random play time and lasts a random
 * break time, logging out first when configured. Play time counts only while logged in with a script
 * running. Neither starts while a script holds {@link #setLockState the lock}, nor before the player is
 * out of combat and not interacting (given up after {@link #SAFE_WAIT_MILLIS}).
 * <p>
 * A break pauses every script ({@code Microbot.pauseAllScripts}) and clears the walker's route; after a
 * logged-out break it logs back in to the world it left (a Play Now through {@link LoginManager}) and
 * then resumes them. Mobile adaptations: driven by {@link #tick} from the mobile plugin (no Swing
 * panel, overlay, window title, Discord notice or client shutdown); a stopped script ends the break.
 */
public final class BreakHandlerScript
{
	private static final Logger LOG = LoggerFactory.getLogger(BreakHandlerScript.class);
	private static final int MINUTES_TO_SECONDS = 60;
	static final long SAFE_WAIT_MILLIS = TimeUnit.MINUTES.toMillis(2);
	static final int MAX_LOGOUT_ATTEMPTS = 3;
	static final long LOGOUT_RETRY_MILLIS = 5_000;
	static final long LOGIN_RETRY_MILLIS = 15_000;

	/** The plugin's break settings, and whether a script is running. */
	public interface Settings
	{
		boolean scheduledBreaks();

		int playMinutesMin();

		int playMinutesMax();

		int breakMinutesMin();

		int breakMinutesMax();

		boolean logoutOnBreak();

		boolean onlyMicroBreaks();

		boolean scriptRunning();

		/** Upstream 2.6.47: the account was logged out on purpose (a terminal logout), so no break logs it back in. */
		default boolean loginHeld()
		{
			return false;
		}
	}

	/** Seconds until the next scheduled break; {@code -1} while none is scheduled. */
	public static volatile int breakIn = -1;
	/** Seconds left in the current break; {@code -1} outside a break. */
	public static volatile int breakDuration = -1;
	public static volatile Duration setBreakDurationTime = Duration.ZERO;
	public static volatile int totalBreaks;
	/** Upstream's lock: while set, no break starts. */
	public static AtomicBoolean lockState = new AtomicBoolean(false);

	private static final AtomicReference<BreakHandlerState> currentState = new AtomicReference<>(BreakHandlerState.WAITING_FOR_BREAK);
	private static Settings settings;
	private static long lastTick;
	private static long playMillis;
	private static long breakMillis;
	private static long stateSince;
	private static long lastAttemptAt;
	private static int attempts;
	private static int preBreakWorld = -1;
	private static boolean microBreak;

	private BreakHandlerScript()
	{
	}

	public static void setLockState(boolean state)
	{
		lockState.set(state);
	}

	public static boolean isLockState()
	{
		return lockState.get();
	}

	/** As upstream: any state but waiting for the next break. */
	public static boolean isBreakActive()
	{
		return currentState.get() != BreakHandlerState.WAITING_FOR_BREAK;
	}

	/** As upstream: a break taken in game, or a micro-break. */
	public static boolean isIngameBreakActive()
	{
		return currentState.get() == BreakHandlerState.INGAME_BREAK_ACTIVE || isMicroBreakActive();
	}

	public static boolean isMicroBreakActive()
	{
		return Rs2AntibanSettings.takeMicroBreaks && Rs2AntibanSettings.microBreakActive;
	}

	public static BreakHandlerState getCurrentState()
	{
		return currentState.get();
	}

	public static String formatDuration(Duration duration, String header)
	{
		return String.format(header + " %s", formatDuration(duration));
	}

	/** As upstream: {@code HH:MM:SS}. */
	public static String formatDuration(Duration duration)
	{
		if (duration == null || duration.isNegative() || duration.isZero())
		{
			return "00:00:00";
		}
		return String.format("%02d:%02d:%02d", duration.toHours(), duration.toMinutes() % 60, duration.getSeconds() % 60);
	}

	/** Starts (or restarts) the handler with the plugin's settings. */
	public static synchronized void start(Settings breakSettings, long now)
	{
		settings = breakSettings;
		resetBreak(now);
		scheduleNext();
	}

	/** Ends any break, resuming scripts; logs nothing back in. */
	public static synchronized void stop()
	{
		if (isBreakActive() || microBreak)
		{
			resume();
		}
		resetBreak(0);
		settings = null;
		breakIn = -1;
	}

	/** The settings changed: a new play time when scheduled breaks were turned on or off. */
	public static synchronized void settingsChanged()
	{
		if (settings != null && currentState.get() == BreakHandlerState.WAITING_FOR_BREAK)
		{
			scheduleNext();
		}
	}

	/** One step, about every 600 ms, off the client thread. */
	public static synchronized void tick(long now)
	{
		final Settings config = settings;
		if (config == null)
		{
			return;
		}
		final long elapsed = lastTick == 0 ? 0 : Math.max(0, now - lastTick);
		lastTick = now;
		final BreakHandlerState state = currentState.get();
		if (state != BreakHandlerState.WAITING_FOR_BREAK && !config.scriptRunning())
		{
			LOG.info("Break ended: no script is running");
			resume();
			resetBreak(now);
			scheduleNext();
			return;
		}
		switch (state)
		{
			case WAITING_FOR_BREAK:
				waiting(config, elapsed, now);
				break;
			case BREAK_REQUESTED:
				requested(now);
				break;
			case INITIATING_BREAK:
				initiating(config, now);
				break;
			case LOGOUT_REQUESTED:
				countDown(elapsed);
				logoutRequested(now);
				break;
			case LOGGED_OUT:
				countDown(elapsed);
				if (breakDuration <= 0)
				{
					transition(BreakHandlerState.LOGIN_REQUESTED, now);
				}
				break;
			case INGAME_BREAK_ACTIVE:
				countDown(elapsed);
				if (breakDuration <= 0)
				{
					transition(BreakHandlerState.BREAK_ENDING, now);
				}
				break;
			case LOGIN_REQUESTED:
			case LOGGING_IN:
			case LOGIN_EXTENDED_SLEEP:
				loginRequested(now);
				break;
			case BREAK_ENDING:
				LOG.info("Break over after {} breaks so far", totalBreaks);
				totalBreaks++;
				resume();
				resetBreak(now);
				scheduleNext();
				break;
			default:
				break;
		}
	}

	private static void waiting(Settings config, long elapsed, long now)
	{
		final boolean scheduled = config.scheduledBreaks() && !config.onlyMicroBreaks();
		if (scheduled && config.scriptRunning() && Microbot.isLoggedIn() && !Microbot.pauseAllScripts.get())
		{
			playMillis += elapsed;
			while (playMillis >= 1_000)
			{
				playMillis -= 1_000;
				if (breakIn > 0)
				{
					breakIn--;
				}
			}
		}
		final boolean due = scheduled && breakIn == 0 && config.scriptRunning();
		final boolean micro = Rs2AntibanSettings.microBreakActive && config.scriptRunning();
		if ((due || micro) && !isLockState())
		{
			microBreak = micro;
			LOG.info("Break requested (micro: {})", micro);
			transition(BreakHandlerState.BREAK_REQUESTED, now);
		}
		else if (Rs2AntibanSettings.microBreakActive && !config.scriptRunning())
		{
			Rs2AntibanSettings.microBreakActive = false;
		}
	}

	private static void requested(long now)
	{
		if (!Rs2Player.isInCombat() && !Rs2Player.isInteracting())
		{
			transition(BreakHandlerState.INITIATING_BREAK, now);
		}
		else if (now - stateSince > SAFE_WAIT_MILLIS)
		{
			LOG.warn("Not safe to break for {} s; skipping this break", SAFE_WAIT_MILLIS / 1_000);
			Rs2AntibanSettings.microBreakActive = false;
			resetBreak(now);
			scheduleNext();
		}
	}

	private static void initiating(Settings config, long now)
	{
		Microbot.pauseAllScripts.set(true);
		Rs2Walker.clearWalkingRoute("break-handler:initiating-break");
		preBreakWorld = Microbot.isLoggedIn() ? Microbot.getClient().getWorld() : -1;
		if (microBreak)
		{
			breakDuration = Rs2Random.between(Rs2AntibanSettings.microBreakDurationLow * MINUTES_TO_SECONDS,
				Rs2AntibanSettings.microBreakDurationHigh * MINUTES_TO_SECONDS + 1);
		}
		else
		{
			breakDuration = randomSeconds(config.breakMinutesMin(), config.breakMinutesMax());
		}
		setBreakDurationTime = Duration.ofSeconds(breakDuration);
		breakMillis = 0;
		LOG.info("{} for {}", microBreak ? "Micro break" : "Break", formatDuration(setBreakDurationTime));
		transition(!microBreak && config.logoutOnBreak() ? BreakHandlerState.LOGOUT_REQUESTED : BreakHandlerState.INGAME_BREAK_ACTIVE, now);
	}

	private static void logoutRequested(long now)
	{
		if (!Microbot.isLoggedIn())
		{
			transition(BreakHandlerState.LOGGED_OUT, now);
			return;
		}
		if (attempts >= MAX_LOGOUT_ATTEMPTS)
		{
			LOG.warn("Could not log out; taking the break in game");
			transition(BreakHandlerState.INGAME_BREAK_ACTIVE, now);
			return;
		}
		if (attempts == 0 || now - lastAttemptAt >= LOGOUT_RETRY_MILLIS)
		{
			attempts++;
			lastAttemptAt = now;
			Rs2Player.logout();
		}
	}

	private static void loginRequested(long now)
	{
		if (Microbot.isLoggedIn())
		{
			transition(BreakHandlerState.BREAK_ENDING, now);
			return;
		}
		final Settings config = settings;
		if (config != null && config.loginHeld())
		{
			LOG.info("Break over; not logging back in: the account was logged out on purpose");
			resume();
			resetBreak(now);
			scheduleNext();
			return;
		}
		if (HeadlessLogin.isLoginInProgress() && !HeadlessLogin.isLoginGivenUp())
		{
			return;
		}
		if (attempts == 0 || now - lastAttemptAt >= LOGIN_RETRY_MILLIS)
		{
			attempts++;
			lastAttemptAt = now;
			currentState.set(BreakHandlerState.LOGGING_IN);
			LOG.info("Logging back in after the break (attempt {})", attempts);
			if (preBreakWorld > 0)
			{
				LoginManager.login(preBreakWorld);
			}
			else
			{
				LoginManager.login();
			}
		}
	}

	private static void countDown(long elapsed)
	{
		breakMillis += elapsed;
		while (breakMillis >= 1_000)
		{
			breakMillis -= 1_000;
			if (breakDuration > 0)
			{
				breakDuration--;
			}
		}
	}

	private static void transition(BreakHandlerState next, long now)
	{
		if (currentState.getAndSet(next) != next)
		{
			stateSince = now;
			attempts = 0;
			lastAttemptAt = 0;
		}
	}

	private static void resume()
	{
		Rs2AntibanSettings.microBreakActive = false;
		Microbot.pauseAllScripts.set(false);
	}

	private static void resetBreak(long now)
	{
		currentState.set(BreakHandlerState.WAITING_FOR_BREAK);
		stateSince = now;
		lastTick = now;
		attempts = 0;
		lastAttemptAt = 0;
		breakMillis = 0;
		breakDuration = -1;
		setBreakDurationTime = Duration.ZERO;
		preBreakWorld = -1;
		microBreak = false;
	}

	private static void scheduleNext()
	{
		final Settings config = settings;
		playMillis = 0;
		if (config == null || !config.scheduledBreaks() || config.onlyMicroBreaks())
		{
			breakIn = -1;
			return;
		}
		breakIn = randomSeconds(config.playMinutesMin(), config.playMinutesMax());
	}

	/** Uniform seconds between two minute bounds (either order, at least a minute). */
	private static int randomSeconds(int minutesA, int minutesB)
	{
		int low = Math.max(1, Math.min(minutesA, minutesB));
		int high = Math.max(low, Math.max(minutesA, minutesB));
		return Rs2Random.between(low * MINUTES_TO_SECONDS, high * MINUTES_TO_SECONDS + 1);
	}

	/** Tests: back to the initial, stopped state. */
	static synchronized void resetForTest()
	{
		resetBreak(0);
		settings = null;
		breakIn = -1;
		totalBreaks = 0;
		lockState.set(false);
	}
}
