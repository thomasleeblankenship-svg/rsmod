package net.runelite.client.plugins.microbot;

import net.runelite.client.plugins.microbot.util.Global;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/** Waits in the adapted {@link Global}: never block the client thread, honour interrupts. */
public class GlobalWaitTest {
    private static final long WAIT_SECONDS = 5;

    @After
    public void tearDown() {
        RuntimeFixture.reset();
    }

    @Test
    public void waitsReturnImmediatelyOnClientThread() {
        RuntimeFixture fx = new RuntimeFixture().installLoggedIn();
        when(fx.client.isClientThread()).thenReturn(true);
        AtomicInteger polls = new AtomicInteger();

        long before = System.nanoTime();
        assertFalse(Global.sleepUntil(() -> polls.incrementAndGet() > 1_000_000, 60_000));
        assertFalse(Global.sleepUntilTrue(() -> false, 10, 60_000));
        assertNull(Global.sleepUntilNotNull(() -> null, 60_000));
        assertFalse(Global.sleepUntilNextTick());
        assertFalse(Global.sleepTicks(100));
        Global.sleep(60_000);
        Global.sleepUntilOnClientThread(() -> false, 60_000);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);

        assertEquals("the condition must not even be polled on the client thread", 0, polls.get());
        assertTrue("client-thread waits took " + elapsedMillis + "ms", elapsedMillis < 1_000);
    }

    @Test
    public void waitsReturnImmediatelyWithoutRuntime() {
        long before = System.nanoTime();
        assertFalse(Global.sleepUntil(() -> false, 60_000));
        Global.sleep(60_000);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before) < 1_000);
    }

    @Test
    public void randomSleepsAcceptUpstreamArgumentShapes() {
        new RuntimeFixture().installLoggedIn();
        long before = System.nanoTime();
        Global.sleep(-5, -3);       // upstream Rs2Random.between tolerates any order and sign
        Global.sleep(20, 10);       // reversed bounds
        Global.sleep(7, 7);         // empty range
        Global.sleepGaussian(-1_000, 10); // floored at zero
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before) < 1_000);
    }

    @Test
    public void sleepUntilReturnsTrueWhenConditionHolds() {
        new RuntimeFixture().installLoggedIn();
        AtomicInteger polls = new AtomicInteger();
        assertTrue(Global.sleepUntil(() -> polls.incrementAndGet() >= 3, 60_000));
        assertEquals(3, polls.get());
    }

    @Test
    public void humanTakeoverEndsWaitEarly() {
        new RuntimeFixture().installLoggedIn();
        MicrobotRuntime.noteHumanInput();
        AtomicInteger polls = new AtomicInteger();
        assertFalse(Global.sleepUntil(() -> polls.incrementAndGet() > 1_000_000, 60_000));
        assertEquals(0, polls.get());
    }

    @Test
    public void interruptEndsWaitAndKeepsFlag() throws Exception {
        new RuntimeFixture().installLoggedIn();
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean result = new AtomicBoolean(true);
        AtomicBoolean flagKept = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            result.set(Global.sleepUntil(() -> {
                started.countDown();
                return false;
            }, 60_000));
            flagKept.set(Thread.currentThread().isInterrupted());
        });
        waiter.setDaemon(true);
        waiter.start();
        assertTrue(started.await(WAIT_SECONDS, TimeUnit.SECONDS));
        waiter.interrupt();
        waiter.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));

        assertFalse(waiter.isAlive());
        assertFalse(result.get());
        assertTrue("InterruptedException must not be swallowed", flagKept.get());
    }

    @Test
    public void sleepUntilNextTickReleasesOnGameTick() throws Exception {
        RuntimeFixture fx = new RuntimeFixture().installLoggedIn();
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicBoolean ticked = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            waiting.countDown();
            ticked.set(Global.sleepUntilNextTick(TimeUnit.SECONDS.toMillis(WAIT_SECONDS)));
        });
        waiter.setDaemon(true);
        waiter.start();
        assertTrue(waiting.await(WAIT_SECONDS, TimeUnit.SECONDS));
        // The waiter may not have reached the latch yet; keep ticking until it returns.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (waiter.isAlive() && System.nanoTime() < deadline) {
            fx.tick();
            waiter.join(10);
        }
        assertFalse(waiter.isAlive());
        assertTrue(ticked.get());
    }
}
