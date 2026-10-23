package net.runelite.client.plugins.microbot.util.walker;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.GameState;
import org.junit.Test;
import static org.junit.Assert.*;

/** Deterministic fake clock exercises the production wait policy without sleeping or a live client. */
public class LoadingSnapshotWaiterTest
{
	@Test
	public void waitsThroughLoadingThenReturnsObservedSnapshot() throws Exception
	{
		AtomicLong clock = new AtomicLong();
		AtomicInteger attempts = new AtomicInteger();
		String result = LoadingSnapshotWaiter.await(remaining -> attempts.incrementAndGet() < 3
			? new LoadingSnapshotWaiter.Observation<>(GameState.LOADING, null)
			: new LoadingSnapshotWaiter.Observation<>(GameState.LOGGED_IN, "new scene"),
			() -> true, clock::get, millis -> clock.addAndGet(millis), 20_000);
		assertEquals("new scene", result);
		assertEquals(600, clock.get());
		assertEquals(3, attempts.get());
	}

	@Test
	public void logoutAndMissingLoggedInCollisionAreNotRetried() throws Exception
	{
		for (GameState state : new GameState[]{GameState.LOGIN_SCREEN, GameState.HOPPING,
			GameState.CONNECTION_LOST, GameState.LOGGED_IN})
		{
			AtomicInteger attempts = new AtomicInteger();
			assertNull(LoadingSnapshotWaiter.await(remaining ->
			{
				attempts.incrementAndGet();
				return new LoadingSnapshotWaiter.Observation<>(state, null);
			}, () -> true, () -> 0, millis -> fail("Must not sleep on " + state), 20_000));
			assertEquals(1, attempts.get());
		}
	}

	@Test
	public void cancellationDuringLoadingPreventsAnotherProbe() throws Exception
	{
		AtomicLong clock = new AtomicLong();
		AtomicBoolean active = new AtomicBoolean(true);
		AtomicInteger attempts = new AtomicInteger();
		assertNull(LoadingSnapshotWaiter.await(remaining ->
		{
			attempts.incrementAndGet();
			return new LoadingSnapshotWaiter.Observation<>(GameState.LOADING, null);
		}, active::get, clock::get, millis ->
		{
			clock.addAndGet(millis);
			active.set(false);
		}, 20_000));
		assertEquals(1, attempts.get());
	}

	@Test
	public void loadingCannotExceedTenSecondsOrRemainingWalkBudget() throws Exception
	{
		for (long walkDeadline : new long[]{800, 20_000})
		{
			AtomicLong clock = new AtomicLong();
			assertNull(LoadingSnapshotWaiter.await(remaining ->
				new LoadingSnapshotWaiter.Observation<>(GameState.LOADING, null), () -> true,
				clock::get, millis -> clock.addAndGet(millis), walkDeadline));
			assertEquals(Math.min(10_000, walkDeadline), clock.get());
		}
	}

	@Test
	public void lateOrCancelledProbeCannotReturnSnapshot() throws Exception
	{
		AtomicLong clock = new AtomicLong();
		assertNull(LoadingSnapshotWaiter.await(remaining ->
		{
			assertEquals(700, remaining);
			clock.addAndGet(remaining);
			return new LoadingSnapshotWaiter.Observation<>(GameState.LOGGED_IN, "too late");
		}, () -> true, clock::get, millis -> fail("Must not sleep"), 700));
		AtomicBoolean active = new AtomicBoolean(true);
		assertNull(LoadingSnapshotWaiter.await(remaining ->
		{
			active.set(false);
			return new LoadingSnapshotWaiter.Observation<>(GameState.LOGGED_IN, "cancelled");
		}, active::get, () -> 0, millis -> fail("Must not sleep"), 700));
	}
}
