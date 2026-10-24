package net.runelite.client.plugins.microbot.util;

import java.awt.Rectangle;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.item.Rs2ItemManager;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.http.api.item.ItemPrice;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Read batch 16e (tile, dialogue, UI helper, deposit box, rune pouch, shop and item manager reads) against upstream 2.6.38 semantics. */
public class HubReadBatchSixteenETest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final int BASE_X = 3200;
	private static final int BASE_Y = 3200;

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final EventBus events = new EventBus();
	private Client client;
	private WorldView view;
	private Scene scene;
	private int[][] flags;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		// LOGIN_SCREEN keeps the varbit cache from holding values between assertions.
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
		flags = new int[104][104];
		CollisionData collision = mock(CollisionData.class);
		when(collision.getFlags()).thenReturn(flags);
		CollisionData[] maps = {collision, collision, collision, collision};
		view = mock(WorldView.class);
		scene = mock(Scene.class);
		when(view.getScene()).thenReturn(scene);
		when(view.getCollisionMaps()).thenReturn(maps);
		when(view.getBaseX()).thenReturn(BASE_X);
		when(view.getBaseY()).thenReturn(BASE_Y);
		when(view.getSizeX()).thenReturn(104);
		when(view.getSizeY()).thenReturn(104);
		when(view.isTopLevel()).thenReturn(true);
		when(client.getTopLevelWorldView()).thenReturn(view);
		when(client.getCollisionMaps()).thenReturn(maps);
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	@Test
	public void tileWalkabilityAndEdgeReads()
	{
		WorldPoint centre = new WorldPoint(BASE_X + 50, BASE_Y + 50, 0);
		flags[51][50] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		List<WorldPoint> around = Rs2Tile.getWalkableTilesAroundTile(centre, 1);
		assertEquals("eight neighbours less one full tile", 7, around.size());
		assertFalse(around.contains(centre));
		assertFalse(around.contains(centre.dx(1)));
		assertTrue("other plane", Rs2Tile.getWalkableTilesAroundTile(new WorldPoint(BASE_X + 50, BASE_Y + 50, 1), 1).isEmpty());

		assertTrue(Rs2Tile.isEdgePassable(centre, centre.dy(1)));
		assertFalse("into a full tile", Rs2Tile.isEdgePassable(centre, centre.dx(1)));
		assertFalse("not adjacent", Rs2Tile.isEdgePassable(centre, centre.dy(2)));
		assertTrue(Rs2Tile.isEdgePassable(centre, centre));
		when(scene.isInstance()).thenReturn(true);
		assertFalse("instance", Rs2Tile.isEdgePassable(centre, centre.dy(1)));

		Tile tile = mock(Tile.class);
		when(tile.getSceneLocation()).thenReturn(new Point(52, 50));
		assertTrue(Rs2Tile.isValidTile(tile));
		flags[52][50] = CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION;
		assertFalse(Rs2Tile.isValidTile(tile));
		assertFalse(Rs2Tile.isValidTile(null));
		assertEquals(Rs2Tile.getReachableTilesFromTile(centre, 2, true), Rs2Tile.getReachableTilesFromTileIgnoreCollision(centre, 2));
	}

	@Test
	public void tileObjectReachabilityFromThePlayer()
	{
		Player player = mock(Player.class);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(BASE_X + 10, BASE_Y + 10, 0));
		when(player.getWorldView()).thenReturn(view);
		net.runelite.api.coords.LocalPoint playerLocal = net.runelite.api.coords.LocalPoint.fromScene(10, 10, view);
		net.runelite.api.coords.LocalPoint chestLocal = net.runelite.api.coords.LocalPoint.fromScene(20, 20, view);
		when(player.getLocalLocation()).thenReturn(playerLocal);
		when(client.getLocalPlayer()).thenReturn(player);

		net.runelite.api.GameObject chest = mock(net.runelite.api.GameObject.class);
		when(chest.getPlane()).thenReturn(0);
		when(chest.getWorldView()).thenReturn(view);
		when(chest.getWorldLocation()).thenReturn(new WorldPoint(BASE_X + 20, BASE_Y + 20, 0));
		when(chest.getLocalLocation()).thenReturn(chestLocal);
		when(chest.getSceneMinLocation()).thenReturn(new Point(20, 20));
		when(chest.getSceneMaxLocation()).thenReturn(new Point(21, 21));
		for (int x = 20; x <= 21; x++)
		{
			for (int y = 20; y <= 21; y++)
			{
				flags[x][y] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
			}
		}
		assertTrue(Rs2Tile.isTileObjectReachable(chest));
		assertTrue(Rs2Tile.isGameObjectInteractableFromReachableTile(chest));

		// Wall the player into a 3x3 pen far from the chest.
		for (int i = 8; i <= 12; i++)
		{
			flags[i][8] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
			flags[i][12] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
			flags[8][i] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
			flags[12][i] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		}
		assertFalse(Rs2Tile.isTileObjectReachable(chest));
		assertFalse(Rs2Tile.isGameObjectInteractableFromReachableTile(chest));
		when(chest.getPlane()).thenReturn(1);
		assertFalse("other plane", Rs2Tile.isTileObjectReachable(chest));
		assertFalse(Rs2Tile.isTileObjectReachable(null));
		assertFalse("empty scene has no booth", Rs2Tile.isBankBooth(new WorldPoint(BASE_X, BASE_Y, 0)));
	}

	@Test
	public void dialogueOptionAndSpriteReads()
	{
		assertTrue(Rs2Dialogue.getDialogueOptions().isEmpty());
		Widget menu = mock(Widget.class);
		Widget title = mock(Widget.class);
		Widget yes = mock(Widget.class);
		Widget blank = mock(Widget.class);
		when(title.getText()).thenReturn("Select an option");
		when(yes.getText()).thenReturn("Yes");
		when(blank.getText()).thenReturn(" ");
		when(menu.getDynamicChildren()).thenReturn(new Widget[]{title, yes, blank, null});
		when(client.getWidget(net.runelite.api.widgets.InterfaceID.DIALOG_OPTION, 1)).thenReturn(menu);
		when(client.getWidget(net.runelite.api.widgets.ComponentID.DIALOG_OPTION_OPTIONS)).thenReturn(menu);
		assertEquals(List.of(yes), Rs2Dialogue.getDialogueOptions());

		assertTrue(Rs2Dialogue.getCombinationOptions().isEmpty());
		Widget root = mock(Widget.class);
		Widget list = mock(Widget.class);
		Widget bow = mock(Widget.class);
		Widget label = mock(Widget.class);
		when(bow.getActions()).thenReturn(new String[]{"Make"});
		when(bow.getName()).thenReturn("<col=ff9040>Maple longbow (u)</col>");
		when(label.getActions()).thenReturn(null);
		when(list.getStaticChildren()).thenReturn(new Widget[]{label, bow});
		when(client.getWidget(270, 1)).thenReturn(root);
		when(client.getWidget(270, 13)).thenReturn(root);
		when(client.getWidget(270, 14)).thenReturn(list);
		assertEquals(List.of(bow), Rs2Dialogue.getCombinationOptions());
		assertSame(bow, Rs2Dialogue.getCombinationOption("longbow"));
		assertSame(bow, Rs2Dialogue.getCombinationOption("maple LONGBOW (u)", true));
		assertNull(Rs2Dialogue.getCombinationOption("longbow", true));

		assertNull(Rs2Dialogue.getDialogueSprite());
		Widget sprite = mock(Widget.class);
		Widget picture = mock(Widget.class);
		when(client.getWidget(InterfaceID.OBJECTBOX, 0)).thenReturn(sprite);
		when(client.getWidget(InterfaceID.OBJECTBOX, 1)).thenReturn(picture);
		assertSame(picture, Rs2Dialogue.getDialogueSprite());
	}

	@Test
	public void uiHelperReads()
	{
		assertTrue(Rs2UiHelper.isFallbackRectangle(null));
		assertTrue(Rs2UiHelper.isFallbackRectangle(new Rectangle(1, 1, 5, 5)));
		assertTrue(Rs2UiHelper.isFallbackRectangle(new Rectangle(0, 0, 5, 5)));
		assertFalse(Rs2UiHelper.isFallbackRectangle(new Rectangle(4, 4, 5, 5)));
		assertTrue(Rs2UiHelper.isMouseWithinRectangle(new Rectangle(1, 1, 5, 5)));
		assertEquals(0, Rs2UiHelper.compareVersions("1.9.7", "1.9.7.0"));
		assertEquals(-1, Rs2UiHelper.compareVersions("1.9.7", "1.9.10"));
		assertEquals(1, Rs2UiHelper.compareVersions("2.0.0-beta", "1.99"));
		assertEquals(-1, Rs2UiHelper.compareVersions(null, "1"));
		assertEquals(7, Rs2UiHelper.parseVersionPart("7rc1"));
		assertEquals(0, Rs2UiHelper.parseVersionPart("rc"));
		Actor actor = mock(Actor.class);
		assertTrue(Rs2UiHelper.hasActor(new NewMenuEntry(0, 0, 0, 0, -1, "Goblin", actor)));
		assertFalse(Rs2UiHelper.isGameObject(new NewMenuEntry(0, 0, 0, 0, -1, "Goblin", actor)));
		when(client.getCanvasWidth()).thenReturn(800);
		when(client.getCanvasHeight()).thenReturn(600);
		assertEquals("no tile", Rs2UiHelper.getDefaultRectangle(), Rs2UiHelper.getTileClickbox(null));
	}

	@Test
	public void depositBoxPouchAndShopReads()
	{
		assertNull(Rs2DepositBox.getDepositBoxWidget());
		assertNull(Rs2DepositBox.getItems());
		Widget root = mock(Widget.class);
		Widget container = mock(Widget.class);
		Widget first = mock(Widget.class);
		Widget second = mock(Widget.class);
		when(container.getDynamicChildren()).thenReturn(new Widget[]{first, second});
		when(client.getWidget(192, 0)).thenReturn(root);
		when(client.getWidget(192, 24)).thenReturn(container);
		assertSame(root, Rs2DepositBox.getDepositBoxWidget());
		assertEquals(List.of(first, second), Rs2DepositBox.getItems());
		assertSame(second, Rs2DepositBox.getItemWidget(1));
		assertNull(Rs2DepositBox.getItemWidget(2));

		assertTrue("no pouch", Rs2RunePouch.isEmpty());

		assertEquals(-1, Rs2Shop.getSlot("Bucket"));
		Widget stock = mock(Widget.class);
		Widget pot = mock(Widget.class);
		Widget bucket = mock(Widget.class);
		when(pot.getItemId()).thenReturn(1931);
		when(bucket.getItemId()).thenReturn(1925);
		when(stock.getDynamicChildren()).thenReturn(new Widget[]{pot, bucket});
		ItemComposition potDefinition = mock(ItemComposition.class);
		ItemComposition bucketDefinition = mock(ItemComposition.class);
		when(potDefinition.getName()).thenReturn("Pot");
		when(bucketDefinition.getName()).thenReturn("Bucket");
		when(client.getItemDefinition(1931)).thenReturn(potDefinition);
		when(client.getItemDefinition(1925)).thenReturn(bucketDefinition);
		when(client.getWidget(InterfaceID.Shopmain.ITEMS)).thenReturn(stock);
		assertEquals(1, Rs2Shop.getSlot("bucket"));
	}

	@Test
	public void itemManagerReads() throws Exception
	{
		Rs2ItemManager manager = new Rs2ItemManager();
		assertNull("no item manager", manager.getItemComposition(1511));
		assertEquals(-1, manager.getPrice(1511));
		assertEquals(-1, Rs2ItemManager.getItemIdByName(" ", true));

		ItemManager items = mock(ItemManager.class);
		ItemComposition logs = mock(ItemComposition.class);
		when(logs.getPrice()).thenReturn(4);
		when(items.getItemComposition(1511)).thenAnswer(i ->
		{
			assertTrue("read on the client thread", onClientThread.get());
			return logs;
		});
		ItemPrice oak = new ItemPrice();
		oak.setId(1521);
		oak.setName("Oak logs");
		ItemPrice plain = new ItemPrice();
		plain.setId(1511);
		plain.setName("Logs");
		when(items.search(anyString())).thenReturn(List.of(oak, plain));
		java.lang.reflect.Field field = Microbot.class.getDeclaredField("itemManager");
		field.setAccessible(true);
		Object previous = field.get(null);
		field.set(null, items);
		try
		{
			assertSame(logs, manager.getItemComposition(1511));
			assertEquals(4, manager.getPrice(1511));
			assertEquals("first search result", 1521, manager.getItemId("logs"));
			assertEquals(2, manager.searchItem("logs").size());
			assertTrue(manager.searchItem("").isEmpty());
			assertEquals("exact name preferred", 1511, Rs2ItemManager.getItemIdByName("logs", false));
			assertEquals("no exact match without fuzzy", -1, Rs2ItemManager.getItemIdByName("log", false));
		}
		finally
		{
			field.set(null, previous);
		}
	}
}
