/*
 * Mobile adaptation of uploaded Auto Woodcutting v1.8.7, source commit
 * 080d9674fe87f282c958c25b5bb8f6b96665fe36. Original algorithms retained;
 * desktop host/lifecycle and client-thread API access adapted for RuneMobile.
 */
package net.runelite.client.plugins.microbot.woodcutting;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2LogBasket;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.skills.fletching.Rs2Fletching;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.woodcutting.enums.*;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTreeLocations;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.api.gameval.AnimationID.*;
import static net.runelite.api.gameval.ItemID.TINDERBOX;
import static net.runelite.client.plugins.microbot.util.player.Rs2Player.getRealSkillLevel;


@Slf4j
public class AutoWoodcuttingScript extends Script {

    public static final List<Integer> BURNING_ANIMATION_IDS = List.of(
            FORESTRY_CAMPFIRE_BURNING_LOGS,
            FORESTRY_CAMPFIRE_BURNING_MAGIC_LOGS,
            FORESTRY_CAMPFIRE_BURNING_MAHOGANY_LOGS,
            FORESTRY_CAMPFIRE_BURNING_MAPLE_LOGS,
            FORESTRY_CAMPFIRE_BURNING_OAK_LOGS,
            FORESTRY_CAMPFIRE_BURNING_REDWOOD_LOGS,
            FORESTRY_CAMPFIRE_BURNING_TEAK_LOGS,
            FORESTRY_CAMPFIRE_BURNING_WILLOW_LOGS,
            FORESTRY_CAMPFIRE_BURNING_YEW_LOGS,
            HUMAN_CREATEFIRE
    );

    public static final int FORESTRY_DISTANCE = 15;
    private static final List<WoodcuttingTree> PROGRESSIVE_TREE_ORDER = List.of(
            WoodcuttingTree.TREE,
            WoodcuttingTree.OAK,
            WoodcuttingTree.WILLOW,
            WoodcuttingTree.TEAK_TREE,
            WoodcuttingTree.MAPLE,
            WoodcuttingTree.MAHOGANY,
            WoodcuttingTree.YEW,
            WoodcuttingTree.MAGIC,
            WoodcuttingTree.REDWOOD
    );
    private static volatile WorldPoint returnPoint;
    public volatile boolean cannotLightFire = false;
    volatile WoodcuttingScriptState woodcuttingScriptState = WoodcuttingScriptState.WOODCUTTING;
    private final AutoWoodcuttingPlugin plugin;
    public int currentLogBasketCount = -1;
    private WoodcuttingTree activeTree = WoodcuttingTree.TREE;
    private ResourceLocationOption activeLocation;
    @Inject
    public AutoWoodcuttingScript(AutoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }
    @Inject
    Rs2TileObjectCache rs2TileObjectCache;

    private boolean handleFiremaking(AutoWoodcuttingConfig config) {
        WoodcuttingTree treeType = getActiveTree();
        if (Rs2Inventory.hasItem(TINDERBOX) && Rs2Inventory.hasItem(treeType.getLog())) {
            return true;
        }
        BankLocation bank = Rs2Bank.getNearestBank();
        if (!Rs2Bank.isOpen() && !Rs2Bank.walkToBankAndUseBank(bank)) {
            stopForActionFailure("Firemaking supplies bank unavailable: " + Rs2Walker.getLastStatus());
            return false;
        }
        if (!Rs2Inventory.hasItem(TINDERBOX)) {
            if (Rs2Inventory.isFull() && !Rs2Bank.depositAll(item -> item.getId() == treeType.getLogID())) {
                stopForActionFailure("Could not make space for tinderbox");
                return false;
            }
            if (!Rs2Bank.withdrawItem(true, "Tinderbox") || !Rs2Inventory.hasItem(TINDERBOX)) {
                stopForActionFailure("Tinderbox withdrawal failed");
                return false;
            }
        }
        if (!Rs2Inventory.hasItem(treeType.getLog()) && !Rs2Bank.withdrawAll(treeType.getLog())) {
            stopForActionFailure("No firemaking logs could be withdrawn");
            return false;
        }
        if (!Rs2Bank.closeBank()) {
            stopForActionFailure("Supply bank did not close");
            return false;
        }
        return Rs2Inventory.hasItem(TINDERBOX) && Rs2Inventory.hasItem(treeType.getLog());
    }

