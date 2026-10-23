package net.runelite.client.plugins.microbot.util;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.leaguetransport.Rs2LeaguesTransport;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Upstream read methods added for the pathfinder port, with mocked RuneLite interfaces. */
public class PathfinderReadDepsTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final int DRAMEN_STAFF = 772;
	private static final int COINS = 995;
	private static final int OAK = 1521;

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final EventBus events = new EventBus();
	private Client client;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			onClientThread.set(true);
			try { ((Runnable) call.getArgument(0)).run(); }
			finally { onClientThread.set(false); }
			return null;
		}).when(thread).invoke(any(Runnable.class));
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
		define(DRAMEN_STAFF, "Dramen staff");
		define(COINS, "Coins");
		define(OAK, "Oak logs");
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	private void define(int id, String name)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn(name);
		when(definition.getInventoryActions()).thenReturn(new String[5]);
		when(definition.getNote()).thenReturn(-1);
		when(client.getItemDefinition(id)).thenReturn(definition);
	}

	private static ItemContainer container(Item... items)
	{
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		return container;
	}

	@Test
	public void equipmentAllGetAndIsWearingByIds()
	{
		ItemContainer worn = container(new Item(-1, 0), new Item(-1, 0), new Item(-1, 0), new Item(DRAMEN_STAFF, 1));
		when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(worn);

		List<Rs2ItemModel> all = Rs2Equipment.all().collect(Collectors.toList());
		assertEquals(1, all.size());
		assertEquals(3, all.get(0).getSlot());
		assertTrue(Rs2Equipment.isWearing(OAK, DRAMEN_STAFF));
		assertFalse(Rs2Equipment.isWearing(OAK, COINS));
		assertEquals(DRAMEN_STAFF, Rs2Equipment.get(OAK, DRAMEN_STAFF).getId());
		assertNull(Rs2Equipment.get(new int[0]));
	}

	@Test
	public void bankCountByNameAndGetAllReadTheRememberedBank()
	{
		onClientThread.set(true);
		try
		{
			events.post(new ItemContainerChanged(InventoryID.BANK.getId(), container(new Item(OAK, 50), new Item(COINS, 10_000))));
		}
		finally
		{
			onClientThread.set(false);
		}

		assertEquals(2, Rs2Bank.getAll().count());
		assertEquals(1, Rs2Bank.getAll(item -> item.getId() == COINS).count());
		assertEquals(10_000, Rs2Bank.count("coins"));
		assertEquals(50, Rs2Bank.count("logs"));
		assertEquals(0, Rs2Bank.count("logs", true));
		assertEquals(50, Rs2Bank.count("Oak logs", true));
	}

	@Test
	public void isInInstanceReadsTheTopLevelScene()
	{
		WorldView top = mock(WorldView.class);
		Scene scene = mock(Scene.class);
		when(client.getTopLevelWorldView()).thenReturn(top);
		when(top.getScene()).thenReturn(scene);
		assertFalse(Rs2Player.IsInInstance());
		when(scene.isInstance()).thenReturn(true);
		assertTrue(Rs2Player.IsInInstance());
	}

	@Test
	public void outsideLeaguesEveryTransportIsAllowed()
	{
		Rs2LeaguesTransport.LeaguesContext context = Rs2LeaguesTransport.leaguesContext();
		assertFalse(context.isActive());
		assertTrue(context.getUnlockedRegions().isEmpty());
		Map<WorldPoint, Set<Transport>> catalogue = Transport.loadAllFromResources();
		Transport seasonal = catalogue.values().stream().flatMap(Set::stream)
			.filter(t -> t.getType() == TransportType.SEASONAL_TRANSPORT).findFirst().orElse(null);
		Transport stairs = catalogue.values().stream().flatMap(Set::stream)
			.filter(t -> t.getType() == TransportType.TRANSPORT).findFirst().orElseThrow();
		assertTrue(Rs2LeaguesTransport.isTransportAllowed(context, stairs));
		if (seasonal != null)
		{
			assertTrue(Rs2LeaguesTransport.isTransportAllowed(context, seasonal));
		}
		assertFalse(Rs2LeaguesTransport.isTransportAllowed(context, null));
	}
}
