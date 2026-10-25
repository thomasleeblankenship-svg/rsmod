package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The minigame teleport executor: dropdown names, rat pit options and the Wilderness policy. */
public class MinigameTeleportsTest
{
	private static final WorldPoint CASTLE_WARS = new WorldPoint(2442, 3093, 0);
	private static final WorldPoint FEROX = new WorldPoint(3150, 3635, 0);

	@Test
	public void theDropdownNameIsTheTextBeforeTheColon()
	{
		assertEquals("castle wars", MinigameTeleports.destinationName("Castle Wars"));
		assertEquals("rat pits", MinigameTeleports.destinationName(" Rat Pits: Varrock"));
		assertNull(MinigameTeleports.destinationName(" "));
		assertNull(MinigameTeleports.destinationName(null));
	}

	@Test
	public void ratPitsNameTheirPit()
	{
		assertEquals("port sarim", MinigameTeleports.ratPitsOption("Rat Pits: Port Sarim"));
		assertNull(MinigameTeleports.ratPitsOption("Rat Pits"));
		assertNull(MinigameTeleports.ratPitsOption("Castle Wars"));
	}

	@Test
	public void feroxAndWildernessLandingsAreRefused()
	{
		assertTrue(MinigameTeleports.isRefused("Clan Wars", FEROX));
		assertTrue(MinigameTeleports.isRefused("Last Man Standing", FEROX));
		assertTrue(MinigameTeleports.isRefused("Castle Wars", new WorldPoint(3100, 3700, 0)));
		assertTrue(MinigameTeleports.isRefused(null, CASTLE_WARS));
		assertFalse(MinigameTeleports.isRefused("Castle Wars", CASTLE_WARS));
	}

	@Test
	public void aRefusedTeleportTouchesNothing()
	{
		try (MockedStatic<Rs2Tab> tabs = mockStatic(Rs2Tab.class);
			MockedStatic<MobileActions> actions = mockStatic(MobileActions.class))
		{
			assertFalse(MinigameTeleports.teleport("Clan Wars", FEROX));
			assertFalse(MinigameTeleports.teleport("Rat Pits", CASTLE_WARS));
			assertFalse(MinigameTeleports.teleport(null));
			tabs.verifyNoInteractions();
			actions.verify(() -> MobileActions.widget(anyInt(), anyInt(), anyString()), never());
		}
	}

	@Test
	public void noChatTabMeansNoTeleport()
	{
		try (MockedStatic<Rs2Tab> tabs = mockStatic(Rs2Tab.class);
			MockedStatic<MobileActions> actions = mockStatic(MobileActions.class))
		{
			tabs.when(() -> Rs2Tab.switchTo(InterfaceTab.CHAT)).thenReturn(false);
			actions.when(() -> MobileActions.read(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
			assertFalse(MinigameTeleports.teleport("Castle Wars", CASTLE_WARS));
			actions.verify(() -> MobileActions.widget(anyInt(), anyInt(), anyString()), never());
		}
	}
}
