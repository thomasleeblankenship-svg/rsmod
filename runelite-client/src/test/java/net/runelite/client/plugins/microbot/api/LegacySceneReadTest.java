package net.runelite.client.plugins.microbot.api;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Hub read batch 6: upstream's legacy {@code Rs2GameObject} scene reads, {@code Rs2Npc.getNpc(s)}
 * and small player/dialogue reads, with mocked RuneLite interfaces; live reads must happen on the
 * client thread.
 */
public class LegacySceneReadTest
{
	static
	{
		// Rs2Walker reaches MobileActions, which reaches JavaBridge; set the flag before it loads.
		System.setProperty("runelite.bridge", "true");
	}

	private ReadApiFixture f;
	private int[][] flags;

	@Before
	public void setUp() throws Exception
	{
		f = new ReadApiFixture();
		when(f.topLevel.getSizeX()).thenReturn(32);
		when(f.topLevel.getSizeY()).thenReturn(32);
		when(f.localPlayer.getLocalLocation()).thenReturn(local(5, 5));
		flags = new int[32][32];
		CollisionData collision = mock(CollisionData.class);
		when(collision.getFlags()).thenAnswer(i -> flags);
		when(f.topLevel.getCollisionMaps()).thenReturn(new CollisionData[]{collision, null, null, null});
		when(f.topLevel.isInstance()).thenReturn(false);
	}

	@After
	public void tearDown() throws Exception
	{
		f.close();
	}

	private static LocalPoint local(int sceneX, int sceneY)
	{
		return new LocalPoint((sceneX << 7) + 64, (sceneY << 7) + 64, ReadApiFixture.TOP_LEVEL_ID);
	}

	private static WorldPoint world(int sceneX, int sceneY)
	{
		return new WorldPoint(ReadApiFixture.BASE_X + sceneX, ReadApiFixture.BASE_Y + sceneY, 0);
	}

	private Tile tile(int x, int y)
	{
		Tile tile = f.tiles[0][x][y];
		if (tile == null)
		{
			tile = mock(Tile.class);
			when(tile.getSceneLocation()).thenReturn(new Point(x, y));
			when(tile.getLocalLocation()).thenReturn(local(x, y));
			when(tile.getWorldLocation()).thenReturn(world(x, y));
			when(tile.getGameObjects()).thenReturn(new GameObject[5]);
			when(tile.getGroundItems()).thenReturn(new ArrayList<TileItem>());
			f.tiles[0][x][y] = tile;
		}
		return tile;
	}

	private void definition(int id, String name, String... actions)
	{
		ObjectComposition composition = mock(ObjectComposition.class);
		when(composition.getName()).thenAnswer(i -> f.onClientThread(name));
		when(composition.getActions()).thenAnswer(i -> f.onClientThread(actions));
		when(f.client.getObjectDefinition(id)).thenReturn(composition);
	}

	private GameObject gameObject(int id, int x, int y)
	{
		GameObject object = mock(GameObject.class);
		when(object.getId()).thenReturn(id);
		when(object.getSceneMinLocation()).thenAnswer(i -> f.onClientThread(new Point(x, y)));
		when(object.getLocalLocation()).thenAnswer(i -> f.onClientThread(local(x, y)));
		when(object.getWorldLocation()).thenAnswer(i -> f.onClientThread(world(x, y)));
		when(object.getWorldView()).thenReturn(f.topLevel);
		when(object.sizeX()).thenReturn(1);
		when(object.sizeY()).thenReturn(1);
		tile(x, y).getGameObjects()[0] = object;
		return object;
	}

	private void wallIn(int x, int y)
	{
		for (int ox = -1; ox <= 1; ox++)
		{
			for (int oy = -1; oy <= 1; oy++)
			{
				if (ox != 0 || oy != 0)
				{
					flags[x + ox][y + oy] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
				}
			}
		}
	}

	private <T> T onClientThread(java.util.concurrent.Callable<T> read)
	{
		try
		{
			return f.clientExecutor.submit(read).get();
		}
		catch (Exception e)
		{
			throw new AssertionError(e);
		}
	}

	private static List<Integer> ids(List<? extends TileObject> objects)
	{
		return objects.stream().map(o -> (int) o.getId()).collect(Collectors.toList());
	}

