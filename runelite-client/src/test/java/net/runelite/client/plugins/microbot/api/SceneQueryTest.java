package net.runelite.client.plugins.microbot.api;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Tile-object and ground-item cache/query semantics with mocked RuneLite interfaces; not live-game evidence. */
public class SceneQueryTest
{
	private ReadApiFixture f;

	@Before
	public void setUp() throws Exception
	{
		f = new ReadApiFixture();
	}

	@After
	public void tearDown() throws Exception
	{
		f.close();
	}

	/** Tile at scene (x, y) on {@code plane}; the player stands at scene (5, 5). */
	private Tile tile(int x, int y, int plane)
	{
		Tile tile = f.tiles[plane][x][y];
		if (tile == null)
		{
			tile = mock(Tile.class);
			when(tile.getSceneLocation()).thenReturn(new Point(x, y));
			when(tile.getWorldLocation()).thenReturn(new WorldPoint(ReadApiFixture.BASE_X + x, ReadApiFixture.BASE_Y + y, plane));
			when(tile.getGameObjects()).thenReturn(new GameObject[5]);
			when(tile.getGroundItems()).thenReturn(new ArrayList<>());
			f.tiles[plane][x][y] = tile;
		}
		return tile;
	}

	private void objectDefinition(int id, String name)
	{
		ObjectComposition composition = mock(ObjectComposition.class);
		when(composition.getName()).thenAnswer(i -> f.onClientThread(name));
		when(f.client.getObjectDefinition(id)).thenReturn(composition);
	}

	private GameObject gameObject(int id, int minX, int minY, int maxX, int maxY)
	{
		GameObject object = mock(GameObject.class);
		when(object.getId()).thenReturn(id);
		when(object.getWorldView()).thenReturn(f.topLevel);
		when(object.getSceneMinLocation()).thenReturn(new Point(minX, minY));
		when(object.getWorldLocation()).thenReturn(new WorldPoint(ReadApiFixture.BASE_X + minX + 1, ReadApiFixture.BASE_Y + minY + 1, 0));
		for (int x = minX; x <= maxX; x++)
		{
			for (int y = minY; y <= maxY; y++)
			{
				tile(x, y, 0).getGameObjects()[0] = object;
			}
		}
		return object;
	}

	private TileItem groundItem(int x, int y, int id, String name, int quantity, int ownership)
	{
		TileItem item = mock(TileItem.class);
		when(item.getId()).thenReturn(id);
		when(item.getQuantity()).thenReturn(quantity);
		when(item.getOwnership()).thenReturn(ownership);
		ItemComposition composition = mock(ItemComposition.class);
		when(composition.getName()).thenAnswer(i -> f.onClientThread(name));
		when(f.client.getItemDefinition(id)).thenReturn(composition);
		tile(x, y, 0).getGroundItems().add(item);
		return item;
	}

	@Test
	public void multiTileGameObjectIsListedOnceAtItsSceneMinTile()
	{
		objectDefinition(10, "Oak tree");
		gameObject(10, 6, 6, 7, 7);

		List<Rs2TileObjectModel> objects = f.services.getRs2TileObjectCache().query().within(10).toList();
		assertEquals(1, objects.size());
		Rs2TileObjectModel tree = objects.get(0);
		assertEquals(TileObjectType.GAME, tree.getTileObjectType());
		assertEquals(new WorldPoint(ReadApiFixture.BASE_X + 6, ReadApiFixture.BASE_Y + 6, 0), tree.getWorldLocation());
		assertEquals("Oak tree", tree.getName());
	}

	@Test
	public void namesResolveImpostorsStripColourTagsAndFilterTheNullName()
	{
		ObjectComposition base = mock(ObjectComposition.class);
		ObjectComposition impostor = mock(ObjectComposition.class);
		when(base.getImpostorIds()).thenReturn(new int[]{21});
		when(base.getImpostor()).thenAnswer(i -> f.onClientThread(impostor));
		when(impostor.getName()).thenReturn("<col=ffff00>Door</col>");
		when(f.client.getObjectDefinition(20)).thenReturn(base);
		WallObject door = mock(WallObject.class);
		when(door.getId()).thenReturn(20);
		when(door.getWorldLocation()).thenReturn(new WorldPoint(ReadApiFixture.BASE_X + 5, ReadApiFixture.BASE_Y + 8, 0));
		when(tile(5, 8, 0).getWallObject()).thenReturn(door);

		objectDefinition(30, "null");
		gameObject(30, 4, 4, 4, 4);

		Rs2TileObjectModel nearest = f.services.getRs2TileObjectCache().query()
			.where(obj -> obj.getName() != null && !obj.getName().equals("null"))
			.within(10)
			.nearest();
		assertEquals("Door", nearest.getName());
		assertEquals(TileObjectType.WALL, nearest.getTileObjectType());
		assertEquals(2, f.services.getRs2TileObjectCache().query().within(10).count());
	}

