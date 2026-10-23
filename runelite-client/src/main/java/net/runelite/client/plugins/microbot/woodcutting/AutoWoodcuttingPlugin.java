/*
 * Mobile adaptation of uploaded Auto Woodcutting v1.8.7, source commit
 * 080d9674fe87f282c958c25b5bb8f6b96665fe36. Original algorithms retained;
 * desktop host/lifecycle and client-thread API access adapted for RuneMobile.
 */
package net.runelite.client.plugins.microbot.woodcutting;

import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.events.*;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.woodcutting.forestry.*;
import net.runelite.client.plugins.microbot.woodcutting.enums.ForestryEvents;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTree;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@Slf4j
public class AutoWoodcuttingPlugin extends Plugin {
    public static final String version = "1.8.7";
    @Inject
    @Getter(AccessLevel.MODULE)
    public AutoWoodcuttingScript autoWoodcuttingScript;
    @Inject
    public AutoWoodcuttingConfig config;
    private final OverlayManager overlayManager;
    private final AutoWoodcuttingOverlay woodcuttingOverlay;

    private EggEvent eggEvent;
    private EntlingsEvent entlingsEvent;
    private FlowersEvent flowersEvent;
    private FoxEvent foxEvent;
    private HivesEvent hivesEvent;
    private LeprechaunEvent leprechaunEvent;
    private RitualEvent ritualEvent;
    private RootEvent rootEvent;
    private StrugglingSaplingEvent saplingEvent;

    // Forestry event variables
    public final List<Rs2NpcModel> ritualCircles = new CopyOnWriteArrayList<>();
    public volatile ForestryEvents currentForestryEvent = ForestryEvents.NONE;
    private volatile boolean active;
    private final net.runelite.client.headless.ScriptInputSession inputPermission;
    public final GameObject[] saplingOrder = new GameObject[3];
    public final List<GameObject> saplingIngredients = new CopyOnWriteArrayList<>();
    
    // thread-safe counter for completed forestry events
    private final AtomicInteger completedForestryEvents = new AtomicInteger(0);

    private static final Pattern WOOD_CUT_PATTERN = Pattern.compile("You get (?:some|an)[\\w ]+(?:logs?|mushrooms)\\.");

    @Inject
    public Rs2TileObjectCache rs2TileObjectCache;