	@Test
	public void gameObjectsAreScannedOnTheClientThreadNearestFirst()
	{
		definition(1, "Zqxv statue");
		gameObject(1, 9, 5);
		gameObject(2, 6, 5);
		gameObject(3, 5, 30);
		GroundObject rug = mock(GroundObject.class);
		when(rug.getId()).thenReturn(4);
		when(rug.getLocalLocation()).thenAnswer(i -> f.onClientThread(local(5, 6)));
		when(rug.getWorldLocation()).thenAnswer(i -> f.onClientThread(world(5, 6)));
		when(tile(5, 6).getGroundObject()).thenReturn(rug);

		List<GameObject> nearby = Rs2GameObject.getGameObjects(10);
		assertEquals(List.of(2, 1), onClientThread(() -> ids(nearby)));
		assertEquals("default radius is half the scene", 3, Rs2GameObject.getGameObjects().size());
		assertEquals(List.of(1), onClientThread(() -> ids(Rs2GameObject.getGameObjects(o -> o.getId() == 1))));
		assertEquals("distance 0: the four neighbours only", List.of(2),
			onClientThread(() -> ids(Rs2GameObject.getGameObjects(o -> true, world(5, 5), 0))));

		assertTrue(Rs2GameObject.exists(4));
		assertFalse(Rs2GameObject.exists(5));
		assertEquals(4, onClientThread(() -> Rs2GameObject.findObjectByLocation(world(5, 6)).getId()).intValue());
		assertEquals(List.of(2, 1, 3, 4), onClientThread(() -> ids(Rs2GameObject.getAll())));
		assertEquals(1, Rs2GameObject.getGroundObjects().size());

		assertEquals("Zqxv statue", Rs2GameObject.getCompositionName(nearby.get(1)).orElse(null));
		assertTrue(Rs2GameObject.nameMatches("zqxv").test(nearby.get(1)));
		assertTrue(Rs2GameObject.nameMatches("zqxv statue", true).test(nearby.get(1)));
		assertFalse(Rs2GameObject.nameMatches("zqxv", true).test(nearby.get(1)));
	}

	@Test
	public void nameMatchingIsLimitedToTheObjectIdConstantsThatContainTheName()
	{
		assertTrue(Rs2GameObject.getObjectIdsByName("oaktree").contains(net.runelite.api.gameval.ObjectID.OAKTREE));
		definition(1, "Oak tree");
		GameObject notAnOak = gameObject(1, 6, 5);
		assertFalse("id 1 is not an oak constant", Rs2GameObject.nameMatches("oak").test(notAnOak));
	}

	@Test
	public void compositionResolvesTheImpostorUnlessIgnored()
	{
		ObjectComposition patch = mock(ObjectComposition.class);
		ObjectComposition grown = mock(ObjectComposition.class);
		when(patch.getImpostorIds()).thenReturn(new int[]{1, 2});
		when(patch.getImpostor()).thenReturn(grown);
		when(grown.getActions()).thenReturn(new String[]{"Chop down", null});
		when(f.client.getObjectDefinition(8550)).thenReturn(patch);

		assertSame(grown, Rs2GameObject.convertToObjectComposition(8550));
		assertSame(patch, Rs2GameObject.convertToObjectComposition(8550, true));
		assertTrue(Rs2GameObject.hasAction(grown, "chop", false));
		assertFalse(Rs2GameObject.hasAction(grown, "chop", true));
	}

	@Test
	public void findReachableObjectSkipsAWalledInMatch()
	{
		definition(10, "Zqxv tree", "Chop down");
		definition(11, "Zqxv tree", "Inspect");
		gameObject(10, 8, 5);
		wallIn(8, 5);
		gameObject(10, 12, 5);
		gameObject(11, 6, 5);

		GameObject tree = Rs2GameObject.findReachableObject("zqxv tree", true, 20, world(5, 5), true, "Chop down");
		assertNotNull(tree);
		assertEquals(world(12, 5), onClientThread(tree::getWorldLocation));
		assertEquals("without the action check the nearest reachable match wins", world(6, 5),
			onClientThread(Rs2GameObject.findReachableObject("zqxv tree", true, 20, world(5, 5))::getWorldLocation));
		assertNull(Rs2GameObject.findReachableObject("zqxv tree", true, 2, world(12, 12), true, "Chop down"));
	}

	@Test
	public void findBankIsTheNearestBankIdOfferingBankOrCollect()
	{
		int booth = net.runelite.client.plugins.microbot.util.gameobject.Rs2BankID.bankIds[0];
		int chest = net.runelite.client.plugins.microbot.util.gameobject.Rs2BankID.bankIds[1];
		definition(booth, "Bank booth", "Bank", "Collect");
		definition(chest, "Closed booth", "Examine");
		definition(1, "Zqxv statue", "Bank");
		gameObject(chest, 6, 5);
		gameObject(1, 7, 5);
		gameObject(booth, 9, 5);

		GameObject bank = Rs2GameObject.findBank();
		assertNotNull(bank);
		assertEquals("not the closed booth or the statue", booth, bank.getId());
		assertNull("outside the radius", Rs2GameObject.findBank(3));
	}

