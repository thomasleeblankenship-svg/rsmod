package net.runelite.client.plugins.microbot.api;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.player.Rs2PlayerCache;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** api batch 1: NPC model reads and the static cache streams. */
public class ApiBatchOneTest
{
	private static Rs2NpcModel npc(String name, WorldPoint location)
	{
		Rs2NpcModel npc = mock(Rs2NpcModel.class, CALLS_REAL_METHODS);
		doReturn(name).when(npc).getName();
		doReturn(location).when(npc).getWorldLocation();
		return npc;
	}

	@Test
	public void namesMatchExactlyOrByContainment()
	{
		Rs2NpcModel guard = npc("Guard", null);
		assertTrue(Rs2NpcModel.matches(true, "guard").test(guard));
		assertFalse(Rs2NpcModel.matches(true, "gua").test(guard));
		assertTrue(Rs2NpcModel.matches(false, "UAR").test(guard));
		assertFalse(Rs2NpcModel.matches(false, "man").test(npc(null, null)));
	}

	@Test
	public void distanceToAnAnchorUsesTheWorldLocation()
	{
		Rs2NpcModel cow = npc("Cow", new WorldPoint(3200, 3200, 0));
		assertTrue(cow.isWithinDistance(new WorldPoint(3205, 3200, 0), 5));
		assertFalse(cow.isWithinDistance(new WorldPoint(3206, 3200, 0), 5));
		assertFalse(cow.isWithinDistance(null, 5));
	}

	@Test
	public void streamsAreEmptyBeforeTheRuntimeIsInstalled()
	{
		assertEquals(0, Rs2NpcCache.getNpcsStream().count());
		assertEquals(0, Rs2PlayerCache.getPlayersStream().count());
		assertEquals(0, Rs2TileObjectCache.getObjectsStream().count());
		assertEquals(0, Rs2TileItemCache.getTileItemsStream().count());
	}
}
