package net.runelite.client.plugins.microbot.util.inventory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Hub item (2) inventory reads over the real inventory snapshot and read services. */
public class Rs2InventoryHubReadTest
{
	static
	{
		// JavaBridge captures this flag on first load; set it before any test reaches the bridge.
		System.setProperty("runelite.bridge", "true");
	}

	private static final int SHARK = 385;
	private static final int ROCK_CAKE = 2379;
	private static final int WINE = 1993;
	private static final int NOTED_SHARK = 386;
	private static final int BONES = 526;
	private static final int COINS = 995;

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
		define(SHARK, "Shark", false, false, "Eat");
		define(ROCK_CAKE, "Dwarven rock cake", false, false, "Eat");
		define(WINE, "Jug of wine", false, false, "Drink");
		define(NOTED_SHARK, "Shark", true, true);
		define(BONES, "Bones", false, false, "Bury");
		define(COINS, "Coins", true, false);
		define(ItemID.DIVINE_RUNE_POUCH, "Divine rune pouch", false, false, "Open");
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	private void define(int id, String name, boolean stackable, boolean noted, String... actions)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn(name);
		when(definition.isStackable()).thenReturn(stackable);
		when(definition.getNote()).thenReturn(noted ? 799 : -1);
		String[] menu = new String[5];
		System.arraycopy(actions, 0, menu, 0, actions.length);
		when(definition.getInventoryActions()).thenReturn(menu);
		when(client.getItemDefinition(id)).thenReturn(definition);
	}

	private void inventory(Item... items)
	{
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		when(client.getItemContainer(InventoryID.INV)).thenReturn(container);
		onClientThread.set(true);
		try { events.post(new ItemContainerChanged(InventoryID.INV, container)); }
		finally { onClientThread.set(false); }
	}

	@Test
	public void quantitiesAndAmountsDistinguishStacksFromSlots()
	{
		inventory(new Item(COINS, 1500), new Item(SHARK, 1), new Item(SHARK, 1), new Item(NOTED_SHARK, 40));

		assertEquals(1500, Rs2Inventory.itemQuantity(COINS));
		assertEquals(42, Rs2Inventory.itemQuantity("shark"));
		assertEquals(0, Rs2Inventory.itemQuantity("shar"));
		assertEquals(42, Rs2Inventory.itemQuantity("shar", false));
		assertTrue(Rs2Inventory.hasItemAmount(COINS, 1000));
		assertFalse(Rs2Inventory.hasItemAmount(COINS, 1000, true));
		assertTrue(Rs2Inventory.hasItemAmount(COINS, 1500, true));
		assertTrue("stackable: compares stack size", Rs2Inventory.hasItemAmount("Coins", 1200));
		assertTrue("unstacked: compares slots", Rs2Inventory.hasItemAmount("Shark", 3, false));
		assertFalse(Rs2Inventory.hasItemAmount("Shark", 4, false));
		assertFalse("nothing named so", Rs2Inventory.hasItemAmount("Lobster", 1));
	}

	@Test
	public void hasItemOverloadsMatchPartiallyByDefault()
	{
		inventory(new Item(SHARK, 1), new Item(BONES, 1));

		assertTrue(Rs2Inventory.hasItem(999, BONES));
		assertFalse(Rs2Inventory.hasItem(999, 998));
		assertTrue(Rs2Inventory.hasItem("lobster", "shar"));
		assertFalse(Rs2Inventory.hasItem(new String[]{"shar"}, true));
		assertTrue(Rs2Inventory.hasItem(new String[]{"BONES"}, true));
		assertTrue(Rs2Inventory.hasItem("bon", false));
		assertFalse(Rs2Inventory.hasItem("bon", true));
	}

	@Test
	public void emptinessAndOnlyContains()
	{
		inventory();
		assertTrue(Rs2Inventory.isEmpty());
		assertTrue("an empty inventory only contains anything", Rs2Inventory.onlyContains(SHARK));

		inventory(new Item(SHARK, 1), new Item(BONES, 1));
		assertFalse(Rs2Inventory.isEmpty());
		assertTrue(Rs2Inventory.onlyContains(SHARK, BONES));
		assertFalse(Rs2Inventory.onlyContains(SHARK));
		assertTrue(Rs2Inventory.onlyContains("shark", "BONES"));
		assertFalse(Rs2Inventory.onlyContains("shar", "bones"));
	}

	@Test
	public void foodExcludesRockCakesAndNotesAndIncludesWine()
	{
		inventory(new Item(SHARK, 1), new Item(ROCK_CAKE, 1), new Item(WINE, 1), new Item(NOTED_SHARK, 10), new Item(BONES, 1));

		List<Integer> food = Rs2Inventory.getInventoryFood().stream().map(Rs2ItemModel::getId).collect(Collectors.toList());
		assertEquals(List.of(SHARK, WINE), food);
		assertEquals(List.of(BONES), Rs2Inventory.getBones().stream().map(Rs2ItemModel::getId).collect(Collectors.toList()));
	}

	@Test
	public void runePouchIsDetectedByAnyPouchId()
	{
		inventory(new Item(SHARK, 1));
		assertFalse(Rs2Inventory.hasRunePouch());

		inventory(new Item(ItemID.DIVINE_RUNE_POUCH, 1));
		assertTrue(Rs2Inventory.hasRunePouch());
	}

	@Test
	public void inventoryWidgetIsTheFirstVisibleItemContainer()
	{
		assertNull(Rs2Inventory.getInventory());

		Widget side = mock(Widget.class);
		when(side.isHidden()).thenReturn(true);
		when(side.getDynamicChildren()).thenReturn(new Widget[0]);
		when(client.getWidget(ComponentID.INVENTORY_CONTAINER)).thenReturn(side);
		Widget shop = mock(Widget.class);
		when(shop.getDynamicChildren()).thenReturn(new Widget[0]);
		when(client.getWidget(InterfaceID.Shopside.ITEMS)).thenReturn(shop);

		assertSame("hidden side panel is skipped", shop, Rs2Inventory.getInventory());
	}
}