	@Test
	public void npcsAreFoundByNameOrIdNearestFirst()
	{
		NPC far = f.npc(1, "Tool leprechaun", world(9, 5));
		NPC near = f.npc(2, "Guildmaster Jane", world(6, 5));
		when(far.getLocalLocation()).thenAnswer(i -> f.onClientThread(local(9, 5)));
		when(near.getLocalLocation()).thenAnswer(i -> f.onClientThread(local(6, 5)));

		assertEquals("Tool leprechaun", Rs2Npc.getNpc("leprechaun").getName());
		assertNull(Rs2Npc.getNpc("leprechaun", true));
		assertEquals("Guildmaster Jane", Rs2Npc.getNpc(2).getName());
		assertEquals("Guildmaster Jane", Rs2Npc.getNpcs().findFirst().get().getName());
		assertFalse(Rs2Npc.getNpc(2, List.of(near.getIndex())).isPresent());
		assertNull(Rs2Npc.getNpc(""));
	}

	@Test
	public void theBankerIsTheNearestNpcOfferingBank()
	{
		NPC guard = f.npc(1, "Guard", world(6, 5));
		NPC banker = f.npc(2, "Banker", world(9, 5));
		when(guard.getLocalLocation()).thenAnswer(i -> f.onClientThread(local(6, 5)));
		when(banker.getLocalLocation()).thenAnswer(i -> f.onClientThread(local(9, 5)));
		net.runelite.api.NPCComposition guardDefinition = mock(net.runelite.api.NPCComposition.class);
		when(guardDefinition.getActions()).thenReturn(new String[]{"Attack", null});
		net.runelite.api.NPCComposition bankerDefinition = mock(net.runelite.api.NPCComposition.class);
		when(bankerDefinition.getActions()).thenReturn(new String[]{"Talk-to", "Bank", "Collect"});
		when(guard.getComposition()).thenAnswer(i -> f.onClientThread(guardDefinition));
		when(banker.getComposition()).thenAnswer(i -> f.onClientThread(bankerDefinition));
		assertEquals("Banker", Rs2Npc.getBankerNPC().getName());
		doAnswer(i -> f.onClientThread(guardDefinition)).when(banker).getComposition();
		assertNull(Rs2Npc.getBankerNPC());
	}

	@Test
	public void wildernessAttackRangeNeedsBothSidesInRange()
	{
		WorldPoint level10 = new WorldPoint(3100, 3600, 0);
		f.placePlayer(level10);
		when(f.localPlayer.getCombatLevel()).thenAnswer(i -> f.onClientThread(70));
		Player inRange = f.player(1, "Pker", new WorldPoint(3100, 3600, 0));
		when(inRange.getCombatLevel()).thenAnswer(i -> f.onClientThread(78));
		Player tooHigh = f.player(2, "Maxed", new WorldPoint(3100, 3600, 0));
		when(tooHigh.getCombatLevel()).thenAnswer(i -> f.onClientThread(110));
		Player outside = f.player(3, "Banker", new WorldPoint(3100, 3500, 0));
		when(outside.getCombatLevel()).thenAnswer(i -> f.onClientThread(70));

		assertEquals(70, Rs2Player.getCombatLevel());
		assertEquals(List.of("Pker"), Rs2Player.getPlayersInCombatLevelRange().stream()
			.map(p -> p.getName()).collect(Collectors.toList()));
		f.placePlayer(world(5, 5));
		assertTrue("outside the wilderness", Rs2Player.getPlayersInCombatLevelRange().isEmpty());
	}

	@Test
	public void staminaAndCombinationDialogueReads()
	{
		when(f.client.getVarbitValue(net.runelite.api.Varbits.RUN_SLOWED_DEPLETION_ACTIVE)).thenReturn(1);
		assertTrue(Rs2Player.hasStaminaActive());

		assertFalse(Rs2Dialogue.hasCombinationDialogue());
		Widget make = mock(Widget.class);
		when(make.isHidden()).thenAnswer(i -> f.onClientThread(false));
		when(f.client.getWidget(270, 1)).thenReturn(make);
		assertTrue(Rs2Dialogue.hasCombinationDialogue());
	}

