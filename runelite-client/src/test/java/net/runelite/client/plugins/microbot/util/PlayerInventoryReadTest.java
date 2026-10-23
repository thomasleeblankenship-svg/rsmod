package net.runelite.client.plugins.microbot.util;

import java.time.Instant;
import java.lang.reflect.Constructor;
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
import net.runelite.client.plugins.microbot.api.ClientThreadCalls;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
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
 * (reads run inline); cross-thread hops are covered by NpcQueryTest.
 */
public class PlayerInventoryReadTest
{
	private Client client;
	private Player player;
	private MicrobotReadServices services;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		player = mock(Player.class);
		when(client.isClientThread()).thenReturn(true);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getAnimation()).thenReturn(AnimationID.IDLE);
		services = new MicrobotReadServices(client, new ClientThreadCalls(client, mock(ClientThread.class)));
		MicrobotReadServices.install(services);
	}

	@After
	public void tearDown()
	{
		MicrobotReadServices.uninstall(services);
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
		services.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, container));
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
	public void otherContainersAreIgnored()
	{
		define(995, "Coins", true);
		inventoryChanged(inventory(new Item(995, 5)));
		services.onItemContainerChanged(new ItemContainerChanged(InventoryID.BANK, inventory(new Item(995, 9), new Item(995, 9))));
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
		services.onGameStateChanged(event);

		assertFalse(services.isLoggedIn());
		assertEquals(0, Rs2Inventory.count());
		assertNull(Rs2Player.lastAnimationTime);
		assertFalse(Rs2Player.isAnimating());
	}

	@Test
	public void isAnimatingUsesCurrentAnimationOrRecentLocalAnimationEvent()
	{
		assertFalse(Rs2Player.isAnimating());

		when(player.getAnimation()).thenReturn(879);
		assertTrue(Rs2Player.isAnimating());

		// Event for the local player records history for the 600 ms window.
		services.onAnimationChanged(animationChanged(player));
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
		services.onAnimationChanged(animationChanged(other));
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
	public void withoutInstalledServicesReadsReportIdleAndRequireFails()
	{
		MicrobotReadServices.uninstall(services);
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
	public void uninstallOfAStaleProviderKeepsTheCurrentOne()
	{
		MicrobotReadServices replacement = new MicrobotReadServices(client, new ClientThreadCalls(client, mock(ClientThread.class)));
		MicrobotReadServices.install(replacement);
		MicrobotReadServices.uninstall(services);
		assertSame(replacement, MicrobotReadServices.require());
		assertSame(replacement.getRs2NpcCache(), MicrobotReadServices.require().getRs2NpcCache());
		services = replacement;
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

	@Test
	public void clientThreadCallRunsInlineOnTheClientThread()
	{
		ClientThread thread = mock(ClientThread.class);
		ClientThreadCalls calls = new ClientThreadCalls(client, thread);
		assertEquals("ok", calls.call(() -> "ok").orElse(null));
		verify(thread, never()).invoke(any(Runnable.class));
	}
}
