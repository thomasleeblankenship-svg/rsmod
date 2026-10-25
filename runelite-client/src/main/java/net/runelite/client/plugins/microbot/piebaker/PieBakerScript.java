package net.runelite.client.plugins.microbot.piebaker;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Pie Baker's loop for mobile: banks raw pies and casts Bake Pie. Upstream ran these steps from the
 * plugin's game-tick handler, on the client thread; mobile actions wait for the game, so they run here,
 * one step every 600 ms.
 */
public class PieBakerScript extends Script {
    /** Failed bank opens or casts in a row before the script gives up and turns the plugin off. */
    private static final int MAX_FAILURES = 5;
    /** Most casts one tick-perfect pass makes: one inventory of pies. */
    private static final int MAX_TICK_PERFECT_CASTS = 28;

    private int failures;

    /** Starts the loop for {@code rawPieId}; {@code -1} (an unknown pie name) turns the plugin off. */
    public boolean run(PieBakerConfig config, int rawPieId) {
        failures = 0;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;
                if (rawPieId == -1) {
                    stopPlugin("Pie Baker stopped: unknown pie \"" + config.selectedPie() + "\".");
                    return;
                }
                if (Rs2Inventory.hasItem(rawPieId)) {
                    bake(config, rawPieId);
                } else {
                    bank(rawPieId);
                }
                if (failures >= MAX_FAILURES) {
                    stopPlugin("Pie Baker stopped: could not open a bank or cast Bake Pie (runes, Lunar spellbook?).");
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
                // Mobile: stop rather than retry a failing step every 600 ms.
                stopPlugin("Pie Baker stopped after an error: " + ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void bake(PieBakerConfig config, int rawPieId) {
        // The spellbook is out of reach while the bank is open.
        if (Rs2Bank.isOpen() && !Rs2Bank.closeBank()) {
            failures++;
            return;
        }
        int casts = config.tickPerfect() ? MAX_TICK_PERFECT_CASTS : 1;
        for (int i = 0; i < casts && Rs2Inventory.hasItem(rawPieId) && super.isRunning(); i++) {
            if (!Rs2Magic.cast(MagicAction.BAKE_PIE)) {
                failures++;
                return;
            }
            failures = 0;
        }
    }

    private void bank(int rawPieId) {
        if (!Rs2Bank.isOpen()) {
            if (Rs2Bank.openBank()) {
                failures = 0;
            } else {
                failures++;
            }
            return;
        }
        // Keep runes and the rune pouch; upstream deposited everything and needed an empty inventory.
        if (!Rs2Inventory.onlyContains(PieBakerScript::isRuneItem)) {
            Rs2Bank.depositAllExcept(PieBakerScript::isRuneItem);
            return;
        }
        if (!Rs2Bank.hasItem(rawPieId)) {
            stopPlugin("Pie Baker stopped: no more raw pies in the bank.");
            return;
        }
        Rs2Bank.withdrawAll(rawPieId);
        sleepUntil(() -> Rs2Inventory.hasItem(rawPieId), 2_000);
    }

    static boolean isRuneItem(Rs2ItemModel item) {
        String name = item == null || item.getName() == null ? "" : item.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(" rune") || name.contains("rune pouch");
    }

    /** Stops with {@code message}, turning the plugin off (not just this loop) so the Microbot Mobile screen shows it. */
    private void stopPlugin(String message) {
        Microbot.showMessage(message);
        shutdown();
        Microbot.stopPlugin(PieBakerPlugin.class);
    }
}
