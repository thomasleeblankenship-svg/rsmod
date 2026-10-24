package net.runelite.client.plugins.microbot.util.grandexchange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.grandexchange.models.ItemMappingData;
import net.runelite.client.plugins.microbot.util.grandexchange.models.TimeSeriesAnalysis;
import net.runelite.client.plugins.microbot.util.grandexchange.models.TimeSeriesInterval;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@link Rs2GrandExchange}'s price lookups against canned GE Tracker and OSRS Wiki responses. */
public class GrandExchangePricesTest
{
	private final Map<String, String> responses = new HashMap<>();
	private final List<String> requested = new ArrayList<>();
	private final AtomicBoolean clientThread = new AtomicBoolean(false);

	@Before
	public void setUp() throws Exception
	{
		Client client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(c -> clientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class), List.of(new MicrobotReadServices(client, thread, events)));
		GrandExchangePrices.setFetcherForTest((url, agent) ->
		{
			requested.add(url);
			return responses.get(url);
		});
	}

	@After
	public void tearDown()
	{
		GrandExchangePrices.setFetcherForTest(null);
		MicrobotRuntime.uninstall();
	}

	@Test
	public void geTrackerFieldsAreReadFromData()
	{
		responses.put(GrandExchangePrices.GE_TRACKER_API_URL + 995,
			"{\"data\":{\"overall\":1,\"buying\":2,\"selling\":3,\"buyingQuantity\":40,\"sellingQuantity\":50}}");
		assertEquals(1, Rs2GrandExchange.getPrice(995));
		assertEquals(2, Rs2GrandExchange.getOfferPrice(995));
		assertEquals(3, Rs2GrandExchange.getSellPrice(995));
		assertEquals(40, Rs2GrandExchange.getBuyingVolume(995));
		assertEquals(50, Rs2GrandExchange.getSellingVolume(995));
		assertEquals(-1, Rs2GrandExchange.getPrice(440));
		responses.put(GrandExchangePrices.GE_TRACKER_API_URL + 440, "not json {");
		assertEquals(-1, Rs2GrandExchange.getPrice(440));
	}

	@Test
	public void realTimePricesPreferTheWikiAndAreCached()
	{
		responses.put(GrandExchangePrices.WIKI_API_URL + 561, "{\"data\":{\"561\":{\"high\":210,\"low\":200,\"highVolume\":5000}}}");
		WikiPrice price = Rs2GrandExchange.getRealTimePrices(561);
		assertEquals(210, price.buyPrice);
		assertEquals(200, price.sellPrice);
		Rs2GrandExchange.getRealTimePrices(561);
		assertEquals(1, requested.size());
		assertEquals(221, Rs2GrandExchange.getAdaptiveBuyPrice(561, 1.0, 1));
		assertEquals(190, Rs2GrandExchange.getAdaptiveSellPrice(561, 1.0, 1));
	}

	@Test
	public void realTimePricesFallBackToGeTracker()
	{
		responses.put(GrandExchangePrices.GE_TRACKER_API_URL + 1511, "{\"data\":{\"overall\":100,\"selling\":90,\"buyingQuantity\":20}}");
		WikiPrice price = Rs2GrandExchange.getRealTimePrices(1511);
		assertEquals(100, price.buyPrice);
		assertEquals(90, price.sellPrice);
		assertEquals(20, price.volume);
		// Low volume adds 10%: ceil(100 * 1.1 * 1.1) is 122 in floating point, as upstream.
		assertEquals(122, Rs2GrandExchange.getAdaptiveBuyPrice(1511, 1.1, 0));
		assertNull(Rs2GrandExchange.getRealTimePrices(4151));
		assertEquals(-1, Rs2GrandExchange.getAdaptiveSellPrice(4151, 0.9, 0));
	}

	@Test
	public void timeSeriesSkipsPointsWithoutBothPrices()
	{
		responses.put(GrandExchangePrices.WIKI_TIMESERIES_URL + "?id=995&timestep=1h",
			"{\"data\":[{\"timestamp\":1,\"avgHighPrice\":110,\"avgLowPrice\":90,\"highPriceVolume\":5,\"lowPriceVolume\":7},"
				+ "{\"timestamp\":2,\"avgHighPrice\":null,\"avgLowPrice\":80},"
				+ "{\"timestamp\":3,\"avgHighPrice\":130,\"avgLowPrice\":110,\"highPriceVolume\":1,\"lowPriceVolume\":null}]}");
		TimeSeriesAnalysis analysis = Rs2GrandExchange.getTimeSeriesData(995, TimeSeriesInterval.ONE_HOUR);
		assertEquals(2, analysis.dataPoints.size());
		assertEquals(110, Rs2GrandExchange.getAveragePrice(995));
		assertEquals(13, analysis.totalVolume);
		assertEquals(-1, Rs2GrandExchange.getAveragePrice(995, TimeSeriesInterval.SIX_HOURS));
	}

	@Test
	public void itemMappingFindsTheItemAndIsCached()
	{
		responses.put(GrandExchangePrices.WIKI_MAPPING_URL,
			"[{\"id\":1,\"name\":\"Other\"},{\"id\":561,\"name\":\"Nature rune\",\"members\":false,\"limit\":18000,\"highalch\":108}]");
		ItemMappingData mapping = Rs2GrandExchange.getItemMappingData(561);
		assertEquals("Nature rune", mapping.name);
		assertEquals(18000, mapping.tradeLimitPer4Hours);
		assertEquals(108, mapping.highAlch);
		assertEquals("", mapping.examine);
		Rs2GrandExchange.getItemMappingData(561);
		assertEquals(1, requested.size());
		assertNull(Rs2GrandExchange.getItemMappingData(2));
	}

	@Test
	public void lookupsAreRefusedOnTheClientThread()
	{
		responses.put(GrandExchangePrices.GE_TRACKER_API_URL + 995, "{\"data\":{\"overall\":1}}");
		clientThread.set(true);
		assertEquals(-1, Rs2GrandExchange.getPrice(995));
		assertTrue(requested.isEmpty());
	}
}
