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
}
