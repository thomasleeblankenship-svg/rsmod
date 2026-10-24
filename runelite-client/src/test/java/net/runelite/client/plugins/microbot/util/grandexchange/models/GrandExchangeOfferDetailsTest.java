package net.runelite.client.plugins.microbot.util.grandexchange.models;

import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Upstream's {@code GrandExchangeOfferDetailsTest}, plus the state helpers. */
public class GrandExchangeOfferDetailsTest
{
	@Test
	public void preservesPricesAndProceedsAboveIntegerRange()
	{
		long price = (long) Integer.MAX_VALUE + 1;
		long spent = price * 3;
		GrandExchangeOfferDetails details = new GrandExchangeOfferDetails(995, 3, 4, price, spent, GrandExchangeOfferState.BUYING, false, null);
		assertEquals(price, details.getPrice());
		assertEquals(spent, details.getSpent());
		assertEquals(75, details.getProgressPercentage());
		assertTrue(details.isInProgress());
		assertFalse(details.isCompleted());
	}

	@Test
	public void preservesZeroPriceAndProceeds()
	{
		GrandExchangeOfferDetails details = new GrandExchangeOfferDetails(995, 0, 0, 0L, 0L, GrandExchangeOfferState.EMPTY, false, null);
		assertEquals(0L, details.getPrice());
		assertEquals(0L, details.getSpent());
		assertEquals(0, details.getProgressPercentage());
		assertTrue(new GrandExchangeOfferDetails(1, 1, 2, 1L, 1L, GrandExchangeOfferState.CANCELLED_SELL, true, null).isCompleted());
	}
}
