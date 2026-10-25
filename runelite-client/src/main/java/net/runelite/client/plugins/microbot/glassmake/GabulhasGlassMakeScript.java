package net.runelite.client.plugins.microbot.glassmake;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.Notifier;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.glassmake.GabulhasGlassMakeInfo.states.*;


/**
 * Superglass Make: withdraws seaweed (or giant seaweed) and buckets of sand, casts Superglass Make, picks up
 * any molten glass that fell, and banks. Adapted for Microbot Mobile; see MOBILE_PORT.md in this package.
 */
@Slf4j
public class GabulhasGlassMakeScript extends Script {
    @Inject
    private Notifier notifier;

    private GabulhasGlassMakeInfo.items currentItem;

    private boolean oneTimeSpellBookCheck = false;
    // Mobile: enableAutoRunOn is global; the value before start is put back on stop.
    private Boolean autoRunBefore;

    public boolean run(GabulhasGlassMakeConfig config) {
        oneTimeSpellBookCheck = false;
        Rs2Antiban.antibanSetupTemplates.applyUniversalAntibanSetup();
        Rs2AntibanSettings.actionCooldownChance = 0.2;
        currentItem = config.ITEM();
        if (autoRunBefore == null) {
            autoRunBefore = Microbot.enableAutoRunOn;
        }
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                switch (GabulhasGlassMakeInfo.botStatus) {
                    case Starting:

                        GabulhasGlassMakeInfo.botStatus = GabulhasGlassMakeInfo.states.Banking;
                        break;
                    case Banking:
                        // Mobile: cast only once the bank trip worked; otherwise bank again next loop.
                        if (banking()) {
                            GabulhasGlassMakeInfo.botStatus = GabulhasGlassMakeInfo.states.Glassblowing;
                        }
                        break;
                    case Glassblowing:
                        glassblowing();
                        GabulhasGlassMakeInfo.botStatus = GabulhasGlassMakeInfo.states.Picking;
                        break;
                    case Picking:
                        picking();
                        GabulhasGlassMakeInfo.botStatus = GabulhasGlassMakeInfo.states.Banking;
                        break;

                }

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
                // Mobile: stop rather than retry a failing step every tick.
                stopPlugin("Glass Make stopped after an error: " + ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
        if (autoRunBefore != null) {
            Microbot.enableAutoRunOn = autoRunBefore;
            autoRunBefore = null;
        }
    }

    /** Stops with {@code message}, turning the plugin off (not just this loop) so the Microbot Mobile screen shows it. */
    private void stopPlugin(String message) {
        Microbot.showMessage(message);
        shutdown();
        Microbot.stopPlugin(GabulhasGlassMakePlugin.class);
    }

    // Mobile: open the bank with a bounded number of tries that stop with the script (upstream looped forever).
    private boolean openBankBounded() {
        for (int attempt = 0; attempt < 10 && !Rs2Bank.isOpen() && super.isRunning(); attempt++) {
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 1200);
        }
        return Rs2Bank.isOpen();
    }

    private void takeBreak() {
        if (Rs2Random.nextInt(0, 20, 1, true) == 30) {
            sleep(1000, 20000);
        }


    }

    private boolean banking() {
        takeBreak();

        if (!openBankBounded()) {
            return false;
        }
        Rs2Bank.depositAll("Molten Glass");
        sleepUntil(() -> !Rs2Inventory.contains("Molten Glass"), 100);
        if (currentItem == GabulhasGlassMakeInfo.items.GiantSeaweed) {
            if (Rs2Bank.count("Giant seaweed") < 3 || Rs2Bank.count("Bucket of sand") < 3) {
                notifier.notify("Out of materials");
                // Mobile: stop the plugin (upstream idled until stopped by hand, still showing as running).
                stopPlugin("Glass Make: out of materials");
                return false;
            }

            for (int i = 0; i < 3; i++) {
                Rs2Bank.withdrawOne("Giant seaweed");
            }

            Rs2Bank.withdrawX("Bucket of sand", 18);
        } else {
            if (Rs2Bank.count("Seaweed") < 3 || Rs2Bank.count("Bucket of sand") < 3) {
                notifier.notify("Out of materials");
                // Mobile: stop the plugin (upstream idled until stopped by hand, still showing as running).
                stopPlugin("Glass Make: out of materials");
                return false;
            }

            Rs2Bank.withdrawX("Bucket of sand", 13);
            Rs2Bank.withdrawX(401, 13);


        }


        sleep(60, 100);
        Rs2Bank.closeBank();
        // Mobile: bounded wait for the bank to close.
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
        return !Rs2Bank.isOpen();
    }

    private void glassblowing() {
        superglassmake();
        sleep(60, 100);
        sleepUntil(() -> Rs2Inventory.contains("Molten Glass"), 100);
    }

    private void superglassmake() {
        if (!oneTimeSpellBookCheck) {
            Rs2Magic.oneTimeSpellBookCheck();
            oneTimeSpellBookCheck = true;
        }
        if (Rs2Magic.quickCast(MagicAction.SUPERGLASS_MAKE)) {
            Rs2Bank.preHover();
            sleep(600 * 2, 600 * 4);
        }

    }


    private void picking() {
        if (!openBankBounded()) {
            return;
        }
        Rs2Bank.depositAll("Molten Glass");
        sleepUntil(() -> !Rs2Inventory.contains("Molten Glass"), 100);
        if (Rs2GroundItem.exists("Molten Glass", 1)) {
            sleep(60, 100);
            Rs2Bank.closeBank();
            // Mobile: bounded and stop-aware (upstream looted until none was left, whatever happened).
            for (int attempt = 0; attempt < 30 && super.isRunning() && Rs2GroundItem.exists("Molten Glass", 1); attempt++) {
                Rs2GroundItem.loot("Molten Glass", 1);
                sleep(600, 700);
            }
        }

    }
}

