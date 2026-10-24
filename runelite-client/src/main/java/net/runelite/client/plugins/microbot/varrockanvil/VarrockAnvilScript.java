package net.runelite.client.plugins.microbot.varrockanvil;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.varrockanvil.enums.AnvilItem;
import net.runelite.client.plugins.microbot.varrockanvil.enums.Bars;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

enum State {
    SMITHING,
    BANKING,
    WALK_TO_ANVIL,
    WALK_TO_BANK,
}

/**
 * Varrock West anvil smither: banks for bars and a hammer at Varrock West bank and smiths the selected item
 * at the nearby anvil. Adapted for Microbot Mobile; see MOBILE_PORT.md in this package.
 */
@Slf4j
public class VarrockAnvilScript extends Script {

    public State state = State.BANKING;
    public String debug = "";
    private boolean expectingXPDrop = false;

    private static WorldPoint AnvilLocation = new WorldPoint(3188, 3426, 0);
    private static WorldPoint BankLocation = new WorldPoint(3185, 3438, 0);
    private static int AnvilMakeVarbitPlayer = 2224;
    private static int AnvilContainerWidgetID = 312;
    // Mobile: enableAutoRunOn is global; the value before start is put back on stop.
    private boolean autoRunBefore = true;

    public boolean run(VarrockAnvilConfig config) {
        Bars barType = config.sBarType();
        AnvilItem anvilItem = config.sAnvilItem();
        // Mobile: the "Logout On Complete" setting is not read; stopping never logs out (see MOBILE_PORT.md).
        autoRunBefore = Microbot.enableAutoRunOn;
        Microbot.enableAutoRunOn = false;

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applySmithingSetup();
        Rs2AntibanSettings.dynamicActivity = true;
        Rs2AntibanSettings.dynamicIntensity = true;
        Rs2AntibanSettings.actionCooldownChance = 0.1;
        Rs2AntibanSettings.microBreakChance = 0.01;
        Rs2AntibanSettings.microBreakDurationLow = 0;
        Rs2AntibanSettings.microBreakDurationHigh = 3;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                loop(barType, anvilItem);
            } catch (Exception ex) {
                // Mobile: an uncaught exception would silently end the schedule while the plugin still looked
                // running; turn the plugin off so the Microbot Mobile screen shows it stopped.
                stop("Varrock Anvil stopped after an error: " + ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void loop(Bars barType, AnvilItem anvilItem) {
            if (!super.run() || !Microbot.isLoggedIn()) {
                debug("Not running");
                return;
            }

            if (Rs2AntibanSettings.actionCooldownActive) {
                debug("Cool down active");
                Rs2Antiban.actionCooldown();
                return;
            }

            determineState(barType, anvilItem);

            // If the state is not smithing, then let's reset the variable as we are not expecting an XP drop
            if (state != State.SMITHING) {
                expectingXPDrop = false;
            }

            if (Rs2Dialogue.hasContinue()) {
                debug("Click to continue");
                Rs2Dialogue.clickContinue();
                expectingXPDrop = false;
                return;
            }

            switch (state) {
                case SMITHING:
                    if (Rs2Inventory.count(barType.toString()) < anvilItem.getRequiredBars()) {
                        debug("Out of bars");
                        return;
                    }

                    if (expectingXPDrop && Rs2Player.waitForXpDrop(Skill.SMITHING, 7500)) {
                        debug("Smithing in progress");
                        Rs2Antiban.actionCooldown();
                        Rs2Antiban.takeMicroBreakByChance();
                        sleep(256, 789);
                        return;
                    }

                    if (Microbot.getRs2TileObjectCache().query().withId(2097).interact()) {
                        debug("Using anvil");

                        // Wait until anvil screen is open
                        sleepUntil(() -> Rs2Widget.getWidget(AnvilContainerWidgetID, 1) != null, 10000);
                        sleep(186, 480);

                        if (Rs2Widget.getWidget(AnvilContainerWidgetID, 1) != null) {
                            // Mobile: the "All" and item clicks fall within one game tick of each other and either
                            // can be refused under the one-action-per-tick rule, so each is retried until accepted.
                            if (Microbot.getVarbitPlayerValue(AnvilMakeVarbitPlayer) < Rs2Inventory.count(barType.getId())) {
                                debug("Selecting 'All' in the anvil");
                                sleepUntil(() -> Rs2Widget.clickWidget(312, 7), 2000);
                                sleep(186, 480);
                            }

                            if (sleepUntil(() -> Rs2Widget.clickWidget(AnvilContainerWidgetID, anvilItem.getChildId()), 2000)) {
                                expectingXPDrop = true;
                            }
                            sleep(186, 480);
                        }
                    } else {
                        if (Rs2Player.isMoving()) {
                            return;
                        }
                    }

                    break;

                case BANKING:
                    debug("Banking");
                    bank(barType);
                    break;

                case WALK_TO_BANK:
                    if (Rs2Player.isMoving()) {
                        return;
                    }

                    if (!Rs2Player.isRunEnabled()) {
                        debug("Enabled run for bank");
                        Rs2Player.toggleRunEnergy(true);
                    }
                    Rs2Bank.openBank();
                    break;

                case WALK_TO_ANVIL:
                    if (Rs2Player.isMoving()) {
                        return;
                    }

                    if (!Rs2Player.isRunEnabled()) {
                        debug("Enabled run to anvil");
                        Rs2Player.toggleRunEnergy(true);
                    }

                    debug("Walking to anvil");
                    Rs2Walker.walkTo(AnvilLocation, 10);
                    break;

                default:
                    break;
            }

            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();
            sleep(256, 789);
    }

    // Determine the state of the script
    private void determineState(Bars barType, AnvilItem anvilItem) {
        debug("Determine state");

        if (Rs2Inventory.hasItemAmount(barType.toString(), anvilItem.getRequiredBars()) && Rs2Inventory.hasItem(ItemID.HAMMER)) {
            if (!closeToLocation(AnvilLocation)) {
                state = State.WALK_TO_ANVIL;
                debug("Walking to anvil");
            } else {
                state = State.SMITHING;
                debug("Smithing");
            }
        } else {
            if (!closeToLocation(BankLocation)) {
                state = State.WALK_TO_BANK;
                debug("Walking to bank");
            } else {
                debug("Banking for supplies");
                state = State.BANKING;
            }
        }
    }

    // Handle all banking actions
    private void bank(Bars barType) {
        if (Rs2Bank.openBank()) {
            sleepUntil(Rs2Bank::isOpen);
            debug("Bank is open");
            Rs2Bank.depositAllExcept(ItemID.HAMMER, barType.getId());
            debug("Items deposited");
            sleep(180, 540);

            if (!Rs2Inventory.hasItem(ItemID.HAMMER)) {
                Rs2Bank.withdrawOne(ItemID.HAMMER);
                sleepUntil(() -> Rs2Inventory.hasItem(ItemID.HAMMER), 3500);

                // Exit if we did not end up finding it.
                if (!Rs2Inventory.hasItem(ItemID.HAMMER)) {
                    Rs2Bank.closeBank();
                    stop("Could not find hammer in bank.");
                    return;
                }
                sleep(180, 540);

            }

            if (Rs2Bank.count(barType.toString()) < 1) {
                Rs2Bank.closeBank();
                stop("Out of bars.");
                return;
            }
            Rs2Bank.withdrawAll(barType.toString());
            sleepUntil(() -> Rs2Inventory.hasItem(barType.toString()), 3500);

            // Exit if we did not end up finding it.
            if (!Rs2Inventory.hasItem(barType.toString())) {
                stop("Could not find bars in bank.");
                return;
            }
            sleep(180, 540);
            Rs2Bank.closeBank();
        }
    }

    private void debug(String msg) {
        debug = msg;
        // Mobile: debug level, not stdout, so a 1 s loop does not flood the device log.
        log.debug(msg);
    }

    /**
     * Stops with {@code message}. Mobile: never logs out, and turns the plugin off (not just this loop) so the
     * Microbot Mobile screen and plugin list show it stopped.
     */
    public void stop(String message) {
        debug(message);
        Microbot.showMessage(message);
        shutdown();
        Microbot.stopPlugin(VarrockAnvilPlugin.class);
    }

    private boolean closeToLocation(WorldPoint location) {
        return Rs2Player.getWorldLocation().distanceTo(location) <= 10;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
        Microbot.enableAutoRunOn = autoRunBefore;
    }
}
