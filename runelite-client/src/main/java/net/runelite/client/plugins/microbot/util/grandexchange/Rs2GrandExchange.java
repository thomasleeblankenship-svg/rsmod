package net.runelite.client.plugins.microbot.util.grandexchange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;
import net.runelite.client.plugins.microbot.util.grandexchange.models.ItemMappingData;
import net.runelite.client.plugins.microbot.util.grandexchange.models.TimeSeriesAnalysis;
import net.runelite.client.plugins.microbot.util.grandexchange.models.TimeSeriesInterval;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.apache.commons.lang3.tuple.Pair;

/**
 * The Grand Exchange (upstream Microbot 2.6.38): interface state, the eight offer slots and
 * their offers, price lookups ({@link GrandExchangePrices}), and the actions (open, close,
 * buy, sell, collect, abort). Ported for RuneMobile: the client's offer array is copied once
 * per call on the client thread (a null entry or item id 0 is an empty slot); every click is
 * one {@link MobileActions} dispatch (upstream's {@code doInvoke(NewMenuEntry)} CC_OPs map to
 * {@link MobileActions#widget} with the same child and option), retried across ticks for up to
 * two seconds; keys go through {@link Rs2Keyboard}. Not ported: bank PIN entry.
 */
@Slf4j
public final class Rs2GrandExchange
{
	/** Varp holding the new offer's price per item (a long). */
	static final int GE_NEWOFFER_PRICE_VARP = 5753;
	static final int CHATBOX_SEARCH_RESULTS_CHILD = InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS & 0xFFFF;
	static final int CHATBOX_INPUT_CHILD = InterfaceID.Chatbox.MES_TEXT2 & 0xFFFF;
	static final int COLLECT_ALL_BUTTON = 30474246;
	static final int GE_FRAME = InterfaceID.GeOffers.FRAME;
	/** Child of {@link #GE_FRAME} that closes the interface. */
	static final int CLOSE_CHILD = 11;
	/** The offer screen's back button. */
	static final int BACK_BUTTON = 30474244;
	/** Child of a slot panel carrying "View offer" and "Abort offer". */
	static final int SLOT_MENU_CHILD = 2;
	/** Child of a slot panel showing the item name. */
	static final int SLOT_ITEM_NAME_CHILD = 19;
	/** Child of the search results showing the previous search. */
	static final int PREVIOUS_SEARCH_CHILD = 2;
	/** Children of the offer container holding the custom -X% and +X% buttons. */
	static final int MINUS_X_PERCENT_CHILD = 14;
	static final int PLUS_X_PERCENT_CHILD = 15;

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
		return MobileActions.read(() ->
		{
			try
			{
				return Microbot.getClient().getVarpLongValue(GE_NEWOFFER_PRICE_VARP);
			}
			catch (IllegalArgumentException e)
			{
				// Upstream 2.6.47: some sessions store this varp as an int, which the long read rejects.
				return (long) Microbot.getClient().getVarpValue(GE_NEWOFFER_PRICE_VARP);
			}
		}, 0L);
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

	/** Closes the Grand Exchange (its frame's close button). */
	public static void closeExchange()
	{
		Microbot.status = "Closing Grand Exchange";
		if (!isOpen())
		{
			return;
		}
		dispatch(() -> MobileActions.widget(GE_FRAME, CLOSE_CHILD, ""));
		Global.sleepUntil(() -> !isOpen());
	}

	/** Back from an offer screen to the slot overview. */
	public static void backToOverview()
	{
		Microbot.status = "Back to overview";
		if (!isOpen() && !isOfferScreenOpen())
		{
			return;
		}
		dispatch(() -> MobileActions.widget(BACK_BUTTON, -1, ""));
		Global.sleepUntil(() -> !isOfferScreenOpen());
	}

	/**
	 * Opens the Grand Exchange through the nearest clerk ("Exchange"). A selected item is
	 * deselected first. RuneMobile does not enter a bank PIN: if one is asked for, this returns
	 * {@code false} once the wait for the interface runs out.
	 */
	public static boolean openExchange()
	{
		Microbot.status = "Opening Grand Exchange";
		if (Rs2Inventory.isItemSelected())
		{
			Microbot.getMouse().click();
		}
		if (isOpen())
		{
			return true;
		}
		Rs2NpcModel clerk = Rs2Npc.getNpc("Grand Exchange Clerk");
		return clerk != null && dispatch(() -> MobileActions.npcAt(clerk.getId(), clerk.getIndex(), "Exchange"))
			&& Global.sleepUntil(Rs2GrandExchange::isOpen, 5000);
	}