	@Test
	public void batchTenNpcReadsUseIndexActionAndRandomEventTarget()
	{
		NPC far = f.npc(1, "Gardener", world(9, 5));
		NPC near = f.npc(2, "Genie", world(6, 5));
		when(far.getLocalLocation()).thenAnswer(i -> f.onClientThread(local(9, 5)));
		when(near.getLocalLocation()).thenAnswer(i -> f.onClientThread(local(6, 5)));
		when(far.getIndex()).thenReturn(40);
		when(near.getIndex()).thenReturn(41);
		net.runelite.api.NPCComposition gardener = mock(net.runelite.api.NPCComposition.class);
		when(gardener.getActions()).thenReturn(new String[]{"Talk-to", "Pay", null});
		net.runelite.api.NPCComposition genie = mock(net.runelite.api.NPCComposition.class);
		when(genie.getActions()).thenReturn(new String[]{"Talk-to", null, "Dismiss"});
		when(far.getComposition()).thenAnswer(i -> f.onClientThread(gardener));
		when(near.getComposition()).thenAnswer(i -> f.onClientThread(genie));

		assertEquals("Gardener", Rs2Npc.getNpcByIndex(40).getName());
		assertNull(Rs2Npc.getNpcByIndex(7));
		assertEquals("Gardener", Rs2Npc.getNearestNpcWithAction("Pay").getName());
		assertNull(Rs2Npc.getNearestNpcWithAction("pay"));
		assertNull("not interacting with the player yet", Rs2Npc.getRandomEventNPC());
		when(near.getInteracting()).thenAnswer(i -> f.onClientThread(f.localPlayer));
		assertEquals("Genie", Rs2Npc.getRandomEventNPC().getName());
	}

	@Test
	public void batchTenObjectReadsByLocationCompositionAndImpostor()
	{
		gameObject(8550, 7, 5);
		gameObject(8550, 9, 5);
		definition(8550, "Tree patch", "Inspect", "Guide");
		assertEquals(8550, Rs2GameObject.findGameObjectByLocation(world(9, 5)).getId());
		assertNull(Rs2GameObject.findGameObjectByLocation(world(1, 1)));
		assertNotNull(Rs2GameObject.findObjectComposition(8550));
		assertNull("no Chop action yet", Rs2GameObject.findObjectByImposter(8550, "Chop", false));
		definition(8550, "Tree patch", "Chop down", "Inspect");
		assertEquals(world(7, 5), onClientThread(() -> Rs2GameObject.findObjectByImposter(8550, "chop", false).getWorldLocation()));
		assertNull("exact needs the whole action", Rs2GameObject.findObjectByImposter(8550, "Chop"));
	}

	@Test
	public void batchElevenTileAndGroundItemReads()
	{
		Tile here = tile(6, 5);
		Tile there = tile(9, 5);
		TileItem bones = mock(TileItem.class);
		when(bones.getId()).thenReturn(526);
		TileItem coins = mock(TileItem.class);
		when(coins.getId()).thenReturn(995);
		here.getGroundItems().add(bones);
		there.getGroundItems().add(coins);
		net.runelite.api.ItemComposition bonesDefinition = mock(net.runelite.api.ItemComposition.class);
		when(bonesDefinition.getName()).thenReturn("Bones");
		when(f.client.getItemDefinition(526)).thenAnswer(i -> f.onClientThread(bonesDefinition));

		assertSame(here, net.runelite.client.plugins.microbot.util.tile.Rs2Tile.getTile(world(6, 5).getX(), world(6, 5).getY()));
		assertNull("outside the scene", net.runelite.client.plugins.microbot.util.tile.Rs2Tile.getTile(1, 1));

		net.runelite.client.plugins.microbot.util.models.RS2Item[] near = net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem.getAll(2);
		assertEquals(1, near.length);
		assertEquals("Bones", near[0].getItem().getName());
		assertSame(bones, near[0].getTileItem());
		net.runelite.client.plugins.microbot.util.models.RS2Item[] all = net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem.getAll(5);
		assertEquals("nearest first", List.of(526, 995), java.util.Arrays.stream(all).map(item -> item.getTileItem().getId()).collect(Collectors.toList()));
		assertEquals(0, net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem.getAllFromWorldPoint(3, null).length);
	}

