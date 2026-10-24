package net.runelite.client.plugins.microbot;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.player.Rs2PlayerCache;
import net.runelite.client.plugins.microbot.api.playerstate.Rs2PlayerStateCache;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.util.GameTickBroadcaster;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;
import org.slf4j.event.Level;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mobile subset of Microbot's static service locator.
 * <p>
 * Provenance: adapted from {@code chsami/Microbot}
 * {@code runelite-client/src/main/java/net/runelite/client/plugins/microbot/Microbot.java} at
 * {@code e01e0d77d79b354472a5737b3aaab2449eb034fc} (BSD 2-Clause, see repository {@code LICENSE}).
 * Only the members the runtime and the first upstream script need are carried; signatures and
 * semantics of the kept members match upstream except where noted below.
 * <p>
 * Adaptations:
 * <ul>
 *     <li>Services are bound by {@link MicrobotRuntime#install} and cleared by
 *     {@link MicrobotRuntime#uninstall} instead of Guice static injection, so a disabled plugin
 *     leaves no live client references behind. Getters return {@code null} when not installed.</li>
 *     <li>{@link #isLoggedIn()} reads an immutable snapshot published from the client thread by
 *     {@code GameStateChanged}, instead of reading {@code Client#getGameState()} off-thread.</li>
 *     <li>{@link #getBlockingEventManager()} is created per install and starts with no registered
 *     events: every upstream default event performs game input, which this stage does not
 *     support.</li>
 *     <li>The NPC, tile-object and tile-item cache getters delegate to the installed
 *     {@link MicrobotReadServices} (read-only API stage) instead of Guice-injected fields, and
 *     return {@code null} when it is not installed, like the other getters here.
 *     The player and player-state caches are exposed the same way
 *     ({@link #getRs2PlayerCache()}, {@link #getRs2PlayerStateCache()}); the boat cache is not ported.</li>
 * </ul>
 * {@link #getVarbitValue} and {@link #getVarbitPlayerValue} read the read-API stage's
 * {@code Rs2PlayerStateCache}, as upstream does. {@link #isPluginEnabled} and
 * {@link #showMessage} are mobile adaptations for the woodcutting port; each documents how it
 * differs from upstream.
 * <p>
 * Everything else upstream exposes here (mouse, hopping, plugin control, popups, loot records,
 * item containers, world services, overlays) is unsupported on mobile and deliberately absent,
 * so a script that needs it fails to compile instead of silently doing nothing.
 */
@Slf4j
@NoArgsConstructor
public class Microbot {
    public static AtomicBoolean pauseAllScripts = new AtomicBoolean(false);
    public static String status = "IDLE";
    /**
     * Upstream feature flag. Accepted for source compatibility; mobile scripts never toggle run
     * on automatically, so any value behaves as {@code false}.
     */
    public static boolean enableAutoRunOn = true;

    /**
     * As upstream: true from a stat change until 4 s after the last one. Set from the mobile
     * plugin's {@code StatChanged} handler through {@link #setIsGainingExp(boolean)}.
     */
    public static volatile boolean isGainingExp = false;
    // Mobile: a daemon thread, so it cannot keep the embedded JVM alive.
    private static final java.util.concurrent.ScheduledExecutorService XP_SCHEDULER =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Microbot-xp");
            t.setDaemon(true);
            return t;
        });
    private static java.util.concurrent.ScheduledFuture<?> xpReset;

    /** As upstream: sets {@link #isGainingExp} and clears it 4 s later unless set again. */
    public static synchronized void setIsGainingExp(boolean value) {
        isGainingExp = value;
        if (xpReset != null) {
            xpReset.cancel(false);
        }
        xpReset = XP_SCHEDULER.schedule(() -> { isGainingExp = false; }, 4_000, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private static volatile Client client;
    private static volatile ClientThread clientThread;
    private static volatile EventBus eventBus;
    private static volatile ConfigManager configManager;
    private static volatile PluginManager pluginManager;
    private static volatile BlockingEventManager blockingEventManager;
    private static volatile GameTickBroadcaster gameTickBroadcaster;

    public static Client getClient() {
        return client;
    }

    public static ClientThread getClientThread() {
        return clientThread;
    }

    public static EventBus getEventBus() {
        return eventBus;
    }

    public static ConfigManager getConfigManager() {
        return configManager;
    }

    public static PluginManager getPluginManager() {
        return pluginManager;
    }

    static void bindPluginManager(PluginManager manager) {
        pluginManager = manager;
    }

    private static volatile net.runelite.client.game.WorldService worldService;

    /** RuneLite's world list service; bound by the mobile plugin. {@code null} when not bound. */
    public static net.runelite.client.game.WorldService getWorldService() {
        return worldService;
    }

    static void bindWorldService(net.runelite.client.game.WorldService service) {
        worldService = service;
    }

    private static final net.runelite.client.plugins.microbot.util.mouse.Mouse MOUSE =
            new net.runelite.client.plugins.microbot.util.mouse.Mouse();

    /** As upstream: script mouse clicks, as session-gated taps on mobile. */
    public static net.runelite.client.plugins.microbot.util.mouse.Mouse getMouse() {
        return MOUSE;
    }

    /**
     * As upstream: invokes a script-built menu entry. Mobile has no cursor to move into
     * {@code rectangle}, so the entry is sent exactly through {@link MobileActions#menu}, as one
     * script action (a WALK entry taps the canvas point it carries). No random pause follows; the
     * caller observes the result, as with every mobile action.
     */
    public static void doInvoke(net.runelite.client.plugins.microbot.util.menu.NewMenuEntry entry,
            java.awt.Rectangle rectangle) {
        invoke(entry);
    }

    /** As upstream: {@link #doInvoke} without the post-click pause (mobile has none either way). */
    public static void doInvokeQuick(net.runelite.client.plugins.microbot.util.menu.NewMenuEntry entry,
            java.awt.Rectangle rectangle) {
        invoke(entry);
    }

    /** As upstream: clicks {@code rectangle} with this menu entry; on mobile, as {@link #doInvoke}. */
    public static void click(java.awt.Rectangle rectangle,
            net.runelite.client.plugins.microbot.util.menu.NewMenuEntry entry) {
        invoke(entry);
    }

    private static void invoke(net.runelite.client.plugins.microbot.util.menu.NewMenuEntry entry) {
        if (entry != null && !MobileActions.menu(entry)) {
            log.debug("Menu entry not dispatched: {} {}", entry.getOption(), entry.getTarget());
        }
    }

    private static volatile net.runelite.client.game.ItemManager itemManager;

    /** As upstream: RuneLite's item manager (prices, item compositions); bound by the mobile plugin. */
    public static net.runelite.client.game.ItemManager getItemManager() {
        return itemManager;
    }

    static void bindItemManager(net.runelite.client.game.ItemManager manager) {
        itemManager = manager;
    }

    /** As upstream: the game is between worlds. */
    public static boolean isHopping() {
        return MobileActions.read(() -> client.getGameState() == GameState.HOPPING, false);
    }

    /**
     * As upstream: hops to {@code worldNumber} and returns true once logged in there (up to about
     * 20 seconds), or at once when already there. Mobile: the world switcher is opened and the hop
     * requested as two script actions for the bound session, which records the hop as its own so the
     * mobile plugin keeps the script running through it (any other hop or disconnect still stops
     * it). Refused on the client thread, while logged out or with no bound script session.
     */
    public static boolean hopToWorld(int worldNumber) {
        Client c = client;
        if (c == null || c.isClientThread() || !isLoggedIn() || !MobileActions.active()) {
            return false;
        }
        if (MobileActions.read(c::getWorld, -1) == worldNumber) {
            return true;
        }
        net.runelite.http.api.worlds.WorldResult worlds = worldService == null ? null : worldService.getWorlds();
        net.runelite.http.api.worlds.World target = worlds == null ? null : worlds.findWorld(worldNumber);
        if (target == null) {
            log.warn("Cannot hop: world {} is not in the world list", worldNumber);
            return false;
        }
        net.runelite.api.World rsWorld = MobileActions.read(() -> {
            net.runelite.api.World world = c.createWorld();
            if (world == null) {
                return null;
            }
            world.setActivity(target.getActivity());
            world.setAddress(target.getAddress());
            world.setId(target.getId());
            world.setPlayerCount(target.getPlayers());
            if (target.getRegion() != null) {
                world.setLocation(target.getRegion().getId());
            }
            world.setTypes(net.runelite.client.util.WorldUtil.toWorldTypes(target.getTypes()));
            return world;
        }, null);
        if (rsWorld == null
                || !Global.sleepUntil(MobileActions::openWorldHopper, 2_000)
                || !Global.sleepUntil(() -> MobileActions.hopToWorld(rsWorld), 3_000)) {
            log.warn("Cannot hop to world {}: the request was not accepted", worldNumber);
            return false;
        }
        try {
            boolean started = Global.sleepUntil(() -> isHopping()
                    || MobileActions.read(c::getWorld, -1) == worldNumber, 5_000);
            boolean landed = started && Global.sleepUntil(() -> MobileActions.read(() ->
                    c.getWorld() == worldNumber && c.getGameState() == GameState.LOGGED_IN, false), 15_000);
            if (!landed) {
                log.warn("Hop to world {} did not complete", worldNumber);
            }
            return landed;
        } finally {
            MobileActions.clearExpectedHop();
        }
    }

    // --- plugin control, as upstream (togglePlugin runs on the EDT like the mobile plugin UI) ---

    public static boolean startPlugin(Plugin plugin) {
        return togglePlugin(plugin, true);
    }

    public static boolean startPlugin(Class<? extends Plugin> clazz) {
        return startPlugin(getPlugin(clazz));
    }

    public static boolean startPlugin(String className) {
        return startPlugin(getPlugin(className));
    }

    public static boolean stopPlugin(Plugin plugin) {
        return togglePlugin(plugin, false);
    }

    public static boolean stopPlugin(Class<? extends Plugin> clazz) {
        return stopPlugin(getPlugin(clazz));
    }

    public static boolean stopPlugin(String className) {
        return stopPlugin(getPlugin(className));
    }

    /** {@code null} when not installed or not loaded. */
    public static Plugin getPlugin(String className) {
        PluginManager manager = pluginManager;
        return manager == null || className == null ? null : manager.getPlugins().stream()
                .filter(plugin -> plugin.getClass().getName().equals(className))
                .findFirst().orElse(null);
    }

    /** {@code null} when not installed or not loaded. */
    public static <T extends Plugin> T getPlugin(Class<T> clazz) {
        PluginManager manager = pluginManager;
        return manager == null || clazz == null ? null : manager.getPlugins().stream()
                .filter(plugin -> plugin.getClass() == clazz)
                .map(clazz::cast).findFirst().orElse(null);
    }

    public static boolean isPluginEnabled(Plugin plugin) {
        PluginManager manager = pluginManager;
        return plugin != null && manager != null && manager.isPluginEnabled(plugin);
    }

    public static boolean isPluginEnabled(Class<? extends Plugin> clazz) {
        return isPluginEnabled(getPlugin(clazz));
    }

    /**
     * As upstream: on the EDT, sets the plugin's enabled flag, then starts or stops it. Off the EDT
     * the change is queued and the return value is {@code false}, as upstream.
     */
    private static boolean togglePlugin(Plugin plugin, boolean enable) {
        PluginManager manager = pluginManager;
        if (plugin == null || manager == null) return false;
        final AtomicBoolean success = new AtomicBoolean(false);
        Runnable runnable = () -> {
            try {
                manager.setPluginEnabled(plugin, enable);
                if (enable) {
                    success.set(manager.startPlugin(plugin));
                    manager.startPlugins();
                } else {
                    success.set(manager.stopPlugin(plugin));
                }
            } catch (PluginInstantiationException ex) {
                log.error("Error {}abling plugin ({}):", enable ? "en" : "dis", plugin.getClass().getSimpleName(), ex);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
        return success.get();
    }

    public static BlockingEventManager getBlockingEventManager() {
        return blockingEventManager;
    }

    public static GameTickBroadcaster getGameTickBroadcaster() {
        return gameTickBroadcaster;
    }

    public static Rs2NpcCache getRs2NpcCache() {
        MicrobotReadServices services = MicrobotReadServices.current();
        return services == null ? null : services.getRs2NpcCache();
    }

    public static Rs2TileObjectCache getRs2TileObjectCache() {
        MicrobotReadServices services = MicrobotReadServices.current();
        return services == null ? null : services.getRs2TileObjectCache();
    }

    public static Rs2TileItemCache getRs2TileItemCache() {
        MicrobotReadServices services = MicrobotReadServices.current();
        return services == null ? null : services.getRs2TileItemCache();
    }

    static void bind(Client client, ClientThread clientThread, EventBus eventBus, ConfigManager configManager,
                     BlockingEventManager blockingEventManager, GameTickBroadcaster gameTickBroadcaster) {
        Microbot.client = client;
        Microbot.clientThread = clientThread;
        Microbot.eventBus = eventBus;
        Microbot.configManager = configManager;
        Microbot.blockingEventManager = blockingEventManager;
        Microbot.gameTickBroadcaster = gameTickBroadcaster;
    }

    static void unbind() {
        bind(null, null, null, null, null, null);
        pluginManager = null;
        worldService = null;
        itemManager = null;
        pauseAllScripts.set(false);
        status = "IDLE";
        isGainingExp = false;
    }

    public static boolean isDebug() {
        return java.lang.management.ManagementFactory.getRuntimeMXBean().
                getInputArguments().toString().contains("-agentlib:jdwp");
    }

    /**
     * Upstream delegates to {@code LoginManager.isLoggedIn()}, which reads the game state from the
     * calling thread. Mobile reads the last state the client thread published.
     */
    public static boolean isLoggedIn() {
        return MicrobotRuntime.isLoggedIn();
    }

    /**
     * Logs the stack trace of an exception to the console. Upstream also echoes to chat; mobile
     * logs only.
     *
     * @param scriptName the name of the script where the exception occurred
     * @param ex         the exception
     */
    public static void logStackTrace(String scriptName, Exception ex) {
        log(scriptName, Level.ERROR, ex);
    }

    public static void log(String message) {
        log(message, Level.INFO);
    }

    public static void log(String format, Object... args) {
        log(String.format(format, args), Level.INFO);
    }

    public static void log(Level level, String format, Object... args) {
        log(String.format(format, args), level);
    }

    public static void log(String message, Level level) {
        log(message, level, null);
    }

    public static void log(String message, Level level, Exception ex) {
        if (message == null || message.isEmpty()) {
            return;
        }
        if (level == null) {
            return;
        }

        switch (level) {
            case WARN:
                log.warn(message);
                break;
            case ERROR:
                if (isInterruption(ex)) {
                    log.debug(message, ex);
                } else if (ex != null) {
                    log.error(message, ex);
                } else {
                    log.error(message);
                }
                break;
            case DEBUG:
                log.debug(message);
                break;
            default:
                log.info(message);
                break;
        }
    }

    static boolean isInterruption(Throwable ex) {
        Throwable t = ex;
        for (int depth = 0; t != null && depth < 64; depth++, t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }

    /** Upstream's other-players cache; {@code null} when the read services are not installed. */
    public static Rs2PlayerCache getRs2PlayerCache() {
        MicrobotReadServices services = MicrobotReadServices.current();
        return services == null ? null : services.getRs2PlayerCache();
    }

    /** Upstream's player-state cache; {@code null} when the read services are not installed. */
    public static Rs2PlayerStateCache getRs2PlayerStateCache() {
        MicrobotReadServices services = MicrobotReadServices.current();
        return services == null ? null : services.getRs2PlayerStateCache();
    }

    /** As upstream, reads {@link Rs2PlayerStateCache}; returns 0 when the read services are not installed. */
    public static int getVarbitValue(int id) {
        Rs2PlayerStateCache cache = getRs2PlayerStateCache();
        return cache == null ? 0 : cache.getVarbitValue(id);
    }

    /** As upstream, reads {@link Rs2PlayerStateCache}; returns 0 when the read services are not installed. */
    public static int getVarbitPlayerValue(int id) {
        Rs2PlayerStateCache cache = getRs2PlayerStateCache();
        return cache == null ? 0 : cache.getVarpValue(id);
    }

    /**
     * Upstream asks the {@code PluginManager}. The mobile woodcutting port is driven by the mobile
     * adapter rather than registered with the plugin manager, so this asks the plugin itself.
     */
    public static boolean isPluginEnabled(AutoWoodcuttingPlugin plugin) {
        return plugin != null && plugin.isActive();
    }

    /** Upstream shows a Swing dialog. Mobile sets {@link #status} and logs instead. */
    public static void showMessage(String message) {
        status = message;
        log.info("[Mobile script] {}", message);
    }
}
