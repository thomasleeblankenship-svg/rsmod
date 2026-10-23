package net.runelite.client.plugins.microbot.util.tile;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Hub item (6) tile reads over a 32x32 scene with the player at scene (5, 5). */
public class Rs2TileReadTest
{
	static
	{
		// JavaBridge captures this flag on first load; set it before any test reaches the bridge.
		System.setProperty("runelite.bridge", "true");
	}

	private static final int BASE_X = 3200;
	private static final int BASE_Y = 3200;

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final int[][] flags = new int[32][32];
	private Client client;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		CollisionData collision = mock(CollisionData.class);
		when(collision.getFlags()).thenReturn(flags);
		WorldView view = mock(WorldView.class);
		when(view.isTopLevel()).thenReturn(true);
		when(view.getBaseX()).thenReturn(BASE_X);
		when(view.getBaseY()).thenReturn(BASE_Y);
		when(view.getCollisionMaps()).thenReturn(new CollisionData[]{collision, null, null, null});
		when(client.getTopLevelWorldView()).thenReturn(view);
		Player player = mock(Player.class);
		when(player.getWorldView()).thenReturn(view);
		when(player.getWorldLocation()).thenReturn(at(0, 0));
		when(client.getLocalPlayer()).thenReturn(player);

		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			onClientThread.set(true);
			try { ((Runnable) call.getArgument(0)).run(); }
			finally { onClientThread.set(false); }
			return null;
		}).when(thread).invoke(any(Runnable.class));
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	private static WorldPoint at(int dx, int dy)
	{
		return new WorldPoint(BASE_X + 5 + dx, BASE_Y + 5 + dy, 0);
	}

	@Test
	public void walkableFollowsTheCollisionFlags()
	{
		flags[7][5] = CollisionDataFlag.BLOCK_MOVEMENT_OBJECT;
		flags[8][5] = CollisionDataFlag.BLOCK_MOVEMENT_EAST;

		assertTrue(Rs2Tile.isWalkable(at(1, 0)));
		assertFalse("an object fills it", Rs2Tile.isWalkable(at(2, 0)));
		assertTrue("a wall on one side does not fill the tile", Rs2Tile.isWalkable(at(3, 0)));
		assertFalse("outside the loaded scene", Rs2Tile.isWalkable(new WorldPoint(BASE_X + 40, BASE_Y, 0)));
		assertFalse("no collision data upstairs", Rs2Tile.isWalkable(new WorldPoint(BASE_X + 6, BASE_Y + 5, 1)));
		assertFalse(Rs2Tile.isWalkable((WorldPoint) null));
	}

	@Test
	public void localPointAndTileOverloadsUseTheSameFlags()
	{
		flags[7][5] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;

		assertTrue(Rs2Tile.isWalkable(LocalPoint.fromScene(6, 5, client.getTopLevelWorldView())));
		assertFalse(Rs2Tile.isWalkable(LocalPoint.fromScene(7, 5, client.getTopLevelWorldView())));

		Tile tile = mock(Tile.class);
		when(tile.getWorldLocation()).thenReturn(at(2, 0));
		assertFalse(Rs2Tile.isWalkable(tile));
	}

	@Test
	public void reachableStopsAtWallsAndOtherFloors()
	{
		for (int ox = -1; ox <= 1; ox++)
		{
			for (int oy = -1; oy <= 1; oy++)
			{
				flags[15 + ox][15 + oy] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
			}
		}
		flags[15][15] = 0;

		assertTrue(Rs2Tile.isTileReachable(at(4, 4)));
		assertFalse("walled in on every side", Rs2Tile.isTileReachable(at(10, 10)));
		assertFalse("another floor", Rs2Tile.isTileReachable(new WorldPoint(BASE_X + 6, BASE_Y + 5, 1)));
		assertFalse("outside the scene", Rs2Tile.isTileReachable(new WorldPoint(BASE_X + 60, BASE_Y + 5, 0)));
	}
}
