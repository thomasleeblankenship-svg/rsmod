package net.runelite.client.plugins.microbot;

import net.runelite.client.plugins.microbot.util.gameobject.Rs2Cannon;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util-gap action batch K: random samplers, the cannon and spellbook switching. */
public class HubActionBatchKTest
{
	@Test
	public void samplersStayInsideTheirBounds()
	{
		for (int i = 0; i < 2_000; i++)
		{
			int fancy = Rs2Random.fancyNormalSample(91, 96);
			assertTrue(fancy >= 91 && fancy <= 96);
			int logNormal = Rs2Random.logNormalBounded(150, 250);
			assertTrue(logNormal >= 150 && logNormal <= 250);
			double truncated = Rs2Random.truncatedGauss(600.0, 1200.0, 0);
			assertTrue(truncated >= 600 && truncated < 1200);
			double normal = Rs2Random.truncatedNormalSample(2, 3, null, null);
			assertTrue(normal >= 2 && normal <= 3);
			assertTrue(Rs2Random.nzRandom() > 0);
		}
		assertEquals(5, Rs2Random.fancyNormalSample(5, 5));
		assertEquals(7, Rs2Random.logNormalBounded(7, 3));
		assertEquals(4.0, Rs2Random.truncatedNormalSample(4, 4, null, null), 0);
		assertTrue(Double.isFinite(Rs2Random.gaussRand(45, 10)));
	}

	@Test
	public void theCannonIsLeftAloneWithoutBallsOrAnOwnedCannon()
	{
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
			MockedStatic<Rs2GameObject> objects = mockStatic(Rs2GameObject.class))
		{
			inventory.when(() -> Rs2Inventory.hasItemAmount("cannonball", 15, true)).thenReturn(false);
			assertFalse("no cannonballs", Rs2Cannon.refill(15));
			assertFalse("no broken cannon", Rs2Cannon.repair());
			objects.verify(() -> Rs2GameObject.interact(any(net.runelite.api.TileObject.class), anyString()), never());
		}
	}

	@Test
	public void switchingToTheCurrentBookDoesNothing()
	{
		try (MockedStatic<Rs2Spellbook> books = mockStatic(Rs2Spellbook.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Npc> npcs = mockStatic(Rs2Npc.class))
		{
			books.when(Rs2Spellbook::getCurrentSpellbook).thenReturn(Rs2Spellbook.MODERN);
			assertTrue(Rs2Spellbook.MODERN.switchTo());
			npcs.verify(() -> Rs2Npc.interact(anyString(), anyString()), never());

			books.when(Rs2Spellbook::getCurrentSpellbook).thenReturn(Rs2Spellbook.LUNAR);
			assertFalse("Tyss only switches Arceuus and Modern", Rs2Spellbook.switchViaNpc());
			npcs.verify(() -> Rs2Npc.interact(anyString(), anyString()), never());
		}
	}
}
