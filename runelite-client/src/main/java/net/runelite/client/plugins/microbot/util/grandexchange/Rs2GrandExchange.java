package net.runelite.client.plugins.microbot.util.grandexchange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;
import net.runelite.client.plugins.microbot.util.grandexchange.models.ItemMappingData;
import net.runelite.client.plugins.microbot.util.grandexchange.models.TimeSeriesAnalysis;
import net.runelite.client.plugins.microbot.util.grandexchange.models.TimeSeriesInterval;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.apache.commons.lang3.tuple.Pair;

/**
 * The Grand Exchange (upstream Microbot 2.6.38): interface state, the eight offer slots and
 * their offers, and price lookups ({@link GrandExchangePrices}). Ported for RuneMobile: the client's offer array is copied once per call on the
 * client thread (a null entry or item id 0 is an empty slot), so no offer is dereferenced off it.
 */
@Slf4j
public final class Rs2GrandExchange
{
	/** Varp holding the new offer's price per item (a long). */
	static final int GE_NEWOFFER_PRICE_VARP = 5753;
	static final int CHATBOX_SEARCH_RESULTS_CHILD = InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS & 0xFFFF;
	static final int CHATBOX_INPUT_CHILD = InterfaceID.Chatbox.MES_TEXT2 & 0xFFFF;

	private Rs2GrandExchange()
	{
	}

	/** Whether the Grand Exchange overview is open. */
	public static boolean isOpen()
	{
		return Rs2Widget.isWidgetVisible(InterfaceID.GE_OFFERS, 1);
	}

	/** Whether a buy or sell offer's details screen is open. */
	public static boolean isOfferScreenOpen()
	{
		return Rs2Widget.isWidgetVisible(InterfaceID.GE_OFFERS, 15);
	}

	static boolean isOfferSetupOpen()
	{
		return Rs2Widget.isWidgetVisible(InterfaceID.GeOffers.SETUP);
	}

	/**
	 * Whether {@code slot} can take a new offer: inside the account's slot range and empty. True
	 * while the client has no offer array yet, as upstream.
	 */
	public static boolean isSlotAvailable(GrandExchangeSlots slot)
	{
		if (slot == null || slot.ordinal() >= getMaxSlots())
		{
			return false;
		}
		GrandExchangeOfferDetails[] offers = offers();
		if (offers == null)
		{
			return true;
		}
		return slot.ordinal() < offers.length && offers[slot.ordinal()] == null;
	}

	/** The slots whose panel is shown and which hold an offer. */
	public static GrandExchangeSlots[] getActiveOfferSlots()
	{
		List<GrandExchangeSlots> active = new ArrayList<>();
		for (GrandExchangeSlots slot : GrandExchangeSlots.values())
		{
			if (GrandExchangeWidget.getSlot(slot) != null && !isSlotAvailable(slot))
			{
				active.add(slot);
			}
		}
		return active.toArray(new GrandExchangeSlots[0]);
	}

	/** Whether every slot the account can use is empty. */
	public static boolean isAllSlotsEmpty()
	{
		return getAvailableSlots().length == getMaxSlots();
	}

	public static boolean hasBoughtOffer()
	{
		return anyInState(GrandExchangeOfferState.BOUGHT);
	}

	/** Some offer is bought and none is still buying. */
	public static boolean hasFinishedBuyingOffers()
	{
		return anyInState(GrandExchangeOfferState.BOUGHT) && !anyInState(GrandExchangeOfferState.BUYING);
	}

	public static boolean hasSoldOffer()
	{
		return anyInState(GrandExchangeOfferState.SOLD);
	}

	/** Some offer is sold and none is still selling. */
	public static boolean hasFinishedSellingOffers()
	{
		return anyInState(GrandExchangeOfferState.SOLD) && !anyInState(GrandExchangeOfferState.SELLING);
	}

	/** The slot's index, 0 to 7. */
	public static int getSlotIndex(GrandExchangeSlots slot)
	{
		return slot.ordinal();
	}

	/** Whether {@code slot} holds a completed buy offer. */
	public static boolean hasBoughtOffer(GrandExchangeSlots slot)
	{
		GrandExchangeOfferDetails offer = getOfferDetails(slot);
		return offer != null && offer.getState() == GrandExchangeOfferState.BOUGHT;
	}

	/** Whether {@code slot} holds a completed sell offer. */
	public static boolean hasSoldOffer(GrandExchangeSlots slot)
	{
		GrandExchangeOfferDetails offer = getOfferDetails(slot);
		return offer != null && offer.getState() == GrandExchangeOfferState.SOLD;
	}