	@Test
	public void batchTwelveReachableTilesRespectWallsAndDistance()
	{
		flags[6][5] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		java.util.Map<WorldPoint, Integer> reach = net.runelite.client.plugins.microbot.util.tile.Rs2Tile.getReachableTilesFromTile(world(5, 5), 2);
		assertEquals(Integer.valueOf(0), reach.get(world(5, 5)));
		assertEquals(Integer.valueOf(1), reach.get(world(5, 6)));
		assertEquals("a diagonal needs both sides open", Integer.valueOf(2), reach.get(world(6, 6)));
		assertEquals("open diagonal", Integer.valueOf(1), reach.get(world(4, 6)));
		assertFalse("blocked tile", reach.containsKey(world(6, 5)));
		assertFalse("around the block takes 4 steps", reach.containsKey(world(7, 5)));
		assertEquals(Integer.valueOf(4), net.runelite.client.plugins.microbot.util.tile.Rs2Tile.getReachableTilesFromTile(world(5, 5), 4).get(world(7, 5)));
		assertTrue(net.runelite.client.plugins.microbot.util.tile.Rs2Tile.getReachableTilesFromTile(world(5, 5), 2, true).containsKey(world(6, 5)));
		assertTrue(net.runelite.client.plugins.microbot.util.tile.Rs2Tile.getReachableTilesFromTile(null, 2).isEmpty());
	}

	@Test
	public void batchTwelveNearestWalkableTileSkipsBlockedNeighboursAndThePlayer()
	{
		f.placePlayer(world(5, 6));
		flags[5][4] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		WorldPoint nearest = net.runelite.client.plugins.microbot.util.tile.Rs2Tile.getNearestWalkableTile(world(5, 5));
		assertNotNull(nearest);
		assertNotEquals("not the player's tile", world(5, 6), nearest);
		assertNotEquals("not the blocked tile", world(5, 4), nearest);
		assertEquals(5, net.runelite.client.plugins.microbot.util.player.Rs2Player.getRs2WorldPoint().getY() - ReadApiFixture.BASE_Y - 1);
	}

	@Test
	public void batchTwelveTileObjectIsAWallDecorativeOrGroundObjectNearestFirst()
	{
		net.runelite.api.WallObject near = mock(net.runelite.api.WallObject.class);
		when(near.getId()).thenReturn(10857);
		when(near.getLocalLocation()).thenAnswer(i -> f.onClientThread(local(6, 5)));
		when(near.getWorldLocation()).thenAnswer(i -> f.onClientThread(world(6, 5)));
		when(tile(6, 5).getWallObject()).thenReturn(near);
		net.runelite.api.WallObject far = mock(net.runelite.api.WallObject.class);
		when(far.getId()).thenReturn(10857);
		when(far.getLocalLocation()).thenAnswer(i -> f.onClientThread(local(9, 5)));
		when(far.getWorldLocation()).thenAnswer(i -> f.onClientThread(world(9, 5)));
		when(tile(9, 5).getWallObject()).thenReturn(far);
		gameObject(10857, 7, 5);
		assertSame(near, net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.getTileObject(10857));
		assertSame("game objects are not tile objects here", far,
			net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.getTileObject(10857, world(9, 5), 1));
		assertNull(net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.getTileObject(1));
	}

	@Test
	public void batchTwelveWidgetsWithActionAreFoundInAllChildLists()
	{
		net.runelite.api.widgets.Widget close = mock(net.runelite.api.widgets.Widget.class);
		when(close.getActions()).thenAnswer(i -> f.onClientThread(new String[]{"Close"}));
		net.runelite.api.widgets.Widget label = mock(net.runelite.api.widgets.Widget.class);
		when(label.getText()).thenReturn("<col=ff0000>Abort offer</col>");
		net.runelite.api.widgets.Widget hidden = mock(net.runelite.api.widgets.Widget.class);
		when(hidden.isHidden()).thenReturn(true);
		when(hidden.getActions()).thenReturn(new String[]{"Close"});
		net.runelite.api.widgets.Widget parent = mock(net.runelite.api.widgets.Widget.class);
		when(parent.getDynamicChildren()).thenReturn(new net.runelite.api.widgets.Widget[]{close, hidden});
		when(parent.getStaticChildren()).thenReturn(new net.runelite.api.widgets.Widget[]{label});
		when(f.client.getWidget(602, 0)).thenReturn(parent);
		java.util.Map<net.runelite.api.widgets.Widget, String> closes = net.runelite.client.plugins.microbot.util.widget.Rs2Widget.findWidgetsWithAction("close", 602, false);
		assertEquals(1, closes.size());
		assertEquals("close", closes.get(close));
		assertTrue(net.runelite.client.plugins.microbot.util.widget.Rs2Widget.findWidgetsWithAction("Abort", 602, false).containsKey(label));
		assertTrue(net.runelite.client.plugins.microbot.util.widget.Rs2Widget.findWidgetsWithAction("Abort", 1, false).isEmpty());
	}

