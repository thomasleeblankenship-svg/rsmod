package net.runelite.client.plugins.microbot.util.grandexchange;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.VarPlayer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@link Rs2GrandExchange} reads and {@link GrandExchangeWidget} lookups, all on the client thread. */
public class Rs2GrandExchangeReadTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean clientThread = new AtomicBoolean(false);
	private final GrandExchangeOffer[] offers = new GrandExchangeOffer[8];
	private Client client;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> clientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getGrandExchangeOffers()).thenAnswer(call -> onClientThread(offers));
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
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	private <T> T onClientThread(T value)
	{
		assertTrue("read off the client thread", clientThread.get());
		return value;
	}

	private void offer(GrandExchangeSlots slot, int itemId, GrandExchangeOfferState state, int sold, int total, int price)
	{
		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getItemId()).thenAnswer(c -> onClientThread(itemId));
		when(offer.getState()).thenAnswer(c -> onClientThread(state));
		when(offer.getQuantitySold()).thenAnswer(c -> onClientThread(sold));
		when(offer.getTotalQuantity()).thenAnswer(c -> onClientThread(total));
		when(offer.getPrice()).thenAnswer(c -> onClientThread((long) price));
		when(offer.getSpent()).thenAnswer(c -> onClientThread((long) sold * price));
		offers[slot.ordinal()] = offer;
	}

	@Test
	public void slotsAreFreeUntilAnOfferIsPlacedAndLimitedForFreeToPlay()
	{
		assertTrue(Rs2GrandExchange.isAllSlotsEmpty());
		assertEquals(8, Rs2GrandExchange.getAvailableSlotsCount());
		offer(GrandExchangeSlots.ONE, 995, GrandExchangeOfferState.BUYING, 0, 10, 5);
		offer(GrandExchangeSlots.TWO, 0, GrandExchangeOfferState.EMPTY, 0, 0, 0);
		assertFalse(Rs2GrandExchange.isSlotAvailable(GrandExchangeSlots.ONE));
		assertTrue(Rs2GrandExchange.isSlotAvailable(GrandExchangeSlots.TWO));
		assertSame(GrandExchangeSlots.TWO, Rs2GrandExchange.getAvailableSlot());
		assertEquals(7, Rs2GrandExchange.getAvailableSlotsCount());

		when(client.getVarpValue(VarPlayer.MEMBERSHIP_DAYS)).thenReturn(0);
		assertArrayEquals(new GrandExchangeSlots[]{GrandExchangeSlots.TWO, GrandExchangeSlots.THREE}, Rs2GrandExchange.getAvailableSlots());
		assertFalse(Rs2GrandExchange.isSlotAvailable(GrandExchangeSlots.FOUR));
		assertFalse(Rs2GrandExchange.isSlotAvailable(null));
	}

	@Test
	public void offerStatesAndDetailsAreCopied()
	{
		offer(GrandExchangeSlots.ONE, 995, GrandExchangeOfferState.BOUGHT, 10, 10, 5);
		offer(GrandExchangeSlots.TWO, 440, GrandExchangeOfferState.SELLING, 3, 4, 100);
		offer(GrandExchangeSlots.THREE, 1511, GrandExchangeOfferState.CANCELLED_BUY, 2, 8, 50);
		assertTrue(Rs2GrandExchange.hasBoughtOffer());
		assertTrue(Rs2GrandExchange.hasFinishedBuyingOffers());
		assertFalse(Rs2GrandExchange.hasSoldOffer());
		assertFalse(Rs2GrandExchange.hasFinishedSellingOffers());
		assertTrue(Rs2GrandExchange.hasOffer());
		assertTrue(Rs2GrandExchange.hasBoughtOffer(GrandExchangeSlots.ONE));
		assertFalse(Rs2GrandExchange.hasSoldOffer(GrandExchangeSlots.TWO));

		GrandExchangeOfferDetails selling = Rs2GrandExchange.getOfferDetails(GrandExchangeSlots.TWO);
		assertEquals(440, selling.getItemId());
		assertTrue(selling.isSelling());
		assertEquals(75, selling.getProgressPercentage());
		assertEquals(300L, selling.getSpent());
		assertNull(Rs2GrandExchange.getOfferDetails(GrandExchangeSlots.FOUR));

		assertSame(GrandExchangeSlots.TWO, Rs2GrandExchange.findSlotForItem(440, true));
		assertNull(Rs2GrandExchange.findSlotForItem(440, false));
		assertEquals(3, Rs2GrandExchange.getItemsSoldFromOffer(GrandExchangeSlots.TWO));
		assertEquals(0, Rs2GrandExchange.getItemsBoughtFromOffer(GrandExchangeSlots.TWO));
		assertEquals(2, Rs2GrandExchange.getItemsBoughtFromOffer(GrandExchangeSlots.THREE));
		assertTrue(Rs2GrandExchange.isCancelledOfferWithItems(GrandExchangeSlots.THREE));

		Map<GrandExchangeSlots, GrandExchangeOfferDetails> completed = Rs2GrandExchange.getCompletedOffers();
		assertEquals(2, completed.size());
		assertTrue(completed.containsKey(GrandExchangeSlots.ONE) && completed.containsKey(GrandExchangeSlots.THREE));
		assertEquals(995, Rs2GrandExchange.hasBuyOffer(995).getItemId());
		assertNull(Rs2GrandExchange.hasSellOffer(995));
		assertEquals(List.of(GrandExchangeSlots.THREE, GrandExchangeSlots.TWO, GrandExchangeSlots.ONE),
			Rs2GrandExchange.getActiveOfferSlotsByProgress());

		Map<String, Object> cancel = Rs2GrandExchange.getOfferDetailsForCancellation(GrandExchangeSlots.TWO);
		assertEquals(1, cancel.get("remainingQuantity"));
		assertEquals(false, cancel.get("isBuyOffer"));
		assertEquals(100, cancel.get("price"));
	}

	@Test
	public void offerPriceIsReadFromTheLongNewOfferVarp()
	{
		when(client.getVarpLongValue(Rs2GrandExchange.GE_NEWOFFER_PRICE_VARP)).thenAnswer(c -> onClientThread(3_000_000_000L));
		assertEquals(3_000_000_000L, Rs2GrandExchange.getOfferPrice());
		assertFalse(GrandExchangeWidget.hasOfferPriceChanged(3_000_000_000L));
		assertTrue(GrandExchangeWidget.hasOfferPriceChanged(1175L));
	}

	@Test
	public void offerPriceFallsBackToTheIntVarpWhenTheLongReadIsRefused()
	{
		when(client.getVarpLongValue(Rs2GrandExchange.GE_NEWOFFER_PRICE_VARP)).thenAnswer(c ->
		{
			onClientThread(0L);
			throw new IllegalArgumentException("varp is an int");
		});
		when(client.getVarpValue(Rs2GrandExchange.GE_NEWOFFER_PRICE_VARP)).thenReturn(1175);
		assertEquals(1175L, Rs2GrandExchange.getOfferPrice());
	}

	@Test
	public void offerSetupStateFollowsTheSetupPanelNotTheDetailsPanel()
	{
		Widget setup = mock(Widget.class);
		Widget details = mock(Widget.class);
		when(client.getWidget(InterfaceID.GeOffers.SETUP)).thenReturn(setup);
		when(client.getWidget(InterfaceID.GE_OFFERS, 15)).thenReturn(details);
		when(details.isHidden()).thenReturn(true);
		assertFalse(Rs2GrandExchange.isOfferScreenOpen());
		assertTrue(Rs2GrandExchange.isOfferSetupOpen());
		when(setup.isHidden()).thenReturn(true);
		assertFalse(Rs2GrandExchange.isOfferSetupOpen());
	}

	@Test
	public void nullSlotsAndMissingContainersGiveNull()
	{
		assertNull(GrandExchangeWidget.getSlot(null));
		assertNull(GrandExchangeWidget.getOfferBuyButton(null));
		assertNull(GrandExchangeWidget.getOfferSellButton(null));
		assertNull(GrandExchangeWidget.getConfirm());
		Widget container = mock(Widget.class);
		when(client.getWidget(InterfaceID.GE_OFFERS, GrandExchangeWidget.OFFER_CONTAINER_CHILD)).thenReturn(container);
		assertNull(GrandExchangeWidget.getConfirm());
	}

	@Test
	public void confirmAndSlotButtonsAreFoundInTheirContainers()
	{
		Widget button = mock(Widget.class);
		when(button.getText()).thenAnswer(c -> onClientThread("Confirm"));
		Widget container = mock(Widget.class);
		when(container.getDynamicChildren()).thenAnswer(c -> onClientThread(new Widget[]{mock(Widget.class), button}));
		when(client.getWidget(InterfaceID.GE_OFFERS, GrandExchangeWidget.OFFER_CONTAINER_CHILD)).thenReturn(container);
		assertSame(button, GrandExchangeWidget.getConfirm());

		Widget slotThree = mock(Widget.class);
		Widget buy = mock(Widget.class);
		when(slotThree.getChild(0)).thenAnswer(c -> onClientThread(buy));
		when(client.getWidget(InterfaceID.GE_OFFERS, 9)).thenReturn(slotThree);
		assertSame(buy, GrandExchangeWidget.getOfferBuyButton(GrandExchangeSlots.THREE));
	}

	@Test
	public void searchResultIsTheWidgetBeforeTheMatchingName()
	{
		Widget clickable = mock(Widget.class);
		Widget name = mock(Widget.class);
		when(name.getText()).thenAnswer(c -> onClientThread("<col=ff9040>Oak logs</col>"));
		Widget other = mock(Widget.class);
		when(other.getText()).thenReturn("Logs");
		Widget results = mock(Widget.class);
		when(results.getChildren()).thenAnswer(c -> onClientThread(new Widget[]{other, clickable, name}));
		when(client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS)).thenReturn(results);
		Pair<Widget, Integer> hit = Rs2GrandExchange.getSearchResultWidget("oak logs", true);
		assertSame(clickable, hit.getLeft());
		assertEquals(1, (int) hit.getRight());
		assertNull(Rs2GrandExchange.getSearchResultWidget("Yew logs", false));
	}
}
