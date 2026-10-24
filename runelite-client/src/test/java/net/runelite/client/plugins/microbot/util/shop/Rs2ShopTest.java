package net.runelite.client.plugins.microbot.util.shop;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Shop stock read from the shop interface, and buying through each slot's own option. */
public class Rs2ShopTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private Client client;
	private final AtomicBoolean clientThread = new AtomicBoolean(true);
	private Widget stock;

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
		MicrobotReadServices services = new MicrobotReadServices(client, thread, new EventBus());
		MicrobotRuntime.install(client, thread, new EventBus(), mock(ConfigManager.class), List.of(services));
		define(1925, "Bucket");
		define(1931, "Pot");
		stock = mock(Widget.class);
		Widget[] slots = {slot(1925, 30), slot(1931, 0), slot(-1, 0)}; // built before stubbing: Mockito forbids nesting
		when(stock.getDynamicChildren()).thenReturn(slots);
		when(client.getWidget(InterfaceID.Shopmain.ITEMS)).thenReturn(stock);
		clientThread.set(false);
	}

	@After
	public void tearDown() { MicrobotRuntime.uninstall(); }

	private void define(int id, String name)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn(name);
		when(client.getItemDefinition(id)).thenReturn(definition);
	}

	private static Widget slot(int id, int quantity)
	{
		Widget widget = mock(Widget.class);
		when(widget.getItemId()).thenReturn(id);
		when(widget.getItemQuantity()).thenReturn(quantity);
		return widget;
	}

	@Test
	public void stockIsReadFromTheShopInterface()
	{
		assertTrue(Rs2Shop.isOpen());
		assertTrue(Rs2Shop.hasStock("bucket"));
		assertTrue(Rs2Shop.hasStock(1925));
		assertFalse("a slot at 0 is out of stock", Rs2Shop.hasStock("Pot"));
		assertTrue(Rs2Shop.hasMinimumStock(1925, 30));
		assertFalse(Rs2Shop.hasMinimumStock("Bucket", 31));
		assertFalse("3 of 40 slots", Rs2Shop.isFull());

		when(stock.isHidden()).thenReturn(true);
		assertFalse(Rs2Shop.isOpen());
		assertFalse("no shop, no stock", Rs2Shop.hasStock(1925));
	}

	@Test
	public void buyingUsesTheSlotsBuyOption()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.widget(anyInt(), anyInt(), anyString())).thenReturn(true);
			actions.clearInvocations();
			assertTrue(Rs2Shop.buyItem("Bucket", "10"));
			actions.verify(() -> MobileActions.widget(InterfaceID.Shopmain.ITEMS, 0, "Buy 10"));
			assertFalse("out of stock", Rs2Shop.buyItem(1931, "1"));
			assertFalse("not sold here", Rs2Shop.buyItem("Rope", "1"));
		}
	}

	@Test
	public void buyItemOptimallyUsesTheFewestPurchases()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.widget(anyInt(), anyInt(), anyString())).thenReturn(true);
			actions.clearInvocations();
			Rs2Shop.buyItemOptimally("Bucket", 16);
			actions.verify(() -> MobileActions.widget(InterfaceID.Shopmain.ITEMS, 0, "Buy 10"));
			actions.verify(() -> MobileActions.widget(InterfaceID.Shopmain.ITEMS, 0, "Buy 5"));
			actions.verify(() -> MobileActions.widget(InterfaceID.Shopmain.ITEMS, 0, "Buy 1"));
			actions.verify(() -> MobileActions.widget(anyInt(), anyInt(), anyString()), times(3));
		}
	}
}