    @Provides
    AutoWoodcuttingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoWoodcuttingConfig.class);
    }

    /** Mobile host: construct on the client thread after MicrobotRuntime is installed. */
    public AutoWoodcuttingPlugin(AutoWoodcuttingConfig config, OverlayManager overlayManager) {
        this.inputPermission = net.runelite.client.plugins.microbot.mobile.MobileActions.currentPermission();
        this.config = java.util.Objects.requireNonNull(config);
        this.overlayManager = java.util.Objects.requireNonNull(overlayManager);
        this.rs2TileObjectCache = Microbot.getRs2TileObjectCache();
        this.autoWoodcuttingScript = new AutoWoodcuttingScript(this);
        this.autoWoodcuttingScript.rs2TileObjectCache = this.rs2TileObjectCache;
        this.woodcuttingOverlay = new AutoWoodcuttingOverlay(this, config, Microbot.getClient());
    }

    public boolean isActive() {
        return active && net.runelite.client.plugins.microbot.mobile.MobileActions.active();
    }

    /** Immediate worker-thread signal; client-thread cleanup follows through the mobile adapter. */
    void cancelInput() {
        active = false;
        if (inputPermission != null) {
            inputPermission.close();
        }
    }

    /** Register the host before the adapter starts its scheduled script. Does not start a second loop. */
    public void startMobile() {
        if (active) {
            throw new IllegalStateException("Woodcutting host already started");
        }
        active = true;
        Microbot.getEventBus().register(this);
        overlayManager.add(woodcuttingOverlay);
        // Seed currently loaded entities: the user can enable the script after an event spawns.
        Microbot.getRs2NpcCache().query().toList().stream()
                .filter(n -> n.getId() >= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1 && n.getId() <= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_4)
                .forEach(ritualCircles::add);
        Microbot.getRs2TileObjectCache().query().toList().stream()
                .map(n -> n.getTileObject())
                .filter(n -> n instanceof GameObject)
                .map(n -> (GameObject) n)
                .filter(n -> isSaplingIngredient(n.getId()))
                .forEach(saplingIngredients::add);
        if (config.enableForestry()) {
            addEvents();
        }
    }

    /** Client-thread lifecycle cleanup; revoke MobileActions before invoking this method. */
    public void stopMobile() {
        active = false;
        autoWoodcuttingScript.shutdown();
        removeEvents();
        Microbot.getEventBus().unregister(this);
        ritualCircles.clear();
        saplingIngredients.clear();
        java.util.Arrays.fill(saplingOrder, null);
        currentForestryEvent = ForestryEvents.NONE;
        completedForestryEvents.set(0);
        overlayManager.remove(woodcuttingOverlay);
    }

    private static boolean isSaplingIngredient(int id) {
        return id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_1
                || id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_2
                || id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_3
                || id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4A
                || id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4B
                || id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4C
                || id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_5;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.SPAM
                && event.getType() != ChatMessageType.GAMEMESSAGE
                && event.getType() != ChatMessageType.MESBOX) {
            return;
        }

        final var msg = event.getMessage();
        if (WOOD_CUT_PATTERN.matcher(msg).matches()) {
            woodcuttingOverlay.incrementLogsChopped();
        }

        if (msg.equalsIgnoreCase("you can't light a fire here.")) {
            autoWoodcuttingScript.cannotLightFire = true;
        }

        if (msg.startsWith("The sapling seems to love")) {
            int ingredientNum = msg.contains("first") ? 1 : (msg.contains("second") ? 2 : (msg.contains("third") ? 3 : -1));
            if (ingredientNum == -1) {
                log.debug("unable to find ingredient index from message: {}", msg);
                return;
            }

            GameObject ingredientObj = this.saplingIngredients.stream()
                    .filter(obj -> {
                        String compositionName = Rs2GameObject.getCompositionName(obj).orElse(null);
                        return compositionName != null && msg.contains(compositionName.toLowerCase());
                    })
                    .findAny()
                    .orElse(null);
            if (ingredientObj == null) {
                log.debug("unable to find ingredient from message: {}", msg);
                return;
            }

            this.saplingOrder[ingredientNum - 1] = ingredientObj;
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        NPC npc = event.getNpc();
        int id = npc.getId();
        if (id >= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1 && id <= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_4) {
            this.ritualCircles.add(new Rs2NpcModel(Microbot.getClient(), npc));
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        int id = npc.getId();
        if (id >= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1 && id <= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_4) {
            this.ritualCircles.removeIf(n -> n.getIndex() == npc.getIndex());
        }
    }

    @Subscribe
    public void onGameObjectSpawned(final GameObjectSpawned event) {
        GameObject gameObject = event.getGameObject();
        switch (gameObject.getId()) {
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_1:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_2:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_3:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4A:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4B:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4C:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_5:
                this.saplingIngredients.add(gameObject);
                break;
        }
    }

    @Subscribe
    public void onGameObjectDespawned(final GameObjectDespawned event) {
        final GameObject object = event.getGameObject();

        switch (object.getId()) {
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_1:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_2:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_3:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4A:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4B:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4C:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_5:
                this.saplingIngredients.remove(object);
                for (int i = 0; i < saplingOrder.length; i++) {
                    if (saplingOrder[i] == object) {
                        saplingOrder[i] = null;
                    }
                }
                break;
        }
    }

    private void addEvents() {
        removeEvents();
        var eventManager = Microbot.getBlockingEventManager();

        if (config.eggEvent()) {
            eggEvent = new EggEvent(this);
            eventManager.add(eggEvent);
        }

        if (config.entlingsEvent()) {
            entlingsEvent = new EntlingsEvent(this);
            eventManager.add(entlingsEvent);
        }

        if (config.flowersEvent()) {
            flowersEvent = new FlowersEvent(this);
            eventManager.add(flowersEvent);
        }

        if (config.foxEvent()) {
            foxEvent = new FoxEvent(this);
            eventManager.add(foxEvent);
        }

        if (config.hivesEvent()) {
            hivesEvent = new HivesEvent(this);
            eventManager.add(hivesEvent);
        }

        if (config.leprechaunEvent()) {
            leprechaunEvent = new LeprechaunEvent(this);
            eventManager.add(leprechaunEvent);
        }

        if (config.ritualEvent()) {
            ritualEvent = new RitualEvent(this);
            eventManager.add(ritualEvent);
        }

        if (config.rootEvent()) {
            rootEvent = new RootEvent(this);
            eventManager.add(rootEvent);
        }

        if (config.saplingEvent()) {
            saplingEvent = new StrugglingSaplingEvent(this);
            eventManager.add(saplingEvent);
        }
    }

    private void removeEvents() {
        var eventManager = Microbot.getBlockingEventManager();

        if (eggEvent != null) {
            eventManager.remove(eggEvent);
            eggEvent = null;
        }

        if (entlingsEvent != null) {
            eventManager.remove(entlingsEvent);
            entlingsEvent = null;
        }

        if (flowersEvent != null) {
            eventManager.remove(flowersEvent);
            flowersEvent = null;
        }

        if (foxEvent != null) {
            eventManager.remove(foxEvent);
            foxEvent = null;
        }

        if (hivesEvent != null) {
            eventManager.remove(hivesEvent);
            hivesEvent = null;
        }

        if (leprechaunEvent != null) {
            eventManager.remove(leprechaunEvent);
            leprechaunEvent = null;
        }

        if (ritualEvent != null) {
            eventManager.remove(ritualEvent);
            ritualEvent = null;
        }

        if (rootEvent != null) {
            eventManager.remove(rootEvent);
            rootEvent = null;
        }

        if (saplingEvent != null) {
            eventManager.remove(saplingEvent);
            saplingEvent = null;
        }
    }

    @Subscribe
    public void onConfigChanged (ConfigChanged ev) {
        if (active && (ev.getGroup().equals(AutoWoodcuttingConfig.configGroup) || ev.getGroup().equals("mobilemicrobot"))) {
            if (ev.getKey().equals("enableForestry")) {
                if (config.enableForestry()) {
                    this.addEvents();
                } else {
                    this.removeEvents();
                }
            } else if (config.enableForestry()) {
                var key = ev.getKey();
                var value = ev.getNewValue();
                if (value != null && value.equals("true")) {
                    this.addEvent(key);
                }
                else if (value != null && value.equals("false")) {
                    this.removeEvent(key);
                }
            }
        }
    }

    private void addEvent(String key) {
        var eventManager = Microbot.getBlockingEventManager();
        switch (key) {
            case "eggEvent":
                eggEvent = new EggEvent(this);
                eventManager.add(eggEvent);
                break;
            case "entlingsEvent":
                entlingsEvent = new EntlingsEvent(this);
                eventManager.add(entlingsEvent);
                break;
            case "flowersEvent":
                flowersEvent = new FlowersEvent(this);
                eventManager.add(flowersEvent);
                break;
            case "foxEvent":
                foxEvent = new FoxEvent(this);
                eventManager.add(foxEvent);
                break;
            case "hivesEvent":
                hivesEvent = new HivesEvent(this);
                eventManager.add(hivesEvent);
                break;
            case "leprechaunEvent":
                leprechaunEvent = new LeprechaunEvent(this);
                eventManager.add(leprechaunEvent);
                break;
            case "ritualEvent":
                ritualEvent = new RitualEvent(this);
                eventManager.add(ritualEvent);
                break;
            case "rootEvent":
                rootEvent = new RootEvent(this);
                eventManager.add(rootEvent);
                break;
            case "saplingEvent":
                saplingEvent = new StrugglingSaplingEvent(this);
                eventManager.add(saplingEvent);
                break;
        }
    }

    private void removeEvent(String key) {
        var eventManager = Microbot.getBlockingEventManager();
        switch (key) {
            case "eggEvent":
                if (eggEvent != null) {
                    eventManager.remove(eggEvent);
                    eggEvent = null;
                }
                break;
            case "entlingsEvent":
                if (entlingsEvent != null) {
                    eventManager.remove(entlingsEvent);
                    entlingsEvent = null;
                }
                break;
            case "flowersEvent":
                if (flowersEvent != null) {
                    eventManager.remove(flowersEvent);
                    flowersEvent = null;
                }
                break;
            case "foxEvent":
                if (foxEvent != null) {
                    eventManager.remove(foxEvent);
                    foxEvent = null;
                }
                break;
            case "hivesEvent":
                if (hivesEvent != null) {
                    eventManager.remove(hivesEvent);
                    hivesEvent = null;
                }
                break;
            case "leprechaunEvent":
                if (leprechaunEvent != null) {
                    eventManager.remove(leprechaunEvent);
                    leprechaunEvent = null;
                }
                break;
            case "ritualEvent":
                if (ritualEvent != null) {
                    eventManager.remove(ritualEvent);
                    ritualEvent = null;
                }
                break;
            case "rootEvent":
                if (rootEvent != null) {
                    eventManager.remove(rootEvent);
                    rootEvent = null;
                }
                break;
            case "saplingEvent":
                if (saplingEvent != null) {
                    eventManager.remove(saplingEvent);
                    saplingEvent = null;
                }
                break;
        }
    }
    
    public void incrementForestryEventCompleted() {
        completedForestryEvents.incrementAndGet();
    }
    
    public int getCompletedForestryEventCount() {
        return completedForestryEvents.get();
    }

    public WoodcuttingTree getSelectedTree() {
        if (autoWoodcuttingScript != null) {
            return autoWoodcuttingScript.getActiveTree();
        }
        return config.TREE();
    }
    
    /**
     * Ensures inventory has space for forestry event rewards by dropping logs if needed
     * @param requiredSlots minimum number of free slots needed
     * @return true if enough space was made available
     */
    public boolean ensureInventorySpace(int requiredSlots) {
        int currentFreeSlots = 28 - Rs2Inventory.count();
        if (currentFreeSlots >= requiredSlots) {
            return true;
        }
        
        WoodcuttingTree tree = getSelectedTree();
        String logName = tree.getLog();
        int slotsNeeded = requiredSlots - currentFreeSlots;
        int logsToDelete = Math.min(slotsNeeded, Rs2Inventory.count(logName));
        
        if (logsToDelete <= 0) {
            log.warn("Cannot make inventory space - no logs to drop");
            return false;
        }
        
        log.info("Making space for forestry rewards: dropping {} logs of {}", logsToDelete, tree.getName());
        
        int actualDropped = Rs2Inventory.dropAmount(logName, logsToDelete, InteractOrder.EFFICIENT_ROW);
        
        sleepUntil(() -> (28 - Rs2Inventory.count()) >= requiredSlots, 2000);
        
        boolean success = (28 - Rs2Inventory.count()) >= requiredSlots;
        if (!success) {
            log.warn("Failed to create enough inventory space: dropped {} logs but still need {} slots", 
                actualDropped, requiredSlots);
        }
        
        return success;
    }
}