	@Test
	public void batchThirteenGameObjectWallAndReachReads()
	{
		gameObject(1276, 8, 5);
		gameObject(1276, 6, 5);
		definition(1276, "Tree", "Chop down");
		assertEquals(world(6, 5), onClientThread(() -> Rs2GameObject.getGameObject(1276).getWorldLocation()));
		assertEquals(world(8, 5), onClientThread(() -> Rs2GameObject.getGameObject(1276, world(9, 5), 1).getWorldLocation()));
		assertEquals(world(6, 5), onClientThread(() -> Rs2GameObject.getGameObject("tree").getWorldLocation()));
		assertNull(Rs2GameObject.getGameObject(1));
		assertNotNull(Rs2GameObject.getObjectComposition(1276));

		net.runelite.api.WallObject wall = mock(net.runelite.api.WallObject.class);
		when(wall.getId()).thenReturn(1530);
		when(wall.getLocalLocation()).thenAnswer(i -> f.onClientThread(local(7, 7)));
		when(wall.getWorldLocation()).thenAnswer(i -> f.onClientThread(world(7, 7)));
		when(tile(7, 7).getWallObject()).thenReturn(wall);
		assertEquals(List.of(wall), Rs2GameObject.getWallObjects());

		assertTrue("open ground next to the player", Rs2GameObject.canReach(world(7, 5)));
		wallIn(20, 20);
		assertFalse("walled in far away", Rs2GameObject.canReach(world(20, 20), 1, 1, 1, 1));
	}

	@Test
	public void batchThirteenLineOfSightTileIsTheSourceWhenFree()
	{
		tile(9, 9);
		assertEquals(world(9, 9), net.runelite.client.plugins.microbot.util.tile.Rs2Tile.getNearestWalkableTileWithLineOfSight(world(9, 9)));
		flags[9][9] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		tile(9, 10);
		assertEquals(world(9, 10), net.runelite.client.plugins.microbot.util.tile.Rs2Tile.getNearestWalkableTileWithLineOfSight(world(9, 9)));
	}

	@Test
	public void batchFifteenReachableTilesAndGroundItemTable()
	{
		flags[6][5] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		for (int y = 0; y < 32; y++)
		{
			flags[8][y] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		}
		net.runelite.client.plugins.microbot.util.reachable.Rs2Reachable.PackedTiles reach =
			net.runelite.client.plugins.microbot.util.reachable.Rs2Reachable.getReachableTiles(world(5, 5));
		assertTrue(reach.contains(world(7, 5)));
		assertFalse("blocked tile", reach.contains(world(6, 5)));
		assertFalse("behind the wall column", reach.contains(world(9, 5)));
		int[] count = {0};
		reach.forEach((int packed) -> count[0]++);
		assertEquals(reach.size(), count[0]);
		assertTrue(net.runelite.client.plugins.microbot.util.reachable.Rs2Reachable.isReachable(world(7, 6)));
		assertFalse(net.runelite.client.plugins.microbot.util.reachable.Rs2Reachable.isReachable(world(9, 6)));

		TileItem bass = mock(TileItem.class);
		when(bass.getId()).thenReturn(365);
		when(bass.getQuantity()).thenReturn(1);
		TileItem bass2 = mock(TileItem.class);
		when(bass2.getId()).thenReturn(365);
		when(bass2.getQuantity()).thenReturn(2);
		tile(6, 6).getGroundItems().add(bass);
		tile(6, 6).getGroundItems().add(bass2);
		net.runelite.api.ItemComposition definition = mock(net.runelite.api.ItemComposition.class);
		when(definition.getId()).thenReturn(365);
		when(definition.getName()).thenReturn("Bass");
		when(f.client.getItemDefinition(365)).thenAnswer(i -> f.onClientThread(definition));
		com.google.common.collect.Table<WorldPoint, Integer, net.runelite.client.plugins.grounditems.GroundItem> table =
			net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem.getGroundItems();
		assertEquals(1, table.size());
		net.runelite.client.plugins.grounditems.GroundItem item = table.get(world(6, 6), 365);
		assertEquals("Bass", item.getName());
		assertEquals("stacks on one tile add up", 3, item.getQuantity());
		assertEquals(world(6, 6), item.getLocation());
	}

	private <T extends TileObject> T placed(Class<T> type, int id, int x, int y)
	{
		T object = mock(type);
		when(object.getId()).thenReturn(id);
		when(object.getLocalLocation()).thenAnswer(i -> f.onClientThread(local(x, y)));
		when(object.getWorldLocation()).thenAnswer(i -> f.onClientThread(world(x, y)));
		return object;
	}

