package net.runelite.client.plugins.microbot.util.bank;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util-gap action batch G1: bank slot locks, tabs, herb sack, withdraw-and-equip, the Microbot flags. */
public class HubActionBatchG1Test
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private Client client;
	private final AtomicReference<String> lock = new AtomicReference<>("Lock-slot");

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenReturn(false);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(thread).invoke(any(Runnable.class));
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class), List.of(new MicrobotReadServices(client, thread, events)));
		Widget container = mock(Widget.class);
		Widget slot = mock(Widget.class);
		when(slot.getActions()).thenAnswer(call -> new String[]{"Deposit-1", lock.get(), null});
		when(container.getChild(4)).thenReturn(slot);
		when(client.getWidget(InterfaceID.Bankside.ITEMS)).thenReturn(container);
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void aSlotLockTogglesThroughItsOwnOption()
	{
		Rs2ItemModel hammer = mock(Rs2ItemModel.class);
		when(hammer.getSlot()).thenReturn(4);
		when(hammer.getName()).thenReturn("Hammer");
		try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class, CALLS_REAL_METHODS);
			MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			bank.when(Rs2Bank::isOpen).thenReturn(true);
			actions.when(MobileActions::active).thenReturn(true);
			inventory.when(() -> Rs2Inventory.items(any(Predicate.class))).thenAnswer(call ->
				Stream.of(hammer).filter((Predicate<Rs2ItemModel>) call.getArgument(0)));
			inventory.when(() -> Rs2Inventory.getItemInSlot(4)).thenReturn(hammer);
			actions.when(() -> MobileActions.widget(anyInt(), anyInt(), anyString())).thenAnswer(call ->
			{
				lock.set("Lock-slot".equals(call.getArgument(2)) ? "Unlock-slot" : "Lock-slot");
				return true;
			});
			actions.clearInvocations();

			assertTrue(Rs2Bank.toggleItemLock("hammer", false));
			actions.verify(() -> MobileActions.widget(InterfaceID.Bankside.ITEMS, 4, "Lock-slot"));
			assertFalse("already locked: lockAllBySlot skips it", Rs2Bank.lockAllBySlot(4));
			assertTrue("and toggleAllLocks unlocks it", Rs2Bank.toggleAllLocks());
			assertEquals("Lock-slot", lock.get());

			actions.clearInvocations();
			assertTrue(Rs2Bank.openTab(3));
			actions.verify(() -> MobileActions.widget(InterfaceID.Bankmain.TABS, 13, ""));
			assertFalse(Rs2Bank.openTab(10));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void refusalsAndFlags()
	{
		assertFalse("cant-hop is off by default", Microbot.cantHopWorld);
		assertFalse(Microbot.cantReachTarget);
		assertEquals(0, Microbot.cantReachTargetRetries);
		assertFalse("no script session: no scroll", Rs2Bank.scrollBankToSlot(3));
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class, CALLS_REAL_METHODS))
		{
			inventory.when(() -> Rs2Inventory.items(any(Predicate.class))).thenAnswer(call -> Stream.empty());
			assertFalse("no herb sack", Rs2Bank.emptyHerbSack());
			equipment.when(() -> Rs2Equipment.isWearing("Dragon pickaxe")).thenReturn(true);
			assertTrue("already worn", Rs2Bank.withdrawXAndEquip("Dragon pickaxe", 1));
		}
	}
}
