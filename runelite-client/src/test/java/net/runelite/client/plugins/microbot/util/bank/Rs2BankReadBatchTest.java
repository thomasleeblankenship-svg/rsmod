package net.runelite.client.plugins.microbot.util.bank;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Read batch 14: upstream 2.6.38 {@code Rs2Bank} reads over the remembered bank and the bank widgets. */
public class Rs2BankReadBatchTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final EventBus events = new EventBus();
	private Client client;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		// LOGIN_SCREEN keeps the varbit cache from holding values between assertions.
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(client.getVarbitValue(anyInt())).thenAnswer(i -> varbits.getOrDefault((int) i.getArgument(0), 0));
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn("Item");
		when(definition.getNote()).thenReturn(-1);
		when(definition.getInventoryActions()).thenReturn(new String[5]);
		when(client.getItemDefinition(anyInt())).thenReturn(definition);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			onClientThread.set(true);
			try
			{
				((Runnable) call.getArgument(0)).run();
			}
			finally
			{
				onClientThread.set(false);
			}
			return null;
		}).when(thread).invoke(any(Runnable.class));
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class), List.of(new MicrobotReadServices(client, thread, events)));
		Rs2Bank.clearMemory();
		Rs2Bank.resetBankLiveEpoch();
	}

	@After
	public void tearDown()
	{
		Rs2Bank.clearMemory();
		MicrobotRuntime.uninstall();
	}

	private void bank(int... ids)
	{
		Item[] items = new Item[ids.length];
		for (int i = 0; i < ids.length; i++)
		{
			items[i] = new Item(ids[i], 5);
		}
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		post(new ItemContainerChanged(InventoryID.BANK.getId(), container));
	}

	private void post(Object event)
	{
		onClientThread.set(true);
		try
		{
			events.post(event);
		}
		finally
		{
			onClientThread.set(false);
		}
	}

	@Test
	public void rememberedBankReadsAndTheLiveEpoch()
	{
		assertEquals(0, Rs2Bank.getBankLiveEpoch());
		bank(ItemID.LAWRUNE, ItemID.BH_RUNE_POUCH);
		assertEquals(1, Rs2Bank.getBankLiveEpoch());
		assertEquals(ItemID.LAWRUNE, Rs2Bank.get(ItemID.LAWRUNE).getId());
		assertEquals(1, Rs2Bank.get(item -> item.getId() == ItemID.BH_RUNE_POUCH).getSlot());
		assertNull(Rs2Bank.get(1));
		assertTrue(Rs2Bank.hasRunePouch());
		bank(ItemID.LAWRUNE);
		assertEquals(2, Rs2Bank.getBankLiveEpoch());
		assertFalse(Rs2Bank.hasRunePouch());

		GameStateChanged logout = new GameStateChanged();
		logout.setGameState(GameState.LOGIN_SCREEN);
		post(logout);
		assertEquals(0, Rs2Bank.getBankLiveEpoch());
	}

	@Test
	public void tabsFollowTheVarbits()
	{
		bank(1, 2, 3, 4, 5, 6);
		varbits.put(VarbitID.BANK_CURRENTTAB, 2);
		assertEquals(2, Rs2Bank.getCurrentTab());
		assertTrue(Rs2Bank.isTabOpen(2));
		varbits.put(VarbitID.BANK_TAB_1, 2);
		varbits.put(VarbitID.BANK_TAB_2, 3);
		assertEquals(1, Rs2Bank.getItemTabForBankItem(1));
		assertEquals(2, Rs2Bank.getItemTabForBankItem(4));
		assertEquals("untabbed", 0, Rs2Bank.getItemTabForBankItem(5));
		assertEquals(-1, Rs2Bank.getItemTabForBankItem(6));
	}

	@Test
	public void widgetReads()
	{
		assertTrue(Rs2Bank.getItems().isEmpty());
		assertNull(Rs2Bank.getItemWidget(0));
		Widget slot = mock(Widget.class);
		when(slot.getBounds()).thenAnswer(i ->
		{
			assertTrue("read on the client thread", onClientThread.get());
			return new Rectangle(10, 20, 36, 32);
		});
		Widget items = mock(Widget.class);
		when(items.getDynamicChildren()).thenReturn(new Widget[]{slot});
		when(client.getWidget(ComponentID.BANK_ITEM_CONTAINER)).thenReturn(items);
		assertSame(slot, Rs2Bank.getItemWidget(0));
		assertEquals(new Rectangle(10, 20, 36, 32), Rs2Bank.getItemBounds(0));
		assertNull(Rs2Bank.getItemBounds(1));
		Widget tabs = mock(Widget.class);
		when(tabs.getDynamicChildren()).thenReturn(new Widget[]{mock(Widget.class), mock(Widget.class)});
		when(client.getWidget(ComponentID.BANK_TAB_CONTAINER)).thenReturn(tabs);
		assertEquals(2, Rs2Bank.getTabs().size());
		assertFalse(Rs2Bank.isBankPinWidgetVisible());
		when(client.getWidget(ComponentID.BANK_PIN_CONTAINER)).thenReturn(mock(Widget.class));
		assertTrue(Rs2Bank.isBankPinWidgetVisible());
	}
}
