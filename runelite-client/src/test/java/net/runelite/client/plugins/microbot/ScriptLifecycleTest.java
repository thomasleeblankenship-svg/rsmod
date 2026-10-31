package net.runelite.client.plugins.microbot;

import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptHeartbeatRegistry;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Script scheduling, guard, cancellation, heartbeat and cleanup. Uses real executor threads with
 * latches (no timing assumptions beyond generous upper bounds) and the mocked runtime fixture.
 */
public class ScriptLifecycleTest {
    private static final long WAIT_SECONDS = 5;

    @After
    public void tearDown() {
        RuntimeFixture.reset();
    }

    @Test
    public void guardRefusesWithoutRuntime() {
        assertFalse(new TestScript().run());
    }

    @Test
    public void guardPassesWhenInstalledAndIdle() {
        new RuntimeFixture().installLoggedIn();
        assertTrue(new TestScript().run());
    }

    @Test
    public void guardHonoursPauseAndInterrupt() {
        new RuntimeFixture().installLoggedIn();
        TestScript script = new TestScript();

        Microbot.pauseAllScripts.set(true);
        assertFalse(script.run());
        Microbot.pauseAllScripts.set(false);

        Thread.currentThread().interrupt();
        try {
            assertFalse(script.run());
        } finally {
            Thread.interrupted();
        }
        assertTrue(script.run());
    }

    @Test
    public void guardYieldsToHumanInputUntilIdleWindowPasses() {
        RuntimeFixture fx = new RuntimeFixture().installLoggedIn();
        TestScript script = new TestScript();
        MicrobotRuntime.noteHumanInput();
        assertFalse(script.run());
        fx.advance(RuntimeFixture.HUMAN_IDLE_MILLIS);
        assertTrue(script.run());
    }

    @Test
    public void guardHoldsStillThroughDeathsTutorial() {
        new RuntimeFixture().installLoggedIn();
        TestScript script = new TestScript();
        try (org.mockito.MockedStatic<net.runelite.client.plugins.microbot.util.events.DeathEvent> death =
                 org.mockito.Mockito.mockStatic(net.runelite.client.plugins.microbot.util.events.DeathEvent.class)) {
            death.when(net.runelite.client.plugins.microbot.util.events.DeathEvent::inTutorial).thenReturn(true);
            assertFalse("a loop between the death event's steps stopped scripts on a missing tool", script.run());
            death.when(net.runelite.client.plugins.microbot.util.events.DeathEvent::inTutorial).thenReturn(false);
            assertTrue(script.run());
        }
    }

