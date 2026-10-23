package net.runelite.client.plugins.microbot;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Installs {@link MicrobotRuntime} against a real RuneLite {@link EventBus}, a manual clock and
 * Mockito doubles of {@link Client}, {@link ClientThread} and {@link ConfigManager}. These are
 * boundary mocks: results from tests that use this fixture are not live-game evidence.
 */
public final class RuntimeFixture {
    public static final long STALE_TICK_MILLIS = 5_000L;
    public static final long HUMAN_IDLE_MILLIS = 1_800L;

    public final Client client = mock(Client.class);
    public final ClientThread clientThread = mock(ClientThread.class);
    public final ConfigManager configManager = mock(ConfigManager.class);
    public final EventBus eventBus = new EventBus();
    public final AtomicLong clock = new AtomicLong(1_000_000L);

    public RuntimeFixture install() {
        return install(Collections.emptyList());
    }

    public RuntimeFixture install(List<? extends MicrobotRuntimeService> services) {
        MicrobotRuntime.install(client, clientThread, eventBus, configManager, services,
                clock::get, STALE_TICK_MILLIS, HUMAN_IDLE_MILLIS);
        return this;
    }

    /** Installs as if called on the client thread while logged in, so the snapshot seeds at once. */
    public RuntimeFixture installLoggedIn() {
        when(client.isClientThread()).thenReturn(true);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        install();
        when(client.isClientThread()).thenReturn(false);
        return this;
    }

    public void gameState(GameState state) {
        GameStateChanged event = new GameStateChanged();
        event.setGameState(state);
        eventBus.post(event);
    }

    public void tick() {
        eventBus.post(new GameTick());
    }

    public void advance(long millis) {
        clock.addAndGet(millis);
    }

    public static void reset() {
        MicrobotRuntime.uninstall();
        Microbot.pauseAllScripts.set(false);
    }
}
