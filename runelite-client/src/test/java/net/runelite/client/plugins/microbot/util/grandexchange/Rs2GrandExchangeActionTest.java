package net.runelite.client.plugins.microbot.util.grandexchange;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.VarPlayer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/** {@link Rs2GrandExchange} actions: what is dispatched through {@link MobileActions}, and when. */
public class Rs2GrandExchangeActionTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final int SLOT_ONE_PANEL = (InterfaceID.GE_OFFERS << 16) | GrandExchangeWidget.FIRST_SLOT_CHILD;

	private final AtomicBoolean clientThread = new AtomicBoolean(false);
	private final GrandExchangeOffer[] offers = new GrandExchangeOffer[8];
	private Client client;
	private Widget offerScreen;
	private MockedStatic<MobileActions> actions;
	private MockedStatic<Rs2Inventory> inventory;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> clientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getGrandExchangeOffers()).thenReturn(offers);
		when(client.getVarpValue(VarPlayer.MEMBERSHIP_DAYS)).thenReturn(10);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			clientThread.set(true);
			try
			{
				((Runnable) call.getArgument(0)).run();
			}
			finally
			{
				clientThread.set(false);
			}
			return null;
		}).when(thread).invoke(any(Runnable.class));
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class), List.of(new MicrobotReadServices(client, thread, events)));

		when(client.getWidget(InterfaceID.GE_OFFERS, 1)).thenReturn(mock(Widget.class));
		offerScreen = mock(Widget.class);
		when(offerScreen.isHidden()).thenReturn(true);
		when(client.getWidget(InterfaceID.GE_OFFERS, 15)).thenReturn(offerScreen);
		actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS);
		inventory = mockStatic(Rs2Inventory.class);
	}

	@After
	public void tearDown()
	{
		inventory.close();
		actions.close();
		MicrobotRuntime.uninstall();
	}

	private void offerInSlotOne(String name)
	{
		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getItemId()).thenReturn(1511);
		when(offer.getState()).thenReturn(GrandExchangeOfferState.BUYING);
		offers[0] = offer;
		Widget panel = mock(Widget.class);
		when(panel.getId()).thenReturn(SLOT_ONE_PANEL);
		Widget label = mock(Widget.class);
		when(label.getText()).thenReturn(name);
		when(panel.getChild(Rs2GrandExchange.SLOT_ITEM_NAME_CHILD)).thenReturn(label);
		when(client.getWidget(InterfaceID.GE_OFFERS, GrandExchangeWidget.FIRST_SLOT_CHILD)).thenReturn(panel);
		when(client.getWidget(Rs2GrandExchange.COLLECT_ALL_BUTTON)).thenReturn(mock(Widget.class));
	}

	@Test
	public void requestsAreValidatedAsUpstream()
	{
		assertFalse(Rs2GrandExchange.isValidRequest(null));
		assertFalse(Rs2GrandExchange.isValidRequest(GrandExchangeRequest.builder().itemName("Logs").quantity(1).build()));
		assertTrue(Rs2GrandExchange.isValidRequest(GrandExchangeRequest.builder().action(GrandExchangeAction.BUY).itemName("Logs").quantity(1).build()));
		assertFalse(Rs2GrandExchange.isValidRequest(GrandExchangeRequest.builder().action(GrandExchangeAction.BUY).itemName(" ").quantity(1).build()));
		assertFalse(Rs2GrandExchange.isValidRequest(GrandExchangeRequest.builder().action(GrandExchangeAction.SELL).itemName("Logs").quantity(1).build()));
		assertTrue(Rs2GrandExchange.isValidRequest(GrandExchangeRequest.builder().action(GrandExchangeAction.SELL).itemName("Logs").quantity(1).percent(-5).build()));
		assertTrue(Rs2GrandExchange.isValidRequest(GrandExchangeRequest.builder().action(GrandExchangeAction.COLLECT).build()));
	}

	@Test
	public void collectOptionFollowsTheButtonsActions()
	{
		assertEquals("Bank", Rs2GrandExchange.collectOption(true, 4, 1));
		assertEquals("Collect-items", Rs2GrandExchange.collectOption(false, 4, 1));
		assertEquals("Collect-notes", Rs2GrandExchange.collectOption(false, 4, 20));
		assertEquals("Collect", Rs2GrandExchange.collectOption(false, 3, 20));
		assertEquals(7, Rs2GrandExchange.extractNumber("+7%"));
		assertEquals(-1, Rs2GrandExchange.extractNumber("+X%"));
	}

	@Test
	public void quantityIsRetriedAtMostThreeTimes()
	{
		AtomicInteger attempts = new AtomicInteger();
		assertFalse(Rs2GrandExchange.retryQuantity(5, () -> 1, attempts::incrementAndGet));
		assertEquals(3, attempts.get());
		AtomicInteger quantity = new AtomicInteger(1);
		assertTrue(Rs2GrandExchange.retryQuantity(5, quantity::get, () -> quantity.set(5)));
	}

	@Test
	public void abortOfferAbortsTheNamedSlotThenCollectsToTheBank()
	{
		offerInSlotOne("Oak logs");
		actions.when(() -> MobileActions.widget(anyInt(), anyInt(), anyString())).thenReturn(true);
		actions.clearInvocations();
		assertFalse(Rs2GrandExchange.abortOffer("Yew logs", true));
		actions.verify(() -> MobileActions.widget(anyInt(), anyInt(), anyString()), never());

		assertTrue(Rs2GrandExchange.abortOffer("oak logs", true));
		actions.verify(() -> MobileActions.widget(SLOT_ONE_PANEL, Rs2GrandExchange.SLOT_MENU_CHILD, "Abort offer"));
		actions.verify(() -> MobileActions.widget(Rs2GrandExchange.COLLECT_ALL_BUTTON, 0, "Collect to bank"));
	}

	@Test
	public void collectOfferViewsTheSlotThenBanksEachCollectButton()
	{
		offerInSlotOne("Oak logs");
		Widget coins = mock(Widget.class);
		when(coins.getId()).thenReturn(InterfaceID.GE_OFFERS << 16 | GrandExchangeWidget.COLLECT_CONTAINER_CHILD);
		when(coins.getIndex()).thenReturn(3);
		when(coins.getActions()).thenReturn(new String[]{null, "Collect", "Bank"});
		Widget container = mock(Widget.class);
		when(container.getChildren()).thenReturn(new Widget[]{mock(Widget.class), coins});
		when(client.getWidget(InterfaceID.GE_OFFERS, GrandExchangeWidget.COLLECT_CONTAINER_CHILD)).thenReturn(container);
		actions.when(() -> MobileActions.widget(SLOT_ONE_PANEL, Rs2GrandExchange.SLOT_MENU_CHILD, "View offer")).thenAnswer(c ->
		{
			when(offerScreen.isHidden()).thenReturn(false);
			return true;
		});
		actions.when(() -> MobileActions.widget(anyInt(), eq(3), eq("Bank"))).thenAnswer(c ->
		{
			when(offerScreen.isHidden()).thenReturn(true);
			return true;
		});
		actions.clearInvocations();
		assertTrue(Rs2GrandExchange.collectOffer(GrandExchangeSlots.ONE, true));
		actions.verify(() -> MobileActions.widget(InterfaceID.GE_OFFERS << 16 | GrandExchangeWidget.COLLECT_CONTAINER_CHILD, 3, "Bank"));
	}

	@Test
	public void sellReportsFailureWhenConfirmIsMissing()
	{
		inventory.when(() -> Rs2Inventory.hasItem("Oak plank", false)).thenReturn(true);
		inventory.when(() -> Rs2Inventory.interact("Oak plank", "Offer", false)).thenReturn(true);
		when(client.getWidget(InterfaceID.GeOffers.SETUP_DESC)).thenReturn(mock(Widget.class));
		when(client.getVarpLongValue(Rs2GrandExchange.GE_NEWOFFER_PRICE_VARP)).thenReturn(100L);
		when(client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)).thenReturn(5);
		actions.clearInvocations();
		assertFalse(Rs2GrandExchange.sellItem("Oak plank", 5, 100));
		actions.verify(() -> MobileActions.widget(anyInt(), anyInt(), anyString()), never());
	}

	@Test
	public void closeUsesTheFrameCloseButton()
	{
		actions.when(() -> MobileActions.widget(Rs2GrandExchange.GE_FRAME, Rs2GrandExchange.CLOSE_CHILD, "")).thenAnswer(c ->
		{
			when(client.getWidget(InterfaceID.GE_OFFERS, 1)).thenReturn(null);
			return true;
		});
		Rs2GrandExchange.closeExchange();
		assertFalse(Rs2GrandExchange.isOpen());
	}
}
