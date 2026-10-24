package net.runelite.client.plugins.microbot.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.GameObject;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Untradeable and value-based looting over the real tile-item cache; pickups confirmed by the inventory. */
public class GroundItemLootTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final int COINS = 995;
	private static final int BONES = 526;
	private static final int PAGE = 11341;

	private ReadApiFixture f;
	private final AtomicReference<Item[]> inventory = new AtomicReference<>(new Item[0]);
	private final ItemManager prices = mock(ItemManager.class);

	@Before
	public void setUp() throws Exception
	{
		f = new ReadApiFixture();
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenAnswer(call -> inventory.get());
		when(f.client.getItemContainer(net.runelite.api.InventoryID.INVENTORY)).thenReturn(container);
	}

	@After
	public void tearDown() throws Exception
	{
		f.close();
	}

	private Tile tile(int x, int y)
	{
		Tile tile = f.tiles[0][x][y];
		if (tile == null)
		{
			tile = mock(Tile.class);
			when(tile.getSceneLocation()).thenReturn(new Point(x, y));
			when(tile.getWorldLocation()).thenReturn(new WorldPoint(ReadApiFixture.BASE_X + x, ReadApiFixture.BASE_Y + y, 0));
			when(tile.getGameObjects()).thenReturn(new GameObject[5]);
			when(tile.getGroundItems()).thenReturn(new ArrayList<>());
			f.tiles[0][x][y] = tile;
		}
		return tile;
	}

	private void ground(int x, int y, int id, String name, int quantity, boolean tradeable)
	{
		TileItem item = mock(TileItem.class);
		when(item.getId()).thenReturn(id);
		when(item.getQuantity()).thenReturn(quantity);
		when(item.getOwnership()).thenReturn(TileItem.OWNERSHIP_NONE);
		ItemComposition composition = mock(ItemComposition.class);
		when(composition.getName()).thenAnswer(i -> f.onClientThread(name));
		when(composition.isTradeable()).thenAnswer(i -> f.onClientThread(tradeable));
		when(f.client.getItemDefinition(id)).thenReturn(composition);
		tile(x, y).getGroundItems().add(item);
	}

	/** Pickups land in the inventory; returns the ids that were taken. */
	private List<Integer> pickups(MockedStatic<MobileActions> actions)
	{
		List<Integer> taken = new ArrayList<>();
		actions.when(MobileActions::active).thenReturn(true);
		actions.when(() -> MobileActions.groundItem(anyInt(), any(), anyString())).thenAnswer(call ->
		{
			int id = call.getArgument(0);
			taken.add(id);
			inventory.set(new Item[]{new Item(id, 1)});
			return true;
		});
		return taken;
	}

	@Test
	public void untradeablesAreLootedButNotCoinsOrTradeables()
	{
		ground(6, 5, COINS, "Coins", 100, true);
		ground(7, 5, BONES, "Bones", 1, true);
		ground(8, 5, PAGE, "Ancient page", 1, false);
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			List<Integer> taken = pickups(actions);
			assertTrue(Rs2GroundItem.lootUntradables(new LootingParameters(10, 1, 1, 1, false, false, "untradeable")));
			assertEquals(List.of(PAGE), taken);
		}
	}

	@Test
	public void lootAtGePriceTakesTheFirstStackWorthAtLeastThePrice()
	{
		ground(6, 5, BONES, "Bones", 1, true);
		ground(7, 5, PAGE, "Ancient page", 2, false);
		when(prices.getItemPrice(BONES)).thenReturn(100L);
		when(prices.getItemPrice(PAGE)).thenReturn(300L);
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS);
			MockedStatic<Microbot> microbot = mockStatic(Microbot.class, CALLS_REAL_METHODS))
		{
			microbot.when(Microbot::getItemManager).thenReturn(prices);
			List<Integer> taken = pickups(actions);
			assertTrue("2 x 300 is worth 500", Rs2GroundItem.lootAtGePrice(500));
			assertEquals(List.of(PAGE), taken);
			assertFalse("nothing is worth 1000", Rs2GroundItem.lootAtGePrice(1000));
		}
	}

	@Test
	public void valueLootingNeedsTheItemManager()
	{
		ground(6, 5, PAGE, "Ancient page", 1, false);
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS);
			MockedStatic<Microbot> microbot = mockStatic(Microbot.class, CALLS_REAL_METHODS))
		{
			microbot.when(Microbot::getItemManager).thenReturn(null);
			List<Integer> taken = pickups(actions);
			assertFalse(Rs2GroundItem.lootItemBasedOnValue(1, 14));
			assertTrue(taken.isEmpty());
		}
	}
}