	/** Walks to the Grand Exchange bank. */
	public static boolean walkToGrandExchange()
	{
		return Rs2Walker.walkTo(BankLocation.GRAND_EXCHANGE.getWorldPoint());
	}

	/**
	 * Runs {@code request}: COLLECT (one slot whose item matches, else everything), BUY (search,
	 * price, percent, quantity, confirm) or SELL (offer from the inventory, then the same). Opens or
	 * walks to the Grand Exchange first, and closes it after a success when asked.
	 */
	public static boolean processOffer(GrandExchangeRequest request)
	{
		if (!isValidRequest(request) || !useGrandExchange())
		{
			return false;
		}
		boolean success = false;
		switch (request.getAction())
		{
			case COLLECT:
				success = collect(request);
				break;
			case BUY:
				success = buy(request);
				break;
			case SELL:
				success = sell(request);
				break;
			default:
				break;
		}
		if (success && request.isCloseAfterCompletion())
		{
			closeExchange();
		}
		return success;
	}

	/** A BUY request for exactly {@code itemName}. */
	public static boolean buyItem(String itemName, int price, int quantity)
	{
		return processOffer(GrandExchangeRequest.builder().action(GrandExchangeAction.BUY).itemName(itemName)
			.exact(true).price(price).quantity(quantity).build());
	}

	/** A SELL request for {@code itemName}. */
	public static boolean sellItem(String itemName, int quantity, int price)
	{
		return processOffer(GrandExchangeRequest.builder().action(GrandExchangeAction.SELL).itemName(itemName)
			.quantity(quantity).price(price).build());
	}

	/**
	 * Collects every completed offer ("Collect to bank" or "Collect to inventory"). True at once
	 * when every slot is empty; a full inventory is banked first, as upstream.
	 */
	public static boolean collectAll(boolean collectToBank)
	{
		if (isAllSlotsEmpty())
		{
			return true;
		}
		if (Rs2Inventory.isFull() && Rs2Bank.openBank())
		{
			Rs2Bank.depositAll();
		}
		if (!isOpen())
		{
			openExchange();
		}
		Global.sleepUntil(Rs2GrandExchange::isOpen);
		if (Rs2Widget.getWidget(COLLECT_ALL_BUTTON) == null)
		{
			return false;
		}
		String option = collectToBank ? "Collect to bank" : "Collect to inventory";
		return dispatch(() -> MobileActions.widget(COLLECT_ALL_BUTTON, 0, option) || MobileActions.widget(COLLECT_ALL_BUTTON, -1, option));
	}

	public static boolean collectAllToInventory()
	{
		return collectAll(false);
	}

	public static boolean collectAllToBank()
	{
		return collectAll(true);
	}

	/**
	 * As upstream (deprecated there): withdraws {@code npcName}'s tradeable loot except
	 * {@code itemsToNotSell} ({@link Rs2Bank#withdrawLootItems}) and sells it with
	 * {@link #sellInventory}; false when nothing was withdrawn.
	 */
	@Deprecated
	public static boolean sellLoot(String npcName, List<String> itemsToNotSell)
	{
		return Rs2Bank.withdrawLootItems(npcName, itemsToNotSell) && sellInventory();
	}

	/** Sells every tradeable inventory item at 5% under the guide price; true once the inventory is empty. */
	public static boolean sellInventory()
	{
		Rs2Inventory.items().filter(Rs2ItemModel::isTradeable).collect(Collectors.toList()).forEach(item ->
		{
			if (getAvailableSlot() == null && hasSoldOffer())
			{
				collectAllToBank();
				Global.sleep(600);
			}
			processOffer(GrandExchangeRequest.builder().action(GrandExchangeAction.SELL).itemName(item.getName())
				.quantity(item.getQuantity()).percent(-5).build());
		});
		return Rs2Inventory.isEmpty();
	}

	/** Aborts the offer whose item is named {@code name}, then collects everything. */
	public static boolean abortOffer(String name, boolean collectToBank)
	{
		if (!useGrandExchange())
		{
			return false;
		}
		for (GrandExchangeSlots slot : GrandExchangeSlots.values())
		{
			if (!isSlotAvailable(slot) && name != null && name.equalsIgnoreCase(slotItemName(slot)))
			{
				return abort(slot) && collectAll(collectToBank);
			}
		}
		return false;
	}

