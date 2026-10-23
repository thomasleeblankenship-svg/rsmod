package net.runelite.client.plugins.microbot.util.walker;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import net.runelite.api.GameState;

/** Waits only for a known scene-loading transition, bounded by the caller's overall walking deadline. */
final class LoadingSnapshotWaiter
{
	static final class Observation<T>
	{
		final GameState state;
		final T snapshot;

		Observation(GameState state, T snapshot)
		{
			this.state = state;
			this.snapshot = snapshot;
		}
	}

	interface Probe<T>
	{
		Observation<T> observe(long remainingMillis);
	}

	interface Sleeper
	{
		void sleep(long millis) throws InterruptedException;
	}

	private LoadingSnapshotWaiter()
	{
	}

	static <T> T await(Probe<T> probe, BooleanSupplier active, LongSupplier clock, Sleeper sleeper,
		long walkDeadline) throws InterruptedException
	{
		long deadline = Math.min(walkDeadline, clock.getAsLong() + 10_000);
		while (active.getAsBoolean() && !Thread.currentThread().isInterrupted())
		{
			long remaining = deadline - clock.getAsLong();
			if (remaining <= 0)
			{
				return null;
			}
			Observation<T> observation = probe.observe(remaining);
			if (clock.getAsLong() >= deadline || !active.getAsBoolean() || Thread.currentThread().isInterrupted())
			{
				return null;
			}
			if (observation == null)
			{
				return null;
			}
			if (observation.state == GameState.LOGGED_IN && observation.snapshot != null)
			{
				return observation.snapshot;
			}
			if (observation.state != GameState.LOADING)
			{
				return null;
			}
			remaining = deadline - clock.getAsLong();
			if (remaining > 0)
			{
				sleeper.sleep(Math.min(300, remaining));
			}
		}
		return null;
	}
}