    public static WorldPoint getReturnPoint(AutoWoodcuttingConfig config) {
        if (config.walkBack().equals(WoodcuttingWalkBack.LAST_LOCATION)) {
            return returnPoint == null ? Rs2Player.getWorldLocation() : returnPoint;
        } else {
            return initialPlayerLocation == null ? Rs2Player.getWorldLocation() : initialPlayerLocation;
        }
    }

    public boolean run(AutoWoodcuttingConfig config) {
        if (!plugin.isActive()) {
            throw new IllegalStateException("Woodcutting host must be started before its script");
        }
        initialPlayerLocation = Rs2Player.getWorldLocation();
        returnPoint = initialPlayerLocation;
        cannotLightFire = false;
        woodcuttingScriptState = WoodcuttingScriptState.WOODCUTTING;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyWoodcuttingSetup();
        Rs2AntibanSettings.dynamicActivity = true;
        Rs2AntibanSettings.dynamicIntensity = true;
        activeTree = config.TREE();
        activeLocation = null;
        if (config.firemakeOnly()) {
            woodcuttingScriptState = WoodcuttingScriptState.FIREMAKING;
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (preFlightChecks(config)) return;
                switch (woodcuttingScriptState) {
                    case WOODCUTTING:
                        if (beforeCuttingTreesChecks(config)) return;
                        handleWoodcutting(config);
                        break;
                    case FIREMAKING:
                        if (handleFiremaking(config) && walkBack(config)) {
                            woodcuttingScriptState = WoodcuttingScriptState.RESETTING;
                        }
                        break;
                    case RESETTING:
                        resetInventory(config);
                }
            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
                log.error("Woodcutting script stopped after an error", ex);
                shutdown();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleWoodcutting(AutoWoodcuttingConfig config) {
        WoodcuttingTree treeType = getActiveTree();
        Rs2TileObjectModel tree = null;
        if (config.HardwoodTreePatch()) {
            // Fossil Island hardwood patches keep the same base object IDs across states
            // (grown / stump / empty). Only interact when "Chop down" is actually available.
            var patchIds = List.of(30480, 30481, 30482);
            String chopAction = treeType.getAction();
            tree = rs2TileObjectCache.query()
                    .where(x -> patchIds.contains(x.getId()))
                    .where(x -> {
                        var composition = x.getObjectComposition();
                        return composition != null && Rs2GameObject.hasAction(composition, chopAction);
                    })
                    .nearest();
        } else {
            tree = rs2TileObjectCache.query().within(getInitialPlayerLocation(), config.distanceToStray()).withName(treeType.getName()).nearestOnClientThread();
        }

        if (tree != null) {
            if (tree.click(treeType.getAction())) {
                Rs2Player.waitForAnimation();
                Rs2Antiban.actionCooldown();

                if (config.walkBack().equals(WoodcuttingWalkBack.LAST_LOCATION)) {
                    returnPoint = Rs2Player.getWorldLocation();
                }
            }
        }
    }

    private boolean beforeCuttingTreesChecks(AutoWoodcuttingConfig config) {
        WoodcuttingTree treeType = getActiveTree();

        if (Rs2Equipment.isWearing(ItemID.DRAGON_AXE) || Rs2Equipment.isWearing(ItemID.DRAGON_AXE_2H) || Rs2Equipment.isWearing(ItemID.CRYSTAL_AXE) ||
                Rs2Equipment.isWearing(ItemID.CRYSTAL_AXE_2H) || Rs2Equipment.isWearing(ItemID.INFERNAL_AXE) ||
                Rs2Equipment.isWearing(ItemID.TRAILBLAZER_AXE))
            Rs2Combat.setSpecState(true, 1000);
        boolean willBank = willBankItems(config);
        int currentLogCountBeforeFill = Rs2Inventory.count(treeType.getLogID());
        if ( currentLogCountBeforeFill > 0 && currentLogBasketCount < Rs2LogBasket.LOG_BASKET_CAPACITY && Rs2LogBasket.hasLogBasket()  && willBank) {
            if (currentLogBasketCount == -1) {
                Rs2LogBasket.BasketContents content  = Rs2LogBasket.getCurrentBasketContents();
                if (content == null) {
                    stopForActionFailure("Log basket contents could not be confirmed");
                    return true;
                }
                currentLogBasketCount = content.quantity;
                log.info("Initialized log basket count to {}", currentLogBasketCount);
            }
            if (currentLogBasketCount < Rs2LogBasket.LOG_BASKET_CAPACITY && Rs2Inventory.isFull() && Rs2Inventory.contains(treeType.getLog())) {

                if (!Rs2LogBasket.fillLogBasket()) {
                    stopForActionFailure("Log basket fill did not complete");
                    return true;
                }
                Rs2Antiban.actionCooldown();
                int currentLogCountAfterFill = Rs2Inventory.count(treeType.getLogID());
                int addedLogs = currentLogCountBeforeFill - currentLogCountAfterFill;
                currentLogBasketCount += addedLogs;
                log.info("Added {} logs to basket, current count: {}", addedLogs, currentLogBasketCount);
            }
        }
       

        if (Rs2Inventory.isFull()) {
            woodcuttingScriptState = WoodcuttingScriptState.RESETTING;
            return true;
        }

        if (handleLooting(config)) {
            Rs2Antiban.actionCooldown();
            return true;
        }

        return false;
    }

    private boolean preFlightChecks(AutoWoodcuttingConfig config) {
        if (!plugin.isActive() || Thread.currentThread().isInterrupted()) { shutdown(); return true; }
        if (!Microbot.isLoggedIn()) return true;
        if (!super.run()) return true;
        if (Rs2Player.getRealSkillLevel(Skill.WOODCUTTING) <= 0) return true;

        if (!config.enableWoodcutting()) {
            updateActiveTree(config);
            return true;
        }

        // Mobile user preference: never log out or hop in response to nearby players.
        // In particular, do not read the desktop plugin's persisted "Hop" configuration.
        if (Rs2AntibanSettings.actionCooldownActive) return true;

        if (initialPlayerLocation == null) {
            initialPlayerLocation = Rs2Player.getWorldLocation();
        }

        if (returnPoint == null) {
            returnPoint = Rs2Player.getWorldLocation();
        }

        updateActiveTree(config);

        if (config.progressiveMode() && ensureProgressiveLocation(config)) {
            return true;
        }

        if (!getActiveTree().hasRequiredLevel()) {
            Microbot.showMessage("You do not have the required woodcutting level to cut this tree. " + Rs2Player.getRealSkillLevel(Skill.WOODCUTTING));
            shutdown();
            return true;
        }

        if (Rs2Inventory.items(i -> Rs2Inventory.isProtectedAxe(i.getId(), i.getName())).findAny().isEmpty()) {
            if (!Rs2Equipment.hasWoodcuttingAxe()) {
                Microbot.showMessage("Unable to find axe in inventory/equipped");
                shutdown();
                return true;
            }
        }

        if (woodcuttingScriptState != WoodcuttingScriptState.RESETTING &&
                (Rs2Player.isMoving() || (Rs2Player.isAnimating() && !BURNING_ANIMATION_IDS.contains(Rs2Player.getLastAnimationID())))) {
            return true;
        }

        if (this.plugin.currentForestryEvent != ForestryEvents.NONE) {
            this.plugin.currentForestryEvent = ForestryEvents.NONE;
        }

        return Rs2AntibanSettings.actionCooldownActive;
    }

    private void resetInventory(AutoWoodcuttingConfig config) {
        switch (config.primaryAction()) {
            case DROP:
                var itemNames = Arrays.stream(config.itemsToKeep().split(",")).map(String::trim).toArray(String[]::new);
                if (!Rs2Inventory.dropAllExcept(false, config.interactOrder(), itemNames) || Rs2Inventory.isFull()) {
                    stopForActionFailure("Inventory drop failed: " + Rs2Inventory.dropFailure()
                            + "; check Items to keep if every item was retained");
                    return;
                }
                woodcuttingScriptState = WoodcuttingScriptState.WOODCUTTING;
                break;
            case BANK:
                if (!handleBanking(config))
                    return;
                woodcuttingScriptState = WoodcuttingScriptState.WOODCUTTING;
                break;
            case BURN_CAMPFIRE:
            case BURN:
                burnLog(config);
                if (!isRunning() || !plugin.isActive()) return;
                if (Rs2Inventory.contains(getActiveTree().getLog())) return;
                if (!walkBack(config)) return;

                if (config.firemakeOnly()) {
                    woodcuttingScriptState = WoodcuttingScriptState.FIREMAKING;
                } else {
                    woodcuttingScriptState = WoodcuttingScriptState.WOODCUTTING;
                }
                break;
            case FLETCH:
                if (handleFletchingWorkflow(config)) {
                    woodcuttingScriptState = WoodcuttingScriptState.WOODCUTTING;
                } else if (plugin.isActive()) {
                    stopForActionFailure("Fletching workflow did not complete");
                }
                break;
        }
    }

    private boolean ensureProgressiveLocation(AutoWoodcuttingConfig config) {
        if (activeLocation == null || activeLocation.getWorldPoint() == null) {
            return false;
        }

        WorldPoint targetPoint = activeLocation.getWorldPoint();

        if (initialPlayerLocation == null || !initialPlayerLocation.equals(targetPoint)) {
            initialPlayerLocation = targetPoint;
        }

        if (returnPoint == null || !returnPoint.equals(targetPoint)) {
            returnPoint = targetPoint;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return true;
        }

        int acceptableDistance = Math.min(Math.max(1, config.distanceToStray()), 5);
        int distanceToTarget = playerLocation.distanceTo(targetPoint);
        if (distanceToTarget > acceptableDistance) {
            if (Rs2Player.isMoving()) {
                return true;
            }

            if (!Rs2Walker.walkTo(targetPoint, 3)) {
                stopForActionFailure("Progressive travel failed: " + Rs2Walker.getLastStatus());
            }
            return true;
        }

        return false;
    }

    private boolean handleBanking(AutoWoodcuttingConfig config) {
        BankLocation nearestBank = Rs2Bank.getNearestBank();
        boolean isBankOpen = Rs2Bank.isNearBank(nearestBank, 8) ? Rs2Bank.openBank() : Rs2Bank.walkToBankAndUseBank(nearestBank);
        if (!isBankOpen || !Rs2Bank.isOpen()) {
            stopForActionFailure("Bank could not be opened: " + Rs2Walker.getLastStatus());
            return false;
        }

        // empty log basket first if we have one
        if (Rs2LogBasket.hasLogBasket() && currentLogBasketCount > 0 && !Rs2LogBasket.emptyLogBasketAtBank()) {
            stopForActionFailure("Log basket could not be emptied at bank");
            return false;
        }
        currentLogBasketCount = 0;
        // deposit items
        List<String> itemNames = Arrays.stream(config.itemsToBank().split(",")).map(String::toLowerCase).collect(Collectors.toList());
        itemNames.add(config.fletchingType().getContainsInventoryName().toLowerCase());
        if (!Rs2Bank.depositAll(i -> itemNames.stream().anyMatch(itemName -> i.getName().toLowerCase().contains(itemName)))) {
            stopForActionFailure("Bank deposit did not complete");
            return false;
        }
        Rs2Inventory.waitForInventoryChanges(1800);
        if (!Rs2Bank.closeBank() || !waitUntil(() -> !Rs2Bank.isOpen())) {
            stopForActionFailure("Bank did not close");
            return false;
        }
        if (!Rs2Walker.walkTo(getReturnPoint(config))) {
            stopForActionFailure("Return route failed: " + Rs2Walker.getLastStatus());
            return false;
        }
        return true;
    }

    private boolean handleLooting(AutoWoodcuttingConfig config)
    {
        if (!config.lootBirdNests() && !config.lootSeeds()) {
            return false; // No looting options selected
        }

        List<String> itemsToLootList = new ArrayList<>();

            if (config.lootSeeds()) {
                itemsToLootList.add("seed");
            }
            if (config.lootBirdNests()) {
                itemsToLootList.add("nest");
            }

            String[] itemsToLoot = itemsToLootList.toArray(new String[0]);

        LootingParameters itemLootParams = new LootingParameters(
                15,
                1,
                1,
                1,
                false,
                config.lootMyItemsOnly(),
                itemsToLoot
        );
        return Rs2GroundItem.lootItemsBasedOnNames(itemLootParams);
    }

    private void burnLog(AutoWoodcuttingConfig config) {
        WoodcuttingTree treeType = getActiveTree();
        WorldPoint fireSpot;
        boolean useCampfire = false;

        // prioritize campfire if available
        Rs2TileObjectModel fire = rs2TileObjectCache.query().where(x -> x.getId() == 49927).nearest(6); // Forester's campfire
        if (fire == null) {
            fire = rs2TileObjectCache.query().where(x -> x.getId() == 26185).nearest(6);
        }
        if (config.primaryAction() == WoodcuttingPrimaryAction.BURN_CAMPFIRE) {
            if (fire != null) {
                useCampfire = true;
            }
        }
        if ((Rs2Player.isStandingOnGameObject() || cannotLightFire) && !Rs2Player.isAnimating() && !useCampfire) {
            fireSpot = fireSpot(1);
            if (fireSpot == null) {
                Microbot.showMessage("No reachable firemaking tile within eight tiles; stopped");
                shutdown();
                return;
            }
            if (!Rs2Walker.walkFastCanvas(fireSpot)) {
                stopForActionFailure("Firemaking tile could not be reached: " + Rs2Walker.getLastStatus());
                return;
            }
            cannotLightFire = false;
        }
        if (!isFiremake() && !useCampfire) {
            Rs2Inventory.waitForInventoryChanges(() -> {
                Rs2Inventory.use("tinderbox");
                waitUntil(Rs2Inventory::isItemSelected);
                Rs2Inventory.useLast(treeType.getLogID());
            }, 300, 100);
        } else if (!isFiremake() && useCampfire) {
            Rs2Inventory.useItemOnObject(treeType.getLogID(), fire.getId());
            waitUntil(() -> (!Rs2Player.isMoving() && Rs2Widget.findWidget("How many would you like to burn?", null, false) != null), 5000);
            Rs2Random.waitEx(400, 200);
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            waitUntil(Rs2Player::isAnimating, 2000);
            Microbot.log("Sleeping until not animating or no more logs");
            waitUntil(() -> !Rs2Inventory.contains(treeType.getLog()) || !Rs2Player.isAnimating(), 40000);

            return;
        }
        waitUntil(() -> !isFiremake());
        if (!isFiremake()) {
            waitUntil(() -> cannotLightFire, 1500);
        }
        if (!cannotLightFire && isFiremake()) {
            waitUntil(() -> Rs2Player.waitForXpDrop(Skill.FIREMAKING, 40000), 40000);
        }
    }

    /** Bounded search: no recursive expansion across an entire blocked scene. */
    private WorldPoint fireSpot(int distance) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return null;
        }
        for (int radius = Math.max(1, distance); radius <= 8 && plugin.isActive(); radius++) {
            final int searchRadius = radius;
            WorldPoint candidate = Rs2Tile.getWalkableTilesAroundPlayer(radius).stream()
                    .filter(point -> rs2TileObjectCache.query()
                            .where(object -> object.getWorldLocation().equals(point))
                            .nearest(searchRadius) == null)
                    .min(Comparator.comparingInt(playerLocation::distanceTo))
                    .orElse(null);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isFiremake() {
        if (cannotLightFire) return false;
        return Rs2Player.isAnimating(1800) && BURNING_ANIMATION_IDS.contains(Rs2Player.getLastAnimationID());
    }



    private boolean walkBack(AutoWoodcuttingConfig config) {
        WorldPoint target = getReturnPoint(config);
        if (target == null) {
            stopForActionFailure("No return position is available");
            return false;
        }
        if (!Rs2Walker.walkTo(new WorldPoint(target.getX() - Rs2Random.between(-1, 1), target.getY() - Rs2Random.between(-1, 1), target.getPlane()))) {
            stopForActionFailure("Walk-back route failed: " + Rs2Walker.getLastStatus());
            return false;
        }
        return waitUntil(() -> {
            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            return playerLocation != null && playerLocation.distanceTo(target) <= 4;
        });
    }
    
    /**
     * determine if this workflow will bank items
     */
    private boolean willBankItems(AutoWoodcuttingConfig config) {
        return config.primaryAction() == WoodcuttingPrimaryAction.BANK || 
               (config.primaryAction() == WoodcuttingPrimaryAction.FLETCH && 
                (config.secondaryAction() == WoodcuttingSecondaryAction.BANK ||
                 config.secondaryAction() == WoodcuttingSecondaryAction.STRING_AND_BANK));
    }
    
    /**
     * handle fletching workflow with secondary actions
     */
    private boolean handleFletchingWorkflow(AutoWoodcuttingConfig config) {
        // fletch logs in inventory
        if (!Rs2Fletching.hasKnife()) {
            log.info("Unable to find knife in inventory/equipped");
            switch (config.secondaryAction()) {
                case BANK:
                case STRING_AND_BANK:
                    if (!handleBanking(config)) {
                        walkBack(config);
                        return false;
                    }
                    break;
                case DROP:
                case STRING_AND_DROP:
                    log.info("Dropping items to find knife");
                    String [] itemNames = Arrays.stream(config.itemsToKeep().split(",")).map(String::trim).toArray(String[]::new);
                    // additional item to keep axe and log basket
                    itemNames = Arrays.copyOf(itemNames, itemNames.length + 1);
                    itemNames[itemNames.length - 1] = "axe";
                    if (Rs2Inventory.hasItem("log basket")) {
                        itemNames = Arrays.copyOf(itemNames, itemNames.length + 1);
                        itemNames[itemNames.length - 1] = "log basket";
                    }


                    if (!Rs2Inventory.dropAllExcept(false, InteractOrder.COLUMN, itemNames)) {
                        return false;
                    }
                    break;
                case NONE:
                    break;
            }
            return !Rs2Inventory.isFull();
        }
        WoodcuttingTree treeType = getActiveTree();
        int logCount = Rs2Inventory.count(treeType.getLogID());
        if (logCount > 0) {
            //TODO: should we really stop script if fletching failed?
            boolean startFletchingSucces = Rs2Fletching.fletchItems(treeType.getLogID(), config.fletchingType().getContainsInventoryName(), "All");
            int fletchedItems = Rs2Inventory.getList(itemBounds -> itemBounds.getName().contains(config.fletchingType().getContainsInventoryName())).size();
            log.info("We fletched {} {} into {} of {} , success: {}", logCount, treeType, fletchedItems, config.fletchingType().getContainsInventoryName(), startFletchingSucces);
            if (!startFletchingSucces) {
                return false;
            }
            if (Rs2Inventory.count(treeType.getLogID()) != 0) {
                return false;
            }
        }

        // handle secondary action
        switch (config.secondaryAction()) {
            case BANK:
                if (!handleBanking(config)) return false;
                walkBack(config);
                break;
            case DROP:

                if (!Rs2Fletching.dropFletchedItems(config.fletchingType().getContainsInventoryName())) {
                    return false;
                }
                Rs2Inventory.waitForInventoryChanges(1800);
                break;
            case STRING_AND_DROP:
            case STRING_AND_BANK:
                if (Rs2Inventory.contains("bow string")) {
                    if (!Rs2Fletching.stringBows(config.fletchingType().getContainsInventoryName())) {
                        return false;
                    }
                }
                if (config.secondaryAction() == WoodcuttingSecondaryAction.STRING_AND_BANK) {
                    if (!handleBanking(config)) return false;
                    walkBack(config);
                } else {
                    if (!Rs2Fletching.dropFletchedItems(config.fletchingType().getContainsInventoryName())) {
                        return false;
                    }
                    Rs2Inventory.waitForInventoryChanges(1800);
                }
                break;
            case NONE:
                break;
        }


        return !Rs2Inventory.isFull();
    }

    public WoodcuttingTree getActiveTree() {
        return activeTree;
    }

    private void updateActiveTree(AutoWoodcuttingConfig config) {
        WoodcuttingTree previousTree = activeTree;
        WoodcuttingTree resolvedTree;
        ResourceLocationOption candidateLocation = null;
        boolean progressive = config.progressiveMode();

        if (progressive) {
            int woodcuttingLevel = getRealSkillLevel(Skill.WOODCUTTING);
            ProgressiveSelection selection = determineProgressiveSelection(woodcuttingLevel);
            resolvedTree = selection.getTree();
            candidateLocation = selection.getLocation();
        } else {
            resolvedTree = config.TREE();
        }

        if (resolvedTree == null) {
            resolvedTree = WoodcuttingTree.TREE;
        }

        activeTree = resolvedTree;

        if (progressive) {
            boolean keepExistingLocation = previousTree == resolvedTree && activeLocation != null && activeLocation.hasRequirements();

            if (!keepExistingLocation) {
                activeLocation = candidateLocation;
            }
        } else {
            activeLocation = null;
        }
    }

    private ProgressiveSelection determineProgressiveSelection(int woodcuttingLevel) {
        ProgressiveSelection bestSelection = null;

        for (WoodcuttingTree tree : PROGRESSIVE_TREE_ORDER) {
            if (woodcuttingLevel < tree.getWoodcuttingLevel()) {
                break;
            }

            ResourceLocationOption location = WoodcuttingTreeLocations.getBestAccessibleLocation(tree);
            if (location != null) {
                bestSelection = new ProgressiveSelection(tree, location);
            }
        }

        if (bestSelection != null) {
            return bestSelection;
        }

        ResourceLocationOption fallbackLocation = WoodcuttingTreeLocations.getBestAccessibleLocation(WoodcuttingTree.TREE);
        return new ProgressiveSelection(WoodcuttingTree.TREE, fallbackLocation);
    }

    private static class ProgressiveSelection {
        private final WoodcuttingTree tree;
        private final ResourceLocationOption location;

        private ProgressiveSelection(WoodcuttingTree tree, ResourceLocationOption location) {
            this.tree = tree;
            this.location = location;
        }

        public WoodcuttingTree getTree() {
            return tree;
        }

        public ResourceLocationOption getLocation() {
            return location;
        }
    }

    private void stopForActionFailure(String message) {
        Microbot.showMessage(message + "; script stopped");
        shutdown();
    }

    private boolean waitUntil(java.util.function.BooleanSupplier condition) {
        return waitUntil(condition, 5000);
    }

    private boolean waitUntil(java.util.function.BooleanSupplier condition, long timeoutMillis) {
        boolean completed = net.runelite.client.plugins.microbot.util.Global.sleepUntil(
                () -> !plugin.isActive() || Thread.currentThread().isInterrupted() || condition.getAsBoolean(), (int) Math.min(Integer.MAX_VALUE, Math.max(0, timeoutMillis)));
        return completed && plugin.isActive() && !Thread.currentThread().isInterrupted();
    }

    @Override
    public void shutdown() {
        plugin.cancelInput();
        super.shutdown();
        currentLogBasketCount = -1;
        Rs2Fletching.stopFletchingWhileMoving();
        Rs2Walker.setTarget(null);
        returnPoint = null;
        initialPlayerLocation = null;
        Rs2Antiban.resetAntibanSettings();
        activeLocation = null;
    }
}
