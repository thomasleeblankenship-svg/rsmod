package net.runelite.client.plugins.microbot.shortestpath;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/** Webwalker C2-5: when a walk to the same goal may reuse the last route instead of searching again. */
public class RouteReuseTest
{
	private static final WorldPoint DRAYNOR_BANK = new WorldPoint(3092, 3245, 0);
	private static final WorldPoint FALADOR = new WorldPoint(2965, 3378, 0);
	private static final MobileTransportPolicy POLICY = MobileTransportPolicy.DEFAULT;

	private static PathResult route;
	private static CollisionMap map;

	@BeforeClass
	public static void plan()
	{
		PathfinderConfig config = MobileRouteTest.offlineConfig(POLICY);
		map = config.getMap();
		route = MobilePathfinder.search(config, DRAYNOR_BANK, FALADOR);
		assertTrue(route.isFound());
		assertFalse("the Draynor-Falador route crosses a gate or door", route.getTransports().isEmpty());
	}

	private static MobilePathfinder.CachedRoute cached(PathResult result, long at)
	{
		return new MobilePathfinder.CachedRoute(result, POLICY, at);
	}

	@Test
	public void aStartOnTheRouteGetsTheRestOfIt()
	{
		List<WorldPoint> tiles = route.getTiles();
		int k = 20;
		PathResult rest = MobilePathfinder.reuse(cached(route, 0), tiles.get(k), FALADOR, map, POLICY, 1_000);
		assertNotNull(rest);
		assertEquals(PathResult.Status.FOUND, rest.getStatus());
		assertEquals(tiles.subList(k, tiles.size()), rest.getTiles());
		for (Map.Entry<Integer, PathResult.TransportStep> entry : route.getTransports().entrySet())
		{
			if (entry.getKey() >= k)
			{
				assertSame("transport indices follow the shorter route", entry.getValue(), rest.transportAt(entry.getKey() - k));
			}
		}
		assertEquals(route.getTransports().keySet().stream().filter(i -> i >= k).count(), rest.getTransports().size());
	}

	@Test
	public void aStartOneStepOffTheRouteIsPrepended()
	{
		List<WorldPoint> tiles = route.getTiles();
		WorldPoint off = null;
		int k = -1;
		for (int i = 10; i < tiles.size() && off == null; i++)
		{
			for (int dx = -1; dx <= 1 && off == null; dx++)
			{
				for (int dy = -1; dy <= 1 && off == null; dy++)
				{
					WorldPoint candidate = tiles.get(i).dx(dx).dy(dy);
					if (!tiles.contains(candidate) && map.canStep(candidate.getX(), candidate.getY(), 0, -dx, -dy))
					{
						off = candidate;
						k = i;
					}
				}
			}
		}
		assertNotNull("a walkable tile beside the route", off);
		PathResult rest = MobilePathfinder.reuse(cached(route, 0), off, FALADOR, map, POLICY, 1_000);
		assertNotNull(rest);
		assertEquals(off, rest.getTiles().get(0));
		assertTrue("continues from the furthest neighbouring tile", tiles.indexOf(rest.getTiles().get(1)) >= k);
		assertEquals("same end as the planned route", tiles.get(tiles.size() - 1), rest.getTiles().get(rest.getTiles().size() - 1));
	}

	@Test
	public void nothingIsReusedForAnotherGoalPolicyAgeOrAFarStart()
	{
		WorldPoint onRoute = route.getTiles().get(5);
		assertNull(MobilePathfinder.reuse(null, onRoute, FALADOR, map, POLICY, 0));
		assertNull("another goal", MobilePathfinder.reuse(cached(route, 0), onRoute, FALADOR.dx(1), map, POLICY, 0));
		assertNull("another policy", MobilePathfinder.reuse(cached(route, 0), onRoute, FALADOR, map,
			MobileTransportPolicy.admitting(EnumSet.of(TransportType.TRANSPORT)), 0));
		assertNull("expired", MobilePathfinder.reuse(cached(route, 0), onRoute, FALADOR, map, POLICY,
			MobilePathfinder.ROUTE_REUSE_MILLIS + 1));
		assertNull("far from the route", MobilePathfinder.reuse(cached(route, 0), new WorldPoint(3222, 3218, 0), FALADOR, map, POLICY, 0));
	}

	@Test
	public void warmUpLoadsTheSharedDataOnceInTheBackground() throws Exception
	{
		MobilePathfinder.warmUp();
		assertFalse("without a client nothing starts", threadRunning());
		net.runelite.client.plugins.microbot.RuntimeFixture fixture = new net.runelite.client.plugins.microbot.RuntimeFixture().installLoggedIn();
		try
		{
			MobilePathfinder.setTransportPolicy(POLICY);
			MobilePathfinder.warmUp();
			MobilePathfinder.warmUp();
			java.lang.reflect.Field shared = MobilePathfinder.class.getDeclaredField("sharedConfig");
			shared.setAccessible(true);
			long deadline = System.currentTimeMillis() + 30_000;
			while (shared.get(null) == null && System.currentTimeMillis() < deadline)
			{
				Thread.sleep(50);
			}
			assertNotNull("loaded in the background", shared.get(null));
			assertTrue(Thread.getAllStackTraces().keySet().stream()
				.filter(t -> t.getName().equals("pathfinder-warmup")).count() <= 1);
		}
		finally
		{
			net.runelite.client.plugins.microbot.RuntimeFixture.reset();
		}
	}

	private static boolean threadRunning()
	{
		return Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.getName().equals("pathfinder-warmup"));
	}

	@Test
	public void aRouteWithATeleportAheadIsSearchedAgain()
	{
		MobileTransportPolicy items = MobileTransportPolicy.admitting(
			EnumSet.of(TransportType.TRANSPORT, TransportType.AGILITY_SHORTCUT, TransportType.TELEPORTATION_ITEM));
		PathfinderConfig config = MobileRouteTest.offlineConfig(items,
			t -> t.getType() != TransportType.TELEPORTATION_ITEM || "Varrock tablet".equals(t.getDisplayInfo()));
		WorldPoint varrock = new WorldPoint(3213, 3424, 0);
		PathResult teleport = MobilePathfinder.search(config, new WorldPoint(3222, 3218, 0), varrock);
		assertTrue(teleport.isFound());
		assertTrue(teleport.getTransports().values().stream().anyMatch(s -> s.getType() == TransportType.TELEPORTATION_ITEM));
		assertNull(MobilePathfinder.reuse(new MobilePathfinder.CachedRoute(teleport, items, 0),
			teleport.getTiles().get(0), varrock, map, items, 0));
	}
}
