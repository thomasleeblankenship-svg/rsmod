package net.runelite.client.plugins.microbot.util.bank;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.VarClientInt;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InterfaceID;
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
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Upstream bank action overloads: chosen menu option, typed amounts and observed transfers. */
public class Rs2BankActionTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final int COINS = 995;
	private static final int LOBSTER = 379;

	private Client client;
	private final AtomicBoolean clientThread = new AtomicBoolean(true);
	private final EventBus events = new EventBus();
	private final StringBuilder typed = new StringBuilder();
	private volatile int prompt;
	private Widget bankSlot;

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
		when(client.getWidget(12, 1)).thenReturn(mock(Widget.class));
		define(COINS, "Coins");
		define(LOBSTER, "Lobster");
		ItemContainer bank = mock(ItemContainer.class);
		when(bank.getItems()).thenReturn(new Item[]{new Item(COINS, 10_000), new Item(LOBSTER, 100)});
		when(client.getItemContainer(net.runelite.api.InventoryID.BANK)).thenReturn(bank);
		Widget items = mock(Widget.class);
		bankSlot = mock(Widget.class);
		when(bankSlot.getActions()).thenReturn(new String[]{"Withdraw-1", "Withdraw-5", "Withdraw-10", "Withdraw-X", "Withdraw-All"});
		when(items.getChild(anyInt())).thenReturn(bankSlot);
		when(client.getWidget(InterfaceID.Bankmain.ITEMS)).thenReturn(items);
		when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenAnswer(call -> prompt);
		inventory();
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
		boolean was = clientThread.get();
		clientThread.set(true);
		try { events.post(new ItemContainerChanged(InventoryID.INV, container)); }
		finally { clientThread.set(was); }
	}

	@After
	public void tearDown() { MicrobotRuntime.uninstall(); }

	@Test
	public void standardAmountsUseTheirOwnMenuOption()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(MobileActions::active).thenReturn(true);
			actions.when(() -> MobileActions.bank(1, LOBSTER, "Withdraw-5")).thenAnswer(call ->
			{
				inventory(new Item(LOBSTER, 1), new Item(LOBSTER, 1), new Item(LOBSTER, 1), new Item(LOBSTER, 1), new Item(LOBSTER, 1));
				return true;
			});
			assertTrue(Rs2Bank.withdrawX(LOBSTER, 5));
			actions.verify(() -> MobileActions.bank(anyInt(), anyInt(), eq("Withdraw-X")), never());
		}
	}

	@Test
	public void theRememberedAmountOptionIsUsedWithoutTyping()
	{
		when(bankSlot.getActions()).thenReturn(new String[]{"Withdraw-1", "Withdraw-5", "Withdraw-10", "Withdraw-2500", "Withdraw-X"});
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(MobileActions::active).thenReturn(true);
			actions.when(() -> MobileActions.bank(0, COINS, "Withdraw-2500")).thenAnswer(call ->
			{
				inventory(new Item(COINS, 2500));
				return true;
			});
			assertTrue(Rs2Bank.withdrawX("Coins", 2500));
			actions.verify(() -> MobileActions.type(anyChar()), never());
		}
	}

	@Test
	public void otherAmountsTypeTheAmountIntoThePrompt()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(MobileActions::active).thenReturn(true);
			actions.when(() -> MobileActions.bank(0, COINS, "Withdraw-X")).thenAnswer(call ->
			{
				prompt = 7;
				return true;
			});
			actions.when(() -> MobileActions.type(anyChar())).thenAnswer(call ->
			{
				typed.append((char) call.getArgument(0));
				return true;
			});
			actions.when(() -> MobileActions.key(KeyEvent.VK_ENTER)).thenAnswer(call ->
			{
				prompt = 0;
				inventory(new Item(COINS, Integer.parseInt(typed.toString())));
				return true;
			});
			assertTrue(Rs2Bank.withdrawX(COINS, 1234));
			assertEquals("1234", typed.toString());
			assertEquals(1234, Rs2Inventory.itemQuantity(COINS));
		}
	}

	@Test
	public void deficitAndCheckInventoryVariantsDoNothingWhenAlreadySatisfied()
	{
		inventory(new Item(COINS, 500));
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(MobileActions::active).thenReturn(true);
			assertTrue(Rs2Bank.withdrawDeficit(COINS, 500));
			assertTrue(Rs2Bank.withdrawX(true, COINS, 400));
			actions.verify(() -> MobileActions.bank(anyInt(), anyInt(), anyString()), never());
		}
	}

	@Test
	public void depositAllUsesTheDepositInventoryButtonAndWaitsForAnEmptyInventory()
	{
		inventory(new Item(LOBSTER, 1), new Item(COINS, 3));
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(MobileActions::active).thenReturn(true);
			actions.when(() -> MobileActions.widget(InterfaceID.Bankmain.DEPOSITINV, -1, "")).thenAnswer(call ->
			{
				inventory();
				return true;
			});
			assertTrue(Rs2Bank.depositAll());
			assertTrue(Rs2Inventory.isEmpty());
		}
	}

	@Test
	public void depositAllExceptKeepsTheNamedItems()
	{
		inventory(new Item(LOBSTER, 1), new Item(COINS, 3));
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(MobileActions::active).thenReturn(true);
			actions.when(() -> MobileActions.inventory(0, LOBSTER, "Deposit-All")).thenAnswer(call ->
			{
				inventory(new Item(-1, 0), new Item(COINS, 3));
				return true;
			});
			assertTrue(Rs2Bank.depositAllExcept("Coins"));
			assertEquals(3, Rs2Inventory.itemQuantity(COINS));
			actions.verify(() -> MobileActions.inventory(anyInt(), eq(COINS), anyString()), never());
		}
	}

	@Test
	public void nothingIsDispatchedWhenTheItemIsNotInTheBank()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(MobileActions::active).thenReturn(true);
			assertFalse(Rs2Bank.withdrawOne("Shark"));
			assertFalse(Rs2Bank.withdrawAll(9999));
			actions.verify(() -> MobileActions.bank(anyInt(), anyInt(), anyString()), never());
		}
	}

	@Test
	public void depositItemsDepositsOneOfEachAndAFishBarrelIsEmptied()
	{
		inventory(new Item(LOBSTER, 1), new Item(COINS, 3), new Item(net.runelite.api.gameval.ItemID.FISH_BARREL_OPEN, 1));
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(MobileActions::active).thenReturn(true);
			actions.when(() -> MobileActions.inventory(0, LOBSTER, "Deposit-1")).thenAnswer(call ->
			{
				inventory(new Item(-1, 0), new Item(COINS, 3), new Item(net.runelite.api.gameval.ItemID.FISH_BARREL_OPEN, 1));
				return true;
			});
			actions.when(() -> MobileActions.inventory(eq(2), eq(net.runelite.api.gameval.ItemID.FISH_BARREL_OPEN), eq("Empty"))).thenReturn(true);
			assertTrue(Rs2Bank.depositItems(LOBSTER));
			assertTrue(Rs2Bank.emptyFishBarrel());
			assertFalse("nothing to deposit", Rs2Bank.depositItems(9999));
		}
	}
}
