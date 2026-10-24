package net.runelite.client.plugins.microbot.util.walker;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The walker's answer to Rs2Tile's dangerous-tile hook: a scene walk to a safe tile, only for a running script. */
public class DangerDodgeTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final WorldPoint SAFE = new WorldPoint(3202, 3200, 0);

	@Before
	public void setUp() throws Exception
	{
		Client client = mock(Client.class);
		when(client.isClientThread()).thenReturn(true);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	@Test
	public void theRuntimeRegistersTheDodgeAndClearsItOnUninstall()
	{
		assertNotNull("installed: the hook is set", Rs2Tile.onPlayerOnDangerousTile);
		MicrobotRuntime.uninstall();
		assertNull("uninstalled: no dodge without a runtime", Rs2Tile.onPlayerOnDangerousTile);
	}

	@Test
	public void noScriptNoDodge()
	{
		assertFalse("without an active script session the player is never moved", Rs2Walker.dodgeDangerousTile());
	}

	@Test
	public void theDodgeWalksOnceToTheSafeTile()
	{
		try (MockedStatic<Rs2Tile> tiles = mockStatic(Rs2Tile.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class, CALLS_REAL_METHODS))
		{
			tiles.when(Rs2Tile::getSafeTile).thenReturn(SAFE);
			walker.when(() -> Rs2Walker.walkCanvas(any(WorldPoint.class))).thenAnswer(call -> call.getArgument(0));
			walker.clearInvocations();
			assertEquals(SAFE, Rs2Walker.dodgeToSafeTile());
			walker.verify(() -> Rs2Walker.walkCanvas(SAFE));

			tiles.when(Rs2Tile::getSafeTile).thenReturn(null);
			walker.clearInvocations();
			assertNull("no safe tile, no walk", Rs2Walker.dodgeToSafeTile());
			walker.verify(() -> Rs2Walker.walkCanvas(any(WorldPoint.class)), never());
		}
	}

	@Test
	public void anActiveScriptStartsADodge()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(MobileActions::active).thenReturn(true);
			// The dodge thread is not mocked: with no safe tile known there, it finds none and ends.
			assertTrue(Rs2Walker.dodgeDangerousTile());
		}
	}
}
