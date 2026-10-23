package net.runelite.client.plugins.microbot.api;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.MicrobotScriptAdapter;
import net.runelite.client.plugins.microbot.statemachine.StateSnapshot;
import net.runelite.client.plugins.microbot.statemachineexample.StateMachineExampleConfig;
import net.runelite.client.plugins.microbot.statemachineexample.StateMachineExampleScript;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual unmodified upstream script and runtime; dedicated mocked engine thread, not a game. */
public class UpstreamExampleIntegrationTest
{
	private ReadApiFixture fixture;
	private EventBus events;
	private MicrobotScriptAdapter<StateMachineExampleScript> adapter;

	@Before
	public void setUp() throws Exception
	{
		fixture = new ReadApiFixture();
		MicrobotReadServices.uninstall(fixture.services);
		fixture.npc(1, "Test NPC", new WorldPoint(3206, 3205, 0));
		when(fixture.localPlayer.getAnimation()).thenReturn(-1);
		ItemContainer inventory = mock(ItemContainer.class);
		when(inventory.getItems()).thenReturn(new Item[]{new Item(995, 1000), new Item(1, 1), new Item(-1, 0)});
		when(fixture.client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
		// Assert the login guard too, not only entity getters, stays on the client thread.
		when(fixture.client.getGameState()).thenAnswer(call -> fixture.onClientThread(fixture.gameState));
		events = new EventBus();
		fixture.clientExecutor.submit(() -> MicrobotRuntime.install(fixture.client, fixture.clientThread,
			events, mock(ConfigManager.class), Collections.singletonList(
				new MicrobotReadRuntimeService(fixture.services, events)))).get(3, TimeUnit.SECONDS);
	}

	@After
	public void tearDown() throws Exception
	{
		if (adapter != null) { adapter.onStop(null); }
		MicrobotRuntime.uninstall();
		fixture.close();
	}

	private static int integerField(Object object, String name) throws Exception
	{
		Field field = object.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.getInt(object);
	}

	@Test
	public void unchangedExampleRunsAllReadPhasesAndCompletesReport() throws Exception
	{
		StateMachineExampleConfig config = new StateMachineExampleConfig()
		{
			@Override public int tickDelay() { return 10; }
			@Override public int idleDuration() { return 0; }
		};
		adapter = new MicrobotScriptAdapter<>("upstream example", StateMachineExampleScript::new,
			script -> script.run(config));
		adapter.onStart(null);
		StateMachineExampleScript script = adapter.getScript();
		ScheduledExecutorService observer = Executors.newSingleThreadScheduledExecutor();
		CompletableFuture<Void> completedReport = new CompletableFuture<>();
		try
		{
			observer.scheduleWithFixedDelay(() ->
			{
				StateSnapshot<?> snapshot = script.getSnapshot();
				if (snapshot != null && snapshot.recentTrace().stream()
					.anyMatch(trace -> "REPORT".equals(trace.from().name())))
				{
					completedReport.complete(null);
				}
			}, 0, 5, TimeUnit.MILLISECONDS);
			completedReport.get(4, TimeUnit.SECONDS);
			assertTrue(script.isRunning());
			assertTrue(integerField(script, "cycleCount") >= 1);
			assertEquals(1, integerField(script, "nearbyNpcCount"));
			assertEquals(0, integerField(script, "nearbyObjectCount"));
			assertEquals(0, integerField(script, "nearbyItemCount"));
			assertEquals(2, Rs2Inventory.count());
			assertSame(fixture.services.getRs2NpcCache(), Microbot.getRs2NpcCache());
		}
		finally
		{
			observer.shutdownNow();
		}
		adapter.onStop(null);
		assertFalse(script.isRunning());
		assertNull(adapter.getScript());
	}

	@Test
	public void uninstallClearsOldCacheHandlesAndSingletonGetters()
	{
		assertEquals(1, Microbot.getRs2NpcCache().query().within(15).count());
		MicrobotRuntime.uninstall();
		assertNull(MicrobotReadServices.current());
		assertEquals(0, fixture.services.getRs2NpcCache().query().toList().size());
		try
		{
			Microbot.getRs2NpcCache();
			fail("Uninstalled singleton must refuse cache access");
		}
		catch (IllegalStateException expected)
		{
			// Explicit refusal, rather than stale entity access.
		}
	}

	@Test
	public void inventoryLoginGuardHopsToClientThreadAndClearsOnLoggingIn() throws Exception
	{
		assertEquals(2, Rs2Inventory.count());
		fixture.gameState = GameState.LOGGING_IN;
		GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.LOGGING_IN);
		fixture.clientExecutor.submit(() -> events.post(event)).get(3, TimeUnit.SECONDS);
		assertFalse(Microbot.isLoggedIn());
		assertEquals(0, Rs2Inventory.count());
	}
}
