package net.runelite.client.plugins.microbot.util.grandexchange;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.util.grandexchange.models.ItemMappingData;
import net.runelite.client.plugins.microbot.util.grandexchange.models.TimeSeriesAnalysis;
import net.runelite.client.plugins.microbot.util.grandexchange.models.TimeSeriesDataPoint;
import net.runelite.client.plugins.microbot.util.grandexchange.models.TimeSeriesInterval;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;

/**
 * The price lookups behind {@link Rs2GrandExchange}'s price methods (upstream Microbot 2.6.38):
 * GE Tracker for the overall, buying and selling prices and volumes, and the OSRS Wiki for
 * real-time prices, time series and item mapping. Same URLs, caches and fallbacks as upstream.
 *
 * <p>RuneMobile: a lookup blocks for up to ten seconds, so one made on the client thread is
 * refused (logged, answers as a failed lookup) instead of freezing the game.
 */
@Slf4j
final class GrandExchangePrices
{
	static final String GE_TRACKER_API_URL = "https://www.ge-tracker.com/api/items/";
	static final String WIKI_API_URL = "https://prices.runescape.wiki/api/v1/osrs/latest?id=";
	static final String WIKI_TIMESERIES_URL = "https://prices.runescape.wiki/api/v1/osrs/timeseries";
	static final String WIKI_MAPPING_URL = "https://prices.runescape.wiki/api/v1/osrs/mapping";
	private static final long PRICE_CACHE_DURATION = 60_000;
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	private static final Map<Integer, WikiPrice> PRICE_CACHE = new ConcurrentHashMap<>();
	private static final Map<Integer, ItemMappingData> MAPPING_CACHE = new ConcurrentHashMap<>();

	/** (url, user agent or null) to the response body, or {@code null} on a failure or non-200 status. */
	private static volatile BiFunction<String, String, String> fetcher = GrandExchangePrices::httpGet;

	private GrandExchangePrices()
	{
	}

	/** Test seam: replaces the HTTP fetch and clears the caches; {@code null} restores the real one. */
	static void setFetcherForTest(BiFunction<String, String, String> replacement)
	{
		fetcher = replacement == null ? GrandExchangePrices::httpGet : replacement;
		PRICE_CACHE.clear();
		MAPPING_CACHE.clear();
	}

	/** GE Tracker's {@code data.<field>} for {@code itemId}, or -1. */
	static int geTracker(int itemId, String field)
	{
		JsonObject data = object(fetch(GE_TRACKER_API_URL + itemId, null), "data");
		JsonElement value = data == null ? null : data.get(field);
		return value == null || value.isJsonNull() ? -1 : value.getAsInt();
	}

	/** Real-time prices, cached for a minute: the Wiki first, then GE Tracker. */
	static WikiPrice getRealTimePrices(int itemId)
	{
		WikiPrice cached = PRICE_CACHE.get(itemId);
		if (cached != null && !cached.isExpired(PRICE_CACHE_DURATION))
		{
			return cached;
		}
		WikiPrice price = getWikiPrices(itemId);
		if (price == null)
		{
			int buy = geTracker(itemId, "overall");
			int sell = geTracker(itemId, "selling");
			if (buy > 0 && sell > 0)
			{
				price = new WikiPrice(buy, sell, geTracker(itemId, "buyingQuantity"));
			}
		}
		if (price != null)
		{
			PRICE_CACHE.put(itemId, price);
		}
		return price;
	}

	private static WikiPrice getWikiPrices(int itemId)
	{
		JsonObject data = object(fetch(WIKI_API_URL + itemId, "OSRS - Price Fetcher"), "data");
		JsonObject item = data == null || !data.has(String.valueOf(itemId)) ? null : data.getAsJsonObject(String.valueOf(itemId));
		if (item == null)
		{
			log.warn("No price data found for item ID: {}", itemId);
			return null;
		}
		int buy = intOr(item, "high", -1);
		int sell = intOr(item, "low", -1);
		return buy > 0 && sell > 0 ? new WikiPrice(buy, sell, intOr(item, "highVolume", 0)) : null;
	}

	/** Upstream's adaptive buy price: +5% per retry, +10% more for low volume; -1 without data. */
	static int getAdaptiveBuyPrice(int itemId, double basePercentage, int retryAttempt)
	{
		double retryMultiplier = 1.0 + retryAttempt * 0.05;
		WikiPrice price = getRealTimePrices(itemId);
		if (price != null)
		{
			double multiplier = basePercentage * retryMultiplier * (price.volume < 100 ? 1.1 : 1.0);
			return (int) Math.ceil(price.buyPrice * multiplier);
		}
		int gePrice = geTracker(itemId, "overall");
		return gePrice > 0 ? (int) Math.ceil(gePrice * basePercentage * retryMultiplier) : -1;
	}