	/** Aborts every offer, collects everything, and reports whether all slots are empty. */
	public static boolean abortAllOffers(boolean collectToBank)
	{
		if (!useGrandExchange())
		{
			return false;
		}
		for (GrandExchangeSlots slot : GrandExchangeSlots.values())
		{
			if (GrandExchangeWidget.getSlot(slot) != null && !isSlotAvailable(slot))
			{
				abort(slot);
				Global.sleep(250, 750);
			}
		}
		Global.sleep(1000);
		collectAll(collectToBank);
		return isAllSlotsEmpty();
	}

	/** Views {@code slot}'s offer and collects it to the bank or the inventory. */
	public static boolean collectOffer(GrandExchangeSlots slot, boolean toBank)
	{
		if (slot == null || slotItemName(slot) == null)
		{
			log.info("No offer to collect in slot: {}", slot);
			return false;
		}
		if (!viewOffer(slot) || !Global.sleepUntil(Rs2GrandExchange::isOfferScreenOpen, 3000))
		{
			log.info("Failed to open offer screen for slot: {}", slot);
			return false;
		}
		return collectViewedOffer(toBank);
	}

	/** Collects {@code slot} and returns the quantity its offer had traded, or 0 on failure. */
	public static int collectOfferAndGetQuantity(GrandExchangeSlots slot, boolean toBank, int itemId)
	{
		GrandExchangeOfferDetails details = slot == null || itemId == 0 ? null : getOfferDetails(slot);
		if (details == null)
		{
			log.error("No offer details found for slot: {}", slot);
			return 0;
		}
		return collectOffer(slot, toBank) ? details.getQuantitySold() : 0;
	}

	/** Aborts the offers in {@code slotsToCancel} and returns their details (see {@link #getOfferDetailsForCancellation}). */
	public static List<Map<String, Object>> cancelSpecificOffers(List<GrandExchangeSlots> slotsToCancel, boolean collectToBank)
	{
		List<Map<String, Object>> cancelled = new ArrayList<>();
		if (slotsToCancel == null || slotsToCancel.isEmpty() || !useGrandExchange())
		{
			return cancelled;
		}
		for (GrandExchangeSlots slot : slotsToCancel)
		{
			Map<String, Object> details = getOfferDetailsForCancellation(slot);
			if (details != null && GrandExchangeWidget.getSlot(slot) != null && !isSlotAvailable(slot) && abort(slot))
			{
				cancelled.add(details);
				Global.sleep(250, 750);
			}
		}
		if (!cancelled.isEmpty())
		{
			Global.sleep(1000);
			collectAll(collectToBank);
		}
		return cancelled;
	}

	/** Places the remainder of a cancelled offer again (preferring {@code targetSlot}). */
	public static boolean restoreOffer(Map<String, Object> offerDetails, GrandExchangeSlots targetSlot)
	{
		if (offerDetails == null || !useGrandExchange())
		{
			return false;
		}
		int itemId = (Integer) offerDetails.get("itemId");
		int remaining = (Integer) offerDetails.get("remainingQuantity");
		int price = (Integer) offerDetails.get("price");
		boolean isBuyOffer = (Boolean) offerDetails.get("isBuyOffer");
		if (remaining <= 0)
		{
			return true;
		}
		if ((targetSlot == null || !isSlotAvailable(targetSlot)) && getAvailableSlot() == null)
		{
			log.warn("No available slots to restore offer for item {}", itemId);
			return false;
		}
		String itemName = MobileActions.read(() ->
		{
			ItemComposition definition = Microbot.getClient().getItemDefinition(itemId);
			return definition == null ? null : definition.getName();
		}, null);
		if (itemName == null || itemName.isEmpty())
		{
			return false;
		}
		return isBuyOffer ? buyItem(itemName, price, remaining) : sellItem(itemName, remaining, price);
	}

