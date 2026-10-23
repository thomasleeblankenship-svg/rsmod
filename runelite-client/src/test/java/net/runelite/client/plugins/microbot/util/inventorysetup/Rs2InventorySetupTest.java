package net.runelite.client.plugins.microbot.util.inventorysetup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsStackCompareID;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Mobile inventory setups: matching by item totals and loading through observed bank actions. */
public class Rs2InventorySetupTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final int LOBSTER = 379;
	private static final int COINS = 995;
	private static final int SWORD = 1277;

	private Client client;
	private final AtomicBoolean clientThread = new AtomicBoolean(true);
	private final EventBus events = new EventBus();
	private final AtomicReference<Item[]> worn = new AtomicReference<>(new Item[0]);
	private ScriptInputSession session;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> clientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			boolean was = clientThread.get();
			clientThread.set(true);
			try { ((Runnable) call.getArgument(0)).run(); }
			finally { clientThread.set(was); }
			return null;
		}).when(thread).invoke(any(Runnable.class));
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
		define(LOBSTER, "Lobster");
		define(COINS, "Coins");
		define(SWORD, "Bronze sword");
		ItemContainer equipment = mock(ItemContainer.class);
		when(equipment.getItems()).thenAnswer(call -> worn.get());
		when(client.getItemContainer(net.runelite.api.InventoryID.EQUIPMENT)).thenReturn(equipment);
		when(client.getItemContainer(InventoryID.WORN)).thenReturn(equipment);
		clientThread.set(false);
		session = new ScriptInputSession();
		MobileActions.bind(session);
	}

	@After
	public void tearDown()
	{
		MobileActions.unbind(session);
		MicrobotRuntime.uninstall();
	}

	private void define(int id, String name)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn(name);
		when(definition.getNote()).thenReturn(-1);
		when(definition.getInventoryActions()).thenReturn(new String[]{null, "Wield", null, null, "Drop"});
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

	private static InventorySetupsItem row(int id, String name, int quantity, int slot)
	{
		return new InventorySetupsItem(id, name, quantity, false, InventorySetupsStackCompareID.Standard, false, slot);
	}

	private static InventorySetup setup(List<InventorySetupsItem> inventory, List<InventorySetupsItem> equipment)
	{
		return new InventorySetup(new ArrayList<>(inventory), new ArrayList<>(equipment), null, null, null, new HashMap<>(),
			"test", "", null, false, null, false, false, 4, false, -1);
	}

	private final InventorySetup trip = setup(
		List.of(row(LOBSTER, "Lobster", 1, 0), row(LOBSTER, "Lobster", 1, 1), row(LOBSTER, "Lobster", 1, 2),
			row(COINS, "Coins", 1000, 3), InventorySetupsItem.getDummyItem()),
		List.of(row(SWORD, "Bronze sword", 1, 3)));

	@Test
	public void inventoryMatchesByTotalsWhateverTheSlotOrder()
	{
		Rs2InventorySetup loader = new Rs2InventorySetup(trip, null);
		inventory(new Item(COINS, 1500), new Item(LOBSTER, 1), new Item(LOBSTER, 1));
		assertFalse("one lobster short", loader.doesInventoryMatch());
		inventory(new Item(LOBSTER, 1), new Item(COINS, 1000), new Item(LOBSTER, 1), new Item(LOBSTER, 1));
		assertTrue(loader.doesInventoryMatch());
		assertTrue("spellbook 4 means any", loader.hasSpellBook());
	}

	@Test
	public void loadInventoryDepositsExtrasAndWithdrawsOnlyWhatIsMissing()
	{
		inventory(new Item(LOBSTER, 1), new Item(COINS, 1000));
		try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class, CALLS_REAL_METHODS))
		{
			bank.when(Rs2Bank::isOpen).thenReturn(true);
			bank.when(() -> Rs2Bank.depositAll(any(java.util.function.Predicate.class))).thenReturn(true);
			bank.when(Rs2Bank::setWithdrawAsItem).thenReturn(true);
			bank.when(() -> Rs2Bank.count(anyInt())).thenReturn(50);
			bank.when(() -> Rs2Bank.withdrawX(LOBSTER, 2)).thenAnswer(call ->
			{
				inventory(new Item(LOBSTER, 1), new Item(COINS, 1000), new Item(LOBSTER, 1), new Item(LOBSTER, 1));
				return true;
			});
			bank.clearInvocations();
			assertTrue(new Rs2InventorySetup(trip, null).loadInventory());
			bank.verify(() -> Rs2Bank.withdrawX(LOBSTER, 2));
			bank.verify(() -> Rs2Bank.withdrawX(eq(COINS), anyInt()), never());
		}
	}

	@Test
	public void aMissingSupplyIsReportedToTheHandlerAndFailsTheLoad()
	{
		inventory(new Item(COINS, 1000));
		List<String> reported = new ArrayList<>();
		try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class, CALLS_REAL_METHODS))
		{
			bank.when(Rs2Bank::isOpen).thenReturn(true);
			bank.when(() -> Rs2Bank.depositAll(any(java.util.function.Predicate.class))).thenReturn(true);
			bank.when(Rs2Bank::setWithdrawAsItem).thenReturn(true);
			bank.when(() -> Rs2Bank.count(anyInt())).thenReturn(0);
			bank.when(() -> Rs2Bank.withdrawX(anyInt(), anyInt())).thenReturn(false);
			assertFalse(new Rs2InventorySetup(trip, null).withMissingSupplyHandler(reported::add).loadInventory());
		}
		assertEquals(1, reported.size());
		assertTrue(reported.get(0), reported.get(0).contains("Lobster x3"));
	}

	@Test
	public void equipmentMatchesWhenTheSetupItemIsWorn()
	{
		Rs2InventorySetup loader = new Rs2InventorySetup(trip, null);
		assertFalse(loader.doesEquipmentMatch());
		worn.set(new Item[]{new Item(-1, 0), new Item(-1, 0), new Item(-1, 0), new Item(SWORD, 1)});
		assertTrue(loader.doesEquipmentMatch());
		assertEquals(1, loader.getEquipmentItems().size());
		assertEquals("dummy rows are left out", 4, loader.getInventoryItems().size());
	}
}
