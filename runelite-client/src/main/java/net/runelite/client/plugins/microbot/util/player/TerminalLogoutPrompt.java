/*
 * Adapted from chsami/Microbot (2.6.38),
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/player/TerminalLogoutPrompt.java
 * (Copyright (c) 2026, DRO (droplugins)). BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.player;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * As upstream: a terminal logout kept separate from its notification. A script calls
 * {@link #begin(String)} with the reason (for example, "Ran out of supplies: Shark"), then
 * {@link #tick()} from its loop; the logout is attempted at most every 600 ms, and once the login
 * screen is reached the reason is shown once and the prompt resets.
 * <p>
 * Mobile adaptation: upstream also posts auto-login suppression requests; mobile has no
 * auto-login, so there is nothing to suppress.
 */
public final class TerminalLogoutPrompt
{
	static final long RETRY_MILLIS = 600L;

	private final LongSupplier clock;
	private String message;
	private long stop;
	private boolean attempted;
	private long lastAttemptAt;

	public TerminalLogoutPrompt()
	{
		this(() -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
	}

	TerminalLogoutPrompt(LongSupplier clock)
	{
		this.clock = clock;
	}

	/** Starts a logout with this reason; ignored while one is already active. */
	public synchronized void begin(String text)
	{
		if (message == null)
		{
			message = text;
			startNewStop();
		}
	}

	public synchronized boolean active()
	{
		return message != null;
	}

	public synchronized String message()
	{
		return message;
	}

	/** Abandons the active logout. */
	public synchronized void reset()
	{
		message = null;
		startNewStop();
	}

	/** One throttled logout step; true once logged out (the reason is then shown). */
	public boolean tick()
	{
		return throttledTick(Rs2Player::logoutWithoutAutoLoginStep, Microbot::showMessage);
	}

	/** As {@link #tick()} with the given logout step and notifier, unthrottled (upstream's test seam). */
	public boolean tick(Supplier<Rs2Player.TerminalLogoutState> logout, Consumer<String> notify)
	{
		return step(logout, notify);
	}

	boolean throttledTick(Supplier<Rs2Player.TerminalLogoutState> logout, Consumer<String> notify)
	{
		synchronized (this)
		{
			if (message == null)
			{
				return false;
			}
			long now = clock.getAsLong();
			if (attempted && now - lastAttemptAt < RETRY_MILLIS)
			{
				return false;
			}
			attempted = true;
			lastAttemptAt = now;
		}
		return step(logout, notify);
	}

	private boolean step(Supplier<Rs2Player.TerminalLogoutState> logout, Consumer<String> notify)
	{
		long tickStop;
		synchronized (this)
		{
			if (message == null)
			{
				return false;
			}
			tickStop = stop;
		}
		if (logout.get() != Rs2Player.TerminalLogoutState.LOGGED_OUT)
		{
			return false;
		}
		String completed;
		synchronized (this)
		{
			if (stop != tickStop || message == null)
			{
				return false;
			}
			completed = message;
			message = null;
			startNewStop();
		}
		notify.accept(completed);
		return true;
	}

	private void startNewStop()
	{
		stop++;
		attempted = false;
	}
}
