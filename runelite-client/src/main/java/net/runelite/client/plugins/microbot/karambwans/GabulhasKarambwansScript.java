package net.runelite.client.plugins.microbot.karambwans;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.karambwans.enums.KarambwanBankLocation;
import net.runelite.client.plugins.microbot.karambwans.enums.FairyRingAccessMethod;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.karambwans.GabulhasKarambwansInfo.botStatus;
import static net.runelite.client.plugins.microbot.karambwans.GabulhasKarambwansInfo.states;

/**
 * Karambwan fisher: fishes karambwans at the DKP fairy ring spot and banks in Zanaris (or Seers' with the
 * Kandarin hard diary), returning by fairy ring. Adapted for Microbot Mobile; see MOBILE_PORT.md.
 */
@Slf4j
public class GabulhasKarambwansScript extends Script {
    // Zanaris fairy ring object ID — Rs2GameObject.getAll() does NOT find it; use the tile object cache.
    public static final int FAIRY_RING_ID = 29560;
    public static final int SPIRITUAL_FAIRY_TREE_ID = 35003;
    // Fairy ring is at 4434, not 4435 — off-by-one causes findObjectByLocation to miss it.
    private final WorldPoint zanarisRingPoint = new WorldPoint(2412, 4434, 0);
    private final WorldPoint fishingPoint = new WorldPoint(2899, 3118, 0);
    private final WorldPoint bankPoint = new WorldPoint(2381, 4455, 0);
    private GabulhasKarambwansConfig config;
    // Mobile: enableAutoRunOn is global; the value before start is put back on stop.
    private Boolean autoRunBefore;

