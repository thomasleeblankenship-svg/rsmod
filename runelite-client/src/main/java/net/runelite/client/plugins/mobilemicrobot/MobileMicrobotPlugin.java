package net.runelite.client.plugins.mobilemicrobot;

import com.google.inject.Provides;
import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
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
import net.runelite.client.plugins.microbot.accountselector.AutoLoginSuppressionRequest;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
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
	@Inject private net.runelite.client.Notifier notifier;
	@Inject private net.runelite.client.input.MouseManager mouseManager;
	@Inject private net.runelite.client.ui.overlay.infobox.InfoBoxManager infoBoxManager;
	@Inject private net.runelite.client.game.WorldService worldService;
	@Inject private net.runelite.client.game.ItemManager itemManager;

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
	private final MobileAutoLogin autoLogin = new MobileAutoLogin(() -> enabled && config.autoLogin(),
		() -> config.autoLoginWorld(), () -> client.getLoginIndex(), System::currentTimeMillis);
	private java.util.concurrent.ScheduledExecutorService autoLoginExecutor;
	private boolean membershipRefreshed;
	private GameState previousGameState = GameState.UNKNOWN;

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
		autoLoginExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable ->
		{
			Thread thread = new Thread(runnable, "mobile-auto-login");
			thread.setDaemon(true);
			return thread;
		});
		autoLogin.start(autoLoginExecutor);
		net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript.start(breakSettings(), System.currentTimeMillis());
		autoLoginExecutor.scheduleWithFixedDelay(this::tickBreakHandler, 600, 600, java.util.concurrent.TimeUnit.MILLISECONDS);
		net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin.start();
		net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin.setMasterEnabled(config.antiban());
		// As upstream's MicrobotPlugin: the hop allowance survives restarts (per network).
		autoLoginExecutor.execute(() -> net.runelite.client.plugins.microbot.util.security.HopBudget.getInstance().persistTo(
			new java.io.File(net.runelite.client.RuneLite.RUNELITE_DIR,
				net.runelite.client.plugins.microbot.util.security.HopBudget.FILE_NAME).toPath(),
			net.runelite.client.plugins.microbot.util.security.HopBudget.networkKey()));
		clientThread.invokeLater(() ->
		{
			if (lifecycle != lifecycleGeneration.get())
			{
				return;
			}
			try
			{
				MicrobotRuntime.install(client, clientThread, eventBus, configManager, pluginManager, worldService, itemManager,
					java.util.List.of(readServices));
				runtimeInstalled = true;
				net.runelite.client.plugins.microbot.Microbot.bindNotifier(notifier);
				net.runelite.client.plugins.microbot.Microbot.bindMouseManager(mouseManager);
				net.runelite.client.plugins.microbot.Microbot.bindInfoBoxManager(infoBoxManager);
				if (net.runelite.client.plugins.microbot.Microbot.getBlockingEventManager() != null)
				{
					net.runelite.client.plugins.microbot.Microbot.getBlockingEventManager().addDefaultEvents();
				}
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
				// The Microbot screen's Start turns this plugin on when it is off; run that start now.
				MobileScriptBridge.startPending();
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
		autoLogin.stop();
		net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript.stop();
		net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin.stop();
		if (autoLoginExecutor != null)
		{
			autoLoginExecutor.shutdownNow();
			autoLoginExecutor = null;
		}
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
			net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin.setMasterEnabled(config.antiban());
			net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript.settingsChanged();
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
					autoLogin.resume();
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
		if (isLoginServerRequest(previousGameState, state))
		{
			// As upstream: every login or hop the login server sees spends one of the hop allowance.
			net.runelite.client.plugins.microbot.util.security.HopBudget.getInstance().recordHop();
		}
		previousGameState = state;
		LoginManager.setLastKnownGameState(state);
		autoLogin.observe(state);
		net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin.onGameStateChanged(state);
		if (state == GameState.LOGGED_IN)
		{
			membershipRefreshed = false;
		}
		if (state == GameState.HOPPING && MobileActions.isExpectedHop())
		{
			// The running script asked for this hop (Microbot.hopToWorld); it keeps its session.
			// Any other hop, or a disconnect during this one, still stops it below.
			LOG.info("Script hop to world {} in progress; keeping its session", MobileActions.expectedHopWorld());
			return;
		}
		if (state != GameState.LOGGED_IN && state != GameState.LOADING)
		{
			if (keepsScriptsThroughLogout() && (current != null || hub.isRunning()))
			{
				// Auto-login (or the break handler, after its break) signs back in; the script keeps its
				// session and continues once logged in.
				boolean onBreak = net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript.isBreakActive();
				LOG.info("Logged out ({}); {} will sign back in and the script continues", state, onBreak ? "the break handler" : "auto-login");
				MobileScriptBridge.update(onBreak ? "Paused: on a break; logs back in afterwards" : "Paused: logged out; auto-login is signing back in",
					hub.isRunning() ? HubPluginHost.name(hub.activePlugin()) : activeKind == null ? "" : activeKind.name(), true);
				return;
			}
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

	/** As upstream's {@code MicrobotPlugin.isLoginServerRequest}: a login or hop reaching the login server. */
	static boolean isLoginServerRequest(GameState previous, GameState next)
	{
		return next == GameState.LOGGING_IN && previous != GameState.HOPPING && previous != GameState.LOGGING_IN;
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
			autoLogin.resume();
			String status = hub.started(plugin, enabled && runtimeInstalled);
			MobileScriptBridge.update(status, HubPluginHost.name(plugin), hub.isRunning());
		}
		else
		{
			boolean wasActive = hub.activePlugin() == plugin;
			hub.stopped(plugin);
			if (wasActive)
			{
				// The script stopped itself (a user Stop ends the session first, so it is not active here):
				// keep the reason it gave, e.g. "No essence in bank!".
				String reason = MobileScriptBridge.scriptDetail();
				MobileScriptBridge.update("Hub plugin stopped: " + HubPluginHost.name(plugin)
					+ (reason.isEmpty() ? "" : " - " + reason), "", false);
			}
		}
	}

	/**
	 * As upstream's {@code AutoLoginPlugin}: a script's terminal logout ({@code Rs2Player.logoutWithoutAutoLogin})
	 * holds auto-login off until a script starts again or the user logs in.
	 */
	@Subscribe
	public void onAutoLoginSuppressionRequest(AutoLoginSuppressionRequest event)
	{
		if (event.isRenewal())
		{
			autoLogin.renewSuppression();
		}
		else if (event.isLoggedOut())
		{
			autoLogin.holdLoggedOut();
		}
		else
		{
			autoLogin.suppress();
		}
	}

	/** As upstream's {@code MicrobotPlugin}: any stat change counts as gaining experience for 4 s. */
	/** Feeds upstream's death-state tracking ({@code Rs2Death}), as upstream's MicrobotPlugin does. */
	@Subscribe
	public void onActorDeath(net.runelite.api.events.ActorDeath event)
	{
		net.runelite.client.plugins.microbot.util.death.Rs2Death.handleActorDeath(event);
	}

	/** Notes a grave appearing for {@code Rs2Death}. */
	@Subscribe
	public void onVarbitChanged(net.runelite.api.events.VarbitChanged event)
	{
		net.runelite.client.plugins.microbot.util.death.Rs2Death.onVarbitChanged(event);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		net.runelite.client.plugins.microbot.Microbot.setIsGainingExp(true);
		if (enabled)
		{
			net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin.onStatChanged(event);
		}
	}

	/** Feeds the anti-ban engine's cooking-activity read ({@code AntibanPlugin.isCooking}). */
	@Subscribe
	public void onChatMessage(net.runelite.api.events.ChatMessage event)
	{
		if (enabled)
		{
			net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin.onChatMessage(event);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!enabled)
		{
			return;
		}
		if (!membershipRefreshed)
		{
			// The membership varp is loaded by the first tick in game; auto-login's world choice reads it.
			membershipRefreshed = true;
			LoginManager.refreshMembership();
		}
		// The anti-ban engine: idle and mining tracking, action cooldowns, micro-break release, play styles.
		net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin.onGameTick();
		if (hub.checkSession())
		{
			MobileScriptBridge.update("Hub plugin stopped: input session cancelled (app backgrounded or input fault)", "", false);
		}
		if (!config.runScript())
		{
			// Only when a built-in script is still up: a Hub script runs with runScript off, and stopping
			// here every tick would mark it stopped and wipe its status (and any failure reason).
			if (current != null || inputSession != null)
			{
				stopActive("Stopped");
			}
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
			if (!keepsScriptsThroughLogout())
			{
				stopActive("Paused: not logged in");
			}
			return;
		}

		ScriptKind desired = config.selectedScript();
		if (desired.isHub())
		{
			// "Run script" with a Hub script selected (e.g. from the generic settings list): start it the Hub way.
			setRun(false);
			startHub(desired);
			return;
		}
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
		// A built-in stop never reports a running Hub plugin as stopped.
		if (!hub.isRunning())
		{
			MobileScriptBridge.update(reason, "", false);
		}
	}

	private void cancelInput()
	{
		ScriptInputSession session = inputSession;
		if (session != null)
		{
			session.close();
		}
	}

	/** A logout only pauses the script: auto-login, or the break handler after its break, signs back in. */
	private boolean keepsScriptsThroughLogout()
	{
		return autoLogin.keepsScriptsThroughLogout() || net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript.isBreakActive();
	}

	private void tickBreakHandler()
	{
		try
		{
			net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript.tick(System.currentTimeMillis());
		}
		catch (RuntimeException exception)
		{
			LOG.warn("Break handler step failed", exception);
		}
	}

	/** The break handler reads the plugin's break settings live. */
	private net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript.Settings breakSettings()
	{
		return new net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript.Settings()
		{
			@Override
			public boolean scheduledBreaks()
			{
				return config.antiban() && config.breakHandler();
			}

			@Override
			public int playMinutesMin()
			{
				return config.breakPlayMin();
			}

			@Override
			public int playMinutesMax()
			{
				return config.breakPlayMax();
			}

			@Override
			public int breakMinutesMin()
			{
				return config.breakLengthMin();
			}

			@Override
			public int breakMinutesMax()
			{
				return config.breakLengthMax();
			}

			@Override
			public boolean logoutOnBreak()
			{
				return config.breakLogout();
			}

			@Override
			public boolean onlyMicroBreaks()
			{
				return config.breakOnlyMicro();
			}

			@Override
			public boolean scriptRunning()
			{
				return inputActive();
			}

			@Override
			public boolean loginHeld()
			{
				return autoLogin.loginHeld();
			}
		};
	}

	boolean inputActive()
	{
		ScriptInputSession session = inputSession;
		return enabled && ((session != null && session.isActive()) || hub.isRunning());
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
		if (selected.isHub())
		{
			startHub(selected);
			return;
		}
		final long generation = commandGeneration.incrementAndGet();
		cancelInput();
		hub.end(true);
		final ScriptInputSession permission = new ScriptInputSession();
		autoLogin.resume();
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
		if (hub.end(true))
		{
			MobileScriptBridge.update("Stopped by user", "", false);
		}
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

	/**
	 * Starts a Hub-backed script from the Microbot screen: stops any built-in script, selects it and turns its
	 * plugin on; {@link #onPluginChanged} then gives it its input session through {@link HubPluginHost}.
	 */
	private void startHub(ScriptKind selected)
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
			stopActive("Starting script");
			configManager.setConfiguration(GROUP, "selectedScript", selected);
			Plugin plugin = net.runelite.client.plugins.microbot.Microbot.getPlugin(selected.getHubPluginClass());
			if (plugin == null)
			{
				MobileScriptBridge.update("Not available: " + selected, "", false);
				return;
			}
			if (hub.activePlugin() != plugin)
			{
				net.runelite.client.plugins.microbot.Microbot.startPlugin(plugin);
			}
		});
	}
}
