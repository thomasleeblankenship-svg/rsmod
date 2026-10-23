package net.runelite.client.plugins.microbot;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.player.Rs2PlayerCache;
import net.runelite.client.plugins.microbot.api.playerstate.Rs2PlayerStateCache;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.util.GameTickBroadcaster;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;
import org.slf4j.event.Level;

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

    private static volatile Client client;
    private static volatile ClientThread clientThread;
    private static volatile EventBus eventBus;
    private static volatile ConfigManager configManager;
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
        pauseAllScripts.set(false);
        status = "IDLE";
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