	@Test
	public void batchSixteenWallDecorativeAndGroundFamilies()
	{
		net.runelite.api.WallObject door = placed(net.runelite.api.WallObject.class, 1530, 7, 5);
		when(tile(7, 5).getWallObject()).thenReturn(door);
		net.runelite.api.DecorativeObject banner = placed(net.runelite.api.DecorativeObject.class, 2000, 6, 6);
		when(tile(6, 6).getDecorativeObject()).thenReturn(banner);
		net.runelite.api.GroundObject rug = placed(net.runelite.api.GroundObject.class, 3000, 9, 5);
		when(tile(9, 5).getGroundObject()).thenReturn(rug);
		definition(1530, "Door", "Open");
		definition(2000, "Qwyxian standard");
		definition(3000, "Rug");

		assertSame(door, Rs2GameObject.getWallObject(1530));
		assertSame(door, Rs2GameObject.getWallObject("door"));
		assertNull(Rs2GameObject.getWallObject(1530, world(20, 20), 2));
		assertSame(banner, Rs2GameObject.getDecorativeObject("Qwyxian standard", true));
		assertEquals(List.of(banner), Rs2GameObject.getDecorativeObjects());
		assertSame(rug, Rs2GameObject.getGroundObject(new Integer[]{1, 3000}));
		assertSame("local-point anchor", rug, Rs2GameObject.getGroundObject(local(9, 5), 128));
		assertSame(rug, Rs2GameObject.findGroundObjectByLocation(world(9, 5)));
		assertEquals(List.of(banner, door, rug), Rs2GameObject.getTileObjects());
		assertEquals("WallObject", Rs2GameObject.getObjectType(door));
		assertEquals("GroundObject", Rs2GameObject.getObjectType(rug));
		assertEquals("DecorativeObject", Rs2GameObject.getObjectType(banner));
	}

	@Test
	public void batchSixteenGameObjectFindsAndTiles()
	{
		gameObject(10355, 7, 5);
		gameObject(10355, 9, 5);
		definition(10355, "Qwyxian coffer", "Deposit");
		assertEquals(world(7, 5), onClientThread(() -> Rs2GameObject.findObject(10355, world(7, 5)).getWorldLocation()));
		assertEquals(world(9, 5), onClientThread(() -> Rs2GameObject.findObjectById(10355, world(9, 5).getX()).getWorldLocation()));
		assertEquals(world(7, 5), onClientThread(() -> Rs2GameObject.findObject(new Integer[]{10355}).getWorldLocation()));
		assertEquals(world(7, 5), onClientThread(() -> Rs2GameObject.get("qwyxian coffer").getWorldLocation()));
		assertNotNull(Rs2GameObject.findObjectByIdAndDistance(10355, 3));
		assertNull(Rs2GameObject.findObjectByIdAndDistance(10355, 0));
		assertEquals("GameObject", Rs2GameObject.getObjectType(Rs2GameObject.get("qwyxian coffer")));
		assertTrue(Rs2GameObject.canWalkTo(Rs2GameObject.get("qwyxian coffer"), 5));
		tile(6, 6);
		assertTrue(Rs2GameObject.getTiles().size() >= 3);
	}

