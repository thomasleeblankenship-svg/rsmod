package net.runelite.client.plugins.microbot;

import net.runelite.api.GameState;
import org.junit.After;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Install/uninstall, service ordering and client-thread snapshots. Mocked client boundary. */
public class MicrobotRuntimeTest {

    @After
    public void tearDown() {
        RuntimeFixture.reset();
    }

    @Test
    public void installBindsServicesAndUninstallClearsThem() {
        RuntimeFixture fx = new RuntimeFixture().install();

        assertTrue(MicrobotRuntime.isInstalled());
        assertSame(fx.client, Microbot.getClient());
        assertSame(fx.clientThread, Microbot.getClientThread());
        assertSame(fx.eventBus, Microbot.getEventBus());
        assertSame(fx.configManager, Microbot.getConfigManager());
        assertNotNull(Microbot.getBlockingEventManager());
        assertNotNull(Microbot.getGameTickBroadcaster());

        Microbot.pauseAllScripts.set(true);
        MicrobotRuntime.uninstall();

        assertFalse(MicrobotRuntime.isInstalled());
        assertNull(Microbot.getClient());
        assertNull(Microbot.getClientThread());
        assertNull(Microbot.getEventBus());
        assertNull(Microbot.getBlockingEventManager());
        assertNull(Microbot.getGameTickBroadcaster());
        assertFalse("uninstall must not leave scripts paused for the next session", Microbot.pauseAllScripts.get());
        MicrobotRuntime.uninstall();
    }

    @Test
    public void secondInstallIsRefused() {
        new RuntimeFixture().install();
        try {
            new RuntimeFixture().install();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(MicrobotRuntime.isInstalled());
        }
    }

    @Test
    public void servicesStartInOrderAndStopInReverse() {
        List<String> calls = new ArrayList<>();
        new RuntimeFixture().install(Arrays.asList(new RecordingService("a", calls), new RecordingService("b", calls)));
        MicrobotRuntime.uninstall();
        assertEquals(Arrays.asList("start a", "start b", "stop b", "stop a"), calls);
    }

    @Test
    public void failedServiceRollsBackInstall() {
        List<String> calls = new ArrayList<>();
        RecordingService failing = new RecordingService("b", calls) {
            @Override
            public void startUp() throws Exception {
                super.startUp();
                throw new Exception("boom");
            }
        };
        try {
            new RuntimeFixture().install(Arrays.asList(new RecordingService("a", calls), failing));
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertEquals("boom", expected.getCause().getMessage());
        }
        assertFalse(MicrobotRuntime.isInstalled());
        assertNull(Microbot.getClient());
        assertEquals(Arrays.asList("start a", "start b", "stop a"), calls);
    }

    @Test
    public void servicesSeeBoundMicrobotDuringStartAndStop() {
        List<Object> seen = new ArrayList<>();
        MicrobotRuntimeService probe = new MicrobotRuntimeService() {
            @Override
            public void startUp() {
                seen.add(Microbot.getClient());
            }

            @Override
            public void shutDown() {
                seen.add(Microbot.getClient());
            }
        };
        RuntimeFixture fx = new RuntimeFixture().install(Arrays.asList(probe));
        MicrobotRuntime.uninstall();
        assertEquals(Arrays.asList(fx.client, fx.client), seen);
    }

    @Test
    public void loggedInSeedsImmediatelyOnClientThread() {
        new RuntimeFixture().installLoggedIn();
        assertTrue(Microbot.isLoggedIn());
    }