	@Test
	public void onlyTheCurrentPlaneIsScanned()
	{
		objectDefinition(10, "Ladder");
		GameObject upstairs = mock(GameObject.class);
		when(upstairs.getSceneMinLocation()).thenReturn(new Point(5, 6));
		tile(5, 6, 1).getGameObjects()[0] = upstairs;
		groundItem(5, 6, 995, "Coins", 1, TileItem.OWNERSHIP_NONE);
		when(tile(5, 7, 1).getGroundItems()).thenReturn(new ArrayList<>(List.of(mock(TileItem.class))));

		assertEquals(0, f.services.getRs2TileObjectCache().getStream().count());
		assertEquals(1, f.services.getRs2TileItemCache().getStream().count());
	}

	@Test
	public void cachesScanThePlayersFloorWhenTheWorldViewPlaneLags()
	{
		// After a stair climb the player reports floor 1 before the view's plane updates.
		f.placePlayer(new WorldPoint(ReadApiFixture.BASE_X + 5, ReadApiFixture.BASE_Y + 5, 1));
		objectDefinition(10, "Staircase");
		GameObject stairs = mock(GameObject.class);
		when(stairs.getId()).thenReturn(10);
		when(stairs.getSceneMinLocation()).thenReturn(new Point(5, 6));
		when(stairs.getWorldLocation()).thenReturn(new WorldPoint(ReadApiFixture.BASE_X + 5, ReadApiFixture.BASE_Y + 6, 1));
		tile(5, 6, 1).getGameObjects()[0] = stairs;
		TileItem coins = mock(TileItem.class);
		when(coins.getId()).thenReturn(995);
		tile(5, 7, 1).getGroundItems().add(coins);
		groundItem(5, 8, 1, "Bones", 1, TileItem.OWNERSHIP_NONE);

		assertEquals(1, f.services.getRs2TileObjectCache().query().within(5).count());
		assertEquals(1, f.services.getRs2TileItemCache().query().within(5).count());
		assertEquals(995, f.services.getRs2TileItemCache().query().first().getId());
	}

	@Test
	public void groundItemLootabilityFollowsOwnership()
	{
		groundItem(6, 5, 1, "Bones", 1, TileItem.OWNERSHIP_OTHER);
		groundItem(8, 5, 2, "Coins", 250, TileItem.OWNERSHIP_SELF);
		groundItem(9, 5, 3, "Ashes", 1, TileItem.OWNERSHIP_NONE);
		groundItem(10, 5, 4, "Feather", 5, TileItem.OWNERSHIP_GROUP);
		groundItem(5, 21, 5, "Far rune", 1, TileItem.OWNERSHIP_NONE);

		List<Rs2TileItemModel> nearby = f.services.getRs2TileItemCache().query().within(15).toList();
		assertEquals(4, nearby.size());

		Rs2TileItemModel nearestLootable = f.services.getRs2TileItemCache().query()
			.where(Rs2TileItemModel::isLootAble)
			.within(15)
			.nearest();
		assertEquals("Coins", nearestLootable.getName());
		assertEquals(250, nearestLootable.getQuantity());
		assertTrue(nearestLootable.isOwned());
		assertEquals(3, f.services.getRs2TileItemCache().query().where(Rs2TileItemModel::isLootAble).within(15).count());
	}

	@Test
	public void sceneCachesAreEmptyWhenLoggedOutOrWithoutAPlayer()
	{
		objectDefinition(10, "Oak tree");
		gameObject(10, 6, 6, 6, 6);
		groundItem(6, 5, 1, "Bones", 1, TileItem.OWNERSHIP_NONE);

		f.gameState(GameState.HOPPING);
		assertEquals(0, f.services.getRs2TileObjectCache().getStream().count());
		assertEquals(0, f.services.getRs2TileItemCache().getStream().count());

		f.gameState(GameState.LOGGED_IN);
		when(f.client.getLocalPlayer()).thenReturn(null);
		f.tick++;
		assertEquals(0, f.services.getRs2TileObjectCache().getStream().count());
		assertEquals(0, f.services.getRs2TileItemCache().getStream().count());
	}

	@Test
	public void groundItemSnapshotDoesNotSeeLaterPickupUntilNextTick()
	{
		TileItem bones = groundItem(6, 5, 1, "Bones", 1, TileItem.OWNERSHIP_NONE);
		assertEquals(1, f.services.getRs2TileItemCache().getStream().count());

		f.tiles[0][6][5].getGroundItems().remove(bones);
		assertEquals(1, f.services.getRs2TileItemCache().getStream().count());

		f.tick++;
		assertEquals(0, f.services.getRs2TileItemCache().getStream().count());
	}
}
