package net.runelite.client.plugins.microbot.util.grandexchange;

import lombok.Builder;
import lombok.Getter;

/**
 * One Grand Exchange request for {@code Rs2GrandExchange.processOffer}: the action, the item,
 * price, quantity and the follow-up flags (upstream Microbot 2.6.38, unchanged).
 */
@Getter
@Builder
public class GrandExchangeRequest
{
	/** The slot to use, or {@code null} for the first free one (or every slot, for a collect). */
	private final GrandExchangeSlots slot;
	/** What to do. */
	private final GrandExchangeAction action;
	/** The item's name. */
	private final String itemName;
	/** Whether {@link #itemName} must match exactly (ignoring case). */
	private final boolean exact;
	/** How many to buy or sell. */
	private final int quantity;
	/** Price per item; 0 keeps the guide price. */
	private final int price;
	/** Optional price adjustment in percent (for example 5, -5, 22). */
	private final int percent;
	/** Close the Grand Exchange once the request succeeds. */
	private final boolean closeAfterCompletion;
	/** Collect to the bank instead of the inventory. */
	private final boolean toBank;
}
