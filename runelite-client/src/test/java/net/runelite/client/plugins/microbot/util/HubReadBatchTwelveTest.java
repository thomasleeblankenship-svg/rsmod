package net.runelite.client.plugins.microbot.util;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Point;
import net.runelite.api.Varbits;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.itemcharges.ItemChargeConfig;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Gembag;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.item.Rs2ExplorersRing;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.walker.Rs2MiniMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Hub read batch 12 (util-gap plan) against upstream 2.6.38 semantics, with mocked RuneLite interfaces. */
public class HubReadBatchTwelveTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final EventBus events = new EventBus();
	private Client client;
	private ConfigManager config;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarbitValue(anyInt())).thenAnswer(i -> varbits.getOrDefault((int) i.getArgument(0), 0));
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn("Item");
		when(definition.getNote()).thenReturn(-1);
		when(definition.getInventoryActions()).thenReturn(new String[5]);
		when(client.getItemDefinition(anyInt())).thenReturn(definition);
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
		config = mock(ConfigManager.class);
		MicrobotRuntime.install(client, thread, events, config, List.of(new MicrobotReadServices(client, thread, events)));
		Rs2Gembag.clear();
	}

	@After
	public void tearDown()
	{
		Rs2Gembag.clear();
		MicrobotRuntime.uninstall();
	}

	private void inventory(int... ids)
	{
		Item[] items = new Item[ids.length];
		for (int i = 0; i < ids.length; i++)
		{
			items[i] = new Item(ids[i], 1);
		}
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		when(client.getItemContainer(InventoryID.INV)).thenReturn(container);
		when(client.getItemContainer(net.runelite.api.InventoryID.INVENTORY)).thenReturn(container);
		onClientThread.set(true);
		try
		{
			events.post(new ItemContainerChanged(InventoryID.INV, container));
		}
		finally
		{
			onClientThread.set(false);
		}
	}

	private void chat(String message)
	{
		ChatMessage event = new ChatMessage();
		event.setType(ChatMessageType.GAMEMESSAGE);
		event.setMessage(message);
		onClientThread.set(true);
		try
		{
			events.post(event);
		}
		finally
		{
			onClientThread.set(false);
		}
	}

	@Test
	public void inventoryAllFiltersLikeUpstream()
	{
		inventory(ItemID.LAWRUNE, ItemID.NATURERUNE, ItemID.LAWRUNE);
		assertEquals(2, Rs2Inventory.all(item -> item.getId() == ItemID.LAWRUNE).size());
		assertTrue(Rs2Inventory.all(item -> false).isEmpty());
	}

	@Test
	public void runePouchContainsCountsStrictlyUnlessCombinationsAreAllowed()
	{
		inventory(ItemID.BH_RUNE_POUCH);
		varbits.put(Varbits.RUNE_POUCH_RUNE1, 11);
		varbits.put(Varbits.RUNE_POUCH_AMOUNT1, 30);
		varbits.put(Varbits.RUNE_POUCH_RUNE2, 17);
		varbits.put(Varbits.RUNE_POUCH_AMOUNT2, 5);
		assertTrue(Rs2RunePouch.contains(Runes.LAW));
		assertTrue(Rs2RunePouch.contains(Runes.LAW, 30));
		assertFalse(Rs2RunePouch.contains(Runes.LAW, 31));
		assertTrue(Rs2RunePouch.contains(ItemID.LAWRUNE));
		assertFalse("dust is not air when strict", Rs2RunePouch.contains(Runes.AIR));
		assertTrue("dust stands in for air", Rs2RunePouch.contains(Runes.AIR, 5, true));
		assertFalse(Rs2RunePouch.contains(Runes.AIR, 6, true));
		assertTrue(Rs2RunePouch.contains(Map.of(Runes.LAW, 2, Runes.EARTH, 3), true));
		assertFalse(Rs2RunePouch.contains((Runes) null));
	}

	@Test
	public void clickingPointIsTheCentreAndTheDefaultRectangleIsTheCanvas()
	{
		when(client.getCanvasWidth()).thenReturn(800);
		when(client.getCanvasHeight()).thenReturn(600);
		assertEquals(new Rectangle(1, 1, 800, 600), Rs2UiHelper.getDefaultRectangle());
		assertEquals(new Point(15, 25), Rs2UiHelper.getClickingPoint(new Rectangle(10, 20, 10, 10), true));
		assertNull(Rs2UiHelper.getClickingPoint(null, true));
		assertNull("the fallback rectangle has no real target", Rs2UiHelper.getClickingPoint(Rs2UiHelper.getDefaultRectangle(), false));
		assertEquals(new Point(401, 301), Rs2UiHelper.getClickingPoint(Rs2UiHelper.getDefaultRectangle(), false, false));
	}

	@Test
	public void gemBagIsUnknownUntilCheckedThenFollowsFoundGems()
	{
		inventory(ItemID.GEM_BAG_OPEN);
		assertTrue(Rs2Gembag.hasGemBag());
		assertTrue(Rs2Gembag.isGemBagOpen());
		assertTrue(Rs2Gembag.isUnknown());
		assertEquals(0, Rs2Gembag.getTotalGemCount());
		chat("Sapphires: 3 / Emeralds: 60 / Rubies: 1 / Diamonds: 0 / Dragonstones: 2");
		assertFalse(Rs2Gembag.isUnknown());
		assertEquals(66, Rs2Gembag.getTotalGemCount());
		assertEquals(60, Rs2Gembag.getGemCount("Uncut emerald"));
		assertTrue(Rs2Gembag.isAnyGemSlotFull());
		chat("You just found a ruby!");
		assertEquals(2, Rs2Gembag.getGemCount("uncut ruby"));
		assertEquals(5, Rs2Gembag.getGemBagContents().size());
		assertEquals(2, Rs2Gembag.getGemBagContents().get(2).getQuantity());
	}

	@Test
	public void gemBagCheckMessageWithLineBreaksAndTagsIsRead()
	{
		inventory(ItemID.GEM_BAG);
		chat("<col=ef1020>Sapphires: 1</col><br>Emeralds: 2<br>Rubies: 3<br>Diamonds: 4<br>Dragonstones: 5");
		assertEquals(15, Rs2Gembag.getTotalGemCount());
		inventory(ItemID.LAWRUNE);
		assertFalse("no bag, no count", Rs2Gembag.isUnknown());
		assertEquals(0, Rs2Gembag.getTotalGemCount());
	}

	@Test
	public void explorersRingReadsInventoryAndChargesConfig()
	{
		inventory(ItemID.LUMBRIDGE_RING_HARD);
		assertTrue(Rs2ExplorersRing.hasRing());
		assertEquals(-1, Rs2ExplorersRing.getCharges());
		assertFalse(Rs2ExplorersRing.hasCharges());
		when(config.getRSProfileConfiguration(ItemChargeConfig.GROUP, ItemChargeConfig.KEY_EXPLORERS_RING, Integer.class)).thenReturn(3);
		assertTrue(Rs2ExplorersRing.hasCharges());
		inventory(ItemID.LAWRUNE);
		assertFalse(Rs2ExplorersRing.hasRing());
	}

	@Test
	public void minimapClipIsTheDrawAreaEllipse()
	{
		assertFalse(Rs2MiniMap.isPointInsideMinimap(new Point(5, 5)));
		Widget area = mock(Widget.class);
		when(area.getBounds()).thenReturn(new Rectangle(100, 100, 100, 100));
		when(client.getWidget(ComponentID.FIXED_VIEWPORT_MINIMAP_DRAW_AREA)).thenReturn(area);
		assertTrue(Rs2MiniMap.isPointInsideMinimap(new Point(150, 150)));
		assertFalse("a corner of the box is outside the circle", Rs2MiniMap.isPointInsideMinimap(new Point(102, 102)));
		assertNull(Rs2MiniMap.worldToMinimap(null));
	}
}
