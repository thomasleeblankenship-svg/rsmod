package net.runelite.client.plugins.microbot.util.security;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.World;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.WorldService;
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.PluginManager;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Script-initiated hops: upstream's world selection, and a hop that waits until it has landed. */
public class WorldHopTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private Client client;
	private final AtomicBoolean clientThread = new AtomicBoolean(true);
	private final AtomicInteger world = new AtomicInteger(301);
	private final AtomicInteger tick = new AtomicInteger(1);
	private volatile GameState state = GameState.LOGGED_IN;
	private ScriptInputSession session;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> clientThread.get());
		when(client.getGameState()).thenAnswer(call -> state);
		when(client.getWorld()).thenAnswer(call -> world.get());
		when(client.getTickCount()).thenAnswer(call -> tick.get());
		when(client.getLocalPlayer()).thenReturn(mock(Player.class));
		when(client.getWorldType()).thenReturn(EnumSet.noneOf(net.runelite.api.WorldType.class));
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			boolean was = clientThread.get();
			clientThread.set(true);
			try { ((Runnable) call.getArgument(0)).run(); }
			finally { clientThread.set(was); }
			return null;
		}).when(thread).invoke(any(Runnable.class));
		EventBus events = new EventBus();
		WorldService worlds = mock(WorldService.class);
		WorldResult result = new WorldResult();
		result.setWorlds(List.of(
			world(301, 100, WorldRegion.UNITED_STATES_OF_AMERICA, WorldType.MEMBERS),
			world(302, 100, WorldRegion.UNITED_KINGDOM, WorldType.MEMBERS),
			world(303, 100, WorldRegion.UNITED_KINGDOM, WorldType.MEMBERS, WorldType.PVP),
			world(304, 1999, WorldRegion.UNITED_KINGDOM, WorldType.MEMBERS),
			world(308, 100, WorldRegion.GERMANY)));
		when(worlds.getWorlds()).thenReturn(result);
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class), mock(PluginManager.class), worlds,
			List.of(new MicrobotReadServices(client, thread, events)));
		clientThread.set(false);
		session = new ScriptInputSession();
		MobileActions.bind(session);
	}

	private static net.runelite.http.api.worlds.World world(int id, int players, WorldRegion region, WorldType... types)
	{
		EnumSet<WorldType> set = EnumSet.noneOf(WorldType.class);
		set.addAll(List.of(types));
		return net.runelite.http.api.worlds.World.builder().id(id).players(players).region(region).types(set)
			.address("oldschool" + id + ".runescape.com").activity("-").build();
	}

	@After
	public void tearDown()
	{
		MobileActions.unbind(session);
		MicrobotRuntime.uninstall();
	}

	@Test
	public void worldSelectionKeepsUpstreamsFilters()
	{
		for (int i = 0; i < 20; i++)
		{
			int picked = Login.getRandomWorld(true);
			assertTrue("PvP and full worlds are never picked: " + picked, picked == 301 || picked == 302);
		}
		assertEquals(302, Login.getRandomWorld(true, WorldRegion.UNITED_KINGDOM));
		assertEquals(308, Login.getRandomWorld(false));
		assertEquals("the world after the current one", 302, Login.getNextWorld(true));
	}

	@Test
	public void hopOpensTheSwitcherRequestsTheWorldAndWaitsUntilLoggedInThere()
	{
		World created = mock(World.class);
		when(created.getId()).thenAnswer(call -> 302);
		when(client.createWorld()).thenReturn(created);
		doAnswer(call -> { tick.incrementAndGet(); return null; }).when(client).openWorldHopper();
		doAnswer(call ->
		{
			assertTrue("the hop is recorded as the script's own before it starts", MobileActions.isExpectedHop());
			state = GameState.HOPPING;
			new Thread(() ->
			{
				try { Thread.sleep(300); } catch (InterruptedException ignored) { }
				world.set(302);
				state = GameState.LOGGED_IN;
			}).start();
			return null;
		}).when(client).hopToWorld(created);

		assertTrue(Microbot.hopToWorld(302));
		verify(client).openWorldHopper();
		verify(created).setId(302);
		assertFalse("the expectation is cleared once landed", MobileActions.isExpectedHop());
	}

	@Test
	public void hopIsRefusedForAnUnknownWorldOrWithoutASession()
	{
		assertFalse(Microbot.hopToWorld(999));
		assertTrue("already there", Microbot.hopToWorld(301));
		MobileActions.unbind(session);
		assertFalse(Microbot.hopToWorld(302));
		verify(client, never()).hopToWorld(any());
	}
}
