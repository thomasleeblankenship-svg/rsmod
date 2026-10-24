package net.runelite.client.plugins.microbot.util.grandexchange.models;

import lombok.Getter;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemComposition;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;

/**
 * An immutable copy of one Grand Exchange offer, read on the client thread (upstream Microbot
 * 2.6.38). {@link #getItemName} reads the item definition on the client thread.
 */
public class GrandExchangeOfferDetails
{
	private final int itemId;
	private final int quantitySold;
	private final int totalQuantity;
	private final long price;
	private final long spent;
	private final GrandExchangeOfferState state;
	private final boolean isSelling;
	@Getter
	private final GrandExchangeSlots slot;

	public GrandExchangeOfferDetails(int itemId, int quantitySold, int totalQuantity, long price, long spent,
		GrandExchangeOfferState state, boolean isSelling, GrandExchangeSlots slot)
	{
		this.itemId = itemId;
		this.quantitySold = quantitySold;
		this.totalQuantity = totalQuantity;
		this.price = price;
		this.spent = spent;
		this.state = state;
		this.isSelling = isSelling;
		this.slot = slot;
	}

	/** Completion from 0 to 100. */
	public int getProgressPercentage()
	{
		if (totalQuantity <= 0)
		{
			return 0;
		}
		return (int) ((quantitySold * 100.0) / totalQuantity);
	}

	/** Bought, sold or cancelled. */
	public boolean isCompleted()
	{
		return state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.SOLD
			|| state == GrandExchangeOfferState.CANCELLED_BUY
			|| state == GrandExchangeOfferState.CANCELLED_SELL;
	}

	/** Still buying or selling. */
	public boolean isInProgress()
	{
		return state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.SELLING;
	}

	/** The item's name, or {@code null} when the client thread cannot be reached. */
	public String getItemName()
	{
		return MobileActions.read(() ->
		{
			ItemComposition definition = Microbot.getClient().getItemDefinition(itemId);
			return definition == null ? null : definition.getName();
		}, null);
	}

	public int getItemId()
	{
		return itemId;
	}

	public int getQuantitySold()
	{
		return quantitySold;
	}

	public int getTotalQuantity()
	{
		return totalQuantity;
	}

	public long getPrice()
	{
		return price;
	}

	public long getSpent()
	{
		return spent;
	}

	public GrandExchangeOfferState getState()
	{
		return state;
	}

	public boolean isSelling()
	{
		return isSelling;
	}
}
