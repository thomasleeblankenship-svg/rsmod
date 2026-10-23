package net.runelite.client.plugins.microbot.pouch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Essence pouch tracking and the pouch reads, through the real read services and event bus. */
public class PouchTrackerTest
{
	static
	{
		// JavaBridge captures this flag on first load; set it before any test reaches the bridge.
		System.setProperty("runelite.bridge", "true");
	}

	private static final int ESSENCE = ItemID.BLANKRUNE_HIGH;

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final EventBus events = new EventBus();
	private Client client;
	private int tick = 100;
	private int runecraft = 99;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getTickCount()).thenAnswer(call -> tick);
		when(client.getRealSkillLevel(Skill.RUNECRAFT)).thenAnswer(call -> runecraft);
		when(client.getBoostedSkillLevel(Skill.RUNECRAFT)).thenAnswer(call -> runecraft);
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn("Item");
		when(definition.getInventoryActions()).thenReturn(new String[5]);
		when(client.getItemDefinition(anyInt())).thenReturn(definition);
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
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
		Pouch.resetAll();
	}

	private void post(Object event)
	{
		onClientThread.set(true);
		try { events.post(event); }
		finally { onClientThread.set(false); }
	}

	/** Inventory with the given pouch ids followed by {@code essence} essence, padded with empty slots. */
	private void inventory(int essence, int... pouches)
	{
		List<Item> items = new ArrayList<>();
		Arrays.stream(pouches).forEach(id -> items.add(new Item(id, 1)));
		for (int i = 0; i < essence; i++)
		{
			items.add(new Item(ESSENCE, 1));
		}
		while (items.size() < 28)
		{
			items.add(new Item(-1, 0));
		}
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items.toArray(new Item[0]));
		when(client.getItemContainer(InventoryID.INV)).thenReturn(container);
		post(new ItemContainerChanged(InventoryID.INV, container));
	}

	private void click(int pouchId, String option)
	{
		MenuEntry entry = mock(MenuEntry.class);
		when(entry.getItemId()).thenReturn(pouchId);
		when(entry.getOption()).thenReturn(option);
		post(new MenuOptionClicked(entry));
	}

	private void message(String text)
	{
		post(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", text, "", 0));
	}

	@Test
	public void countWordsParse()
	{
		assertEquals(0, PouchTracker.parseCount("no"));
		assertEquals(7, PouchTracker.parseCount("seven"));
		assertEquals(19, PouchTracker.parseCount("nineteen"));
		assertEquals(20, PouchTracker.parseCount("twenty"));
		assertEquals(27, PouchTracker.parseCount("twenty-seven"));
		assertEquals(40, PouchTracker.parseCount("forty"));
		assertEquals(-1, PouchTracker.parseCount("many"));
		assertEquals(-1, PouchTracker.parseCount("twenty-ten"));
	}

	@Test
	public void itemsMapToPouchesAndColossalCapacityFollowsLevel()
	{
		assertSame(Pouch.GIANT, Pouch.forItem(ItemID.RCU_POUCH_GIANT_DEGRADE));
		assertNull(Pouch.forItem(ESSENCE));
		assertEquals(8, Pouch.COLOSSAL.capacityFor(25));
		assertEquals(16, Pouch.COLOSSAL.capacityFor(50));
		assertEquals(27, Pouch.COLOSSAL.capacityFor(84));
		assertEquals(40, Pouch.COLOSSAL.capacityFor(85));
	}

	@Test
	public void fillingMovesObservedEssenceIntoTheClickedPouches()
	{
		inventory(26, ItemID.RCU_POUCH_SMALL, ItemID.RCU_POUCH_GIANT);
		assertTrue(Rs2Inventory.anyPouchUnknown());
		assertEquals(15, Rs2Inventory.getRemainingCapacityInPouches());

		click(ItemID.RCU_POUCH_SMALL, "Fill");
		click(ItemID.RCU_POUCH_GIANT, "Fill");
		inventory(11, ItemID.RCU_POUCH_SMALL, ItemID.RCU_POUCH_GIANT);

		assertEquals(3, Pouch.SMALL.getHolding());
		assertEquals(12, Pouch.GIANT.getHolding());
		assertFalse("26 essence covered both pouches", Rs2Inventory.anyPouchUnknown());
		assertTrue(Rs2Inventory.allPouchesFull());
		assertFalse(Rs2Inventory.anyPouchEmpty());
		assertEquals(0, Rs2Inventory.getRemainingCapacityInPouches());

		click(ItemID.RCU_POUCH_GIANT, "Empty");
		inventory(23, ItemID.RCU_POUCH_SMALL, ItemID.RCU_POUCH_GIANT);
		assertEquals(0, Pouch.GIANT.getHolding());
		assertTrue(Rs2Inventory.anyPouchFull());
		assertFalse(Rs2Inventory.allPouchesEmpty());
	}

	@Test
	public void checkAndResultMessagesPinContents()
	{
		inventory(0, ItemID.RCU_POUCH_LARGE);
		click(ItemID.RCU_POUCH_LARGE, "Check");
		message("There are six pure essences in this pouch.");
		assertEquals(6, Pouch.LARGE.getHolding());
		assertFalse(Pouch.LARGE.isUnknown());

		click(ItemID.RCU_POUCH_LARGE, "Fill");
		message("You cannot add any more essence to the pouch.");
		assertEquals(9, Pouch.LARGE.getHolding());

		click(ItemID.RCU_POUCH_LARGE, "Empty");
		message("There is no essence in this pouch.");
		assertEquals(0, Pouch.LARGE.getHolding());
	}

	@Test
	public void staleClicksAreIgnored()
	{
		inventory(0, ItemID.RCU_POUCH_LARGE);
		click(ItemID.RCU_POUCH_LARGE, "Check");
		tick += 4;
		message("There is one pure essence in this pouch.");
		assertTrue(Pouch.LARGE.isUnknown());
		assertEquals(0, Pouch.LARGE.getHolding());
	}

	@Test
	public void degradedPouchesShrinkAndLevelGatesUse()
	{
		inventory(0, ItemID.RCU_POUCH_GIANT_DEGRADE, ItemID.RCU_POUCH_SMALL);
		assertTrue(Rs2Inventory.hasDegradedPouch());
		assertEquals(9, Pouch.GIANT.getHoldAmount());

		runecraft = 50;
		assertEquals("the giant pouch needs 75 Runecraft", 3, Rs2Inventory.getRemainingCapacityInPouches());
	}

	@Test
	public void logoutForgetsContents()
	{
		inventory(0, ItemID.RCU_POUCH_LARGE);
		click(ItemID.RCU_POUCH_LARGE, "Check");
		message("There are six pure essences in this pouch.");

		GameStateChanged logout = new GameStateChanged();
		logout.setGameState(GameState.LOGIN_SCREEN);
		post(logout);

		assertTrue(Pouch.LARGE.isUnknown());
		assertEquals(0, Pouch.LARGE.getHolding());
	}

	@Test
	public void noPouchesMeansNothingToReport()
	{
		inventory(5);
		assertFalse(Rs2Inventory.hasAnyPouch());
		assertFalse(Rs2Inventory.anyPouchFull());
		assertEquals(0, Rs2Inventory.getRemainingCapacityInPouches());
	}
}