    public boolean run(GabulhasKarambwansConfig config) {
        this.config = config;
        if (autoRunBefore == null) {
            autoRunBefore = Microbot.enableAutoRunOn;
        }
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.setActivity(Activity.CATCHING_RAW_KARAMBWAN);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                switch (botStatus) {
                    case FISHING:
                        fishingLoop();
                        Rs2Antiban.takeMicroBreakByChance();
                        botStatus = states.WALKING_TO_RING_TO_BANK;
                        Rs2Player.waitForAnimation();
                        break;
                    case WALKING_TO_RING_TO_BANK:
                        // Mobile: move on only once the ring click was accepted (or no ring is needed).
                        if (walkToRingToBank()) {
                            Rs2Random.waitEx(400, 200);
                            botStatus = states.WALKING_TO_BANK;
                        }
                        break;
                    case WALKING_TO_BANK:
                        doBank();
                        botStatus = states.BANKING;
                        Rs2Random.waitEx(400, 200);
                        break;
                    case BANKING:
                        useBank();
                        botStatus = states.WALKING_TO_FISH;
                        Rs2Random.waitEx(400, 200);
                        break;
                    case WALKING_TO_FISH:
                        walkToFish();
                        botStatus = states.FISHING;
                        Rs2Random.waitEx(400, 200);
                        break;
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
                // Mobile: stop rather than retry a failing step every second.
                stopPlugin("Karambwan fisher stopped after an error: " + ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
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
        Microbot.stopPlugin(GabulhasKarambwansPlugin.class);
    }

    private void fishingLoop() {
        while (!Rs2Inventory.isFull() && super.isRunning()) {
            if (!Rs2Player.isAnimating()) {
                if (Rs2Inventory.contains(ItemID.TBWT_RAW_KARAMBWANJI)) {
                    interactWithFishingSpot();
                    Rs2Player.waitForAnimation();
                    sleep(2000, 4000);
                } else {
                    stopPlugin("Raw karambwanji not detected. Shutting down");
                    return;
                }
            }
            // Mobile: pause between checks; upstream spun here while the player was fishing.
            sleep(600);
        }
    }

    private boolean walkToRingToBank() {
        if (config.bankLocation() != KarambwanBankLocation.NONE) {
            return true;
        }
        WorldPoint ringLocation = new WorldPoint(2900, 3111, 0); // Karamja ring
        var fairyRing = net.runelite.client.plugins.microbot.mobile.MobileActions.read(
            () -> Microbot.getRs2TileObjectCache().query().nearest(ringLocation, 3), null);
        if (fairyRing != null && fairyRing.click("Zanaris")) {
            Rs2Player.waitForAnimation();
            return true;
        }
        return false;
    }

    private void doBank() {
        if (config.bankLocation() == KarambwanBankLocation.SEERS &&
                Microbot.getVarbitValue(VarbitID.KANDARIN_DIARY_HARD_COMPLETE) == 1
                && Rs2Magic.hasRequiredRunes(Rs2Spells.CAMELOT_TELEPORT))
        {
            Rs2Magic.cast(Rs2Spells.CAMELOT_TELEPORT, "Seers'", 2);
            sleepUntil(() -> Rs2Bank.isNearBank(net.runelite.client.plugins.microbot.util.bank.enums.BankLocation.CAMELOT, 15), 5000);
        } else {
            Rs2Walker.walkTo(bankPoint, 3);
            // Mobile: bounded wait; upstream looped forever when the walk did not get there.
            sleepUntil(() -> Rs2Player.isInArea(bankPoint, 4) || !super.isRunning(), 20000);
        }
        if (!Rs2Bank.walkToBankAndUseBank()) {
            Rs2Bank.openBank();
        }
    }

    private void useBank() {
        Rs2Bank.depositAll(ItemID.TBWT_RAW_KARAMBWAN);
        Rs2Inventory.waitForInventoryChanges(2000);
        if (Rs2Inventory.contains("scroll") || Rs2Inventory.contains("Scroll")) {
            Rs2Bank.depositAll("scroll");
            Rs2Bank.depositAll("Scroll");
            Rs2Inventory.waitForInventoryChanges(2000);
        }
        if (Rs2Inventory.contains("scrollbox") || Rs2Inventory.contains("Scrollbox")) {
            Rs2Bank.depositAll("scrollbox");
            Rs2Bank.depositAll("Scrollbox");
            Rs2Inventory.waitForInventoryChanges(2000);
        }
        if (Rs2Inventory.contains(ItemID.FISH_BARREL_OPEN) || Rs2Inventory.contains(ItemID.FISH_BARREL_CLOSED)) {
            Rs2Bank.emptyFishBarrel();
            Rs2Inventory.waitForInventoryChanges(2000);
        }



        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 3000);
    }

    private void interactWithFishingSpot() {
        Microbot.getRs2NpcCache().query().withId(NpcID._0_45_48_KARAMBWAN).interact("Fish");
    }

    private void walkToFish() {
        if (config.fairyRingAccessMethod() == FairyRingAccessMethod.POH) {
            if (hasPohCape()) {
                Rs2Equipment.interact(net.runelite.api.EquipmentInventorySlot.CAPE, "Tele to POH");
            } else if (Rs2Inventory.hasItem("Teleport to house")) {
                Rs2Inventory.interact("Teleport to house", "Break");
            } else {
                Rs2Magic.quickCast(MagicAction.TELEPORT_TO_HOUSE);
            }
            sleepUntil(() -> Microbot.getRs2TileObjectCache().query().withId(FAIRY_RING_ID).nearest() != null || Microbot.getRs2TileObjectCache().query().withId(SPIRITUAL_FAIRY_TREE_ID).nearest() != null, 5000);

            boolean interacted = Microbot.getRs2TileObjectCache().query().interact(FAIRY_RING_ID, "Last-destination (DKP)") || 
                                 Microbot.getRs2TileObjectCache().query().interact(SPIRITUAL_FAIRY_TREE_ID, "Last-destination (DKP)");

            if (interacted) {
                waitTillPlayerNextToFishingSpot();
            }
        } else {
            Rs2Walker.walkTo(zanarisRingPoint, 3);
            Rs2Player.waitForWalking();

            sleepUntil(() -> Microbot.getRs2TileObjectCache().query().withId(FAIRY_RING_ID).nearestOnClientThread() != null, 5000);

            // Action is "Last-destination", NOT "Last-destination (DKP)" — the code suffix is not part of the action text.
            boolean interacted = Microbot.getRs2TileObjectCache().query()
                    .withId(FAIRY_RING_ID)
                    .nearestOnClientThread() != null
                    && Microbot.getRs2TileObjectCache().query()
                    .withId(FAIRY_RING_ID)
                    .nearestOnClientThread()
                    .click("Last-destination");

            if (interacted) {
                waitTillPlayerNextToFishingSpot();
            } else {
                Rs2Player.waitForWalking();
            }
        }
    }

    private boolean hasPohCape() {
        return Rs2Equipment.isWearing("Construct. cape") || 
               Rs2Equipment.isWearing("Construct. cape(t)") || 
               Rs2Equipment.isWearing("Max cape") || 
               Rs2Equipment.isWearing("Max cape(t)");
    }

    private void waitTillPlayerNextToFishingSpot() {
        sleepUntil(() -> Rs2Player.distanceTo(fishingPoint) < 2);
    }
}

