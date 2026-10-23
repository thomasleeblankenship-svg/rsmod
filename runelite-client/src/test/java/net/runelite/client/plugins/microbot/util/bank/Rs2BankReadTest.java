package net.runelite.client.plugins.microbot.util.bank;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Hub item (5): bank reads answer from the bank contents last seen, through the real event routing. */
public class Rs2BankReadTest
{
	static
	{
		// JavaBridge captures this flag on first load; set it before any test reaches the bridge.
		System.setProperty("runelite.bridge", "true");
	}

	private static final int LOGS = 1511;
	private static final int OAK = 1521;
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
		define(LOGS, "Logs");
		define(OAK, "Oak logs");
		define(COINS, "Coins");
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
		when(client.getItemDefinition(id)).thenReturn(definition);
	}

	private void post(Object event)
	{
		onClientThread.set(true);
		try { events.post(event); }
		finally { onClientThread.set(false); }
	}

	private void bankSeen(Item... items)
	{
		ItemContainer bank = mock(ItemContainer.class);
		when(bank.getItems()).thenReturn(items);
		post(new ItemContainerChanged(InventoryID.BANK.getId(), bank));
	}

	@Test
	public void nothingIsKnownBeforeTheBankIsSeen()
	{
		assertTrue(Rs2Bank.bankItems().isEmpty());
		assertFalse(Rs2Bank.hasItem(LOGS));
		assertNull(Rs2Bank.getBankItem("Logs"));
		assertTrue("a zero amount is always satisfied", Rs2Bank.hasBankItem("Logs", 0));
	}

	@Test
	public void namesMatchPartiallyByDefaultAndExactlyOnRequest()
	{
		bankSeen(new Item(OAK, 50), new Item(-1, 0), new Item(COINS, 10_000));

		assertEquals(2, Rs2Bank.bankItems().size());
		assertTrue(Rs2Bank.hasItem("logs"));
		assertFalse(Rs2Bank.hasItem("logs", true));
		assertTrue(Rs2Bank.hasItem("OAK LOGS", true));
		assertTrue(Rs2Bank.hasBankItem("oak", 50));
		assertFalse(Rs2Bank.hasBankItem("oak", 51));
		assertFalse(Rs2Bank.hasBankItem("Logs", 1, true));
		assertEquals(OAK, Rs2Bank.getBankItem("logs").getId());
		assertNull(Rs2Bank.getBankItem("Logs", true));
		assertEquals(10_000, Rs2Bank.getBankItem(COINS).getQuantity());
	}

	@Test
	public void idAndCollectionOverloads()
	{
		bankSeen(new Item(LOGS, 3), new Item(COINS, 500));

		assertTrue(Rs2Bank.hasItem(new int[]{OAK, LOGS}));
		assertFalse(Rs2Bank.hasAllItems(new int[]{OAK, LOGS}));
		assertTrue(Rs2Bank.hasAllItems(new int[]{COINS, LOGS}, 3));
		assertFalse(Rs2Bank.hasAllItems(new int[]{COINS, LOGS}, 4));
		assertTrue(Rs2Bank.hasItem(new int[]{COINS}, 500));
		assertTrue(Rs2Bank.hasBankItem(COINS, 500));
		assertFalse(Rs2Bank.hasBankItem(COINS, 501));

		assertTrue(Rs2Bank.hasItem(List.of("Oak", "Coin")));
		assertFalse(Rs2Bank.hasItem(List.of("Oak", "Coin"), true));
		assertTrue(Rs2Bank.hasItem(List.of("coins"), 400));
		assertTrue(Rs2Bank.hasAllItems(List.of("Logs", "Coins"), true, 3));
		assertFalse(Rs2Bank.hasAllItems(List.of("Logs", "Coins"), true, 10));
	}

	@Test
	public void memoryOutlivesClosingButNotLogout()
	{
		bankSeen(new Item(LOGS, 3));
		// Closing the bank sends no bank-container change; the memory stays.
		assertTrue(Rs2Bank.hasItem(LOGS));

		GameStateChanged logout = new GameStateChanged();
		logout.setGameState(GameState.LOGIN_SCREEN);
		post(logout);

		assertFalse(Rs2Bank.hasItem(LOGS));
	}

	@Test
	public void otherContainersDoNotReplaceTheBank()
	{
		bankSeen(new Item(LOGS, 3));
		ItemContainer inventory = mock(ItemContainer.class);
		when(inventory.getItems()).thenReturn(new Item[]{new Item(COINS, 1)});
		post(new ItemContainerChanged(InventoryID.INVENTORY.getId(), inventory));

		assertTrue(Rs2Bank.hasItem(LOGS));
		assertFalse(Rs2Bank.hasItem(COINS));
	}
}
