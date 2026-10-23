package net.runelite.client.plugins.microbot;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.util.GameTickBroadcaster;
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
 *     Player, player-state and boat caches are not ported.</li>
 * </ul>
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
    public static int getVarbitValue(int id) {
        return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() -> client.getVarbitValue(id), 0);
    }
    public static int getVarbitPlayerValue(int id) {
        return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() -> client.getVarpValue(id), 0);
    }
    public static boolean isPluginEnabled(net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin plugin) {
        return plugin != null && plugin.isActive();
    }
    public static void showMessage(String message) {
        status = message;
        log.info("[Mobile script] {}", message);
    }

}
