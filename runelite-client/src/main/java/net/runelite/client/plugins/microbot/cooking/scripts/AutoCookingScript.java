package net.runelite.client.plugins.microbot.cooking.scripts;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.AnimationID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.cooking.AutoCookingConfig;
import net.runelite.client.plugins.microbot.cooking.AutoCookingPlugin;
import net.runelite.client.plugins.microbot.cooking.enums.CookingAreaType;
import net.runelite.client.plugins.microbot.cooking.enums.CookingItem;
import net.runelite.client.plugins.microbot.cooking.enums.CookingLocation;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

enum CookingState {
    COMBINE,
    COOKING,
    WALKING,
    BANKING,
    DROPPING,
}

/**
 * Auto Cooking's "Cooking" activity: banks for the selected raw food and cooks it at a chosen or the nearest
 * range or fire. Adapted for Microbot Mobile; see MOBILE_PORT.md in the cooking package.
 */
@Slf4j
public class AutoCookingScript extends Script {

    private CookingState state;
    // Mobile: enableAutoRunOn is global; the value before start is put back on stop.
    private boolean autoRunBefore = true;
    private boolean init;
    private CookingLocation location;

    public boolean run(AutoCookingConfig config) {
        autoRunBefore = Microbot.enableAutoRunOn;
        Microbot.enableAutoRunOn = false;
        CookingItem cookingItem = config.cookingItem();
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCookingSetup();
        Rs2Antiban.setActivity(Activity.GENERAL_COOKING);
        init = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;

                if (init) {
                    if (initialPlayerLocation == null) {
                        initialPlayerLocation = Rs2Player.getWorldLocation();
                    }

                    if (config.useNearestCookingLocation()) {
                        location = CookingLocation.findNearestCookingLocation(cookingItem);
                    } else {
                        location = config.cookingLocation();
                        if (cookingItem.getCookingAreaType() != CookingAreaType.BOTH) {
                            if (location.getCookingAreaType() != cookingItem.getCookingAreaType()) {
                                stopPlugin("Cooking Area does not match item's cooking area");
                                return;
                            }
                        }
                    }

                    if (location == null) {
                        stopPlugin("No suitable cooking location found");
                        return;
                    }

                    getState(config, location);
                }

                if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;

                switch (state) {
                    case COOKING:
                        if (Rs2Bank.isOpen()) {
                            Rs2Bank.closeBank();
                            return;
                        }
                        if (!cookingItem.hasRequirements()) {
                            stopPlugin("You do not meet the requirements to cook this item");
                            return;
                        }

                        // Mobile: query on the client thread.
                        Rs2TileObjectModel cookingObject = MobileActions.read(() -> {
                            Rs2TileObjectModel found = Microbot.getRs2TileObjectCache().query().withId(location.getCookingObjectID()).nearest();
                            if (found == null) {
                                found = Microbot.getRs2TileObjectCache().query()
                                        .where(o -> o.getWorldLocation().equals(location.getCookingObjectWorldPoint()))
                                        .nearest();
                            }
                            return found;
                        }, null);

                        if (cookingObject != null) {
                            if (!Rs2Camera.isTileOnScreen(cookingObject.getLocalLocation())) {
                                Rs2Camera.turnTo(cookingObject.getLocalLocation());
                                return;
                            }
                            // Mobile: a refused click (one action per game tick) is retried next loop.
                            if (!Rs2Inventory.useItemOnObject(cookingItem.getRawItemID(), cookingObject.getId())) {
                                return;
                            }

                            boolean productionWidgetOpen = Rs2Widget.isProductionWidgetOpen();
                            if (!productionWidgetOpen) {
                                productionWidgetOpen = sleepUntilTrue(Rs2Widget::isProductionWidgetOpen, 200, 12000);
                            }

                            if (!productionWidgetOpen) {
                                return;
                            }

                            sleepUntilTrue(() -> !Rs2Player.isMoving(), 200, 8000);

                            // Mobile: retry the key until accepted.
                            sleepUntil(() -> Rs2Keyboard.keyPress(KeyEvent.VK_SPACE), 3000);
                            Microbot.status = "Cooking " + cookingItem.getRawItemName();
                            
                            Rs2Antiban.actionCooldown();
                            Rs2Antiban.takeMicroBreakByChance();

                            sleepUntil(() -> (Rs2Player.getAnimation() != AnimationID.IDLE));
                            sleepUntilTrue(() -> (!hasRawItem(cookingItem) && !Rs2Player.isAnimating(3500))
                                    || Rs2Dialogue.isInDialogue() || Rs2Player.isMoving(), 500, 150000);
                            
                            if (hasRawItem(cookingItem)) {
                                break;
                            }
                            
                            if (config.shouldDropBurntItems() && hasBurntItem(cookingItem) && !cookingItem.getBurntItemName().isEmpty()) {
                                state = CookingState.DROPPING;
                                return;
                            }

                            state = CookingState.BANKING;
                            break;
                        }
                        state = CookingState.WALKING;
                        break;
                    case DROPPING:
                        Microbot.status = "Dropping " + cookingItem.getBurntItemName();
                        Rs2Inventory.dropAll(item -> item.getName().equalsIgnoreCase(cookingItem.getBurntItemName()), config.getDropOrder());
                        sleepUntilTrue(() -> !hasBurntItem(cookingItem), 500, 150000);
                        state = CookingState.BANKING;
                        break;
                    case BANKING:
                        if (location == CookingLocation.ROUGES_DEN) {
                            // Mobile: the NPC composition reads run on the client thread.
                            Rs2NpcModel npc = MobileActions.read(() -> Microbot.getRs2NpcCache().query()
                                    .where(n -> n.getName() != null && n.getNpc() != null && n.getNpc().getComposition() != null
                                            && n.getNpc().getComposition().getActions() != null
                                            && java.util.Arrays.asList(n.getNpc().getComposition().getActions()).contains("Bank"))
                                    .nearest(), null);
                            if (npc == null) return;
                            boolean isNPCBankOpen = Rs2Bank.openBank(npc.getNpc());
                            if (!isNPCBankOpen) return;
                            sleepUntil(() -> !Rs2Player.isMoving());
                        } else {
                            net.runelite.api.TileObject nearbyBankObject = Rs2GameObject.findBank(20);
                            if (nearbyBankObject != null) {
                                int distanceToBank = Rs2Player.getWorldLocation().distanceTo(nearbyBankObject.getWorldLocation());
                                boolean isBankOpen = Rs2Bank.openBank(nearbyBankObject);
                                if (!isBankOpen || !Rs2Bank.isOpen()) return;
                                sleepUntil(() -> !Rs2Player.isMoving());
                            } else {
                                boolean isBankOpen = Rs2Bank.walkToBankAndUseBank();
                                if (!isBankOpen || !Rs2Bank.isOpen()) return;
                            }
                        }

                        Rs2Bank.depositAll();
                        Rs2Inventory.waitForInventoryChanges(1800);
                        
                        if (!hasRawItemInBank(cookingItem)) {
                            stopPlugin("No Raw Food Item found in Bank");
                            return;
                        }
                        if (Objects.equals(config.cookingItem().getRawItemName(), "giant seaweed")) {
                            Rs2Bank.withdrawX(cookingItem.getRawItemName(), 4, true);
                            Rs2Inventory.waitForInventoryChanges(1800);

                        } else {
                            Rs2Bank.withdrawAll(cookingItem.getRawItemName(), true);
                            Rs2Inventory.waitForInventoryChanges(1800);
                        }
                        
                        state = CookingState.WALKING;
                        Rs2Bank.closeBank();
                        break;
                    case WALKING:
                        boolean hasRawItems = hasRawItem(cookingItem);
                        int distanceToCookingObject = Rs2Player.getWorldLocation().distanceTo(location.getCookingObjectWorldPoint());

                        if (hasRawItems && distanceToCookingObject <= 20) {
                            state = CookingState.COOKING;
                            sleepUntil(() -> !Rs2Player.isMoving());
                            break;
                        }

                        if (!isNearCookingLocation(location, 10)) {
                            boolean walkTo = Rs2Walker.walkTo(location.getCookingObjectWorldPoint(), 2);
                            if (!walkTo) return;
                        } else if (!isNearCookingLocation(location, 2)) {
                            Rs2Walker.walkFastCanvas(location.getCookingObjectWorldPoint());
                        }

                        if (hasRawItems) {
                            state = CookingState.COOKING;
                        } else {
                            state = CookingState.BANKING;
                        }
                        break;
                }
            } catch (Exception ex) {
                // Mobile: stop rather than retry a failing step every second.
                stopPlugin("Auto Cooking stopped after an error: " + ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }
    
    @Override
    public void shutdown(){
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
        Microbot.enableAutoRunOn = autoRunBefore;
    }

    /** Stops with {@code message}, turning the plugin off (not just this loop) so the Microbot Mobile screen shows it. */
    private void stopPlugin(String message) {
        Microbot.showMessage(message);
        shutdown();
        Microbot.stopPlugin(AutoCookingPlugin.class);
    }

    private void getState(AutoCookingConfig config, CookingLocation location) {
        if (!hasRawItem(config.cookingItem())) {
            if (hasBurntItem(config.cookingItem())) {
                state = CookingState.DROPPING;
                init = false;
                return;
            }
            state = CookingState.BANKING;
            init = false;
            return;
        }

        if (!isNearCookingLocation(location, 4)) {
            state = CookingState.WALKING;
            init = false;
            return;
        }

        state = CookingState.COOKING;
        init = false;
    }

    private boolean isNearCookingLocation(CookingLocation location, int distance) {
        return Rs2Player.getWorldLocation().distanceTo(location.getCookingObjectWorldPoint()) <= distance && !Rs2Player.isMoving();
    }

    private boolean hasRawItem(CookingItem cookingItem) {
        return Rs2Inventory.hasItem(cookingItem.getRawItemName(), true);
    }

    private boolean hasRawItemInBank(CookingItem cookingItem) {
        return Rs2Bank.hasBankItem(cookingItem.getRawItemName(), true);
    }

    private boolean hasCookedItem(CookingItem cookingItem) {
        return Rs2Inventory.hasItem(cookingItem.getCookedItemName(), true);
    }

    private boolean hasBurntItem(CookingItem cookingItem) {
        return Rs2Inventory.hasItem(cookingItem.getBurntItemName(), true);
    }
}
