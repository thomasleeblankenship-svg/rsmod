package net.runelite.client.plugins.microbot.statemachine;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.RuntimeFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Behaviour of the unmodified upstream {@link StateMachineScript} on the mobile {@code Script}
 * guard. {@link StateMachineScript#step()} is driven directly on the test thread, so ordering is
 * fully deterministic. Mocked runtime boundary.
 */
public class StateMachineScriptTest {

    enum S { A, B, C, ERR }

    @Before
    public void setUp() {
        new RuntimeFixture().installLoggedIn();
    }

    @After
    public void tearDown() {
        RuntimeFixture.reset();
    }

    @Test
    public void firstMatchingTransitionWinsThenStateActionRuns() {
        Machine m = new Machine();
        m.toB = true;
        m.toC = true;

        assertTrue(m.step());

        assertEquals(S.B, m.getCurrentState());
        assertEquals(Arrays.asList("A->B:go b", "on B"), m.calls);
        StateSnapshot<S> snapshot = m.getSnapshot();
        assertEquals(S.B, snapshot.currentState());
        assertEquals(S.A, snapshot.previousState());
        assertEquals(1, snapshot.loopCount());
        assertEquals(1, snapshot.transitionCount());
        assertEquals(1, snapshot.recentTrace().size());
        m.shutdown();
    }

    @Test
    public void noTransitionKeepsStateAndRunsItsAction() {
        Machine m = new Machine();
        assertTrue(m.step());
        assertTrue(m.step());
        assertEquals(S.A, m.getCurrentState());
        assertEquals(Arrays.asList("on A", "on A"), m.calls);
        assertEquals(2, m.getSnapshot().loopCount());
        assertFalse(m.getSnapshot().pendingTransitions().get(0).isCurrentlyTrue());
        m.shutdown();
    }

    @Test
    public void throwingGuardIsSkippedNotFatal() {
        Machine m = new Machine();
        m.toB = true;
        m.step();
        m.guardThrows = true;
        m.toA = true;
        assertTrue(m.step());
        assertEquals("guard B->C threw and was skipped; B->A fired", S.A, m.getCurrentState());
        m.shutdown();
    }

    @Test
    public void actionExceptionRoutesThroughOnError() {
        Machine m = new Machine();
        m.actionThrows = true;
        assertTrue(m.step());
        assertEquals(S.ERR, m.getCurrentState());
        assertEquals("Error: action failed", m.getSnapshot().lastTransitionReason());
        m.shutdown();
    }

    @Test
    public void pausedGuardSkipsTransitionsAndActions() {
        Machine m = new Machine();
        m.toB = true;
        Microbot.pauseAllScripts.set(true);
        assertFalse(m.step());
        assertTrue(m.calls.isEmpty());
        Microbot.pauseAllScripts.set(false);
        assertTrue(m.step());
        assertEquals(S.B, m.getCurrentState());
        m.shutdown();
    }

    @Test
    public void notInstalledRuntimeSkipsEverything() {
        MicrobotRuntime.uninstall();
        Machine m = new Machine();
        m.toB = true;
        assertFalse(m.step());
        assertTrue(m.calls.isEmpty());
        m.shutdown();
    }

    @Test
    public void shutdownUnregistersAndRestartReinitialises() {
        Machine m = new Machine();
        m.toB = true;
        m.step();
        assertSame(m, StateMachineScript.getRegistry().get("Machine"));

        m.shutdown();
        assertNull(StateMachineScript.getRegistry().get("Machine"));
        assertNull(m.getSnapshot());

        m.toB = false;
        m.calls.clear();
        assertTrue(m.step());
        assertEquals("restart begins from the initial state", S.A, m.getCurrentState());
        assertEquals(1, m.getSnapshot().loopCount());
        m.shutdown();
    }

    @Test
    public void interruptedInitialisationDoesNotRegister() {
        Machine m = new Machine();
        Thread.currentThread().interrupt();
        try {
            assertFalse(m.step());
        } finally {
            Thread.interrupted();
        }
        assertNull(StateMachineScript.getRegistry().get("Machine"));
        m.shutdown();
    }

    static class Machine extends StateMachineScript<S> {
        final List<String> calls = new ArrayList<>();
        volatile boolean toB;
        volatile boolean toC;
        volatile boolean toA;
        volatile boolean guardThrows;
        volatile boolean actionThrows;

        @Override
        protected S initialState() {
            return S.A;
        }

        @Override
        protected List<Transition<S>> defineTransitions() {
            return List.of(
                    Transition.<S>from(S.A).when(() -> toB, "toB").because("go b").goTo(S.B),
                    Transition.<S>from(S.A).when(() -> toC, "toC").because("go c").goTo(S.C),
                    Transition.<S>from(S.B).when(() -> {
                        if (guardThrows) {
                            throw new IllegalStateException("guard");
                        }
                        return false;
                    }, "throws").goTo(S.C),
                    Transition.<S>from(S.B).when(() -> toA, "toA").because("back").goTo(S.A),
                    Transition.<S>from(S.ERR).when(() -> false, "never").goTo(S.A)
            );
        }

        @Override
        protected void onState(S state) {
            if (actionThrows) {
                throw new IllegalStateException("action failed");
            }
            calls.add("on " + state);
        }

        @Override
        protected void onTransition(S from, S to, String reason) {
            calls.add(from + "->" + to + ":" + reason);
        }

        @Override
        protected S onError(S state, Exception e) {
            return S.ERR;
        }
    }
}