	/**
	 * Sets the open chatbox number prompt to {@code value}. RuneMobile types the digits through
	 * {@link Rs2Keyboard} (upstream writes the input varc directly); the caller presses enter.
	 */
	public static void setChatboxValue(int value)
	{
		if (Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2) != null)
		{
			Rs2Keyboard.typeString(Integer.toString(value));
		}
	}

	private static boolean collect(GrandExchangeRequest request)
	{
		String current = request.getSlot() == null ? null : slotItemName(request.getSlot());
		if (current == null)
		{
			return collectAll(request.isToBank());
		}
		boolean matches = request.isExact() ? current.equalsIgnoreCase(request.getItemName())
			: request.getItemName() != null && current.toLowerCase().contains(request.getItemName().toLowerCase());
		if (!matches || !viewOffer(request.getSlot()))
		{
			return false;
		}
		Global.sleepUntil(Rs2GrandExchange::isOfferScreenOpen);
		return collectViewedOffer(request.isToBank());
	}

	private static boolean buy(GrandExchangeRequest request)
	{
		GrandExchangeSlots slot = request.getSlot() != null ? request.getSlot() : getAvailableSlot();
		Widget buyButton = GrandExchangeWidget.getOfferBuyButton(slot);
		if (buyButton == null || !dispatch(() -> Rs2Widget.clickWidget(buyButton)))
		{
			return false;
		}
		Global.sleepUntil(GrandExchangeWidget::isOfferTextVisible);
		Rs2Widget.sleepUntilHasWidgetText("Start typing the name of an item to search for it",
			InterfaceID.CHATBOX, CHATBOX_SEARCH_RESULTS_CHILD, false, 5000);
		boolean chosen = isPreviousSearchMatch(request.getItemName()) ? clickPreviousSearch() : searchItemName(request);
		if (!chosen)
		{
			return false;
		}
		Global.sleepUntil(() -> GrandExchangeWidget.getPricePerItemButton_X() != null);
		setPrice(request.getPrice());
		adjustPriceByPercent(request.getPercent());
		return setQuantity(request.getQuantity()) && confirm() && Global.sleepUntil(() -> !isOfferSetupOpen());
	}

	private static boolean sell(GrandExchangeRequest request)
	{
		if (!Rs2Inventory.hasItem(request.getItemName(), request.isExact()) || getAvailableSlots().length == 0
			|| !Rs2Inventory.interact(request.getItemName(), "Offer", request.isExact()))
		{
			return false;
		}
		Global.sleepUntil(GrandExchangeWidget::isOfferTextVisible);
		if (request.getPrice() > 0)
		{
			setPrice(request.getPrice());
		}
		adjustPriceByPercent(request.getPercent());
		return setQuantity(request.getQuantity()) && confirm() && Global.sleepUntil(() -> !isOfferSetupOpen());
	}

	/** Upstream's validation: BUY needs a name and a quantity; SELL also a price or a percent. */
	static boolean isValidRequest(GrandExchangeRequest request)
	{
		if (request == null || request.getAction() == null)
		{
			return false;
		}
		boolean named = request.getItemName() != null && !request.getItemName().isBlank() && request.getQuantity() > 0;
		switch (request.getAction())
		{
			case BUY:
				return named;
			case SELL:
				return named && (request.getPrice() > 0 || request.getPercent() != 0);
			case COLLECT:
				return true;
			default:
				return false;
		}
	}

	/** Types the name (cut to the 25 characters the search holds) and picks the matching result. */
	private static boolean searchItemName(GrandExchangeRequest request)
	{
		String name = request.getItemName();
		String shown = name.length() > 25 ? name.substring(0, 25) : name;
		Rs2Keyboard.typeString(name);
		if (!Rs2Widget.sleepUntilHasWidgetText(shown, InterfaceID.CHATBOX, CHATBOX_INPUT_CHILD, false, 5000))
		{
			return false;
		}
		Global.sleepUntil(() -> getSearchResultWidget(name, request.isExact()) != null, 2200);
		Pair<Widget, Integer> result = getSearchResultWidget(name, request.isExact());
		if (result == null)
		{
			return false;
		}
		int parent = MobileActions.read(() -> result.getLeft().getId(), -1);
		return parent != -1 && dispatch(() -> MobileActions.widget(parent, result.getRight(), ""));
	}

	private static boolean isPreviousSearchMatch(String itemName)
	{
		return MobileActions.read(() ->
		{
			Widget previous = previousSearch(Microbot.getClient());
			return previous != null && previous.getText() != null && previous.getText().equalsIgnoreCase(itemName);
		}, false);
	}

	private static boolean clickPreviousSearch()
	{
		return dispatch(() -> MobileActions.widget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS, PREVIOUS_SEARCH_CHILD, ""));
	}

	private static Widget previousSearch(Client client)
	{
		Widget results = client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS);
		return results == null ? null : results.getChild(PREVIOUS_SEARCH_CHILD);
	}

	/** "View offer" on {@code slot}'s panel. */
	private static boolean viewOffer(GrandExchangeSlots slot)
	{
		int panel = slotPanelId(slot);
		return panel != -1 && dispatch(() -> MobileActions.widget(panel, SLOT_MENU_CHILD, "View offer"));
	}

	/** "Abort offer" on {@code slot}'s panel. */
	private static boolean abort(GrandExchangeSlots slot)
	{
		int panel = slotPanelId(slot);
		return panel != -1 && dispatch(() -> MobileActions.widget(panel, SLOT_MENU_CHILD, "Abort offer"));
	}

	/** Collects the viewed offer: "Bank", else "Collect", "Collect-items" (one item) or "Collect-notes". */
	private static boolean collectViewedOffer(boolean toBank)
	{
		if (!isOfferScreenOpen())
		{
			log.info("Grand Exchange offer screen is not open, cannot collect offer.");
			return false;
		}
		Widget[] buttons = GrandExchangeWidget.getCollectButtons();
		if (buttons.length == 0)
		{
			log.info("No collect buttons found on the offer screen.");
			return false;
		}
		for (Widget button : buttons)
		{
			int[] target = MobileActions.read(() ->
			{
				String[] actions = button.getActions();
				long named = actions == null ? 0 : java.util.Arrays.stream(actions).filter(java.util.Objects::nonNull).count();
				return new int[]{button.getId(), button.getIndex(), (int) named, button.getItemQuantity()};
			}, null);
			if (target != null && target[2] > 0)
			{
				String option = collectOption(toBank, target[2], target[3]);
				dispatch(() -> MobileActions.widget(target[0], target[1], option));
				Global.sleep(250, 750);
			}
		}
		return Global.sleepUntil(() -> !isOfferScreenOpen());
	}

	/** Upstream's choice of collect option for a button with {@code actionCount} named actions. */
	static String collectOption(boolean toBank, int actionCount, int itemQuantity)
	{
		if (toBank)
		{
			return "Bank";
		}
		if (actionCount > 3)
		{
			return itemQuantity == 1 ? "Collect-items" : "Collect-notes";
		}
		return "Collect";
	}

	/** Uses the +/-5% buttons for multiples of five, else sets and applies the custom +/-X% button. */
	private static void adjustPriceByPercent(int percent)
	{
		if (percent == 0)
		{
			return;
		}
		boolean increase = percent > 0;
		int absPercent = Math.abs(percent);
		long basePrice = getOfferPrice();
		if (absPercent % 5 == 0)
		{
			Widget button = increase ? GrandExchangeWidget.getPricePerItemButton_Plus5Percent() : GrandExchangeWidget.getPricePerItemButton_Minus5Percent();
			if (button == null)
			{
				Microbot.log("Unable to find +-5% button widget.");
				return;
			}
			for (int i = 0; i < absPercent / 5; i++)
			{
				long before = getOfferPrice();
				dispatch(() -> Rs2Widget.clickWidget(button));
				Global.sleepUntil(() -> GrandExchangeWidget.hasOfferPriceChanged(before), 1600);
			}
			return;
		}
		int customChild = increase ? PLUS_X_PERCENT_CHILD : MINUS_X_PERCENT_CHILD;
		Widget button = increase ? GrandExchangeWidget.getPricePerItemButton_PlusXPercent() : GrandExchangeWidget.getPricePerItemButton_MinusXPercent();
		if (button == null)
		{
			Microbot.log("Unable to find +-X% button widget.");
			return;
		}
		int current = extractNumber(MobileActions.read(button::getText, null));
		if (current != absPercent)
		{
			int container = MobileActions.read(button::getId, -1);
			if (current == -1)
			{
				dispatch(() -> Rs2Widget.clickWidget(button));
			}
			else
			{
				dispatch(() -> MobileActions.widget(container, customChild, "Customise"));
			}
			Global.sleepUntil(() -> Rs2Widget.hasWidget("Set a percentage to decrease/increase"), 2000);
			Rs2Keyboard.typeString(Integer.toString(absPercent));
			Rs2Keyboard.enter();
			Global.sleepUntil(() ->
			{
				Widget updated = increase ? GrandExchangeWidget.getPricePerItemButton_PlusXPercent() : GrandExchangeWidget.getPricePerItemButton_MinusXPercent();
				return updated != null && extractNumber(MobileActions.read(updated::getText, null)) != current;
			}, 2000);
		}
		dispatch(() -> Rs2Widget.clickWidget(button));
		Global.sleepUntil(() -> GrandExchangeWidget.hasOfferPriceChanged(basePrice), 2000);
	}

	/**
	 * Clicks confirm, then answers "Yes" to the game's "Your offer is much higher/lower" price
	 * check (the script chose that price), as upstream.
	 */
	private static boolean confirm()
	{
		Widget confirm = GrandExchangeWidget.getConfirm();
		if (confirm == null || !dispatch(() -> Rs2Widget.clickWidget(confirm)))
		{
			return false;
		}
		Global.sleepUntil(() -> Rs2Widget.hasWidget("Your offer is much"), 2000);
		if (Rs2Widget.hasWidget("Your offer is much"))
		{
			dispatch(() -> Rs2Widget.clickWidget("Yes"));
		}
		return true;
	}

	/** Sets the offer quantity through the "X" prompt (up to three tries); closes the exchange on failure. */
	private static boolean setQuantity(int quantity)
	{
		boolean success = retryQuantity(quantity, Rs2GrandExchange::getOfferQuantity, () ->
		{
			Widget button = Global.sleepUntil(() -> GrandExchangeWidget.getQuantityButton_X() != null, 2000)
				? GrandExchangeWidget.getQuantityButton_X() : null;
			if (button == null)
			{
				log.warn("Quantity button not found");
				return;
			}
			enterNumber(button, quantity);
		});
		if (!success)
		{
			log.error("Failed to set quantity after 3 tries, breaking out to avoid infinite loop.");
			closeExchange();
		}
		return success;
	}

	/** Runs {@code attempt} until the quantity matches, at most three times. */
	static boolean retryQuantity(int quantity, IntSupplier currentQuantity, Runnable attempt)
	{
		for (int tries = 0; quantity != currentQuantity.getAsInt() && tries < 3; tries++)
		{
			attempt.run();
		}
		return quantity == currentQuantity.getAsInt();
	}

	/** Sets the price per item through the "X" prompt when it differs. */
	private static void setPrice(int price)
	{
		if (price == getOfferPrice())
		{
			return;
		}
		Widget button = GrandExchangeWidget.getPricePerItemButton_X();
		if (button != null)
		{
			enterNumber(button, price);
		}
	}

	private static void enterNumber(Widget button, int value)
	{
		dispatch(() -> Rs2Widget.clickWidget(button));
		Global.sleepUntil(() -> Rs2Widget.getWidget(InterfaceID.Chatbox.MES_TEXT2) != null);
		Global.sleep(600, 1000);
		setChatboxValue(value);
		Global.sleep(500, 750);
		Rs2Keyboard.enter();
		Global.sleep(1000);
	}

	private static boolean useGrandExchange()
	{
		return isOpen() || openExchange() || walkToGrandExchange();
	}

	/** The item name shown on {@code slot}'s panel, or {@code null}. */
	private static String slotItemName(GrandExchangeSlots slot)
	{
		return MobileActions.read(() ->
		{
			Widget panel = Microbot.getClient().getWidget(InterfaceID.GE_OFFERS, GrandExchangeWidget.FIRST_SLOT_CHILD + slot.ordinal());
			Widget name = panel == null ? null : panel.getChild(SLOT_ITEM_NAME_CHILD);
			return name == null ? null : name.getText();
		}, null);
	}

	private static int slotPanelId(GrandExchangeSlots slot)
	{
		Widget panel = GrandExchangeWidget.getSlot(slot);
		return panel == null ? -1 : MobileActions.read(panel::getId, -1);
	}

	/** One dispatch, retried across game ticks for up to two seconds (one action per tick). */
	private static boolean dispatch(BooleanSupplier action)
	{
		return Global.sleepUntil(action, 2000);
	}

	/** The first number in {@code text}, or -1 (upstream {@code Rs2UiHelper.extractNumber}). */
	static int extractNumber(String text)
	{
		return Rs2UiHelper.extractNumber(text);
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
