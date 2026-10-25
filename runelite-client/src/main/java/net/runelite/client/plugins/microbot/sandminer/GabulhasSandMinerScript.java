package net.runelite.client.plugins.microbot.sandminer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.sandminer.GabulhasSandMinerInfo.botStatus;
import static net.runelite.client.plugins.microbot.sandminer.GabulhasSandMinerInfo.states;

@Slf4j
public class GabulhasSandMinerScript extends Script {
    private final WorldPoint miningPoint = new WorldPoint(3165, 2905, 0);
    private final WorldPoint grinder = new WorldPoint(3150, 2908, 0);
    /** Longest wait for the grinder to take the sandstone; upstream waited without a limit. */
    private static final long DEPOSIT_TIMEOUT_MS = 30_000;
    /** Most empty waterskins one inventory can hold; bounds the drop loop. */
    private static final int MAX_WATERSKIN_DROPS = 28;
    /** {@link Microbot#enableAutoRunOn} as found at start, put back on stop (the flag is global). */
    private Boolean autoRunBefore;

    public boolean run(GabulhasSandMinerConfig config) {
        if (autoRunBefore == null) {
            autoRunBefore = Microbot.enableAutoRunOn;
        }
        Microbot.enableAutoRunOn = false;
        if (config.hopworldclose() || config.hopworldlong()) {
            // Mobile scripts do not hop worlds; the script keeps mining when another player is near.
            Microbot.log("Sandstone miner: world hopping is off on mobile; mining continues when players are near.");
        }
        if (config.turboMode()) {
            Rs2Camera.setZoom(Rs2Random.randomGaussian(100, 20));
            Rs2Camera.setYaw((Rs2Random.dicePercentage(50) ? Rs2Random.randomGaussian(750, 50) : Rs2Random.randomGaussian(1700, 50)));
            Rs2Camera.setPitch(Rs2Random.betweenInclusive(418, 512));
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                switch (botStatus) {
                    case Mining:
                        if (config.useHumidify()) humidifyIfNeeded();
                        miningLoop(config);
                        if (handleSafety()) return;
                        botStatus = states.Depositing;
                        break;
                    case Depositing:
                        deposit(config);
                        botStatus = states.Mining;
                        break;
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
                // Mobile: stop rather than retry a failing step every 600 ms.
                stopPlugin("Sandstone miner stopped after an error: " + ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    /** Walks to a bank and stops when health is low; returns whether it stopped. Mobile does not log out. */
    private boolean handleSafety() {
        var health = Rs2Player.getHealthPercentage();
        if (health < 25) {
            Microbot.log("Health is low: " + health + "%, banking for safety.");
            Rs2Bank.walkToBank();
            stopPlugin("Sandstone miner stopped: health is low (" + health + "%).");
            return true;
        }
        return false;
    }

    @Override
    public void shutdown() {
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
        Microbot.stopPlugin(GabulhasSandMinerPlugin.class);
    }


    private void miningLoop(GabulhasSandMinerConfig config) {
        boolean firstRock = true;
        if (!config.turboMode()) {
            Rs2Walker.walkTo(miningPoint, 0);
            sleep(100, 4000);
        }
        while (!Rs2Inventory.isFull() && super.isRunning()) {
            // Drop empty waterskins if not using humidify (only when idle)
            if (!config.useHumidify() && !Rs2Player.isAnimating()) {
                dropEmptyWaterskins();
            }

            if (!config.turboMode()) sleep(Rs2Random.nextInt(300, 5000, 0.1, true));
            if (!Rs2Player.isInteracting() || !Rs2Player.isAnimating()) {
                if (config.turboMode()) {
                    if (firstRock) {
                        WorldPoint innerMiningPoint = (Rs2Random.dicePercentage(50)) ?
                                new WorldPoint(3164, 2905, 0) : new WorldPoint(3166, 2905, 0);
                        var innerSandstoneRock = Microbot.getRs2TileObjectCache().query()
                                .withName("Sandstone rocks")
                                .nearestOnClientThread(innerMiningPoint, 0);
                        if (innerSandstoneRock != null) innerSandstoneRock.click("Mine");
                        Rs2Player.waitForXpDrop(Skill.MINING, 15000);
                        Rs2Antiban.actionCooldown();
                        firstRock = false;
                        continue;
                    }
                }
                var sandstoneRock = Microbot.getRs2TileObjectCache().query()
                        .withName("Sandstone rocks")
                        .nearestOnClientThread(miningPoint, 5);
                if (sandstoneRock != null) {
                    sandstoneRock.click("Mine");
                    if (config.turboMode()) {
                        Rs2Player.waitForXpDrop(Skill.MINING, 15000);
                    } else {
                        Rs2Player.waitForAnimation();
                    }
                    Rs2Antiban.actionCooldown();
                } else {
                    // No rock in reach (e.g. still walking): wait a tick rather than spin in turbo mode.
                    sleep(600);
                }
            }
        }
    }

    private void humidifyIfNeeded() {
        if (Rs2Equipment.isWearing("Circlet of water")) {
            return;
        }
        if (Rs2Magic.isSpellbook(Rs2Spellbook.LUNAR) && !Rs2Inventory.hasItem(ItemID.WATER_SKIN1, ItemID.WATER_SKIN2, ItemID.WATER_SKIN3, ItemID.WATER_SKIN4)) {
            Rs2Magic.cast(MagicAction.HUMIDIFY);
            sleepUntilOnClientThread(() -> Rs2Inventory.hasItem("Waterskin(4)"));
            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();
            sleep(1000, 2000);
        }
    }

    private void dropEmptyWaterskins() {
        for (int i = 0; i < MAX_WATERSKIN_DROPS && Rs2Inventory.hasItem(ItemID.WATER_SKIN0) && super.isRunning(); i++) {
            Rs2Inventory.drop(ItemID.WATER_SKIN0);
            sleep(550,650);
        }
    }

    private void deposit(GabulhasSandMinerConfig config) {
        if (!config.turboMode()) Rs2Walker.walkTo(grinder);
        var grinderObj = Microbot.getRs2TileObjectCache().query()
                .withId(26199)
                .nearestOnClientThread(grinder, 5);
        if (grinderObj != null) grinderObj.click("Deposit");
        long deadline = System.currentTimeMillis() + DEPOSIT_TIMEOUT_MS;
        while (Rs2Inventory.contains("Sandstone (1kg)", "Sandstone (2kg)", "Sandstone (5kg)", "Sandstone (10kg)") && super.isRunning()) {
            if (System.currentTimeMillis() > deadline) {
                // The next Depositing pass (after a mining pass that finds the inventory full) clicks the grinder again.
                Microbot.log("Sandstone miner: grinder did not take the sandstone, retrying.");
                return;
            }
            if (!config.turboMode()) {
                sleep(100, 3000);
            } else {
                sleepGaussian(300, 200);
            }
        }
    }
}

