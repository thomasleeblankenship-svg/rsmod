package net.runelite.client.plugins.microbot.varrockcleaner;


import net.runelite.api.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

/**
 * Varrock Museum find cleaner: takes uncleaned finds from the dig-site rocks, cleans them at the specimen
 * table, hands the finds in at the storage crate and drops the leftovers. Adapted for Microbot Mobile; see
 * MOBILE_PORT.md in this package.
 */
public class VarrockCleanerScript extends Script {

    /** How long the player may stand idle before a repeating take or clean counts as finished. */
    private static final int IDLE_MILLIS = 1800;

    private enum State {
        TAKE_UNCLEANED,
        CLEAN_FIND,
        STORAGE_CRATE,
        DROP_ITEMS
    }

    private State currentState = State.TAKE_UNCLEANED;

    public boolean run(VarrockCleanerConfig config) {
        shutdown();
        currentState = State.TAKE_UNCLEANED;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;

                switch (currentState) {
                    case TAKE_UNCLEANED:
                        takeUncleanedFinds();
                        break;
                    case CLEAN_FIND:
                        cleanFinds();
                        break;
                    case STORAGE_CRATE:
                        storeFindsInCrate();
                        break;
                    case DROP_ITEMS:
                        dropUnwantedItems();
                        break;
                }

            } catch (Exception ex) {
                // Mobile: stop rather than retry a failing step every 100 ms. Turning the plugin off (not just
                // this loop) lets the Microbot Mobile screen show it stopped.
                Microbot.log("Varrock Cleaner stopped after an error: " + ex);
                Microbot.stopPlugin(VarrockCleanerPlugin.class);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);

        return true;
    }

    private void takeUncleanedFinds() {
        if (Rs2Inventory.isFull()) {
            currentState = State.CLEAN_FIND;
            return;
        }
        if (Microbot.getRs2TileObjectCache().query().interact(ObjectID.DIG_SITE_SPECIMEN_ROCKS, "Take")) {
            // Mobile: let the take run instead of clicking the rocks again every loop.
            sleepUntil(Rs2Player::isAnimating, 5000);
            sleepUntil(() -> Rs2Inventory.isFull() || !Rs2Player.isAnimating(IDLE_MILLIS), 60000);
        }
        if (Rs2Inventory.isFull()) {
            currentState = State.CLEAN_FIND;
        }
    }

    private void cleanFinds() {
        if (Rs2Inventory.contains("Uncleaned find") && Microbot.getRs2TileObjectCache().query().interact(ObjectID.SPECIMEN_TABLE_24556, "Clean")) {
            sleepUntil(() -> !Rs2Inventory.contains("Uncleaned find"), 30000);
            if (!Rs2Inventory.contains("Uncleaned find")) {
                currentState = State.STORAGE_CRATE;
            }
        }
    }

    private void storeFindsInCrate() {
        String[] rareItems = {
                "Old symbol",
                "Ancient symbol",
                "Old coin",
                "Ancient coin",
                "Clean necklace",
                "Pottery",
                "Jewellery",
                "Old chipped vase",
                "Arrowheads"
        };

        if (!Rs2Inventory.contains("Uncleaned find") && Microbot.getRs2TileObjectCache().query().interact(ObjectID.STORAGE_CRATE, "Add finds")) {
            // Mobile: answer option 2 (as upstream's key press) only once the menu is showing, retrying until the
            // key is accepted; with no menu the crate took the finds directly.
            if (Rs2Dialogue.sleepUntilSelectAnOption()) {
                sleepUntil(() -> Rs2Dialogue.keyPressForDialogueOption(2), 3000);
            }
            sleep(1000);

            boolean hasRareItems = false;
            for (String rareItem : rareItems) {
                if (Rs2Inventory.contains(rareItem)) {
                    hasRareItems = true;
                    break;
                }
            }

            if (!hasRareItems) {
                currentState = State.DROP_ITEMS;
            }
        }
    }

    private void dropUnwantedItems() {
        String[] itemsToKeep = {"Antique lamp", "Trowel", "Rock pick", "Specimen brush"};
        if (Rs2Inventory.dropAllExcept(itemsToKeep)) {
            currentState = State.TAKE_UNCLEANED;
        }
    }

    public void stop() {
        Microbot.log("Varrack Cleaner plugin stopped.");
        currentState = State.TAKE_UNCLEANED;
        super.shutdown();
    }
}
