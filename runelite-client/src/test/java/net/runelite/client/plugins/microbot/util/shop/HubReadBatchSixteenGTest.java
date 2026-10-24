package net.runelite.client.plugins.microbot.util.shop;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.shop.models.Rs2ShopItem;
import net.runelite.client.plugins.microbot.util.shop.models.Rs2ShopSource;
import net.runelite.client.plugins.microbot.util.shop.models.Rs2ShopType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Read batch 16g (upstream 2.6.38 store table and shop models). */
public class HubReadBatchSixteenGTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final EventBus events = new EventBus();
	private Client client;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			onClientThread.set(true);
			try
			{
				((Runnable) call.getArgument(0)).run();
			}
			finally
			{
				onClientThread.set(false);
			}
			return null;
		}).when(thread).invoke(any(Runnable.class));
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class), List.of(new MicrobotReadServices(client, thread, events)));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	@Test
	public void storeTableLookups()
	{
		StoreLocations lumbridge = StoreLocations.LUMBRIDGE_GENERAL_STORE;
		assertEquals("Lumbridge General Store", lumbridge.getName());
		assertEquals("Shop keeper", lumbridge.getNpcName());
		assertEquals(new WorldPoint(3212, 3247, 0), lumbridge.getLocation());
		assertEquals(0.40, lumbridge.getBuyRate(), 1e-9);
		assertEquals(Rs2ShopType.GENERAL_STORE, lumbridge.getShopType());
		assertTrue(lumbridge.hasItem(ItemID.BUCKET_EMPTY));
		assertTrue(lumbridge.hasAllItems(ItemID.BUCKET_EMPTY, ItemID.POT_EMPTY));
		assertFalse(lumbridge.hasAllItems(ItemID.BUCKET_EMPTY, ItemID.ABYSSAL_WHIP));
		assertTrue(lumbridge.hasAnyItem(ItemID.ABYSSAL_WHIP, ItemID.POT_EMPTY));
		assertEquals("Lumbridge General Store", lumbridge.toString());

		assertNull("no player", StoreLocations.getNearestStore());
		Player player = mock(Player.class);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3214, 3250, 0));
		when(client.getLocalPlayer()).thenReturn(player);
		assertEquals(lumbridge, StoreLocations.getNearestStore());
		assertEquals(lumbridge, StoreLocations.getNearestStoreWithItem(ItemID.BUCKET_EMPTY));
		assertEquals(lumbridge, StoreLocations.getNearestStoreWithAllItems(ItemID.BUCKET_EMPTY, ItemID.POT_EMPTY));
		assertNull(StoreLocations.getNearestStoreWithAnyItem(-5));
		assertTrue(StoreLocations.getHighestBuyRateStoreWithItem(ItemID.BUCKET_EMPTY).hasItem(ItemID.BUCKET_EMPTY));
	}

	@Test
	public void shopTypeAndSourceReads()
	{
		assertEquals("Grand Exchange", Rs2ShopType.GRAND_EXCHANGE.getName());
		assertEquals(0, Rs2ShopType.GRAND_EXCHANGE.getStockCheckInterval());
		assertEquals(5000, Rs2ShopType.SLAYER_SHOP.getStockCheckInterval());
		assertEquals(3000, Rs2ShopType.GENERAL_STORE.getStockCheckInterval());
		assertFalse(Rs2ShopType.GRAND_EXCHANGE.supportsWorldHopping());

		Rs2ShopSource source = new Rs2ShopSource("Shop keeper", new WorldArea(3200, 3200, 6, 4, 0), Rs2ShopType.GENERAL_STORE, 1.3, 0.4, 0.03, false);
		assertEquals(new WorldPoint(3203, 3202, 0), source.getLocation());
		assertTrue(source.getQuests().isEmpty());
		Rs2ShopSource ge = Rs2ShopSource.createGEShopSource(0.9, 1.1);
		assertEquals(Rs2ShopType.GRAND_EXCHANGE, ge.getShopType());
		assertEquals(10, ge.getLocationArea().getWidth());
	}

	@Test
	public void shopItemPricingAndNoteIds() throws Exception
	{
		ItemManager items = mock(ItemManager.class);
		ItemComposition bucket = mock(ItemComposition.class);
		when(bucket.getId()).thenReturn(ItemID.BUCKET_EMPTY);
		when(bucket.getName()).thenReturn("Bucket");
		when(bucket.getPrice()).thenReturn(10);
		when(bucket.getLinkedNoteId()).thenReturn(ItemID.BUCKET_EMPTY + 1);
		when(items.getItemComposition(ItemID.BUCKET_EMPTY)).thenAnswer(i ->
		{
			assertTrue("read on the client thread", onClientThread.get());
			return bucket;
		});
		java.lang.reflect.Field field = Microbot.class.getDeclaredField("itemManager");
		field.setAccessible(true);
		Object previous = field.get(null);
		field.set(null, items);
		try
		{
			Rs2ShopItem item = new Rs2ShopItem(ItemID.BUCKET_EMPTY, "Shop keeper", new WorldArea(3200, 3200, 6, 6, 0), Rs2ShopType.GENERAL_STORE,
				1.3, 0.4, 0.03, Map.of(), false, "", Duration.ofSeconds(30), 30);
			assertEquals("Bucket", item.getItemName());
			assertEquals(10, item.itemStoreValue());
			assertEquals("ceil(1.3 x 10)", 13.0, item.getInitialPriceSellAt(), 1e-9);
			assertEquals("floor(0.4 x 10)", 4.0, item.getInitialPriceBuyAt(), 1e-9);
			// Upstream's integer arithmetic: (130 - 3 x stock) / 100 is 0 at stock 30 and 1 at stock 10.
			assertEquals(0, item.getCostForBuyingX(2, 30));
			assertEquals(8, item.getCostForBuyingX(2, 10));
			assertEquals("no stock, no cost", 0, item.getCost(0));
			assertEquals("40 x base each", 1040, item.getReturnForSellingX(2, 0));
			assertTrue(item.isNoteable());
			assertEquals("not stackable: the linked note", ItemID.BUCKET_EMPTY + 1, item.getNoteId());
			assertEquals(ItemID.BUCKET_EMPTY, item.getUnNotedId());
		}
		finally
		{
			field.set(null, previous);
		}
	}
}
