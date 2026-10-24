package net.runelite.client.plugins.microbot.util.tile;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Upstream's dangerous-tile tracking: countdown, both maps in the getter, the dodge hook. */
public class Rs2TileDangerTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final WorldPoint HERE = new WorldPoint(3200, 3200, 0);
	private Client client;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenReturn(true);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		Player player = mock(Player.class);
		when(player.getWorldLocation()).thenReturn(HERE);
		when(client.getLocalPlayer()).thenReturn(player);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
		Rs2Tile.clearDangerousTiles();
	}

	@After
	public void tearDown()
	{
		Rs2Tile.onPlayerOnDangerousTile = null;
		Rs2Tile.clearDangerousTiles();
		MicrobotRuntime.uninstall();
	}

	private static GameObject objectAt(WorldPoint location)
	{
		GameObject object = mock(GameObject.class);
		when(object.getWorldLocation()).thenReturn(location);
		return object;
	}

	@Test
	public void aDangerousTileCountsDownAndIsForgotten()
	{
		WorldPoint east = HERE.dx(2);
		Rs2Tile.addDangerousGameObjectTile(objectAt(east), 1200);
		Rs2Tile.addDangerousGameObjectTile(objectAt(east), 600);
		assertEquals("the longer time wins", Integer.valueOf(1200), Rs2Tile.getDangerousGraphicsObjectTiles().get(east));
		Rs2Tile.tickDangerousTiles();
		assertEquals(Integer.valueOf(600), Rs2Tile.getDangerousGraphicsObjectTiles().get(east));
		Rs2Tile.tickDangerousTiles();
		assertTrue(Rs2Tile.getDangerousGraphicsObjectTiles().isEmpty());
		Rs2Tile.addDangerousGameObjectTile(null, 600);
		assertTrue(Rs2Tile.getDangerousGraphicsObjectTiles().isEmpty());
	}

	@Test
	public void theDodgeHookRunsOnlyWhenThePlayerStandsOnTheTile()
	{
		List<WorldPoint> dodged = new ArrayList<>();
		Rs2Tile.onPlayerOnDangerousTile = dodged::add;
		Rs2Tile.addDangerousGameObjectTile(objectAt(HERE.dx(3)), 600);
		assertTrue(dodged.isEmpty());
		Rs2Tile.addDangerousGameObjectTile(objectAt(HERE), 600);
		assertEquals(List.of(HERE), dodged);
	}

	@Test
	public void canvasBoundsAreReadFromTheClient()
	{
		when(client.getCanvasWidth()).thenReturn(800);
		when(client.getCanvasHeight()).thenReturn(600);
		assertTrue(Rs2UiHelper.isRectangleWithinCanvas(new Rectangle(10, 10, 100, 100)));
		assertFalse(Rs2UiHelper.isRectangleWithinCanvas(new Rectangle(750, 10, 100, 100)));
		assertFalse(Rs2UiHelper.isRectangleWithinCanvas(new Rectangle(-1, 10, 5, 5)));
		assertFalse(Rs2UiHelper.isRectangleWithinCanvas(null));
	}
}