	@Test
	public void batchSixteenNpcReads()
	{
		NPC goblin = f.npc(3029, "Goblin", world(6, 5));
		NPC guard = f.npc(3010, "Guard", world(9, 5));
		when(goblin.getLocalLocation()).thenAnswer(i -> f.onClientThread(local(6, 5)));
		when(guard.getLocalLocation()).thenAnswer(i -> f.onClientThread(local(9, 5)));
		when(goblin.getCombatLevel()).thenReturn(2);
		when(guard.getCombatLevel()).thenReturn(21);
		when(guard.getInteracting()).thenAnswer(i -> f.onClientThread(mock(net.runelite.api.Player.class)));
		when(goblin.getInteracting()).thenAnswer(i -> f.onClientThread(f.localPlayer));
		when(guard.isInteracting()).thenReturn(true);
		when(goblin.isInteracting()).thenReturn(true);
		when(goblin.getHealthRatio()).thenReturn(10);
		when(goblin.getHealthScale()).thenReturn(40);
		when(goblin.getPoseAnimation()).thenReturn(819);
		when(goblin.getIdlePoseAnimation()).thenReturn(808);
		net.runelite.api.NPCComposition goblinDefinition = mock(net.runelite.api.NPCComposition.class);
		when(goblinDefinition.getActions()).thenReturn(new String[]{null, "Attack", null, null, null});
		when(goblin.getComposition()).thenAnswer(i -> f.onClientThread(goblinDefinition));
		when(f.client.getNpcDefinition(3029)).thenReturn(goblinDefinition);

		assertEquals("both are busy outside multi-combat", 0, Rs2Npc.getAttackableNpcs().count());
		assertEquals("the goblin is fighting the player", List.of("Goblin"),
			Rs2Npc.getAttackableNpcs(false).map(n -> n.getName()).collect(Collectors.toList()));
		assertEquals(0, Rs2Npc.getAttackableNpcs("gob").count());
		assertEquals(List.of("Goblin"), Rs2Npc.getNpcsForPlayer("goblin").stream().map(n -> n.getName()).collect(Collectors.toList()));
		assertEquals(1, Rs2Npc.getNpcsAttackingPlayer(f.localPlayer).size());
		assertEquals("Attack", Rs2Npc.getAvailableAction(Rs2Npc.getNpc("Goblin"), List.of("Pickpocket", "attack")));
		assertEquals("Goblin", Rs2Npc.getNpcWithAction("Attack").getName());
		assertNull(Rs2Npc.getNpcWithAction("Trade"));
		assertEquals(25.0, Rs2Npc.getHealth(Rs2Npc.getNpc("Goblin")), 0.001);
		assertTrue(Rs2Npc.isMoving(goblin));
		assertEquals("melee reach is one tile", true, Rs2Npc.isInAttackRange(Rs2Npc.getNpc("Goblin")));
		assertFalse(Rs2Npc.isInAttackRange(Rs2Npc.getNpc("Guard")));
		tile(5, 5);
		tile(6, 5);
		assertTrue(Rs2Npc.hasLineOfSight(Rs2Npc.getNpc("Goblin")));
		assertEquals("Goblin", Rs2Npc.getNpcInLineOfSight("gob").getName());

		net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel model = Rs2Npc.getNpc("Goblin");
		assertEquals(128, model.getDistanceFromPlayer());
		assertTrue(model.isWithinDistanceFromPlayer(128));
		assertTrue(model.isWithinDistance(world(5, 5), 1));
		assertEquals(25.0, model.getHealthPercentage(), 0.001);
		assertTrue(model.isInteractingWithPlayer());
		assertTrue(model.isMoving());
		assertNull(model.getHeadIcon());
	}

	@Test
	public void batchSixteenKHouseObjectReads()
	{
		int fairyRing = Rs2GameObject.getObjectIdsByName("poh_fairy_ring").get(0);
		int spiritTree = Rs2GameObject.getObjectIdsByName("poh_spirit_tree").get(0);
		int spiritRing = Rs2GameObject.getObjectIdsByName("poh_spirit_ring").get(0);
		GameObject ring = gameObject(fairyRing, 7, 7);
		GameObject box = gameObject(net.runelite.api.gameval.ObjectID.POH_JEWELLERY_BOX_2, 8, 8);
		net.runelite.api.DecorativeObject glory = placed(net.runelite.api.DecorativeObject.class,
			net.runelite.api.gameval.ObjectID.POH_TROPHY_AMULETOFGLORY_4, 6, 6);
		when(tile(6, 6).getDecorativeObject()).thenReturn(glory);

		assertSame(ring, net.runelite.client.plugins.microbot.util.poh.PohTeleports.getFairyRings());
		assertNull("no spirit tree", net.runelite.client.plugins.microbot.util.poh.PohTeleports.getSpiritTree());
		assertTrue(net.runelite.client.plugins.microbot.util.poh.PohTeleports.isSpiritTree(gameObject(spiritTree, 9, 9)));
		GameObject both = gameObject(spiritRing, 10, 10);
		assertTrue("a spirit ring is both", net.runelite.client.plugins.microbot.util.poh.PohTeleports.isFairyRing(both)
			&& net.runelite.client.plugins.microbot.util.poh.PohTeleports.isSpiritTree(both));
		assertSame(box, net.runelite.client.plugins.microbot.util.poh.data.JewelleryBoxType.getObject());
		assertEquals(net.runelite.client.plugins.microbot.util.poh.data.JewelleryBoxType.FANCY,
			net.runelite.client.plugins.microbot.util.poh.data.JewelleryBoxType.getCurrentJewelleryBoxType());
		assertSame(glory, net.runelite.client.plugins.microbot.util.poh.data.MountedGlory.getObject());
		assertNull(net.runelite.client.plugins.microbot.util.poh.data.MountedXerics.getObject());
		assertNull(net.runelite.client.plugins.microbot.util.poh.data.PohPortal.values()[0].getPortal());

		when(f.client.getVarbitValue(net.runelite.api.gameval.VarbitID.POH_HOUSE_LOCATION)).thenReturn(2);
		assertTrue("Taverley house", net.runelite.client.plugins.microbot.util.poh.PohTeleports.hasHouse());
	}
}
