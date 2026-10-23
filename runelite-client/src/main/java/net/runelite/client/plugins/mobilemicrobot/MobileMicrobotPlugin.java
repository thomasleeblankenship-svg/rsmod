package net.runelite.client.plugins.mobilemicrobot;

import com.google.inject.Provides;
import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mobile-native, tick-driven Microbot-style integration layer.
 * Does not claim binary compatibility with desktop Microbot Script classes.
 */
@PluginDescriptor(
    name = "Microbot Mobile (phase 2)",
    description = "On-device mobile scripts, client-thread reads and controlled input",
    tags = {"microbot", "mobile", "scripts", "automation"},
    enabledByDefault = false
)
public class MobileMicrobotPlugin extends Plugin
{
    private static final Logger LOG = LoggerFactory.getLogger(MobileMicrobotPlugin.class);
    private static final String GROUP = "mobilemicrobot";

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ConfigManager configManager;
    @Inject private MobileMicrobotConfig config;

    private final AtomicLong commandGeneration = new AtomicLong();
    private volatile boolean enabled;
    private MobileScript current;
    private ScriptKind activeKind;
    private int tickCount;

    @Provides
    MobileMicrobotConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(MobileMicrobotConfig.class);
    }

    @Override
    protected void startUp()
    {
        enabled = true;
        commandGeneration.incrementAndGet();
        // Never auto-resume on launch, even if a synced config previously had Run=true.
        configManager.setConfiguration(GROUP, "runScript", false);
        MobileScriptBridge.attach(this);
        MobileScriptBridge.update("Choose a script and enable Run script", "", false);
    }

    @Override
    protected void shutDown()
    {
        // Fail closed immediately for the native-side status surface, even when
        // engine ticks are paused/backgrounded.
        enabled = false;
        commandGeneration.incrementAndGet();
        configManager.setConfiguration(GROUP, "runScript", false);
        MobileScriptBridge.detach(this);
        clientThread.invokeLater(() -> stopActive("Plugin disabled"));
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (GROUP.equals(event.getGroup()))
        {
            tickCount = 0;
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();
        if (state != GameState.LOGGED_IN && state != GameState.LOADING)
        {
            commandGeneration.incrementAndGet();
            stopActive("Stopped: signed out, hopping or disconnected");
            // This avoids a surprise restart after RuneMobile's automatic relogin.
            if (config.runScript())
            {
                configManager.setConfiguration(GROUP, "runScript", false);
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!enabled)
        {
            return;
        }
        if (!config.runScript())
        {
            stopActive("Stopped");
            return;
        }
        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        {
            stopActive("Paused: not logged in");
            return;
        }

        ScriptKind desired = config.selectedScript();
        MobileScriptContext context = new MobileScriptContext(client, config);
        try
        {
            if (current == null || activeKind != desired)
            {
                stopActive("Changing scripts");
                current = newScript(desired);
                activeKind = desired;
                current.onStart(context);
            }
            tickCount++;
            current.onGameTick(context);
            MobileScriptBridge.update(current.status() + " (tick " + tickCount + ")", activeKind.name(), true);
            if (desired == ScriptKind.WOODCUTTING && context.inventoryFull())
            {
                stopActive("Inventory full; automatically stopped");
                configManager.setConfiguration(GROUP, "runScript", false);
            }
        }
        catch (Exception exception)
        {
            LOG.error("Mobile script failed: {}", desired, exception);
            stopActive("Script error: " + exception.getClass().getSimpleName());
            configManager.setConfiguration(GROUP, "runScript", false);
        }
    }

    private static MobileScript newScript(ScriptKind kind)
    {
        switch (kind)
        {
            case WOODCUTTING:
                return new WoodcuttingScript();
            case SCENE_SCAN:
                return new SceneScanScript();
            case DIAGNOSTICS:
            default:
                return new DiagnosticsScript();
        }
    }

    private void stopActive(String reason)
    {
        MobileScript old = current;
        current = null;
        activeKind = null;
        if (old != null)
        {
            try
            {
                old.onStop(new MobileScriptContext(client, config));
            }
            catch (Exception exception)
            {
                LOG.warn("Mobile script cleanup failed", exception);
            }
        }
        MobileScriptBridge.update(reason, "", false);
    }

    // This facade is optional. RuneMobile already supplies a mobile plugin
    // configuration panel; a separate JNI toolbar is not necessary to operate it.
    void requestStart(String kind)
    {
        final ScriptKind selected;
        try
        {
            selected = ScriptKind.valueOf(kind == null ? "" : kind);
        }
        catch (IllegalArgumentException | NullPointerException exception)
        {
            MobileScriptBridge.update("Unknown script: " + kind, "", false);
            return;
        }
        final long generation = commandGeneration.get();
        clientThread.invokeLater(() ->
        {
            if (!enabled || generation != commandGeneration.get())
            {
                return;
            }
            configManager.setConfiguration(GROUP, "selectedScript", selected);
            configManager.setConfiguration(GROUP, "runScript", true);
        });
    }

    void requestStop()
    {
        commandGeneration.incrementAndGet();
        clientThread.invokeLater(() ->
        {
            configManager.setConfiguration(GROUP, "runScript", false);
            stopActive("Stopped by user");
        });
    }
}
