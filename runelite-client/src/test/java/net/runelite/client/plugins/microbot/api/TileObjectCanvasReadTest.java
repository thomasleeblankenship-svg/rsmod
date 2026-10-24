package net.runelite.client.plugins.microbot.api;

import java.awt.Polygon;
import net.runelite.api.GameObject;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The tile-object reads the KSP Chopper port uses: hash, minimap point, tile outline, the GameObject constructor. */
public class TileObjectCanvasReadTest
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

	@Test
	public void aSpawnedGameObjectIsModelledWithTheInstalledServicesAndReadOnTheClientThread() throws Exception
	{
		GameObject sapling = mock(GameObject.class);
		Point minimap = new Point(640, 80);
		Polygon tile = new Polygon(new int[]{1, 2, 3}, new int[]{4, 5, 6}, 3);
		when(sapling.getId()).thenAnswer(i -> f.onClientThread(47484));
		when(sapling.getHash()).thenAnswer(i -> f.onClientThread(123456789L));
		when(sapling.getWorldLocation()).thenAnswer(i -> f.onClientThread(
			new WorldPoint(ReadApiFixture.BASE_X + 3, ReadApiFixture.BASE_Y + 4, 0)));
		when(sapling.getWorldView()).thenReturn(f.topLevel);
		when(sapling.getMinimapLocation()).thenAnswer(i -> f.onClientThread(minimap));
		when(sapling.getCanvasTilePoly()).thenAnswer(i -> f.onClientThread(tile));

		// As in a GameObjectSpawned handler: the model is made on the client thread.
		Rs2TileObjectModel model = f.clientExecutor.submit(() -> new Rs2TileObjectModel(sapling)).get();

		assertEquals(47484, model.getId());
		assertEquals("captured with the snapshot", 123456789L, model.getHash());
		// Called from this (script) thread, so each live read must hop to the client thread.
		assertSame(minimap, model.getMinimapLocation());
		assertSame(tile, model.getCanvasTilePoly());
	}

	@Test
	public void anObjectOffTheMinimapOrScreenReadsAsNull() throws Exception
	{
		GameObject far = mock(GameObject.class);
		when(far.getId()).thenAnswer(i -> f.onClientThread(1276));
		when(far.getWorldLocation()).thenAnswer(i -> f.onClientThread(new WorldPoint(ReadApiFixture.BASE_X, ReadApiFixture.BASE_Y, 0)));
		when(far.getWorldView()).thenReturn(f.topLevel);

		Rs2TileObjectModel model = f.clientExecutor.submit(() -> new Rs2TileObjectModel(far)).get();

		assertNull(model.getMinimapLocation());
		assertNull(model.getCanvasTilePoly());
	}

	@Test
	public void stripTagsRemovesEveryMarkupTag()
	{
		assertEquals("Willow tree", Rs2UiHelper.stripTags("<col=ffff00>Willow</col> <img=1>tree"));
		assertEquals("", Rs2UiHelper.stripTags(null));
	}
}
