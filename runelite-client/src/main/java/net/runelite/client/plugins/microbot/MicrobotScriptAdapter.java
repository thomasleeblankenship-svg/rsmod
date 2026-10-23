package net.runelite.client.plugins.microbot;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptHeartbeatRegistry;
import net.runelite.client.plugins.microbot.statemachine.StateMachineScript;
import net.runelite.client.plugins.microbot.statemachine.StateSnapshot;
import net.runelite.client.plugins.mobilemicrobot.MobileScript;
import net.runelite.client.plugins.mobilemicrobot.MobileScriptContext;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;

/**
 * Runs an upstream-style {@link Script} under the mobile plugin's {@link MobileScript} lifecycle.
 * <p>
 * Mobile-only. The plugin calls these methods on the client thread; none of them block or touch
 * game state. The script's own loop runs on its executor exactly as upstream schedules it.
 * <ul>
 *     <li>{@link #onStart}: requires an installed {@link MicrobotRuntime}, creates a <em>fresh</em>
 *     script instance and calls its launcher (for example {@code script.run(config)}). A launcher
 *     that returns false, throws or schedules no main loop fails the start.</li>
 *     <li>{@link #onGameTick}: throws if the main loop has ended, so the plugin fails closed and
 *     clears its Run setting. An exception or error that killed the loop is the thrown cause.</li>
 *     <li>{@link #onStop}: shuts the script down, then its executor, interrupting a body that is
 *     blocked in a wait. Idempotent. A later {@link #onStart} creates a new instance, so nothing
 *     scheduled by a previous run can execute after a restart.</li>
 * </ul>
 * The plugin already stops scripts on logout, world hop, disconnect, plugin disable and errors;
 * this adapter adds failure detection for the background loop and never restarts it by itself.
 *
 * @param <T> the script type
 */
@Slf4j
public final class MicrobotScriptAdapter<T extends Script> implements MobileScript {

    /** Starts a freshly created script, typically {@code script -> script.run(config)}. */
    @FunctionalInterface
    public interface Launcher<T> {
        /** @return false when the script refuses to start */
        boolean start(T script) throws Exception;
    }

    private final String name;
    private final Supplier<? extends T> factory;
    private final Launcher<? super T> launcher;
    private volatile T script;

    /**
     * @param name     shown in status text and errors
     * @param factory  creates a new script per start; must not return a shared instance
     * @param launcher starts the new instance
     */
    public MicrobotScriptAdapter(String name, Supplier<? extends T> factory, Launcher<? super T> launcher) {
        this.name = Objects.requireNonNull(name, "name");
        this.factory = Objects.requireNonNull(factory, "factory");
        this.launcher = Objects.requireNonNull(launcher, "launcher");
    }

    @Override
    public void onStart(MobileScriptContext context) {
        if (script != null) {
            throw new IllegalStateException(name + " is already started");
        }
        if (!MicrobotRuntime.isInstalled()) {
            throw new IllegalStateException(name + " cannot start: Microbot runtime is not installed");
        }
        T created = Objects.requireNonNull(factory.get(), "script factory returned null");
        boolean started;
        try {
            started = launcher.start(created);
        } catch (Exception e) {
            dispose(created);
            throw new IllegalStateException(name + " failed to start", e);
        }
        if (!started) {
            dispose(created);
            throw new IllegalStateException(name + " refused to start");
        }
        if (created.mainScheduledFuture == null) {
            dispose(created);
            throw new IllegalStateException(name + " started without scheduling its main loop");
        }
        script = created;
    }

    @Override
    public void onGameTick(MobileScriptContext context) {
        T current = script;
        if (current == null) {
            throw new IllegalStateException(name + " is not started");
        }
        if (!current.isRunning()) {
            Throwable cause = terminalCause(current.mainScheduledFuture);
            script = null;
            dispose(current);
            throw new IllegalStateException(name + " stopped unexpectedly", cause);
        }
    }

    @Override
    public void onStop(MobileScriptContext context) {
        T current = script;
        script = null;
        if (current != null) {
            dispose(current);
        }
    }

    @Override
    public String status() {
        T current = script;
        if (current == null) {
            return name + ": stopped";
        }
        StringBuilder text = new StringBuilder(name);
        if (current instanceof StateMachineScript) {
            StateSnapshot<?> snapshot = ((StateMachineScript<?>) current).getSnapshot();
            text.append(": ").append(snapshot == null ? "starting" : snapshot.currentState());
        } else {
            text.append(": running");
        }
        Map<String, Object> health = ScriptHeartbeatRegistry.getHealth(current.getClass().getName());
        if (health != null) {
            text.append(" (loops ").append(health.get("loopCount")).append(')');
        }
        return text.toString();
    }

    /** @return the running instance, or null; for diagnostics and tests */
    public T getScript() {
        return script;
    }

    private static void dispose(Script script) {
        try {
            script.shutdown();
        } catch (RuntimeException e) {
            log.warn("Script shutdown failed for {}", script.getClass().getName(), e);
        } finally {
            script.scheduledExecutorService.shutdownNow();
        }
    }

    /** Non-blocking: only called once the future is done. */
    private static Throwable terminalCause(ScheduledFuture<?> future) {
        if (future == null || !future.isDone() || future.isCancelled()) {
            return null;
        }
        try {
            future.get();
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (CancellationException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