    @Test
    public void loggedInSeedIsQueuedWhenOffClientThreadAndDroppedAfterUninstall() {
        RuntimeFixture fx = new RuntimeFixture();
        when(fx.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        fx.install();
        assertFalse("off the client thread the snapshot starts logged out", Microbot.isLoggedIn());

        ArgumentCaptor<Runnable> seed = ArgumentCaptor.forClass(Runnable.class);
        verify(fx.clientThread).invokeLater(seed.capture());
        verify(fx.client, never()).getGameState();

        seed.getValue().run();
        assertTrue(Microbot.isLoggedIn());

        MicrobotRuntime.uninstall();
        seed.getValue().run();
        assertFalse(Microbot.isLoggedIn());
    }

    @Test
    public void gameStateEventsDriveLoggedInSnapshot() {
        RuntimeFixture fx = new RuntimeFixture().install();
        fx.gameState(GameState.LOGGED_IN);
        assertTrue(Microbot.isLoggedIn());
        fx.gameState(GameState.HOPPING);
        assertFalse(Microbot.isLoggedIn());
        fx.gameState(GameState.LOGGED_IN);
        fx.gameState(GameState.CONNECTION_LOST);
        assertFalse(Microbot.isLoggedIn());
        fx.gameState(GameState.LOGGED_IN);
        fx.gameState(GameState.LOGIN_SCREEN);
        assertFalse(Microbot.isLoggedIn());
    }

    @Test
    public void uninstallUnsubscribesSnapshotListener() {
        RuntimeFixture fx = new RuntimeFixture().install();
        MicrobotRuntime.uninstall();
        fx.gameState(GameState.LOGGED_IN);
        assertFalse(Microbot.isLoggedIn());
    }

    @Test
    public void staleTicksOnlyCountWhileLoggedIn() {
        RuntimeFixture fx = new RuntimeFixture().installLoggedIn();
        fx.advance(RuntimeFixture.STALE_TICK_MILLIS);
        assertFalse("exactly at the threshold is not stale", MicrobotRuntime.isGameTickStale());
        fx.advance(1);
        assertTrue(MicrobotRuntime.isGameTickStale());

        fx.tick();
        assertFalse(MicrobotRuntime.isGameTickStale());
        assertEquals(0, MicrobotRuntime.millisSinceLastGameTick());

        fx.gameState(GameState.LOGIN_SCREEN);
        fx.advance(60_000);
        assertFalse(MicrobotRuntime.isGameTickStale());

        fx.gameState(GameState.LOGGED_IN);
        assertFalse("a fresh login must not inherit the old session's last tick", MicrobotRuntime.isGameTickStale());
    }

    @Test
    public void notInstalledReportsSafeDefaults() {
        assertNull(Microbot.getRs2PlayerStateCache());
        assertNull(Microbot.getRs2PlayerCache());
        assertEquals(0, Microbot.getVarbitValue(1234));
        assertEquals(0, Microbot.getVarbitPlayerValue(1234));
        assertFalse(MicrobotRuntime.isLoggedIn());
        assertFalse(MicrobotRuntime.isGameTickStale());
        assertFalse(MicrobotRuntime.humanOwnsInput());
        assertEquals(Long.MAX_VALUE, MicrobotRuntime.millisSinceLastGameTick());
        MicrobotRuntime.noteHumanInput();
    }

    @Test
    public void humanInputOwnsTheIdleWindowThenReleases() {
        RuntimeFixture fx = new RuntimeFixture().install();
        assertFalse(MicrobotRuntime.humanOwnsInput());
        MicrobotRuntime.noteHumanInput();
        assertTrue(MicrobotRuntime.humanOwnsInput());
        fx.advance(RuntimeFixture.HUMAN_IDLE_MILLIS - 1);
        assertTrue(MicrobotRuntime.humanOwnsInput());
        fx.advance(1);
        assertFalse(MicrobotRuntime.humanOwnsInput());
    }

    private static class RecordingService implements MicrobotRuntimeService {
        private final String name;
        private final List<String> calls;

        RecordingService(String name, List<String> calls) {
            this.name = name;
            this.calls = calls;
        }

        @Override
        public void startUp() throws Exception {
            calls.add("start " + name);
        }

        @Override
        public void shutDown() {
            calls.add("stop " + name);
        }
    }
}
