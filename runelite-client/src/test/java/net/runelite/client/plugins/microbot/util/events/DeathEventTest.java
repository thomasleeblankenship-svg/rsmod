package net.runelite.client.plugins.microbot.util.events;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

/** Death's tutorial is the first-death counter inside Death's Domain; the location is read only then. */
public class DeathEventTest
{
	private MockedStatic<Microbot> microbot;
	private MockedStatic<Rs2Player> player;
	private int deaths = 1;
	private WorldPoint location = new WorldPoint(3168, 5726, 0);

	@Before
	public void setUp()
	{
		microbot = mockStatic(Microbot.class);
		microbot.when(Microbot::isLoggedIn).thenReturn(true);
		microbot.when(() -> Microbot.getVarbitPlayerValue(4517)).thenAnswer(call -> deaths);
		player = mockStatic(Rs2Player.class);
		player.when(Rs2Player::getWorldLocation).thenAnswer(call -> location);
	}

	@After
	public void tearDown()
	{
		microbot.close();
		player.close();
	}

	@Test
	public void theTutorialIsTheFirstDeathInsideTheDomain()
	{
		assertTrue(location.getRegionID() == 12633);
		assertTrue(DeathEvent.inTutorial());
		location = new WorldPoint(3222, 3218, 0);
		assertFalse("respawned in Lumbridge", DeathEvent.inTutorial());
	}

	@Test
	public void anyOtherDeathCountSkipsTheLocationRead()
	{
		deaths = 2;
		assertFalse(DeathEvent.inTutorial());
		player.verify(Rs2Player::getWorldLocation, never());
	}
}
