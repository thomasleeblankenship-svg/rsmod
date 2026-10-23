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
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.MicrobotScriptAdapter;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.statemachineexample.StateMachineExampleConfig;
import net.runelite.client.plugins.microbot.statemachineexample.StateMachineExampleScript;
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
	@Inject private EventBus eventBus;
	@Inject private MicrobotReadServices readServices;
	@Inject private net.runelite.client.ui.overlay.OverlayManager overlayManager;
	@Inject private net.runelite.client.plugins.PluginManager pluginManager;
	@Inject private net.runelite.client.game.WorldService worldService;

	private final AtomicLong commandGeneration = new AtomicLong();
	private final AtomicLong lifecycleGeneration = new AtomicLong();
	private boolean runtimeInstalled;
	private volatile boolean enabled;
	private volatile ScriptInputSession inputSession;
	private volatile boolean restartRequested;
	private final ThreadLocal<Boolean> updatingRun = ThreadLocal.withInitial(() -> false);
	private MobileScript current;
	private ScriptKind activeKind;
	private int tickCount;
	private final HubPluginHost hub = new HubPluginHost(net.runelite.client.plugins.microbot.Microbot::stopPlugin);

	@Provides
	MobileMicrobotConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(MobileMicrobotConfig.class);
	}

	@Override
	protected void startUp()
	{
		enabled = false;
		final long lifecycle = lifecycleGeneration.incrementAndGet();
		commandGeneration.incrementAndGet();
		// Never auto-resume on launch, even if a synced config previously had Run=true.
		setRun(false);
		clientThread.invokeLater(() ->
		{
			if (lifecycle != lifecycleGeneration.get())
			{
				return;
			}
			try
			{
				MicrobotRuntime.install(client, clientThread, eventBus, configManager, pluginManager, worldService,
					java.util.List.of(readServices));
				runtimeInstalled = true;
				net.runelite.client.plugins.microbot.util.walker.Rs2Walker.setUsePathfinder(config.usePathfinder());
				setRun(false);
				enabled = true;
				// RuneLite remembers enabled plugins; a Hub plugin back on after a restart was not
				// explicitly started this session, so turn it off (no silent auto-start).
				for (Plugin plugin : pluginManager == null ? java.util.List.<Plugin>of() : pluginManager.getPlugins())
				{
					if (HubPluginHost.isHubPlugin(plugin) && pluginManager.isPluginActive(plugin))
					{
						net.runelite.client.plugins.microbot.Microbot.stopPlugin(plugin);
					}
				}
				MobileScriptBridge.attach(this);
				MobileScriptBridge.update("Choose a script and enable Run script", "", false);
			}
			catch (RuntimeException exception)
			{
				cancelInput();
				enabled = false;
				if (runtimeInstalled)
				{
					// Never leave a half-started runtime installed behind a failed start.
					MicrobotRuntime.uninstall();
					runtimeInstalled = false;
				}
				setRun(false);
				LOG.error("Mobile runtime initialization failed", exception);
				MobileScriptBridge.update("Runtime initialization failed: " + exception.getClass().getSimpleName(), "", false);
			}
		});
	}

	@Override
	protected void shutDown()
	{
		// Fail closed immediately for the native-side status surface, even when
		// engine ticks are paused/backgrounded.
		enabled = false;
		lifecycleGeneration.incrementAndGet();
		cancelInput();
		commandGeneration.incrementAndGet();
		setRun(false);
		MobileScriptBridge.detach(this);
		hub.end(true);
		clientThread.invokeLater(() ->
		{
			try
			{
				stopActive("Plugin disabled");
			}
			finally
			{
				if (runtimeInstalled)
				{
					MicrobotRuntime.uninstall();
					runtimeInstalled = false;
				}
			}
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (GROUP.equals(event.getGroup()))
		{
			tickCount = 0;
			net.runelite.client.plugins.microbot.util.walker.Rs2Walker.setUsePathfinder(config.usePathfinder());
			if ("runScript".equals(event.getKey()))
			{
				if (!config.runScript())
				{
					if (!updatingRun.get())
					{
						commandGeneration.incrementAndGet();
					}
					cancelInput();
				}
				else if (!updatingRun.get())
				{
					commandGeneration.incrementAndGet();
					cancelInput();
					// One script at a time: a mobile script start turns off an active Hub plugin.
					hub.end(true);
					inputSession = new ScriptInputSession();
					restartRequested = true;
				}
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.HOPPING && MobileActions.isExpectedHop())
		{
			// The running script asked for this hop (Microbot.hopToWorld); it keeps its session.
			// Any other hop, or a disconnect during this one, still stops it below.
			LOG.info("Script hop to world {} in progress; keeping its session", MobileActions.expectedHopWorld());
			return;
		}
		if (state != GameState.LOGGED_IN && state != GameState.LOADING)
		{
			commandGeneration.incrementAndGet();
			stopActive("Stopped: signed out, hopping or disconnected");
			if (hub.end(true))
			{
				MobileScriptBridge.update("Hub plugin stopped: signed out, hopping or disconnected", "", false);
			}
			// This avoids a surprise restart after RuneMobile's automatic relogin.
			if (config.runScript())
			{
				setRun(false);
			}
		}
	}

	/** Hub plugins: enabling one in the plugin list is the explicit start; see {@link HubPluginHost}. */
	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		Plugin plugin = event.getPlugin();
		if (!HubPluginHost.isHubPlugin(plugin))
		{
			return;
		}
		if (event.isLoaded())
		{
			if (enabled && runtimeInstalled && config.runScript())
			{
				commandGeneration.incrementAndGet();
				setRun(false);
				clientThread.invokeLater(() -> stopActive("Stopped: a Hub plugin was started"));
			}
			String status = hub.started(plugin, enabled && runtimeInstalled);
			MobileScriptBridge.update(status, HubPluginHost.name(plugin), hub.isRunning());
		}
		else
		{
			boolean wasActive = hub.activePlugin() == plugin;
			hub.stopped(plugin);
			if (wasActive)
			{
				MobileScriptBridge.update("Hub plugin stopped: " + HubPluginHost.name(plugin), "", false);
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
		if (hub.checkSession())
		{
			MobileScriptBridge.update("Hub plugin stopped: input session cancelled (app backgrounded or input fault)", "", false);
		}
		if (!config.runScript())
		{
			stopActive("Stopped");
			return;
		}
		if (current != null && current.finished())
		{
			String reason = current.status();
			stopActive(reason);
			setRun(false);
			return;
		}
		if (inputSession != null && !inputSession.isActive())
		{
			stopActive("Stopped: input session cancelled (Stop, app backgrounded or input fault)");
			setRun(false);
			return;
		}
		if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
		{
			stopActive("Paused: not logged in");
			return;
		}

		ScriptKind desired = config.selectedScript();
		try
		{
			if (current == null || activeKind != desired || restartRequested)
			{
				ScriptInputSession next = inputSession == null
					? new ScriptInputSession() : inputSession.successor();
				stopActive("Changing scripts");
				inputSession = next;
				if (!next.isActive())
				{
					stopActive("Stopped: app backgrounded during script start");
					setRun(false);
					return;
				}
				current = newScript(desired);
				activeKind = desired;
				current.onStart(new MobileScriptContext(client, config, inputSession));
			}
			MobileScriptContext context = new MobileScriptContext(client, config, inputSession);
			tickCount++;
			current.onGameTick(context);
			MobileScriptBridge.update(current.status() + " (tick " + tickCount + ")", activeKind.name(), true);
			if (desired == ScriptKind.WOODCUTTING && context.inventoryFull())
			{
				stopActive("Inventory full; automatically stopped");
				setRun(false);
			}
		}
		catch (Exception exception)
		{
			LOG.error("Mobile script failed: {}", desired, exception);
			stopActive("Script error: " + exception.getClass().getSimpleName());
			setRun(false);
		}
	}

	private MobileScript newScript(ScriptKind kind)
	{
		switch (kind)
		{
			case STATE_MACHINE_EXAMPLE:
				return new MicrobotScriptAdapter<>("Upstream state machine example",
					StateMachineExampleScript::new,
					script -> script.run(configManager.getConfig(StateMachineExampleConfig.class)));
			case AUTO_WOODCUTTING:
				return new MobileAutoWoodcuttingScript(config, overlayManager);
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
		cancelInput();
		inputSession = null;
		restartRequested = false;
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

	private void cancelInput()
	{
		ScriptInputSession session = inputSession;
		if (session != null)
		{
			session.close();
		}
	}

	boolean inputActive()
	{
		ScriptInputSession session = inputSession;
		return enabled && session != null && session.isActive();
	}

	private void setRun(boolean value)
	{
		boolean previous = updatingRun.get();
		updatingRun.set(true);
		try
		{
			configManager.setConfiguration(GROUP, "runScript", value);
		}
		finally
		{
			updatingRun.set(previous);
		}
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
			requestStop();
			MobileScriptBridge.update("Unknown script: " + kind, "", false);
			return;
		}
		final long generation = commandGeneration.incrementAndGet();
		cancelInput();
		hub.end(true);
		final ScriptInputSession permission = new ScriptInputSession();
		clientThread.invokeLater(() ->
		{
			if (!enabled || generation != commandGeneration.get() || !permission.isActive())
			{
				return;
			}
			stopActive("Starting script");
			inputSession = permission;
			configManager.setConfiguration(GROUP, "selectedScript", selected);
			setRun(true);
		});
	}

	void requestStop()
	{
		final long generation = commandGeneration.incrementAndGet();
		cancelInput();
		clientThread.invokeLater(() ->
		{
			if (!enabled || generation != commandGeneration.get())
			{
				return;
			}
			setRun(false);
			stopActive("Stopped by user");
		});
	}
}
