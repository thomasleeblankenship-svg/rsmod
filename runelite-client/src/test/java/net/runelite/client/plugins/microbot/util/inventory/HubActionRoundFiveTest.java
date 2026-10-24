package net.runelite.client.plugins.microbot.util.inventory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Round-5 Hub actions: item on NPC from the inventory API, and child-widget clicks. */
public class HubActionRoundFiveTest
{
	static
	{
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
		ItemComposition grapes = mock(ItemComposition.class);
		when(grapes.getName()).thenReturn("Zamorak grapes");
		when(grapes.getNote()).thenReturn(-1);
		when(client.getItemDefinition(6749)).thenReturn(grapes);
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(new Item[]{new Item(995, 10), new Item(6749, 1)});
		when(client.getItemContainer(InventoryID.INV)).thenReturn(container);
		events.post(new ItemContainerChanged(InventoryID.INV, container));
		clientThread.set(false);
	}

	@After
	public void tearDown() { MicrobotRuntime.uninstall(); }

	@Test
	public void useItemOnNpcUsesTheItemsSlotOnTheNpcsIndex()
	{
		NPC leprechaun = mock(NPC.class);
		when(leprechaun.getIndex()).thenReturn(42);
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class, CALLS_REAL_METHODS))
		{
			bank.when(Rs2Bank::isOpen).thenReturn(false);
			actions.when(() -> MobileActions.itemOnNpc(anyInt(), anyInt())).thenReturn(true);
			actions.clearInvocations();
			assertTrue(Rs2Inventory.useItemOnNpc(6749, leprechaun));
			actions.verify(() -> MobileActions.itemOnNpc(1, 42));
			assertFalse("no such item", Rs2Inventory.useItemOnNpc(1925, leprechaun));
			assertFalse(Rs2Inventory.useItemOnNpc(6749, (NPC) null));

			bank.when(Rs2Bank::isOpen).thenReturn(true);
			actions.clearInvocations();
			assertFalse("as upstream, not while the bank is open", Rs2Inventory.useItemOnNpc(6749, leprechaun));
			actions.verify(() -> MobileActions.itemOnNpc(anyInt(), anyInt()), never());
		}
	}

	@Test
	public void clickChildWidgetDispatchesThatChildsFirstOption()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.widget(anyInt(), anyInt(), anyString())).thenReturn(true);
			actions.clearInvocations();
			assertTrue(Rs2Widget.clickChildWidget(3407891, 7));
			actions.verify(() -> MobileActions.widget(3407891, 7, ""));
		}
	}
}
