package net.runelite.client.plugins.microbot;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.util.GameTickBroadcaster;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Owns the lifetime of the mobile Microbot runtime: binds {@link Microbot}'s services, publishes
 * client-thread snapshots that script threads may read, and tears everything down again.
 * <p>
 * Mobile-only. Upstream Microbot wires {@link Microbot} once through Guice static injection from
 * its always-on plugin and never unwires it. On mobile the owning plugin can be disabled, the app
 * backgrounded or the JVM restarted, so installation is explicit and reversible.
 * <p>
 * Snapshots, all written on the client thread by event subscribers and read from any thread:
 * <ul>
 *     <li>logged in: {@code GameStateChanged} to or from {@link GameState#LOGGED_IN};</li>
 *     <li>last game tick: {@code GameTick}, used to fail closed when the engine stops ticking
 *     (app backgrounded, connection lost) while a script thread is still running;</li>
 *     <li>human input: {@link #noteHumanInput()}, called by the platform input layer.</li>
 * </ul>
 * All methods are thread-safe. {@link #install} and {@link #uninstall} never block on the client
 * thread.
 */
@Slf4j
public final class MicrobotRuntime {

    /** Ticks are 600ms; this many milliseconds without one while logged in stops scripts. */
    public static final long DEFAULT_STALE_TICK_MILLIS = 5_000L;

    /** Matches upstream {@code InputArbiter.DEFAULT_IDLE_RESUME_MS}. */
    public static final long DEFAULT_HUMAN_IDLE_RESUME_MILLIS = 1_800L;

    private static final LongSupplier MONOTONIC_MILLIS = () -> System.nanoTime() / 1_000_000L;
    private static final Object LOCK = new Object();
    private static volatile Installation installation;

    private MicrobotRuntime() {
    }

    /**
     * Installs the runtime with the default clock and thresholds.
     *
     * @see #install(Client, ClientThread, EventBus, ConfigManager, List, LongSupplier, long, long)
     */
    public static void install(Client client, ClientThread clientThread, EventBus eventBus,
                               ConfigManager configManager, List<? extends MicrobotRuntimeService> services) {
        install(client, clientThread, eventBus, configManager, services, MONOTONIC_MILLIS,
                DEFAULT_STALE_TICK_MILLIS, DEFAULT_HUMAN_IDLE_RESUME_MILLIS);
    }

    /**
     * Binds {@link Microbot}, subscribes the snapshot listeners and starts {@code services} in
     * order. The logged-in snapshot is seeded immediately on the client thread, or by a queued
     * client-thread task otherwise; until then it reads as logged out.
     *
     * @param clock                  monotonic milliseconds; injected by tests
     * @param staleTickMillis        see {@link #isGameTickStale()}
     * @param humanIdleResumeMillis  see {@link #humanOwnsInput()}
     * @throws IllegalStateException when already installed, or when a service fails to start
     *                               (the runtime is then left uninstalled)
     */
    public static void install(Client client, ClientThread clientThread, EventBus eventBus,
                               ConfigManager configManager, List<? extends MicrobotRuntimeService> services,
                               LongSupplier clock, long staleTickMillis, long humanIdleResumeMillis) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(clientThread, "clientThread");
        Objects.requireNonNull(eventBus, "eventBus");
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(clock, "clock");
        synchronized (LOCK) {
            if (installation != null) {
                throw new IllegalStateException("Microbot runtime is already installed");
            }
            Installation next = new Installation(client, eventBus, new ArrayList<>(services), clock,
                    staleTickMillis, humanIdleResumeMillis);
            next.blockingEventManager = new BlockingEventManager();
            next.gameTickBroadcaster = new GameTickBroadcaster(eventBus);
            eventBus.register(next.listener);
            Microbot.bind(client, clientThread, eventBus, configManager,
                    next.blockingEventManager, next.gameTickBroadcaster);
            installation = next;

            Deque<MicrobotRuntimeService> started = new ArrayDeque<>();
            try {
                for (MicrobotRuntimeService service : next.services) {
                    service.startUp();
                    started.push(service);
                }
            } catch (Exception e) {
                stopServices(started);
                teardown(next);
                throw new IllegalStateException("Microbot runtime service failed to start", e);
            }
            next.blockingEventManager.start();

            if (client.isClientThread()) {
                next.seedLoggedIn();
            } else {
                clientThread.invokeLater(() -> {
                    if (installation == next) {
                        next.seedLoggedIn();
                    }
                });
            }
        }
    }

    /**
     * Stops services in reverse order, unsubscribes listeners, shuts down the blocking-event
     * executors and clears {@link Microbot}. Scripts must already be shut down; a script thread
     * that keeps running sees {@link #isInstalled()} false and its guard refuses to continue.
     * Safe to call when not installed.
     */
    public static void uninstall() {
        synchronized (LOCK) {
            Installation current = installation;
            if (current == null) {
                return;
            }
            Deque<MicrobotRuntimeService> started = new ArrayDeque<>();
            current.services.forEach(started::push);
            stopServices(started);
            teardown(current);
        }
    }

    private static void stopServices(Deque<MicrobotRuntimeService> started) {
        while (!started.isEmpty()) {
            MicrobotRuntimeService service = started.pop();
            try {
                service.shutDown();
            } catch (Exception e) {
                log.warn("Microbot runtime service {} failed to shut down", service.getClass().getName(), e);
            }
        }
    }

    private static void teardown(Installation current) {
        installation = null;
        current.eventBus.unregister(current.listener);
        current.eventBus.unregister(current.gameTickBroadcaster);
        current.blockingEventManager.shutdown();
        Microbot.unbind();
    }

    /** @return true between a successful {@link #install} and the next {@link #uninstall} */
    public static boolean isInstalled() {
        return installation != null;
    }

    /** @return the last logged-in state the client thread published; false when not installed */
    public static boolean isLoggedIn() {
        Installation current = installation;
        return current != null && current.loggedIn;
    }

    /**
     * @return milliseconds since the last {@code GameTick} (or since login / install, whichever is
     * later), or {@link Long#MAX_VALUE} when not installed
     */
    public static long millisSinceLastGameTick() {
        Installation current = installation;
        if (current == null) {
            return Long.MAX_VALUE;
        }
        return current.clock.getAsLong() - current.lastTickMillis;
    }

    /**
     * @return true when logged in but no game tick arrived within the stale threshold. A script
     * thread seeing this must stop rather than act on a frozen game state; it is never treated as
     * a pause, because resuming silently after the app returns to the foreground is not allowed.
     */
    public static boolean isGameTickStale() {
        Installation current = installation;
        return current != null && current.loggedIn
                && current.clock.getAsLong() - current.lastTickMillis > current.staleTickMillis;
    }

    /**
     * Records real user input (a touch or key from the person holding the device). Scripts idle,
     * and waits in {@code Global} return early, until the idle window passes with no further input.
     * Cancels nothing, like upstream {@code InputArbiter}.
     */
    public static void noteHumanInput() {
        Installation current = installation;
        if (current != null) {
            current.lastHumanInputMillis = current.clock.getAsLong();
            current.humanInputSeen = true;
        }
    }

    /**
     * Adapted from upstream {@code InputArbiter.isHuman()}: true within the idle window after the
     * last {@link #noteHumanInput()}. Held-button tracking and pointer-distance thresholds are not
     * ported; RuneMobile delivers taps, not held desktop buttons.
     */
    public static boolean humanOwnsInput() {
        Installation current = installation;
        if (current == null || !current.humanInputSeen) {
            return false;
        }
        long elapsed = current.clock.getAsLong() - current.lastHumanInputMillis;
        return elapsed >= 0 && elapsed < current.humanIdleResumeMillis;
    }

    private static final class Installation {
        private final Client client;
        private final EventBus eventBus;
        private final List<MicrobotRuntimeService> services;
        private final LongSupplier clock;
        private final long staleTickMillis;
        private final long humanIdleResumeMillis;
        private final SnapshotListener listener = new SnapshotListener(this);
        private BlockingEventManager blockingEventManager;
        private GameTickBroadcaster gameTickBroadcaster;

        private volatile boolean loggedIn;
        private volatile long lastTickMillis;
        private volatile long lastHumanInputMillis;
        private volatile boolean humanInputSeen;

        private Installation(Client client, EventBus eventBus, List<MicrobotRuntimeService> services,
                             LongSupplier clock, long staleTickMillis, long humanIdleResumeMillis) {
            this.client = client;
            this.eventBus = eventBus;
            this.services = services;
            this.clock = clock;
            this.staleTickMillis = staleTickMillis;
            this.humanIdleResumeMillis = humanIdleResumeMillis;
            this.lastTickMillis = clock.getAsLong();
        }

        /** Client thread only. */
        private void seedLoggedIn() {
            setLoggedIn(client.getGameState() == GameState.LOGGED_IN);
        }

        private void setLoggedIn(boolean value) {
            if (value && !loggedIn) {
                // A fresh login has not ticked yet; do not let the previous session's last tick
                // make it look stale.
                lastTickMillis = clock.getAsLong();
            }
            loggedIn = value;
        }
    }

    /** Public with public methods so RuneLite's {@link EventBus} can bind them. */
    public static final class SnapshotListener {
        private final Installation owner;

        private SnapshotListener(Installation owner) {
            this.owner = owner;
        }

        @Subscribe
        public void onGameTick(GameTick event) {
            owner.lastTickMillis = owner.clock.getAsLong();
        }

        @Subscribe
        public void onGameStateChanged(GameStateChanged event) {
            owner.setLoggedIn(event.getGameState() == GameState.LOGGED_IN);
        }
    }
}
