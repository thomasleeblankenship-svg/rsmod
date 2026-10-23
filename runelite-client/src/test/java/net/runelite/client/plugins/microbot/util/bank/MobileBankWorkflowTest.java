package net.runelite.client.plugins.microbot.util.bank;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Confirmed bank transfers against real read services and event bus, with mock action boundaries. */
public class MobileBankWorkflowTest
{
	static
	{
		// JavaBridge captures this flag on first load; set it before any test reaches the bridge.
		System.setProperty("runelite.bridge", "true");
	}

	private Client client;
	private final AtomicBoolean clientThread = new AtomicBoolean(true);
	private final EventBus events = new EventBus();

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> clientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			clientThread.set(true);
			try { ((Runnable) call.getArgument(0)).run(); }
			finally { clientThread.set(false); }
			return null;
		}).when(thread).invoke(any(Runnable.class));
		MicrobotReadServices services = new MicrobotReadServices(client, thread, events);
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class), List.of(services));
		Widget bank = mock(Widget.class);
		when(client.getWidget(12, 1)).thenReturn(bank);
		define(1511, "Logs");
		define(1351, "Bronze axe");
		inventory(new Item(1511, 1), new Item(1511, 1), new Item(1351, 1));
		clientThread.set(false);
	}

	private void define(int id, String name)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn(name);
		when(client.getItemDefinition(id)).thenReturn(definition);
	}

	private void inventory(Item... items)
	{
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		when(client.getItemContainer(InventoryID.INV)).thenReturn(container);
		clientThread.set(true);
		try { events.post(new ItemContainerChanged(InventoryID.INV, container)); }
		finally { clientThread.set(false); }
	}

	@After
	public void tearDown() { MicrobotRuntime.uninstall(); }

	@Test
	public void depositAllDoesNotRetryStaleDuplicateLogSlotsAndKeepsAxe()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(MobileActions::active).thenReturn(true);
			actions.when(() -> MobileActions.inventory(0, 1511, "Deposit-All")).thenAnswer(call ->
			{
				inventory(new Item(-1, 0), new Item(-1, 0), new Item(1351, 1));
				return true;
			});
			actions.clearInvocations();
			assertTrue(Rs2Bank.depositAll(item -> item.getName().equals("Logs")));
			assertEquals(0, Rs2Inventory.count(1511));
			assertEquals(1, Rs2Inventory.count(1351));
			actions.verify(() -> MobileActions.inventory(0, 1511, "Deposit-All"), times(1));
			actions.verify(() -> MobileActions.inventory(1, 1511, "Deposit-All"), never());
		}
	}

	@Test
	public void cancelledBatchDoesNotDispatchDeposit()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(MobileActions::active).thenReturn(false);
			assertFalse(Rs2Bank.depositAll(item -> item.getName().equals("Logs")));
			assertEquals(2, Rs2Inventory.count(1511));
			actions.verify(() -> MobileActions.inventory(0, 1511, "Deposit-All"), never());
		}
	}
}