	/** The slot holding a sell (or buy) offer for {@code itemId}, or {@code null}. */
	public static GrandExchangeSlots findSlotForItem(int itemId, boolean isSelling)
	{
		GrandExchangeOfferDetails[] offers = offers();
		for (int i = 0; offers != null && i < offers.length; i++)
		{
			GrandExchangeOfferDetails offer = offers[i];
			if (offer != null && offer.isSelling() == isSelling && offer.getItemId() == itemId)
			{
				return GrandExchangeSlots.values()[i];
			}
		}
		return null;
	}

	/** As {@link #findSlotForItem(int, boolean)}, with the id of the first item search match for {@code itemName}. */
	public static GrandExchangeSlots findSlotForItem(String itemName, boolean isSelling)
	{
		int itemId = itemIdByName(itemName);
		return itemId == -1 ? null : findSlotForItem(itemId, isSelling);
	}

	/** A copy of the offer in {@code slot}, or {@code null} when it is empty. */
	public static GrandExchangeOfferDetails getOfferDetails(GrandExchangeSlots slot)
	{
		GrandExchangeOfferDetails[] offers = slot == null ? null : offers();
		return offers == null || slot.ordinal() >= offers.length ? null : offers[slot.ordinal()];
	}

	/** The slot with index {@code index}, or {@code null} when out of range. */
	public static GrandExchangeSlots getSlotFromIndex(int index)
	{
		return index < 0 || index >= GrandExchangeSlots.values().length ? null : GrandExchangeSlots.values()[index];
	}

