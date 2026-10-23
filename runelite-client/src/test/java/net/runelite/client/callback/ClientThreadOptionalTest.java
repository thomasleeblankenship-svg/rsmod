package net.runelite.client.callback;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ClientThread#runOnClientThreadOptional}: inline on the client thread, bounded wait off it,
 * and no late execution of a task whose caller gave up. The "client thread" is a test thread that
 * drains the queue on demand; the {@link Client} is a Mockito double.
 */
public class ClientThreadOptionalTest
{
	private static final long WAIT_SECONDS = 5;

	private final AtomicReference<Thread> gameThread = new AtomicReference<>();
	private ClientThread clientThread;

	@Before
	public void setUp() throws Exception
	{
		Client client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(i -> Thread.currentThread() == gameThread.get());
		clientThread = new ClientThread();
		Field field = ClientThread.class.getDeclaredField("client");
		field.setAccessible(true);
		field.set(clientThread, client);
	}

	private void drainOnGameThread() throws InterruptedException
	{
		Thread t = new Thread(() ->
		{
			gameThread.set(Thread.currentThread());
			clientThread.invoke();
		});
		t.start();
		t.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
	}

	@Test
	public void runsInlineOnClientThread() throws Exception
	{
		AtomicReference<Optional<Integer>> result = new AtomicReference<>();
		Thread t = new Thread(() ->
		{
			gameThread.set(Thread.currentThread());
			result.set(clientThread.runOnClientThreadOptional(() -> 42));
		});
		t.start();
		t.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
		assertEquals(Optional.of(42), result.get());
	}

	@Test
	public void offThreadCallerWaitsForClientThreadResult() throws Exception
	{
		AtomicReference<Optional<String>> result = new AtomicReference<>();
		CountDownLatch done = new CountDownLatch(1);
		Thread caller = new Thread(() ->
		{
			result.set(clientThread.runOnClientThreadOptional(() -> Thread.currentThread().getName(), TimeUnit.SECONDS.toMillis(WAIT_SECONDS)));
			done.countDown();
		}, "script");
		caller.start();

		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
		while (done.getCount() > 0 && System.nanoTime() < deadline)
		{
			drainOnGameThread();
		}
		assertTrue(done.await(0, TimeUnit.SECONDS));
		assertTrue(result.get().isPresent());
		assertFalse("callable must run on the client thread", "script".equals(result.get().get()));
	}

	@Test
	public void timedOutTaskNeverRunsLater() throws Exception
	{
		AtomicInteger runs = new AtomicInteger();
		Optional<Integer> result = clientThread.runOnClientThreadOptional(runs::incrementAndGet, 20);
		assertFalse(result.isPresent());

		drainOnGameThread();
		assertEquals("a cancelled task must not execute with stale intent", 0, runs.get());
	}

	@Test
	public void interruptedCallerGetsEmptyKeepsFlagAndTaskNeverRuns() throws Exception
	{
		AtomicInteger runs = new AtomicInteger();
		AtomicBoolean flagKept = new AtomicBoolean();
		AtomicReference<Optional<Integer>> result = new AtomicReference<>();
		Thread caller = new Thread(() ->
		{
			Thread.currentThread().interrupt();
			result.set(clientThread.runOnClientThreadOptional(runs::incrementAndGet, TimeUnit.SECONDS.toMillis(60)));
			flagKept.set(Thread.currentThread().isInterrupted());
		});
		caller.start();
		caller.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));

		assertFalse(caller.isAlive());
		assertFalse(result.get().isPresent());
		assertTrue(flagKept.get());
		drainOnGameThread();
		assertEquals(0, runs.get());
	}

	@Test
	public void exceptionYieldsEmpty() throws Exception
	{
		AtomicReference<Optional<Object>> result = new AtomicReference<>();
		Thread t = new Thread(() ->
		{
			gameThread.set(Thread.currentThread());
			result.set(clientThread.runOnClientThreadOptional(() ->
			{
				throw new IllegalStateException("boom");
			}));
		});
		t.start();
		t.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
		assertFalse(result.get().isPresent());
	}
}
