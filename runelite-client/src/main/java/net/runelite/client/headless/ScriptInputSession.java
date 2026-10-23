package net.runelite.client.headless;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.client.plugins.microbot.MicrobotRuntime;

/**
 * Permission for one explicitly started script to submit input. Cancellation
 * and native user takeover invalidate it immediately, including queued EDT work.
 * A fresh instance requires an explicit script start; it never renews itself.
 */
public final class ScriptInputSession implements AutoCloseable
{
	private static final AtomicLong MANUAL_GENERATION = new AtomicLong();
	private static final AtomicBoolean SUSPENDED = new AtomicBoolean();
	private final long manualGeneration;
	private final AtomicBoolean open = new AtomicBoolean(true);

	public ScriptInputSession()
	{
		manualGeneration = MANUAL_GENERATION.get();
		if (SUSPENDED.get())
		{
			open.set(false);
		}
	}

	private ScriptInputSession(long generation)
	{
		manualGeneration = generation;
	}

	/** A script swap cancels the old token without erasing a concurrent user takeover. */
	public ScriptInputSession successor()
	{
		ScriptInputSession next = new ScriptInputSession(manualGeneration);
		if (!isActive())
		{
			next.close();
		}
		return next;
	}

	public boolean isActive()
	{
		return open.get() && !SUSPENDED.get() && manualGeneration == MANUAL_GENERATION.get();
	}

	@Override
	public void close()
	{
		open.set(false);
	}

	static void manualInput()
	{
		MANUAL_GENERATION.incrementAndGet();
		MicrobotRuntime.noteHumanInput();
	}

	static void setSuspended(boolean suspended)
	{
		if (SUSPENDED.getAndSet(suspended) != suspended && suspended)
		{
			manualInput();
		}
	}
}
