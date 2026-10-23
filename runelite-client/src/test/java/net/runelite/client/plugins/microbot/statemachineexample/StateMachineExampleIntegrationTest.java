package net.runelite.client.plugins.microbot.statemachineexample;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotReadServicesLifecycle;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.MicrobotScriptAdapter;
import net.runelite.client.plugins.microbot.api.ClientThreadCalls;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.statemachine.StateSnapshot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The unchanged upstream {@link StateMachineExampleScript} running through
 * {@link MicrobotScriptAdapter}, {@link MicrobotRuntime} and the real read-API caches.
 * <p>
 * The game is an empty logged-in scene: Mockito doubles of RuneLite interfaces, with a single
 * dedicated "client thread" executor that runs every {@link ClientThread} task. This proves the
 * wiring, threading and lifecycle; it is not live-game, device or APK evidence.
 */
public class StateMachineExampleIntegrationTest {
    private static final long WAIT_SECONDS = 10;
    private static final int TOP_LEVEL_ID = -1;

    private final ExecutorService gameThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-client-thread"));
    private final AtomicReference<Thread> gameThreadRef = new AtomicReference<>();
    private final Client client = mock(Client.class);
    private final ClientThread clientThread = mock(ClientThread.class);
    private final EventBus eventBus = new EventBus();
    private volatile GameState gameState = GameState.LOGGED_IN;
    private MicrobotScriptAdapter<StateMachineExampleScript> adapter;

    @Before
    public void setUp() throws Exception {
        gameThread.submit(() -> gameThreadRef.set(Thread.currentThread())).get();
        when(client.isClientThread()).thenAnswer(i -> Thread.currentThread() == gameThreadRef.get());
        doAnswer(call -> {
            gameThread.execute(call.getArgument(0));
            return null;
        }).when(clientThread).invoke(any(Runnable.class));
        doAnswer(call -> {
            gameThread.execute(call.getArgument(0));
            return null;
        }).when(clientThread).invokeLater(any(Runnable.class));

        when(client.getGameState()).thenAnswer(i -> gameState);
        when(client.getTickCount()).thenReturn(1);
        WorldView topLevel = mock(WorldView.class);
        Scene scene = mock(Scene.class);
        when(client.getTopLevelWorldView()).thenReturn(topLevel);
        when(client.getWorldView(anyInt())).thenAnswer(i -> (int) i.getArgument(0) == TOP_LEVEL_ID ? topLevel : null);
        when(topLevel.getId()).thenReturn(TOP_LEVEL_ID);
        when(topLevel.isTopLevel()).thenReturn(true);
        when(topLevel.getScene()).thenReturn(scene);
        when(scene.getTiles()).thenReturn(new Tile[4][104][104]);
        IndexedObjectSet<NPC> npcs = emptySet();
        doAnswer(i -> npcs).when(topLevel).npcs();

        Player local = mock(Player.class);
        when(client.getLocalPlayer()).thenReturn(local);
        when(local.getWorldView()).thenReturn(topLevel);
        when(local.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        when(local.getAnimation()).thenReturn(-1);
        when(local.getPoseAnimation()).thenReturn(808);
        when(local.getIdlePoseAnimation()).thenReturn(808);

        MicrobotReadServices readServices = new MicrobotReadServices(client, new ClientThreadCalls(client, clientThread, 2_000));
        MicrobotRuntime.install(client, clientThread, eventBus, mock(ConfigManager.class),
                Collections.singletonList(new MicrobotReadServicesLifecycle(eventBus, readServices)));

        StateMachineExampleConfig config = new StateMachineExampleConfig() {
            @Override
            public int tickDelay() {
                return 5;
            }

            @Override
            public int idleDuration() {
                return 0;
            }
        };
        adapter = new MicrobotScriptAdapter<>("State Machine Example", StateMachineExampleScript::new,
                script -> script.run(config));
    }

    @After
    public void tearDown() throws Exception {
        if (adapter != null) {
            adapter.onStop(null);
        }
        MicrobotRuntime.uninstall();
        gameThread.shutdownNow();
        gameThread.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private static <T> IndexedObjectSet<T> emptySet() {
        IndexedObjectSet<T> set = mock(IndexedObjectSet.class);
        when(set.iterator()).thenAnswer(i -> new ArrayList<T>().iterator());
        return set;
    }

    private boolean awaitSnapshot(Predicate<StateSnapshot<StateMachineExampleScript.State>> condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            StateMachineExampleScript script = adapter.getScript();
            StateSnapshot<StateMachineExampleScript.State> snapshot = script == null ? null : script.getSnapshot();
            if (snapshot != null && condition.test(snapshot)) {
                return true;
            }
            Thread.sleep(5);
        }
        return false;
    }

    private void awaitLoggedIn() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (!Microbot.isLoggedIn() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertTrue("logged-in snapshot was never seeded on the client thread", Microbot.isLoggedIn());
    }

    @Test
    public void unchangedExampleCompletesAFullCycleOnEmptyScene() throws Exception {
        awaitLoggedIn();
        adapter.onStart(null);

        assertTrue("never completed SCAN_NPCS -> ... -> REPORT -> SCAN_NPCS", awaitSnapshot(s -> s.recentTrace().stream()
                .anyMatch(t -> t.from() == StateMachineExampleScript.State.REPORT
                        && t.to() == StateMachineExampleScript.State.SCAN_NPCS)));
        assertTrue(awaitSnapshot(s -> s.transitionCount() >= 7));
        StateSnapshot<StateMachineExampleScript.State> snapshot = adapter.getScript().getSnapshot();
        assertFalse("example fell into ERROR", snapshot.recentTrace().stream()
                .anyMatch(t -> t.to() == StateMachineExampleScript.State.ERROR));
        adapter.onGameTick(null);
        assertTrue(adapter.status(), adapter.status().startsWith("State Machine Example: "));
    }

    @Test
    public void logoutHaltsProgressAndStopLeavesNothingRunning() throws Exception {
        awaitLoggedIn();
        adapter.onStart(null);
        assertTrue(awaitSnapshot(s -> s.loopCount() > 0));

        gameState = GameState.LOGIN_SCREEN;
        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.LOGIN_SCREEN);
        eventBus.post(event);
        assertFalse(Microbot.isLoggedIn());

        long loops = adapter.getScript().getSnapshot().loopCount();
        Thread.sleep(100);
        assertEquals("logged out: the example's isLoggedIn gate must stop every step",
                loops, adapter.getScript().getSnapshot().loopCount(), 1);

        StateMachineExampleScript script = adapter.getScript();
        adapter.onStop(null);
        assertNull(adapter.getScript());
        assertFalse(script.isRunning());
        assertNull(script.getSnapshot());
    }
}
