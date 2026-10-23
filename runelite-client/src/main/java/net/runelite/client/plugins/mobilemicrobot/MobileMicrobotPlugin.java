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
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.MicrobotScriptAdapter;
import net.runelite.client.plugins.microbot.api.MicrobotReadRuntimeService;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
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
				MicrobotRuntime.install(client, clientThread, eventBus, configManager,
					java.util.Collections.singletonList(new MicrobotReadRuntimeService(readServices, eventBus)));
				runtimeInstalled = true;
				setRun(false);
				enabled = true;
				MobileScriptBridge.attach(this);
				MobileScriptBridge.update("Choose a script and enable Run script", "", false);
			}
			catch (RuntimeException exception)
			{
				cancelInput();
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
		if (state != GameState.LOGGED_IN && state != GameState.LOADING)
		{
			commandGeneration.incrementAndGet();
			stopActive("Stopped: signed out, hopping or disconnected");
			// This avoids a surprise restart after RuneMobile's automatic relogin.
			if (config.runScript())
			{
				setRun(false);
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
		if (inputSession != null && !inputSession.isActive())
		{
			stopActive("Stopped: manual input or cancelled input session");
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
					stopActive("Stopped: manual input during script start");
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
