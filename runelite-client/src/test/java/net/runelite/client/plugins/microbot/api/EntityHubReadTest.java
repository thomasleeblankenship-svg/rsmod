package net.runelite.client.plugins.microbot.api;

import java.awt.Rectangle;
import java.awt.Shape;
import java.util.List;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Hub item (3): NPC model reads, reachable and client-thread query variants, object clickbox. */
public class EntityHubReadTest
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

	private WorldPoint at(int dx, int dy)
	{
		return new WorldPoint(ReadApiFixture.BASE_X + 5 + dx, ReadApiFixture.BASE_Y + 5 + dy, 0);
	}

	/** Surrounds the scene tile at {@code (dx, dy)} from the player with full blocks. */
	private void wallIn(int dx, int dy)
	{
		int x = 5 + dx;
		int y = 5 + dy;
		for (int ox = -1; ox <= 1; ox++)
		{
			for (int oy = -1; oy <= 1; oy++)
			{
				flags[x + ox][y + oy] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
			}
		}
	}

	@Test
	public void npcModelCapturesCombatHealthDeathAndInteraction()
	{
		NPC goblin = f.npc(1, "Goblin", at(2, 0));
		when(goblin.getCombatLevel()).thenAnswer(i -> f.onClientThread(5));
		when(goblin.getHealthRatio()).thenAnswer(i -> f.onClientThread(15));
		when(goblin.getHealthScale()).thenAnswer(i -> f.onClientThread(30));
		when(goblin.isDead()).thenAnswer(i -> f.onClientThread(false));
		when(goblin.getInteracting()).thenAnswer(i -> f.onClientThread(f.localPlayer));
		NPC idle = f.npc(2, "Cow", at(3, 0));

		Rs2NpcModel model = f.services.getRs2NpcCache().query().withName("Goblin").first();
		assertEquals(5, model.getCombatLevel());
		assertEquals(50.0, model.getHealthPercentage(), 0.0);
		assertFalse(model.isDead());
		assertSame(f.localPlayer, model.getInteracting());
		assertTrue(model.isInteractingWithPlayer());

		Rs2NpcModel cow = f.services.getRs2NpcCache().query().withName("Cow").first();
		assertEquals("no health bar", -1.0, cow.getHealthPercentage(), 0.0);
		assertFalse(cow.isInteractingWithPlayer());
		assertNull(cow.getInteracting());
	}

	@Test
	public void lineOfSightIsTrueOnTheSameTileAndFalseWithoutAPlayer()
	{
		f.npc(1, "Imp", at(0, 0));
		Rs2NpcModel imp = f.services.getRs2NpcCache().query().first();
		assertTrue(imp.hasLineOfSight());

		when(f.client.getLocalPlayer()).thenReturn(null);
		assertFalse(imp.hasLineOfSight());
	}

	@Test
	public void nearestReachableSkipsAWalledInNpc()
	{
		f.npc(1, "Caged", at(2, 0));
		f.npc(2, "Free", at(6, 0));
		wallIn(2, 0);

		assertEquals("Caged", f.services.getRs2NpcCache().query().nearest().getName());
		assertEquals("Free", f.services.getRs2NpcCache().query().nearestReachable().getName());
		assertEquals("Free", f.services.getRs2NpcCache().query().firstReachable().getName());
		assertNull(f.services.getRs2NpcCache().query().nearestReachable(4));
	}

	@Test
	public void anEntityBesideAReachableTileCounts()
	{
		f.npc(1, "Banker", at(3, 0));
		flags[5 + 3][5] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;

		assertEquals("Banker", f.services.getRs2NpcCache().query().nearestReachable().getName());
	}

	@Test
	public void clientThreadVariantsMatchTheSnapshotQueries()
	{
		f.npc(1, "Man", at(1, 0));
		f.npc(2, "Woman", at(4, 0));

		assertEquals("Man", f.services.getRs2NpcCache().query().nearestOnClientThread().getName());
		assertEquals("Man", f.services.getRs2NpcCache().query().nearestOnClientThread(2).getName());
		assertEquals("Woman", f.services.getRs2NpcCache().query().nearestOnClientThread(at(4, 0), 0).getName());
		assertEquals(2, f.services.getRs2NpcCache().query().toListOnClientThread().size());
		assertEquals("Man", f.services.getRs2NpcCache().query().withId(1).firstOnClientThread().getName());
	}

	@Test
	public void clickboxIsReadOnTheClientThread()
	{
		GameObject tree = mock(GameObject.class);
		Shape box = new Rectangle(10, 10, 20, 20);
		when(tree.getId()).thenAnswer(i -> f.onClientThread(1276));
		when(tree.getWorldLocation()).thenReturn(at(1, 1));
		when(tree.getWorldView()).thenReturn(f.topLevel);
		when(tree.getClickbox()).thenAnswer(i -> f.onClientThread(box));
		Tile tile = mock(Tile.class);
		when(tile.getSceneLocation()).thenReturn(new net.runelite.api.Point(6, 6));
		when(tile.getWorldLocation()).thenReturn(at(1, 1));
		when(tile.getGroundItems()).thenReturn(new java.util.ArrayList<>());
		when(tile.getGameObjects()).thenReturn(new GameObject[]{tree});
		when(tree.getSceneMinLocation()).thenReturn(new net.runelite.api.Point(6, 6));
		f.tiles[0][6][6] = tile;

		List<Rs2TileObjectModel> objects = f.services.getRs2TileObjectCache().query().withId(1276).toList();
		assertEquals(1, objects.size());
		assertSame(box, objects.get(0).getClickbox());
	}
}
