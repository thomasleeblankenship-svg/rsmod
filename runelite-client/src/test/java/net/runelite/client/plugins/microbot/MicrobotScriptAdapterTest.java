package net.runelite.client.plugins.microbot;

import net.runelite.client.plugins.microbot.statemachine.StateMachineScript;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.statemachine.Transition;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link MicrobotScriptAdapter} under the plugin's MobileScript call pattern. The context argument
 * is unused by the adapter and passed as null. Mocked runtime boundary.
 */
public class MicrobotScriptAdapterTest {
    private static final long WAIT_SECONDS = 5;

    private final List<ScriptLifecycleTest.TestScript> created = new ArrayList<>();

    @After
    public void tearDown() {
        for (Script script : created) {
            script.shutdown();
            script.scheduledExecutorService.shutdownNow();
        }
        RuntimeFixture.reset();
    }

    private MicrobotScriptAdapter<ScriptLifecycleTest.TestScript> adapter(Runnable body) {
        return new MicrobotScriptAdapter<>("Test", () -> {
            ScriptLifecycleTest.TestScript script = new ScriptLifecycleTest.TestScript(body);
            created.add(script);
            return script;
        }, script -> script.start(5));
    }

    @Test
    public void startRequiresInstalledRuntime() {
        MicrobotScriptAdapter<ScriptLifecycleTest.TestScript> adapter = adapter(() -> { });
        try {
            adapter.onStart(null);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("not installed"));
        }
        assertTrue("no script may be created without a runtime", created.isEmpty());
    }

    @Test
    public void startRunsFreshScriptAndStopDisposesIt() throws Exception {
        new RuntimeFixture().installLoggedIn();
        MicrobotScriptAdapter<ScriptLifecycleTest.TestScript> adapter = adapter(() -> { });

        adapter.onStart(null);
        ScriptLifecycleTest.TestScript script = adapter.getScript();
        assertTrue(script.isRunning());
        assertTrue(script.firstLoop.await(WAIT_SECONDS, TimeUnit.SECONDS));
        adapter.onGameTick(null);
        assertTrue(adapter.status(), adapter.status().startsWith("Test: running"));

        adapter.onStop(null);
        assertNull(adapter.getScript());
        assertFalse(script.isRunning());
        assertTrue(script.scheduledExecutorService.isShutdown());
        assertTrue(script.scheduledExecutorService.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals("Test: stopped", adapter.status());

        adapter.onStop(null);
    }

    @Test
    public void doubleStartIsRefused() {
        new RuntimeFixture().installLoggedIn();
        MicrobotScriptAdapter<ScriptLifecycleTest.TestScript> adapter = adapter(() -> { });
        adapter.onStart(null);
        try {
            adapter.onStart(null);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertEquals(1, created.size());
        }
    }

    @Test
    public void restartCreatesNewInstanceAndOldWorkNeverRunsAgain() throws Exception {
        new RuntimeFixture().installLoggedIn();
        AtomicInteger firstRunLoops = new AtomicInteger();
        MicrobotScriptAdapter<ScriptLifecycleTest.TestScript> adapter = adapter(firstRunLoops::incrementAndGet);

        adapter.onStart(null);
        ScriptLifecycleTest.TestScript first = adapter.getScript();
        assertTrue(first.firstLoop.await(WAIT_SECONDS, TimeUnit.SECONDS));
        adapter.onStop(null);
        assertTrue(first.scheduledExecutorService.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS));
        int loopsAfterStop = first.loops.get();

        adapter.onStart(null);
        ScriptLifecycleTest.TestScript second = adapter.getScript();
        assertNotSame(first, second);
        assertTrue(second.firstLoop.await(WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(loopsAfterStop, first.loops.get());
    }

    @Test
    public void launcherRefusalFailsStartAndCleansUp() {
        new RuntimeFixture().installLoggedIn();
        List<Script> made = new ArrayList<>();
        MicrobotScriptAdapter<ScriptLifecycleTest.TestScript> adapter = new MicrobotScriptAdapter<>("Test", () -> {
            ScriptLifecycleTest.TestScript s = new ScriptLifecycleTest.TestScript();
            made.add(s);
            return s;
        }, script -> false);
        try {
            adapter.onStart(null);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("refused"));
        }
        assertNull(adapter.getScript());
        assertTrue(made.get(0).scheduledExecutorService.isShutdown());
    }

    @Test
    public void launcherExceptionFailsStartWithCause() {
        new RuntimeFixture().installLoggedIn();
        MicrobotScriptAdapter<ScriptLifecycleTest.TestScript> adapter = new MicrobotScriptAdapter<>("Test",
                ScriptLifecycleTest.TestScript::new, script -> {
                    throw new IllegalArgumentException("bad config");
                });
        try {
            adapter.onStart(null);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertEquals("bad config", expected.getCause().getMessage());
        }
        assertNull(adapter.getScript());
    }

    @Test
    public void launcherThatSchedulesNothingFailsStart() {
        new RuntimeFixture().installLoggedIn();
        MicrobotScriptAdapter<ScriptLifecycleTest.TestScript> adapter = new MicrobotScriptAdapter<>("Test",
                ScriptLifecycleTest.TestScript::new, script -> true);
        try {
            adapter.onStart(null);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("main loop"));
        }
    }

    @Test
    public void crashedLoopFailsClosedOnNextTickWithCause() throws Exception {
        new RuntimeFixture().installLoggedIn();
        MicrobotScriptAdapter<ScriptLifecycleTest.TestScript> adapter = adapter(() -> {
            throw new AssertionError("loop died");
        });
        adapter.onStart(null);
        ScriptLifecycleTest.TestScript script = adapter.getScript();
        assertTrue(ScriptLifecycleTest.awaitStopped(script));

        try {
            adapter.onGameTick(null);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertEquals("loop died", expected.getCause().getMessage());
        }
        assertNull("a crashed script is dropped, never resumed", adapter.getScript());
        assertTrue(script.scheduledExecutorService.isShutdown());
        try {
            adapter.onGameTick(null);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("not started"));
        }
    }

    @Test
    public void stopInterruptsBlockedWaitWithoutBlockingCaller() throws Exception {
        new RuntimeFixture().installLoggedIn();
        CountDownLatch waiting = new CountDownLatch(1);
        CountDownLatch woke = new CountDownLatch(1);
        MicrobotScriptAdapter<ScriptLifecycleTest.TestScript> adapter = adapter(() -> {
            waiting.countDown();
            Global.sleepUntil(() -> false, 60_000);
            woke.countDown();
        });
        adapter.onStart(null);
        assertTrue(waiting.await(WAIT_SECONDS, TimeUnit.SECONDS));

        long before = System.nanoTime();
        adapter.onStop(null);
        long stopMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);

        assertTrue("onStop must not wait for the script thread (" + stopMillis + "ms)", stopMillis < 1_000);
        assertTrue("sleepUntil did not observe the interrupt", woke.await(WAIT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    public void staleTicksStopScriptAndAdapterReportsItOnResume() throws Exception {
        RuntimeFixture fx = new RuntimeFixture().installLoggedIn();
        MicrobotScriptAdapter<ScriptLifecycleTest.TestScript> adapter = adapter(() -> { });
        adapter.onStart(null);
        ScriptLifecycleTest.TestScript script = adapter.getScript();
        assertTrue(script.firstLoop.await(WAIT_SECONDS, TimeUnit.SECONDS));

        fx.advance(RuntimeFixture.STALE_TICK_MILLIS + 1);
        assertTrue(ScriptLifecycleTest.awaitStopped(script));
        fx.tick();
        try {
            adapter.onGameTick(null);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("stopped unexpectedly"));
        }
        assertNull(adapter.getScript());
    }

    @Test
    public void statusShowsStateMachineState() throws Exception {
        new RuntimeFixture().installLoggedIn();
        CountDownLatch stepped = new CountDownLatch(1);
        MicrobotScriptAdapter<TwoStateMachine> adapter = new MicrobotScriptAdapter<>("Machine",
                () -> new TwoStateMachine(stepped), TwoStateMachine::start);
        adapter.onStart(null);
        try {
            assertTrue(stepped.await(WAIT_SECONDS, TimeUnit.SECONDS));
            String status = adapter.status();
            assertTrue(status, status.startsWith("Machine: "));
            assertTrue(status, status.contains("ONLY"));
            assertSame(TwoStateMachine.State.ONLY, adapter.getScript().getSnapshot().currentState());
        } finally {
            adapter.onStop(null);
        }
    }

    static class TwoStateMachine extends StateMachineScript<TwoStateMachine.State> {
        enum State { ONLY }

        private final CountDownLatch stepped;

        TwoStateMachine(CountDownLatch stepped) {
            this.stepped = stepped;
        }

        boolean start() {
            mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
                if (step()) {
                    stepped.countDown();
                }
            }, 0, 5, TimeUnit.MILLISECONDS);
            return true;
        }

        @Override
        protected State initialState() {
            return State.ONLY;
        }

        @Override
        protected List<Transition<State>> defineTransitions() {
            return List.of(Transition.<State>from(State.ONLY).when(() -> false, "never").goTo(State.ONLY));
        }

        @Override
        protected void onState(State state) {
        }
    }
}