	/** The new offer's quantity. */
	static int getOfferQuantity()
	{
		return Microbot.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY);
	}

	/** The new offer's price per item, 0 when unreadable. */
	static long getOfferPrice()
	{
		return MobileActions.read(() -> Microbot.getClient().getVarpLongValue(GE_NEWOFFER_PRICE_VARP), 0L);
	}

	/** The first free slot, or {@code null}. */
	public static GrandExchangeSlots getAvailableSlot()
	{
		GrandExchangeSlots[] slots = getAvailableSlots();
		return slots.length == 0 ? null : slots[0];
	}

	/** Every free slot within the account's slot range. */
	public static GrandExchangeSlots[] getAvailableSlots()
	{
		List<GrandExchangeSlots> free = new ArrayList<>();
		GrandExchangeSlots[] all = GrandExchangeSlots.values();
		for (int i = 0; i < getMaxSlots() && i < all.length; i++)
		{
			if (isSlotAvailable(all[i]))
			{
				free.add(all[i]);
			}
		}
		return free.toArray(new GrandExchangeSlots[0]);
	}

	public static int getAvailableSlotsCount()
	{
		return getAvailableSlots().length;
	}

	/** Items bought so far by the buy offer in {@code slot}; 0 for a sell offer or an empty slot. */
	public static int getItemsBoughtFromOffer(GrandExchangeSlots slot)
	{
		GrandExchangeOfferDetails offer = getOfferDetails(slot);
		return offer == null || offer.isSelling() ? 0 : offer.getQuantitySold();
	}

	/** Items sold so far by the sell offer in {@code slot}; 0 for a buy offer or an empty slot. */
	public static int getItemsSoldFromOffer(GrandExchangeSlots slot)
	{
		GrandExchangeOfferDetails offer = getOfferDetails(slot);
		return offer != null && offer.isSelling() ? offer.getQuantitySold() : 0;
	}

	/** Slots with something to collect: bought, sold, or cancelled after a partial trade. */
	public static Map<GrandExchangeSlots, GrandExchangeOfferDetails> getCompletedOffers()
	{
		Map<GrandExchangeSlots, GrandExchangeOfferDetails> completed = new HashMap<>();
		GrandExchangeOfferDetails[] offers = offers();
		for (int i = 0; offers != null && i < offers.length; i++)
		{
			GrandExchangeOfferDetails offer = offers[i];
			if (offer != null && (offer.getState() == GrandExchangeOfferState.BOUGHT
				|| offer.getState() == GrandExchangeOfferState.SOLD || isCancelledWithItems(offer)))
			{
				completed.put(GrandExchangeSlots.values()[i], offer);
			}
		}
		return completed;
	}

	/** Whether the offer in {@code slot} was cancelled after trading some items. */
	public static boolean isCancelledOfferWithItems(GrandExchangeSlots slot)
	{
		GrandExchangeOfferDetails offer = getOfferDetails(slot);
		return offer != null && isCancelledWithItems(offer);
	}

	/**
	 * The offer in {@code slot} as upstream's map (itemId, totalQuantity, quantitySold, price,
	 * spent, state, slot, remainingQuantity, isBuyOffer), or {@code null} when empty.
	 */
	public static Map<String, Object> getOfferDetailsForCancellation(GrandExchangeSlots slot)
	{
		GrandExchangeOfferDetails offer = getOfferDetails(slot);
		if (offer == null)
		{
			return null;
		}
		Map<String, Object> details = new HashMap<>();
		details.put("itemId", offer.getItemId());
		details.put("totalQuantity", offer.getTotalQuantity());
		details.put("quantitySold", offer.getQuantitySold());
		details.put("price", (int) offer.getPrice());
		details.put("spent", (int) offer.getSpent());
		details.put("state", offer.getState());
		details.put("slot", slot);
		details.put("remainingQuantity", offer.getTotalQuantity() - offer.getQuantitySold());
		details.put("isBuyOffer", !offer.isSelling());
		return details;
	}

	/** Slots holding an offer, least progressed first (the best ones to cancel). */
	public static List<GrandExchangeSlots> getActiveOfferSlotsByProgress()
	{
		List<Pair<GrandExchangeSlots, Double>> progress = new ArrayList<>();
		GrandExchangeOfferDetails[] offers = offers();
		GrandExchangeSlots[] all = GrandExchangeSlots.values();
		for (int i = 0; offers != null && i < offers.length && i < all.length; i++)
		{
			GrandExchangeOfferDetails offer = offers[i];
			if (offer != null && i < getMaxSlots())
			{
				double done = offer.getTotalQuantity() > 0 ? (double) offer.getQuantitySold() / offer.getTotalQuantity() : 0.0;
				progress.add(Pair.of(all[i], done));
			}
		}
		progress.sort(Comparator.comparingDouble(Pair::getRight));
		List<GrandExchangeSlots> slots = new ArrayList<>();
		progress.forEach(p -> slots.add(p.getLeft()));
		return slots;
	}

	/** The buying or bought offer for {@code itemId}, or {@code null}. */
	public static GrandExchangeOfferDetails hasBuyOffer(int itemId)
	{
		return offerFor(itemId, GrandExchangeOfferState.BUYING, GrandExchangeOfferState.BOUGHT);
	}

	/** The selling or sold offer for {@code itemId}, or {@code null}. */
	public static GrandExchangeOfferDetails hasSellOffer(int itemId)
	{
		return offerFor(itemId, GrandExchangeOfferState.SELLING, GrandExchangeOfferState.SOLD);
	}

	/** Whether any offer is bought or sold. */
	public static boolean hasOffer()
	{
		return hasSoldOffer() || hasBoughtOffer();
	}

	/**
	 * The item search result for {@code search}: the clickable widget just before the first
	 * result whose text matches, with its index, or {@code null}.
	 */
	public static Pair<Widget, Integer> getSearchResultWidget(String search, boolean exact)
	{
		return MobileActions.read(() ->
		{
			Widget parent = Microbot.getClient().getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS);
			Widget[] children = parent == null ? null : parent.getChildren();
			if (children == null || search == null)
			{
				return null;
			}
			String wanted = search.toLowerCase();
			for (int i = 1; i < children.length; i++)
			{
				String text = children[i] == null ? null : Rs2UiHelper.stripColTags(children[i].getText());
				if (text != null && (exact ? text.equalsIgnoreCase(search) : text.toLowerCase().contains(wanted)))
				{
					return Pair.of(children[i - 1], i - 1);
				}
			}
			return null;
		}, null);
	}

	/** GE Tracker's buying price for {@code itemId}, or -1. */
	public static int getOfferPrice(int itemId)
	{
		return GrandExchangePrices.geTracker(itemId, "buying");
	}

	/** GE Tracker's overall price for {@code itemId}, or -1. */
	public static int getPrice(int itemId)
	{
		return GrandExchangePrices.geTracker(itemId, "overall");
	}

	/** GE Tracker's selling price for {@code itemId}, or -1. */
	public static int getSellPrice(int itemId)
	{
		return GrandExchangePrices.geTracker(itemId, "selling");
	}

	/** GE Tracker's buying volume for {@code itemId}, or -1. */
	public static int getBuyingVolume(int itemId)
	{
		return GrandExchangePrices.geTracker(itemId, "buyingQuantity");
	}

	/** GE Tracker's selling volume for {@code itemId}, or -1. */
	public static int getSellingVolume(int itemId)
	{
		return GrandExchangePrices.geTracker(itemId, "sellingQuantity");
	}

	/** Real-time prices (OSRS Wiki, then GE Tracker), cached for a minute; {@code null} when unavailable. */
	public static WikiPrice getRealTimePrices(int itemId)
	{
		return GrandExchangePrices.getRealTimePrices(itemId);
	}

	/** A buy price that rises with each retry (see upstream); -1 when no price is available. */
	public static int getAdaptiveBuyPrice(int itemId, double basePercentage, int retryAttempt)
	{
		return GrandExchangePrices.getAdaptiveBuyPrice(itemId, basePercentage, retryAttempt);
	}

	/** A sell price that falls with each retry (see upstream); -1 when no price is available. */
	public static int getAdaptiveSellPrice(int itemId, double basePercentage, int retryAttempt)
	{
		return GrandExchangePrices.getAdaptiveSellPrice(itemId, basePercentage, retryAttempt);
	}

	/** OSRS Wiki price history from {@code fromTimestamp} (or now); empty on failure. */
	public static TimeSeriesAnalysis getTimeSeriesData(int itemId, TimeSeriesInterval interval, Long fromTimestamp)
	{
		return GrandExchangePrices.getTimeSeriesData(itemId, interval, fromTimestamp);
	}

	public static TimeSeriesAnalysis getTimeSeriesData(int itemId, TimeSeriesInterval interval)
	{
		return getTimeSeriesData(itemId, interval, null);
	}

	/** The average price over {@code interval}'s history, or -1. */
	public static int getAveragePrice(int itemId, TimeSeriesInterval interval)
	{
		TimeSeriesAnalysis analysis = getTimeSeriesData(itemId, interval);
		return analysis.averagePrice > 0 ? analysis.averagePrice : -1;
	}

	/** The average price over the last hour, or -1. */
	public static int getAveragePrice(int itemId)
	{
		return getAveragePrice(itemId, TimeSeriesInterval.ONE_HOUR);
	}

	/** OSRS Wiki item mapping (buy limit, alch values), cached for the session; {@code null} when unavailable. */
	public static ItemMappingData getItemMappingData(int itemId)
	{
		return GrandExchangePrices.getItemMappingData(itemId);
	}

	private static int getMaxSlots()
	{
		return Rs2Player.isMember() ? 8 : 3;
	}

	private static boolean isCancelledWithItems(GrandExchangeOfferDetails offer)
	{
		return (offer.getState() == GrandExchangeOfferState.CANCELLED_BUY || offer.getState() == GrandExchangeOfferState.CANCELLED_SELL)
			&& offer.getQuantitySold() > 0;
	}

	private static boolean anyInState(GrandExchangeOfferState state)
	{
		GrandExchangeOfferDetails[] offers = offers();
		for (int i = 0; offers != null && i < offers.length; i++)
		{
			if (offers[i] != null && offers[i].getState() == state)
			{
				return true;
			}
		}
		return false;
	}

	private static GrandExchangeOfferDetails offerFor(int itemId, GrandExchangeOfferState first, GrandExchangeOfferState second)
	{
		GrandExchangeOfferDetails[] offers = offers();
		for (int i = 0; offers != null && i < offers.length; i++)
		{
			GrandExchangeOfferDetails offer = offers[i];
			if (offer != null && offer.getItemId() == itemId && (offer.getState() == first || offer.getState() == second))
			{
				return offer;
			}
		}
		return null;
	}

	/** The first item search match's id for {@code itemName}, or -1. */
	static int itemIdByName(String itemName)
	{
		ItemManager items = Microbot.getItemManager();
		if (items == null || itemName == null)
		{
			return -1;
		}
		return items.search(itemName).stream().findFirst().map(p -> p.getId()).orElse(-1);
	}

	/**
	 * One client-thread copy of the client's offers, indexed by slot: {@code null} entries for
	 * empty slots, or a {@code null} array when the client has none (or is unreachable).
	 */
	static GrandExchangeOfferDetails[] offers()
	{
		return MobileActions.read(() -> copy(Microbot.getClient()), null);
	}

	private static GrandExchangeOfferDetails[] copy(Client client)
	{
		GrandExchangeOffer[] live = client.getGrandExchangeOffers();
		if (live == null)
		{
			return null;
		}
		GrandExchangeSlots[] all = GrandExchangeSlots.values();
		GrandExchangeOfferDetails[] copied = new GrandExchangeOfferDetails[Math.min(live.length, all.length)];
		for (int i = 0; i < copied.length; i++)
		{
			GrandExchangeOffer offer = live[i];
			GrandExchangeOfferState state = offer == null ? null : offer.getState();
			if (offer == null || offer.getItemId() == 0 || state == null || state == GrandExchangeOfferState.EMPTY)
			{
				continue;
			}
			boolean selling = state == GrandExchangeOfferState.SELLING || state == GrandExchangeOfferState.SOLD
				|| state == GrandExchangeOfferState.CANCELLED_SELL;
			copied[i] = new GrandExchangeOfferDetails(offer.getItemId(), offer.getQuantitySold(), offer.getTotalQuantity(),
				offer.getPrice(), offer.getSpent(), state, selling, all[i]);
		}
		return copied;
	}
}
