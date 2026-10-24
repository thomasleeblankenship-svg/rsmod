package net.runelite.client.plugins.microbot.util.npc;

import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.MobilePathfinder;
import net.runelite.client.plugins.microbot.shortestpath.MobileTransportPolicy;
import net.runelite.client.plugins.microbot.shortestpath.PathResult;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/** Upstream's NPC stats, attack styles and spawn locations load from the bundled JSON; no game client. */
public class Rs2NpcManagerTest
{
	@BeforeClass
	public static void load() throws Exception
	{
		Rs2NpcManager.loadJson();
	}

	@Test
	public void statsAndAttackStylesComeFromTheBundledData()
	{
		Rs2NpcStats molanisk = Rs2NpcManager.getStats(1);
		assertNotNull(molanisk);
		assertEquals("Molanisk", molanisk.getName());
		assertEquals(52, Rs2NpcManager.getHealth(1));
		assertEquals(4, Rs2NpcManager.getAttackSpeed(1));
		assertEquals(5, (int) molanisk.getMaxHit());
		assertEquals("crush", Rs2NpcManager.getAttackStyle(1));
		assertTrue(Rs2NpcManager.getSlayerMonsters().contains(1));
		assertNull(Rs2NpcManager.getStats(-5));
		assertEquals(-1, Rs2NpcManager.getHealth(-5));
		assertEquals(1.0, Rs2NpcManager.getXpModifier(494), 0.0);
	}

	@Test
	public void locationsAreKeyedByNameWithWorldPoints()
	{
		List<MonsterLocation> spectres = Rs2NpcManager.getNpcLocations("Aberrant spectre");
		assertFalse(spectres.isEmpty());
		assertFalse(spectres.get(0).getCoords().isEmpty());
		assertNotNull(spectres.get(0).getBestClusterCenter());
		assertTrue(Rs2NpcManager.getNpcLocations("No such monster").isEmpty());
	}

	@Test
	public void aMultiTargetSearchReportsTheTargetItReached()
	{
		PathfinderConfig config = net.runelite.client.plugins.microbot.shortestpath.MobileRouteTestAccess.offlineConfig(
			MobileTransportPolicy.DEFAULT);
		WorldPoint draynorBank = new WorldPoint(3092, 3245, 0);
		WorldPoint near = new WorldPoint(3100, 3250, 0);
		WorldPoint far = new WorldPoint(3222, 3218, 0);
		PathResult result = MobilePathfinder.search(config, draynorBank, Set.of(near, far));
		assertTrue(result.isFound());
		assertEquals("the nearer target is reached first", near, result.getGoal());
		assertEquals(near, result.getTiles().get(result.getTiles().size() - 1));
	}
}