    @Test
    public void guardBlocksWhileBlockingEventRuns() throws Exception {
        new RuntimeFixture().installLoggedIn();
        CountDownLatch executing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean valid = new AtomicBoolean(true);
        Microbot.getBlockingEventManager().add(new BlockingEvent() {
            @Override
            public boolean validate() {
                return valid.get();
            }

            @Override
            public boolean execute() {
                valid.set(false);
                executing.countDown();
                try {
                    release.await(WAIT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return true;
            }

            @Override
            public BlockingEventPriority priority() {
                return BlockingEventPriority.HIGH;
            }
        });
        TestScript script = new TestScript();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (executing.getCount() > 0 && System.nanoTime() < deadline) {
            script.run();
            Thread.yield();
        }
        assertTrue("blocking event never executed", executing.await(0, TimeUnit.SECONDS));
        assertFalse("script must idle while the event runs", script.run());

        release.countDown();
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        boolean resumed = false;
        while (!resumed && System.nanoTime() < deadline) {
            resumed = script.run();
            Thread.yield();
        }
        assertTrue("script should continue once the event finished", resumed);
    }

    @Test
    public void staleTicksStopTheScriptInsteadOfPausingIt() throws Exception {
        RuntimeFixture fx = new RuntimeFixture().installLoggedIn();
        TestScript script = new TestScript();
        script.start(1);
        assertTrue(script.firstLoop.await(WAIT_SECONDS, TimeUnit.SECONDS));

        fx.advance(RuntimeFixture.STALE_TICK_MILLIS + 1);
        assertTrue(awaitStopped(script));
        assertEquals("Stopped: game ticks stalled", Microbot.status);

        // Ticks resuming must not restart it.
        fx.tick();
        assertFalse(script.isRunning());
        script.scheduledExecutorService.shutdownNow();
    }

    @Test
    public void revokedMobilePermissionStopsScriptAndRestoringItDoesNotResume() throws Exception {
        new RuntimeFixture().installLoggedIn();
        AtomicBoolean permitted = new AtomicBoolean(true);
        TestScript script = new TestScript();
        script.setMobilePermission(permitted::get);
        script.start(1);
        assertTrue(script.firstLoop.await(WAIT_SECONDS, TimeUnit.SECONDS));

        permitted.set(false);
        assertTrue(awaitStopped(script));
        assertTrue("revocation stops the loop rather than pausing it", script.mainScheduledFuture.isCancelled());
        assertFalse(script.run());
        int loopsAtStop = script.loops.get();

        permitted.set(true);
        assertFalse("restoring permission must not resume the old loop", script.isRunning());
        assertEquals(loopsAtStop, script.loops.get());
        script.scheduledExecutorService.shutdownNow();
    }

    @Test
    public void runAnnouncesStartOncePerRun() throws Exception {
        RuntimeFixture fx = new RuntimeFixture().installLoggedIn();
        List<ScriptStarted> events = new ArrayList<>();
        Object listener = new Object() {
            @Subscribe
            public void onScriptStarted(ScriptStarted event) {
                events.add(event);
            }
        };
        fx.eventBus.register(listener);
        TestScript script = new TestScript();

        script.start(50);
        script.scheduledFuture = script.scheduledExecutorService.scheduleWithFixedDelay(() -> { }, 0, 50, TimeUnit.MILLISECONDS);
        assertEquals(1, events.size());
        assertSame(script, events.get(0).getScript());

        script.shutdown();
        script.start(50);
        assertEquals("a new run announces again", 2, events.size());
        script.shutdown();
        script.scheduledExecutorService.shutdownNow();
    }

    @Test
    public void shutdownInterruptsBlockedBodyAndClearsQueue() throws Exception {
        new RuntimeFixture().installLoggedIn();
        CountDownLatch inBody = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        TestScript script = new TestScript(() -> {
            inBody.countDown();
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(60));
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        });
        script.start(10);
        script.scheduledFuture = script.scheduledExecutorService.schedule(() -> { }, 1, TimeUnit.HOURS);
        assertTrue(inBody.await(WAIT_SECONDS, TimeUnit.SECONDS));
        assertTrue(script.isRunning());

        script.shutdown();

        assertTrue("blocked body was not interrupted", interrupted.await(WAIT_SECONDS, TimeUnit.SECONDS));
        assertFalse(script.isRunning());
        assertTrue(script.mainScheduledFuture.isCancelled());
        assertTrue(script.scheduledFuture.isCancelled());
        ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) script.scheduledExecutorService;
        assertTrue("cancelled tasks must not stay queued", executor.getQueue().isEmpty());
        int loopsAtStop = script.loops.get();
        assertTrue(executor.getKeepAliveTime(TimeUnit.SECONDS) > 0);
        assertTrue(executor.allowsCoreThreadTimeOut());

        executor.shutdownNow();
        assertTrue(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals("no loop may run after shutdown", loopsAtStop, script.loops.get());
    }

    @Test
    public void shutdownIsIdempotentAndSafeBeforeStart() {
        new RuntimeFixture().installLoggedIn();
        TestScript script = new TestScript();
        script.shutdown();
        script.start(50);
        script.shutdown();
        script.shutdown();
        assertFalse(script.isRunning());
        script.scheduledExecutorService.shutdownNow();
    }

    @Test
    public void shutdownClearsPauseOnlyForARunningScript() {
        new RuntimeFixture().installLoggedIn();
        TestScript idle = new TestScript();
        Microbot.pauseAllScripts.set(true);
        idle.shutdown();
        assertTrue("upstream only clears pause when stopping a running loop", Microbot.pauseAllScripts.get());

        TestScript running = new TestScript();
        running.start(50);
        running.shutdown();
        assertFalse(Microbot.pauseAllScripts.get());
        running.scheduledExecutorService.shutdownNow();
    }

    @Test
    public void heartbeatCountsGuardCallsAndIsRemovedOnShutdown() {
        new RuntimeFixture().installLoggedIn();
        TestScript script = new TestScript();
        String key = TestScript.class.getName();
        ScriptHeartbeatRegistry.remove(key); // other guard tests record without shutting down
        assertNull(ScriptHeartbeatRegistry.getHealth(key));

        script.run();
        script.run();
        assertEquals(2L, ScriptHeartbeatRegistry.getHealth(key).get("loopCount"));

        // Heartbeat is recorded even when the guard refuses, so a paused script is not "stalled".
        Microbot.pauseAllScripts.set(true);
        script.run();
        assertEquals(3L, ScriptHeartbeatRegistry.getHealth(key).get("loopCount"));
        Microbot.pauseAllScripts.set(false);
        assertNotNull(ScriptHeartbeatRegistry.getAllHealth().get(key));

        script.shutdown();
        assertNull(ScriptHeartbeatRegistry.getHealth(key));
    }

    @Test
    public void exceptionInBodyEndsUpstreamStyleLoopAndIsObservable() throws Exception {
        new RuntimeFixture().installLoggedIn();
        TestScript script = new TestScript(() -> {
            throw new IllegalStateException("boom");
        });
        script.start(10);
        assertTrue(awaitStopped(script));
        assertFalse(script.mainScheduledFuture.isCancelled());
        assertEquals(1, script.loops.get());
        script.scheduledExecutorService.shutdownNow();
    }

    @Test
    public void scriptThreadsAreDaemonAndNamedAfterTheScript() throws Exception {
        new RuntimeFixture().installLoggedIn();
        AtomicBoolean daemon = new AtomicBoolean();
        StringBuilder name = new StringBuilder();
        CountDownLatch ran = new CountDownLatch(1);
        TestScript script = new TestScript(() -> {
            daemon.set(Thread.currentThread().isDaemon());
            name.append(Thread.currentThread().getName());
            ran.countDown();
        });
        script.start(1000);
        assertTrue(ran.await(WAIT_SECONDS, TimeUnit.SECONDS));
        assertTrue(daemon.get());
        assertTrue(name.toString(), name.toString().startsWith("TestScript-"));
        script.shutdown();
        script.scheduledExecutorService.shutdownNow();
    }

    static boolean awaitStopped(Script script) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (script.isRunning() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        return !script.isRunning();
    }

    /** Upstream-shaped script: guard first, then the body, inside scheduleWithFixedDelay. */
    static class TestScript extends Script {
        final AtomicInteger loops = new AtomicInteger();
        final CountDownLatch firstLoop = new CountDownLatch(1);
        private final Runnable body;

        TestScript() {
            this(() -> { });
        }

        TestScript(Runnable body) {
            this.body = body;
        }

        boolean start(long delayMillis) {
            mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
                if (!super.run()) {
                    return;
                }
                loops.incrementAndGet();
                firstLoop.countDown();
                body.run();
            }, 0, delayMillis, TimeUnit.MILLISECONDS);
            return true;
        }
    }
}