	/** Upstream's adaptive sell price: -5% per retry, -5% more for low volume; -1 without data. */
	static int getAdaptiveSellPrice(int itemId, double basePercentage, int retryAttempt)
	{
		double retryMultiplier = 1.0 - retryAttempt * 0.05;
		WikiPrice price = getRealTimePrices(itemId);
		if (price != null)
		{
			double multiplier = basePercentage * retryMultiplier * (price.volume < 100 ? 0.95 : 1.0);
			return (int) Math.floor(price.sellPrice * multiplier);
		}
		int gePrice = geTracker(itemId, "selling");
		return gePrice > 0 ? (int) Math.floor(gePrice * basePercentage * retryMultiplier) : -1;
	}

	/** The Wiki time series at {@code interval}; an empty analysis on failure. */
	static TimeSeriesAnalysis getTimeSeriesData(int itemId, TimeSeriesInterval interval, Long fromTimestamp)
	{
		String url = WIKI_TIMESERIES_URL + "?id=" + itemId + "&timestep=" + interval.getApiValue()
			+ (fromTimestamp == null ? "" : "&timestamp=" + fromTimestamp);
		JsonElement root = parse(fetch(url, "Time Series Price Analysis"));
		List<TimeSeriesDataPoint> points = new ArrayList<>();
		if (root != null && root.isJsonObject() && root.getAsJsonObject().has("data"))
		{
			JsonArray data = root.getAsJsonObject().getAsJsonArray("data");
			for (JsonElement element : data)
			{
				JsonObject point = element.getAsJsonObject();
				int high = intOr(point, "avgHighPrice", 0);
				int low = intOr(point, "avgLowPrice", 0);
				if (high > 0 && low > 0)
				{
					long timestamp = point.has("timestamp") ? point.get("timestamp").getAsLong() : 0;
					points.add(new TimeSeriesDataPoint(timestamp, high, low, intOr(point, "highPriceVolume", 0), intOr(point, "lowPriceVolume", 0)));
				}
			}
		}
		return new TimeSeriesAnalysis(points, interval);
	}

	/** The Wiki item mapping (trade limit, alch values), cached for the session; {@code null} on failure. */
	static ItemMappingData getItemMappingData(int itemId)
	{
		ItemMappingData cached = MAPPING_CACHE.get(itemId);
		if (cached != null)
		{
			return cached;
		}
		JsonElement root = parse(fetch(WIKI_MAPPING_URL, "OSRS Item Mapping Fetcher"));
		if (root == null || !root.isJsonArray())
		{
			return null;
		}
		for (JsonElement element : root.getAsJsonArray())
		{
			JsonObject item = element.getAsJsonObject();
			if (item.has("id") && item.get("id").getAsInt() == itemId)
			{
				ItemMappingData mapping = new ItemMappingData(itemId, stringOr(item, "name"), stringOr(item, "examine"),
					item.has("members") && item.get("members").getAsBoolean(), intOr(item, "limit", -1), intOr(item, "value", 0),
					intOr(item, "lowalch", 0), intOr(item, "highalch", 0), stringOr(item, "icon"));
				MAPPING_CACHE.put(itemId, mapping);
				return mapping;
			}
		}
		return null;
	}

	private static String fetch(String url, String userAgent)
	{
		MicrobotReadServices services = MicrobotReadServices.current();
		if (services != null && services.getClient().isClientThread())
		{
			log.warn("Grand Exchange price lookup refused on the client thread: {}", url);
			return null;
		}
		try
		{
			return fetcher.apply(url, userAgent);
		}
		catch (RuntimeException e)
		{
			log.error("Grand Exchange price lookup failed: {}", url, e);
			return null;
		}
	}

	private static String httpGet(String url, String userAgent)
	{
		HttpRequest.Builder request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10));
		if (userAgent != null)
		{
			request.header("User-Agent", userAgent);
		}
		try
		{
			HttpResponse<String> response = HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200)
			{
				log.warn("Grand Exchange price lookup: HTTP {} for {}", response.statusCode(), url);
				return null;
			}
			return response.body();
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			return null;
		}
		catch (java.io.IOException e)
		{
			log.warn("Grand Exchange price lookup failed: {} ({})", url, e.getMessage());
			return null;
		}
	}

	@SuppressWarnings("deprecation")
	private static JsonElement parse(String body)
	{
		if (body == null)
		{
			return null;
		}
		try
		{
			return new JsonParser().parse(body);
		}
		catch (RuntimeException e)
		{
			log.warn("Grand Exchange price lookup: unreadable response ({})", e.getMessage());
			return null;
		}
	}

	private static JsonObject object(String body, String member)
	{
		JsonElement root = parse(body);
		if (root == null || !root.isJsonObject())
		{
			return null;
		}
		JsonElement value = root.getAsJsonObject().get(member);
		return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
	}

	private static int intOr(JsonObject object, String member, int fallback)
	{
		JsonElement value = object.get(member);
		return value == null || value.isJsonNull() ? fallback : value.getAsInt();
	}

	private static String stringOr(JsonObject object, String member)
	{
		JsonElement value = object.get(member);
		return value == null || value.isJsonNull() ? "" : value.getAsString();
	}
}
