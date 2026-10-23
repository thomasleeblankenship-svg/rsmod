package net.runelite.client.plugins.microbot;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptHeartbeatRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for Microbot automation scripts.
 * Provides scheduling helpers, guards against client-thread misuse, and common shutdown/reset logic.
 * <p>
 * Provenance: adapted from {@code chsami/Microbot} {@code Script.java} at
 * {@code e01e0d77d79b354472a5737b3aaab2449eb034fc} (BSD 2-Clause). The fields, constructor-time
 * executor, {@link #isRunning()}, {@link #shutdown()} and {@link #run()} keep upstream's names and
 * signatures, so upstream subclasses compile unchanged. Adaptations, each marked {@code Mobile:}:
 * <ul>
 *     <li>The executor drops cancelled tasks from its queue and lets idle threads expire, so a
 *     stopped script leaves no queued work and, after the keep-alive, no threads.</li>
 *     <li>{@link #run()} refuses to continue when the runtime is not installed and stops the
 *     script when logged in but game ticks have stalled.</li>
 *     <li>Not ported from {@link #run()}: session fatigue, the tutorial-island bypass, auto-run
 *     and stamina use. Not ported from {@link #shutdown()}: walker, path, held-key and
 *     special-attack resets. Those helpers do not exist on mobile yet.</li>
 *     <li>Human takeover reads {@link MicrobotRuntime#humanOwnsInput()} in place of
 *     {@code InputArbiter.isHuman()}.</li>
 * </ul>
 */
@Slf4j
public abstract class Script extends Global implements IScript {
    /** Mobile: how long an idle pool thread survives once a script stops scheduling work. */
    static final long IDLE_THREAD_KEEP_ALIVE_SECONDS = 10;

    private final AtomicBoolean startAnnounced = new AtomicBoolean();

    private void announceStart() {
        if (Microbot.getEventBus() != null && startAnnounced.compareAndSet(false, true)) {
            Microbot.getEventBus().post(new ScriptStarted(this));
        }
    }

    protected ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(10,
        new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread t = new Thread(r);
                t.setName(Script.this.getClass().getSimpleName() + "-" + threadNumber.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        }) {
            {
                // Mobile: no stale cancelled loops in the queue, no idle threads kept forever.
                setRemoveOnCancelPolicy(true);
                setKeepAliveTime(IDLE_THREAD_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS);
                allowCoreThreadTimeOut(true);
            }

            @Override
            public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,
                    long delay, TimeUnit unit) {
                // Announce before a zero-delay loop can report a terminal supply failure.
                announceStart();
                return super.scheduleWithFixedDelay(command, initialDelay, delay, unit);
            }

            @Override
            public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay,
                    long period, TimeUnit unit) {
                announceStart();
                return super.scheduleAtFixedRate(command, initialDelay, period, unit);
            }
        };
    protected ScheduledFuture<?> scheduledFuture;
    protected ScheduledFuture<?> mainScheduledFuture;

    /**
     * Indicates whether the main scheduled script loop is still active.
     */
    public boolean isRunning() {
        return mainScheduledFuture != null && !mainScheduledFuture.isDone();
    }

    @Getter
    protected static WorldPoint initialPlayerLocation;

    /**
     * Cancel scheduled tasks, clear shared state, and reset helpers.
     * Safe to call multiple times; no-ops if already shut down.
     */
    public void shutdown() {
        startAnnounced.set(false);
        ScriptHeartbeatRegistry.remove(this.getClass().getName());
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            mainScheduledFuture.cancel(true);
            initialPlayerLocation = null;
            Microbot.pauseAllScripts.set(false);
        }
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(true);
        }
    }

    /**
     * Default pre-loop guard invoked by script schedulers.
     * Returns {@code false} to pause a loop when a blocking event is executing, scripts are paused,
     * the human owns input, or the current thread is interrupted.
     */
    public boolean run() {
        ScriptHeartbeatRegistry.recordHeartbeat(this.getClass().getName());

        // Mobile: no client services to act on; never continue against a torn-down runtime.
        BlockingEventManager blockingEventManager = Microbot.getBlockingEventManager();
        if (!MicrobotRuntime.isInstalled() || blockingEventManager == null) {
            return false;
        }

        // Mobile: the engine stopped ticking while logged in (backgrounded, frozen, disconnected
        // without a state change yet). Fail closed; the owner must start the script again.
        if (MicrobotRuntime.isGameTickStale()) {
            log.warn("[{}] No game tick for {}ms while logged in; stopping script",
                    getClass().getSimpleName(), MicrobotRuntime.millisSinceLastGameTick());
            Microbot.status = "Stopped: game ticks stalled";
            shutdown();
            return false;
        }

        if (blockingEventManager.shouldBlockAndProcess()) {
            // A blocking event was found & is executing
            return false;
        }
        boolean humanOwnsInput = MicrobotRuntime.humanOwnsInput();
        if (Microbot.pauseAllScripts.get() || humanOwnsInput)
            return false;
        if (Thread.currentThread().isInterrupted())
            return false;

        return true;
    }
}
