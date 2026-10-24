package net.runelite.client.plugins.microbot.util.gameobject;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.tileobject.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.tileobject.TileObjectType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Read batch 16i: upstream 2.6.38's legacy object models. */
public class HubReadBatchSixteenITest
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

	private ObjectComposition composition(String name, int sizeX, int sizeY, String... actions)
	{
		ObjectComposition composition = mock(ObjectComposition.class);
		when(composition.getName()).thenReturn(name);
		when(composition.getSizeX()).thenReturn(sizeX);
		when(composition.getSizeY()).thenReturn(sizeY);
		when(composition.getActions()).thenReturn(actions);
		return composition;
	}

	@Test
	public void objectModelSizeLocationAndDefinitionReads()
	{
		when(client.getTickCount()).thenReturn(100);
		GameObject furnace = mock(GameObject.class);
		when(furnace.getId()).thenReturn(16469);
		when(furnace.sizeX()).thenReturn(2);
		when(furnace.sizeY()).thenReturn(2);
		when(furnace.getSceneMinLocation()).thenReturn(new Point(40, 40));
		Tile tile = mock(Tile.class);
		when(tile.getSceneLocation()).thenReturn(new Point(41, 40));
		when(tile.getWorldLocation()).thenReturn(new WorldPoint(3101, 3100, 0));
		ObjectComposition definition = composition("Furnace", 2, 2, "Smelt", null);
		when(client.getObjectDefinition(16469)).thenReturn(definition);

		Rs2ObjectModel model = new Rs2ObjectModel(furnace, tile);
		assertEquals(Rs2ObjectModel.ObjectType.GAME_OBJECT, model.getObjectType());
		assertEquals(10, model.getType());
		assertEquals(2, model.getSizeX());
		assertEquals("south-west tile of the footprint", new WorldPoint(3100, 3100, 0), model.getLocation());
		assertEquals("Furnace", model.getName());
		assertTrue(model.hasAction("smelt"));
		assertFalse(model.hasAction("Open"));
		assertEquals(2, model.getWidth());
		assertTrue("larger than one tile", model.blocksLineOfSight());
		when(client.getTickCount()).thenReturn(103);
		assertEquals(3, model.getTicksSinceCreation());

		Player player = mock(Player.class);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3104, 3100, 0));
		when(client.getLocalPlayer()).thenReturn(player);
		assertEquals(4, model.getDistanceFromPlayer());
		assertTrue(model.isWithinDistanceFromPlayer(4));
		assertFalse(model.isWithinDistanceFromPlayer(3));

		WallObject wall = mock(WallObject.class);
		when(wall.getWorldLocation()).thenReturn(new WorldPoint(3000, 3000, 0));
		Rs2ObjectModel wallModel = new Rs2ObjectModel(wall, tile);
		assertEquals(Rs2ObjectModel.ObjectType.WALL_OBJECT, wallModel.getObjectType());
		assertEquals(1, wallModel.getSizeX());
		assertEquals(new WorldPoint(3000, 3000, 0), wallModel.getLocation());
		assertTrue(wallModel.blocksLineOfSight());
		assertEquals("Unknown Object", wallModel.getName());
		assertArrayEquals(new String[0], wallModel.getActions());
	}

	@Test
	public void tileObjectModelResolvesImpostorsAndDelegates()
	{
		GameObject gate = mock(GameObject.class);
		when(gate.getId()).thenReturn(1551);
		when(gate.getHash()).thenReturn(77L);
		when(gate.getPlane()).thenReturn(1);
		ObjectComposition base = composition("Gate", 1, 1);
		ObjectComposition open = composition("<col=ffff>Open gate</col>", 1, 1, "Close");
		when(base.getImpostorIds()).thenReturn(new int[]{1, 2});
		when(base.getImpostor()).thenReturn(open);
		when(client.getObjectDefinition(1551)).thenReturn(base);

		Rs2TileObjectModel model = new Rs2TileObjectModel(gate);
		assertEquals(TileObjectType.GAME, model.getTileObjectType());
		assertEquals(1551, model.getId());
		assertEquals(77L, model.getHash());
		assertEquals(1, model.getPlane());
		assertSame("impostor", open, model.getObjectComposition());
		assertEquals("colour tags stripped", "Open gate", model.getName());

		when(client.getObjectDefinition(1551)).thenReturn(null);
		assertNull(model.getName());
		assertEquals(TileObjectType.WALL, new Rs2TileObjectModel(mock(WallObject.class)).getTileObjectType());
	}
}
