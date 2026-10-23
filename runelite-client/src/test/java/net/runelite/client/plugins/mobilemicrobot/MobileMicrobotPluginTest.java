package net.runelite.client.plugins.mobilemicrobot;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Queue;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real RuneMobile types and services, mocked at their boundaries; no game/device. */
public class MobileMicrobotPluginTest
{
	private final Queue<Runnable> commands = new ArrayDeque<>();
	private MobileMicrobotPlugin plugin;
	private Client client;
	private Player player;
	private Settings config;

	private static class Settings implements MobileMicrobotConfig
	{
		boolean run;
		ScriptKind kind = ScriptKind.DIAGNOSTICS;
		@Override public boolean runScript() { return run; }
		@Override public ScriptKind selectedScript() { return kind; }
	}

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		player = mock(Player.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getLocalPlayer()).thenReturn(player);
		ClientThread thread = mock(ClientThread.class);
		doAnswer(call -> { commands.add(call.getArgument(0)); return null; })
			.when(thread).invokeLater(any(Runnable.class));
		config = new Settings();
		ConfigManager manager = mock(ConfigManager.class);
		doAnswer(call ->
		{
			String key = call.getArgument(1);
			if ("runScript".equals(key)) { config.run = call.getArgument(2); }
			if ("selectedScript".equals(key)) { config.kind = call.getArgument(2); }
			return null;
		}).when(manager).setConfiguration(eq("mobilemicrobot"), anyString(), any(Object.class));
		plugin = new MobileMicrobotPlugin();
		inject("client", client);
		inject("clientThread", thread);
		inject("configManager", manager);
		inject("config", config);
		plugin.startUp();
	}

	private void inject(String name, Object value) throws Exception
	{
		Field field = MobileMicrobotPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
	}

	private void drain()
	{
		while (!commands.isEmpty()) { commands.remove().run(); }
	}

	private void start(String script)
	{
		MobileScriptBridge.start(script);
		drain();
		plugin.onGameTick(new GameTick());
	}

	@After
	public void tearDown()
	{
		plugin.shutDown();
		drain();
	}

	@Test
	public void startsOnlyAfterExplicitRequest()
	{
		plugin.onGameTick(new GameTick());
		assertFalse(config.run);
		start("DIAGNOSTICS");
		assertTrue(config.run);
		assertTrue(MobileScriptBridge.statusJson().contains("\"running\":true"));
	}

	@Test
	public void stopInvalidatesPendingStart()
	{
		MobileScriptBridge.start("DIAGNOSTICS");
		MobileScriptBridge.stop();
		drain();
		plugin.onGameTick(new GameTick());
		assertFalse(config.run);
		assertTrue(MobileScriptBridge.statusJson().contains("\"running\":false"));
	}

	@Test
	public void disableInvalidatesPendingStart()
	{
		MobileScriptBridge.start("DIAGNOSTICS");
		plugin.shutDown();
		drain();
		plugin.onGameTick(new GameTick());
		assertFalse(config.run);
		assertTrue(MobileScriptBridge.statusJson().contains("\"ready\":false"));
	}

	@Test
	public void disconnectAndHopRequireAnotherExplicitStart()
	{
		for (GameState state : new GameState[]{GameState.CONNECTION_LOST, GameState.HOPPING,
			GameState.LOGGING_IN, GameState.LOGIN_SCREEN})
		{
			start("DIAGNOSTICS");
			GameStateChanged event = new GameStateChanged();
			event.setGameState(state);
			plugin.onGameStateChanged(event);
			plugin.onGameTick(new GameTick());
			assertFalse(state.name(), config.run);
		}
	}

	@Test
	public void disconnectInvalidatesPendingStart()
	{
		MobileScriptBridge.start("DIAGNOSTICS");
		GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.CONNECTION_LOST);
		plugin.onGameStateChanged(event);
		drain();
		assertFalse(config.run);
	}

	@Test
	public void swapsScriptsWithoutReusingPreviousStatus()
	{
		start("DIAGNOSTICS");
		start("SCENE_SCAN");
		assertTrue(MobileScriptBridge.statusJson().contains("Scene objects"));
		assertTrue(MobileScriptBridge.statusJson().contains("SCENE_SCAN"));
	}

	@Test
	public void exceptionStopsAndDoesNotRetry()
	{
		when(client.getScene()).thenThrow(new IllegalStateException("test scene failure"));
		start("SCENE_SCAN");
		assertFalse(config.run);
		assertTrue(MobileScriptBridge.statusJson().contains("Script error: IllegalStateException"));
		plugin.onGameTick(new GameTick());
		verify(client, times(1)).getScene();
	}

	@Test
	public void invalidScriptCannotEnableExecution()
	{
		start("NOT_A_SCRIPT");
		assertFalse(config.run);
	}

	@Test
	public void actualPlayerPoseApiPreventsWorkWhileMoving()
	{
		when(player.getPoseAnimation()).thenReturn(819);
		when(player.getIdlePoseAnimation()).thenReturn(808);
		MobileScriptContext context = new MobileScriptContext(client, config);
		assertTrue(context.player().isMoving());
		assertFalse(context.player().isIdle());
		new WoodcuttingScript().onGameTick(context);
		verify(client, never()).getScene();
		when(player.getPoseAnimation()).thenReturn(808);
		when(player.getAnimation()).thenReturn(-1);
		assertTrue(context.player().isIdle());
	}
}
