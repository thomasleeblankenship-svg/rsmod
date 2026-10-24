package net.runelite.client.plugins.microbot.util;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.huntkit.Rs2HuntKit;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2LogBasket;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.skills.fletching.Rs2Fletching;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Batch AP: log basket reads, hunt kit widgets, quick prayer flip, small reads. */
public class UtilActionBatchAPTest
{
	@Test
	public void basketReadsFollowUpstreamThresholds()
	{
		try (MockedStatic<Rs2LogBasket> basket = mockStatic(Rs2LogBasket.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class))
		{
			basket.when(Rs2LogBasket::hasLogBasket).thenReturn(true);
			basket.when(Rs2LogBasket::checkBasketContents).thenReturn(new Rs2LogBasket.BasketContents(10, "Yew logs"));
			inventory.when(() -> Rs2Inventory.count(1515)).thenReturn(20);
			inventory.when(Rs2Inventory::emptySlotCount).thenReturn(2);
			inventory.when(() -> Rs2Inventory.hasItem(ItemID.LOG_BASKET_OPEN)).thenReturn(false);
			assertTrue(Rs2LogBasket.shouldFillBasket(1515));
			assertTrue("few free slots also fills", Rs2LogBasket.shouldFillBasket(1515, 30));
			assertTrue(Rs2LogBasket.isInventoryNearFull(1515));
			assertTrue(Rs2LogBasket.hasLogs());
			assertFalse(Rs2LogBasket.isBasketFull());
			assertEquals(53, Rs2LogBasket.getEffectiveLogCapacity());
			assertEquals(ItemID.LOG_BASKET_CLOSED, Rs2LogBasket.getLogBasketId());
			assertEquals(" | Basket: Available (20 logs)", Rs2LogBasket.getLogBasketStatus(1515));
			basket.when(Rs2LogBasket::checkBasketContents).thenReturn(new Rs2LogBasket.BasketContents(28, "Yew logs"));
			assertFalse(Rs2LogBasket.shouldFillBasket(1515));
			assertTrue(Rs2LogBasket.isBasketFull());
		}
	}

	@Test
	public void quickPrayerFlipNeedsSelectedPrayers()
	{
		try (MockedStatic<Rs2Prayer> prayer = mockStatic(Rs2Prayer.class, CALLS_REAL_METHODS);
			MockedStatic<Microbot> microbot = mockStatic(Microbot.class))
		{
			prayer.when(Rs2Prayer::isOutOfPrayer).thenReturn(false);
			prayer.when(Rs2Prayer::isQuickPrayerEnabled).thenReturn(false);
			prayer.when(() -> Rs2Prayer.toggleQuickPrayer(anyBoolean())).thenReturn(true);
			microbot.when(() -> Microbot.getVarbitValue(anyInt())).thenReturn(0);
			prayer.clearInvocations();
			Rs2Prayer.toggleQuickPrayer();
			prayer.verify(() -> Rs2Prayer.toggleQuickPrayer(anyBoolean()), never());
			microbot.when(() -> Microbot.getVarbitValue(anyInt())).thenReturn(4);
			Rs2Prayer.toggleQuickPrayer();
			prayer.verify(() -> Rs2Prayer.toggleQuickPrayer(true));
		}
	}

	@Test
	public void smallReadsWorkWithoutAClient()
	{
		assertEquals(28, Rs2Inventory.size());
		assertTrue(Rs2HuntKit.getItemWidgets().isEmpty());
		assertNull(Rs2HuntKit.getItemWidget(40));
		assertNull(Rs2HuntKit.itemBounds(null));
		assertEquals("Fletching: Idle", Rs2Fletching.getFletchingStatus());
	}
}
