package net.runelite.client.headless;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.client.plugins.microbot.MicrobotRuntime;

/**
 * Permission for one explicitly started script to submit input.
 * <p>
 * The session lives until an explicit close (Stop, script swap, plugin disable, logout or an
 * input fault) or until the app is backgrounded. A fresh instance requires an explicit script
 * start; it never renews itself.
 * <p>
 * Real user input does <em>not</em> end the session. It bumps the input epoch, which
 * invalidates any generated tap or key queued before it (see {@link #allows(long)}), and it
 * notifies {@link MicrobotRuntime#noteHumanInput()}, so scripts idle until the human-input
 * window passes and then continue.
 */
public final class ScriptInputSession implements AutoCloseable
{
	private static final AtomicLong INPUT_EPOCH = new AtomicLong();
	private static final AtomicLong SUSPEND_GENERATION = new AtomicLong();
	private static final AtomicBoolean SUSPENDED = new AtomicBoolean();
	private final long suspendGeneration;
	private final AtomicBoolean open = new AtomicBoolean(true);

	public ScriptInputSession()
	{
		suspendGeneration = SUSPEND_GENERATION.get();
		if (SUSPENDED.get())
		{
			open.set(false);
		}
	}

	private ScriptInputSession(long suspendGeneration)
	{
		this.suspendGeneration = suspendGeneration;
	}

	/** A script swap cancels the old token without erasing a concurrent backgrounding. */
	public ScriptInputSession successor()
	{
		ScriptInputSession next = new ScriptInputSession(suspendGeneration);
		if (!isActive())
		{
			next.close();
		}
		return next;
	}

	/** True until closed or until the app is backgrounded; manual input does not end it. */
	public boolean isActive()
	{
		return open.get() && !SUSPENDED.get() && suspendGeneration == SUSPEND_GENERATION.get();
	}

	/**
	 * The current input epoch. Capture it when a generated input is queued, and deliver that input
	 * only while {@link #allows(long)} still holds for the captured value.
	 */
	public static long inputEpoch()
	{
		return INPUT_EPOCH.get();
	}

	/** True when this session is active and no real input has arrived since {@code epoch}. */
	public boolean allows(long epoch)
	{
		return isActive() && epoch == INPUT_EPOCH.get();
	}

	@Override
	public void close()
	{
		open.set(false);
	}

	/** Real user input: drop queued generated input and let scripts yield, without stopping them. */
	static void manualInput()
	{
		INPUT_EPOCH.incrementAndGet();
		MicrobotRuntime.noteHumanInput();
	}

	/** Backgrounding ends every existing session; resuming never renews them. */
	static void setSuspended(boolean suspended)
	{
		if (SUSPENDED.getAndSet(suspended) != suspended && suspended)
		{
			SUSPEND_GENERATION.incrementAndGet();
			manualInput();
		}
	}
}
