package net.runelite.client.plugins.microbot.thievingstalls;

import lombok.AllArgsConstructor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.thievingstalls.constants.InventoryStrategyFetcher;
import net.runelite.client.plugins.microbot.thievingstalls.constants.ThievingSpotMapper;
import net.runelite.client.plugins.microbot.thievingstalls.model.BotApi;
import net.runelite.client.plugins.microbot.thievingstalls.model.IStallThievingSpot;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;


/**
 * Stall thiever: steals from the chosen stall from its safespot and banks or drops the loot. Adapted for
 * Microbot Mobile (no world hopping, stops turn the plugin off); see MOBILE_PORT.md in this package.
 */
@AllArgsConstructor(onConstructor_ = @Inject)
public class StallThievingScript extends Script {
    private BotApi botApi;
    private ThievingSpotMapper thievingSpotMapper;
    private InventoryStrategyFetcher inventoryStrategyMapper;
    // Mobile: enableAutoRunOn is global; the value before start is put back on stop.
    private final java.util.concurrent.atomic.AtomicReference<Boolean> autoRunBefore = new java.util.concurrent.atomic.AtomicReference<>();

    public boolean run(StallThievingConfig config) {
        autoRunBefore.compareAndSet(null, Microbot.enableAutoRunOn);
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                execute(config);

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
                // Mobile: stop rather than retry a failing step every 100 ms; turning the plugin off lets the
                // Microbot Mobile screen show it stopped.
                Microbot.showMessage("Stall Thieving stopped after an error: " + ex);
                shutdown();
                Microbot.stopPlugin(StallThievingPlugin.class);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private void execute(final StallThievingConfig config)
    {
        final IStallThievingSpot thievingSpot = thievingSpotMapper.getThievingSpot(config.THIEVING_SPOT());
        if (botApi.isInventoryFull()) {
            inventoryStrategyMapper.getInventoryStrategy(config).execute(thievingSpot);
            return;
        }

        thievingSpot.thieve();
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Boolean before = autoRunBefore.getAndSet(null);
        if (before != null) {
            Microbot.enableAutoRunOn = before;
        }
    }
}
