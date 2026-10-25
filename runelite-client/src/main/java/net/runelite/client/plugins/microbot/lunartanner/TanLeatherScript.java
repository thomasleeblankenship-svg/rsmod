package net.runelite.client.plugins.microbot.lunartanner;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.http.api.item.ItemPrice;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** Casts Tan Leather on hides and banks the leather; mobile port notes in {@code MOBILE_PORT.md}. */
public class TanLeatherScript extends Script {
    /** Failed casts in a row before the script gives up (missing runes, wrong spellbook). */
    private static final int MAX_CAST_FAILURES = 5;

    public static String combinedMessage = "";
    public static long hidesTanned = 0;
    private long profitPerHide = 0;
    private long startTime;

    // State management
    private enum State {
        TANNING,
        BANKING
    }

    private State currentState = State.TANNING;
    private int castFailures;

    public boolean run(TanLeatherConfig config) {
        startTime = System.currentTimeMillis();
        castFailures = 0;
        profitPerHide = price(config.ITEM().getFinished()) - price(config.ITEM().getName());

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) return;
                if (Rs2Inventory.hasItem(config.ITEM().getName(), true)) {
                    int initialHideCount = Rs2Inventory.count(config.ITEM().getFinished());
                    // Mobile: wait (bounded) for the leather instead of spinning until it appears, which never
                    // ended when the cast failed.
                    boolean tanned = Rs2Magic.cast(MagicAction.TAN_LEATHER)
                            && sleepUntil(() -> Rs2Inventory.count(config.ITEM().getFinished()) != initialHideCount, 3_000);
                    if (!tanned) {
                        if (++castFailures >= MAX_CAST_FAILURES) {
                            stopPlugin("Tan Leather stopped: the spell could not be cast (runes, Lunar spellbook?).");
                        }
                        return;
                    }
                    castFailures = 0;

                    int hidesTannedThisAction = Rs2Inventory.count(config.ITEM().getFinished()) - initialHideCount;
                    hidesTanned += hidesTannedThisAction;
                } else {
                    bank(config);
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
                // Mobile: stop rather than retry a failing step.
                stopPlugin("Tan Leather stopped after an error: " + ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    // Calculate the profit and display it
    private void calculateProfitAndDisplay(TanLeatherConfig config) {
        double elapsedHours = (System.currentTimeMillis() - startTime) / 3600000.0;
        int hidesPerHour = elapsedHours > 0 ? (int) (hidesTanned / elapsedHours) : 0;
        long totalProfit = profitPerHide * hidesTanned;
        long profitPerHour = profitPerHide * hidesPerHour;

        // Format the message
        combinedMessage = config.ITEM().getFinished() + ": " +
                QuantityFormatter.quantityToRSDecimalStack((int) hidesTanned) + " (" +
                QuantityFormatter.quantityToRSDecimalStack(hidesPerHour) + "/hr) | " +
                "Profit: " + QuantityFormatter.quantityToStackSize(totalProfit) + " (" +
                QuantityFormatter.quantityToStackSize(profitPerHour) + "/hr)";
    }

    // Bank the finished hide
    private void bank(TanLeatherConfig config) {
        if (currentState != State.BANKING) {
            currentState = State.BANKING;
            if (!Rs2Bank.openBank()) {
                // Mobile: retry on the next pass; upstream stayed in BANKING and never banked again.
                currentState = State.TANNING;
                return;
            }

            Rs2Bank.depositAll(config.ITEM().getFinished());
            sleepUntilOnClientThread(() -> !Rs2Inventory.hasItem(config.ITEM().getFinished()));

            if (Rs2Bank.hasItem(config.ITEM().getName())) {
                Rs2Bank.withdrawAll(config.ITEM().getName());
                sleepUntilOnClientThread(() -> Rs2Inventory.hasItem(config.ITEM().getName()));
            } else {
                stopPlugin("No more " + config.ITEM().getName() + " to tan.");
                return;
            }

            Rs2Bank.closeBank();
            currentState = State.TANNING;
            calculateProfitAndDisplay(config);
        }
    }

    /** Grand Exchange price of the first item matching {@code name}, or 0 when none matches. */
    private static long price(String name) {
        List<ItemPrice> matches = Microbot.getItemManager().search(name);
        return matches == null || matches.isEmpty() ? 0 : matches.get(0).getPrice();
    }

    /** Stops with {@code message}, turning the plugin off (not just this loop) so the Microbot Mobile screen shows it. */
    private void stopPlugin(String message) {
        Microbot.showMessage(message);
        shutdown();
        Microbot.stopPlugin(TanLeatherPlugin.class);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        hidesTanned = 0; // Reset the count of tanned hides
        combinedMessage = ""; // Reset the combined message
        currentState = State.TANNING; // Reset the current state
    }
}
