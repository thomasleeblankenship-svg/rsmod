package net.runelite.client.plugins.microbot;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.SplashScreen;
import org.slf4j.event.Level;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provenance: adapted from {@code chsami/Microbot} {@code BlockingEventManager.java} at
 * {@code e01e0d77d79b354472a5737b3aaab2449eb034fc} (BSD 2-Clause). The only change is which
 * default events are pre-registered; see the constructor.
 * <p>
 * Orchestrates detection and execution of blocking events that must run before scripts continue.
 * Maintains a prioritized list, periodically validates them with backoff, and runs at most one at a time on a dedicated executor.
 */
@Slf4j
public class BlockingEventManager
{
    private static final int MAX_QUEUE_SIZE = 10;
    private final List<BlockingEvent> blockingEvents = new CopyOnWriteArrayList<>();
    // Track which events are already in the queue
    private final Set<BlockingEvent> pendingEvents = ConcurrentHashMap.newKeySet();

    // Change the queue to hold just the event references
    private final BlockingQueue<BlockingEvent> eventQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    private final ExecutorService blockingExecutor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Getter
    private final ThreadFactory threadFactory = runnable -> {
        Thread t = new Thread(runnable, "Microbot-BlockingEvent");
        t.setDaemon(true);
        return t;
    };

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(threadFactory);
    private ScheduledFuture<?> loopFuture;
    private static final long INITIAL_DELAY_MS = 300;
    private volatile long currentDelay = INITIAL_DELAY_MS;
    private static final long MAX_DELAY_MS = 5000; // Maximum delay of 5 seconds
    private final AtomicInteger failureCount = new AtomicInteger(0);

    public BlockingEventManager()
    {
        // single-threaded executor for running event.execute()
        this.blockingExecutor = Executors.newSingleThreadExecutor(threadFactory);

        // Mobile: no events are pre-registered here; the mobile plugin adds the default popup
        // and tutorial events (addDefaultEvents) once the runtime is installed.
    }

    public synchronized void start() {
        if (loopFuture != null && !loopFuture.isCancelled() && !loopFuture.isDone()) {
            return;
        }
        startLoop();
    }

    public void shutdown() {
        if (loopFuture != null) loopFuture.cancel(true);
        scheduler.shutdownNow();
        blockingExecutor.shutdownNow();
    }

    /**
     * Mobile: the popup and tutorial events upstream pre-registers, added by the mobile plugin.
     * They act only while a script holds input. Not added: the events that silently change game
     * settings (level-up interface, hide roofs, world-switcher confirmation), the friend
     * recommendation survey (answered at random upstream), and plugin pause (mobile pauses through
     * {@code Microbot.pauseAllScripts}).
     */
    public void addDefaultEvents()
    {
        add(new net.runelite.client.plugins.microbot.util.events.WelcomeScreenEvent());
        add(new net.runelite.client.plugins.microbot.util.events.BankTutorialEvent());
        add(new net.runelite.client.plugins.microbot.util.events.DeathEvent());
        add(new net.runelite.client.plugins.microbot.util.events.BankJagexPopupEvent());
        add(new net.runelite.client.plugins.microbot.util.events.MembershipPromptEvent());
    }

    public void add(BlockingEvent event)
    {
        blockingEvents.add(event);
        sortBlockingEvents();
    }

    public void remove(BlockingEvent event)
    {
        blockingEvents.remove(event);
    }

    public List<BlockingEvent> getEvents()
    {
        return Collections.unmodifiableList(blockingEvents);
    }

    private void sortBlockingEvents()
    {
        blockingEvents.sort(
                Comparator.comparingInt((BlockingEvent e) -> e.priority().getLevel())
                        .reversed()
        );
    }

    private void startLoop() {
        loopFuture = scheduler.schedule(this::loopOnce, 0, TimeUnit.MILLISECONDS);
    }

    private void loopOnce() {
        try {
            validateAndEnqueueWithBackoff();
        } catch (Throwable t) {
            log.error("BlockingEvent loop error", t);
        } finally {
            // re-schedule using the latest currentDelay (volatile)
            loopFuture = scheduler.schedule(this::loopOnce, currentDelay, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Runs every 300ms on the scheduler thread: tries each event.validate()
     * and, if true, offers it into the queue (drops if full).
     */
    private void validateAndEnqueueWithBackoff() {
        boolean hasValidEvents = false;
        if (!SplashScreen.isOpen()) {
            for (BlockingEvent event : blockingEvents) {
                try {
                    if (event.validate()) {
                        hasValidEvents = true;
                        if (pendingEvents.contains(event) && eventQueue.isEmpty()) {
                            eventQueue.offer(event);
                        } else if (pendingEvents.add(event)) {
                            if (!eventQueue.offer(event)) {
                                pendingEvents.remove(event);
                            }
                        }
                    }
                } catch (Exception ex) {
                    Microbot.log("Error validating BlockingEvent (" + event.getName() + ")", Level.ERROR, ex);
                }
            }
        }

        if (!hasValidEvents) {
            // Increase delay exponentially
            int failures = failureCount.incrementAndGet();
            currentDelay = Math.min(INITIAL_DELAY_MS * (1L << Math.min(failures, 4)), MAX_DELAY_MS);
        } else {
            // Reset on success
            failureCount.set(0);
            currentDelay = INITIAL_DELAY_MS;
        }
    }

    /**
     * If an event is already running, returns true immediately.
     * Otherwise poll the queue; if we get an event, mark running and execute.
     */
    /**
     * Returns {@code true} if a blocking event is currently running or one was dequeued and scheduled.
     * Called from {@link net.runelite.client.plugins.microbot.Script#run()} to pause script loops until blockers finish.
     */
    public boolean shouldBlockAndProcess()
    {
        if (isRunning.get())
        {
            return true;
        }

        BlockingEvent event = eventQueue.poll();    
        if (event == null)
        {
            return false;
        }


        if (!isRunning.compareAndSet(false, true))
        {
            // Another thread started processing; re-queue so the event is not lost
            eventQueue.offer(event);
            return true;
        }

        blockingExecutor.execute(() -> {
            boolean executedSuccess = false;
            try
            {
                executedSuccess = event.execute();
            }
            catch (Exception ex)
            {
                Microbot.log("Error executing BlockingEvent (" + event.getName() + ")", Level.ERROR, ex);
            }
            finally
            {
                if (executedSuccess) {
                    log.debug("BlockingEvent {} executed successfully", event.getName());
                    pendingEvents.remove(event);
                } else {
                    //queue it back if execution failed
                    log.debug("BlockingEvent {} execution failed, re-queuing", event.getName());
                    if (!eventQueue.offer(event)) {
                        log.debug("BlockingEvent queue is full, dropping event: {}", event.getName());
                        pendingEvents.remove(event);
                    }
                }
                isRunning.set(false);
            }
        });

        return true;
    }
}
