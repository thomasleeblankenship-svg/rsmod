package net.runelite.client.plugins.microbot.util;

import java.time.Instant;
import java.lang.reflect.Constructor;
import java.util.List;
import net.runelite.api.AnimationID;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Rs2Player/Rs2Inventory read semantics against the upstream methods, with
 * mocked RuneLite interfaces. Here the test thread is the client thread
 * (reads run inline); cross-thread hops are covered by NpcQueryTest. Services
 * are installed through the real MicrobotRuntime and EventBus.
 */
public class PlayerInventoryReadTest
{
	private Client client;
	private Player player;
	private MicrobotReadServices services;
	private final EventBus eventBus = new EventBus();

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		player = mock(Player.class);
		when(client.isClientThread()).thenReturn(true);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getAnimation()).thenReturn(AnimationID.IDLE);
		ClientThread clientThread = ReadApiFixture.clientThreadFor(client);
		services = new MicrobotReadServices(client, clientThread, eventBus);
		// Installed on the client thread while logged in, so Microbot.isLoggedIn() seeds at once.
		MicrobotRuntime.install(client, clientThread, eventBus, mock(ConfigManager.class), List.of(services));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	private ItemContainer inventory(Item... items)
	{
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		return container;
	}

	private void define(int id, String name, boolean stackable)
	{
		ItemComposition composition = mock(ItemComposition.class);
		when(composition.getName()).thenReturn(name);
		when(composition.isStackable()).thenReturn(stackable);
		when(composition.getNote()).thenReturn(-1);
		when(client.getItemDefinition(id)).thenReturn(composition);
	}

	private void inventoryChanged(ItemContainer container)
	{
		eventBus.post(new ItemContainerChanged(InventoryID.INV, container));
	}

	private static AnimationChanged animationChanged(Player actor)
	{
		AnimationChanged event = new AnimationChanged();
		event.setActor(actor);
		return event;
	}

	@Test
	public void countIsOccupiedSlotsNotSummedStackQuantity()
	{
		define(995, "Coins", true);
		define(1511, "Logs", false);
		inventoryChanged(inventory(
			new Item(995, 1000),
			new Item(-1, 0),
			new Item(1511, 1),
			new Item(1511, 1),
			new Item(-1, 0)));

		assertEquals(3, Rs2Inventory.count());
		assertEquals(2, Rs2Inventory.count(1511));
		assertEquals(1, Rs2Inventory.count("coin"));
		assertEquals(0, Rs2Inventory.count("coin", true));
		assertEquals(1000, Rs2Inventory.all().get(0).getQuantity());
		assertEquals(2, Rs2Inventory.all().get(1).getSlot());
	}

	@Test
	public void containsByNameIsExactByDefaultLikeUpstream()
	{
		define(1521, "Oak logs", false);
		define(590, "Tinderbox", false);
		inventoryChanged(inventory(new Item(1521, 1), new Item(590, 1)));

		assertFalse(Rs2Inventory.contains("Logs"));
		assertTrue(Rs2Inventory.contains("oak LOGS"));
		assertTrue(Rs2Inventory.contains("Logs", false));
		assertTrue(Rs2Inventory.contains(false, "logs", "rune"));
		assertTrue(Rs2Inventory.contains("Bones", "Tinderbox"));
		assertTrue(Rs2Inventory.contains(995, 590));
		assertFalse(Rs2Inventory.contains(995));
		// hasItem/get by name stay substring matches, as upstream.
		assertTrue(Rs2Inventory.hasItem("logs"));
	}

	@Test
	public void otherContainersAreIgnored()
	{
		define(995, "Coins", true);
		inventoryChanged(inventory(new Item(995, 5)));
		eventBus.post(new ItemContainerChanged(InventoryID.BANK, inventory(new Item(995, 9), new Item(995, 9))));
		assertEquals(1, Rs2Inventory.count());
	}

	@Test
	public void emptySnapshotFallsBackToTheLiveContainerWhenLoggedIn()
	{
		define(1511, "Logs", false);
		ItemContainer live = inventory(new Item(1511, 1), new Item(1511, 1));
		when(client.getItemContainer(InventoryID.INV)).thenReturn(live);
		assertEquals(2, Rs2Inventory.count());
	}

	@Test
	public void logoutClearsInventoryAndAnimationState()
	{
		define(1511, "Logs", false);
		inventoryChanged(inventory(new Item(1511, 1)));
		Rs2Player.lastAnimationTime = Instant.now();

		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.LOGIN_SCREEN);
		eventBus.post(event);

		assertFalse(Microbot.isLoggedIn());
		assertEquals(0, Rs2Inventory.count());
		assertNull(Rs2Player.lastAnimationTime);
		assertFalse(Rs2Player.isAnimating());
	}

	@Test
	public void regionLoadKeepsInventoryAndAnimationState()
	{
		define(1511, "Logs", false);
		inventoryChanged(inventory(new Item(1511, 1)));
		Instant animated = Instant.now();
		Rs2Player.lastAnimationTime = animated;

		GameStateChanged event = new GameStateChanged();
		event.setGameState(GameState.LOADING);
		eventBus.post(event);

		assertEquals(1, Rs2Inventory.count());
		assertSame(animated, Rs2Player.lastAnimationTime);
	}

	@Test
	public void isAnimatingUsesCurrentAnimationOrRecentLocalAnimationEvent()
	{
		assertFalse(Rs2Player.isAnimating());

		when(player.getAnimation()).thenReturn(879);
		assertTrue(Rs2Player.isAnimating());

		// Event for the local player records history for the 600 ms window.
		eventBus.post(animationChanged(player));
		when(player.getAnimation()).thenReturn(AnimationID.IDLE);
		assertTrue(Rs2Player.isAnimating());
		assertEquals(879, Rs2Player.getLastAnimationID());
		assertFalse(Rs2Player.isAnimating(0));

		Rs2Player.lastAnimationTime = Instant.now().minusMillis(601);
		assertFalse(Rs2Player.isAnimating());
	}

	@Test
	public void otherPlayersAnimationsAreIgnored()
	{
		Player other = mock(Player.class);
		when(other.getAnimation()).thenReturn(879);
		eventBus.post(animationChanged(other));
		assertNull(Rs2Player.lastAnimationTime);
		assertFalse(Rs2Player.isAnimating());
	}

	@Test
	public void isMovingComparesPoseWithIdlePose()
	{
		when(player.getIdlePoseAnimation()).thenReturn(808);
		when(player.getPoseAnimation()).thenReturn(808);
		assertFalse(Rs2Player.isMoving());

		when(player.getPoseAnimation()).thenReturn(819);
		assertTrue(Rs2Player.isMoving());

		when(client.getLocalPlayer()).thenReturn(null);
		assertFalse(Rs2Player.isMoving());
		assertEquals(-1, Rs2Player.getAnimation());
	}

	@Test
	public void runtimeUninstallStopsServicesAndReadsReportIdle()
	{
		define(1511, "Logs", false);
		inventoryChanged(inventory(new Item(1511, 1)));
		MicrobotRuntime.uninstall();

		// Unsubscribed: later events no longer reach the snapshot.
		inventoryChanged(inventory(new Item(1511, 1), new Item(1511, 1)));
		assertNull(MicrobotReadServices.current());
		assertFalse(Rs2Player.isMoving());
		assertEquals(-1, Rs2Player.getAnimation());
		assertEquals(0, Rs2Inventory.count());
		try
		{
			MicrobotReadServices.require();
			fail("require() must not invent a provider");
		}
		catch (IllegalStateException expected)
		{
			// no provider installed
		}
	}

	@Test
	public void aSecondProviderCannotStartWhileOneIsInstalled()
	{
		MicrobotReadServices second = new MicrobotReadServices(client, mock(ClientThread.class), new EventBus());
		try
		{
			second.startUp();
			fail("two providers must not be installed at once");
		}
		catch (IllegalStateException expected)
		{
			assertSame(services, MicrobotReadServices.require());
		}
	}

	@Test
	public void cacheQueryablesCannotBeConstructedOutsideTheirCache() throws Exception
	{
		for (Class<?> type : new Class<?>[]{
			net.runelite.client.plugins.microbot.api.npc.Rs2NpcQueryable.class,
			net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable.class,
			net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemQueryable.class})
		{
			for (Constructor<?> constructor : type.getDeclaredConstructors())
			{
				assertFalse(type + " exposes a public constructor", java.lang.reflect.Modifier.isPublic(constructor.getModifiers()));
			}
		}
	}
}
