package net.runelite.client.plugins.microbot.breakhandler;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The break handler's shared lock state, as scripts see it.
 * <p>
 * Upstream (chsami/Microbot {@code breakhandler/BreakHandlerScript.java} at
 * {@code a2114401bbbc35e9762db9e8aa666d70ffcd24d8}) is a plugin that logs out or idles on a schedule
 * to imitate human play; scripts raise {@link #setLockState(boolean) the lock} around steps that
 * must not be interrupted. Mobile does not port the scheduled breaks (they are an anti-detection
 * feature), so no break is ever active: {@link #isBreakActive()} is always {@code false} and
 * {@link #breakIn} stays {@code -1}. The lock itself is kept so scripts that set and read it
 * behave as upstream.
 */
public final class BreakHandlerScript
{
	/** Seconds until the next break; {@code -1} because mobile schedules none. */
	public static volatile int breakIn = -1;
	/** Duration of the current break; {@code -1} because mobile takes none. */
	public static volatile int breakDuration = -1;
	public static volatile int totalBreaks = 0;
	/** Upstream's lock: while set, upstream never starts a break. */
	public static AtomicBoolean lockState = new AtomicBoolean(false);

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

	/** Always {@code false}: mobile takes no scheduled breaks. */
	public static boolean isBreakActive()
	{
		return false;
	}

	/** Always {@code false}: mobile takes no scheduled breaks. */
	public static boolean isIngameBreakActive()
	{
		return false;
	}

	/** Always {@code false}: mobile takes no scheduled breaks. */
	public static boolean isMicroBreakActive()
	{
		return false;
	}
}
