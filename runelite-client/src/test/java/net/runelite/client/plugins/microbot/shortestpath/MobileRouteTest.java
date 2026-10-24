package net.runelite.client.plugins.microbot.shortestpath;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Routes over the real whole-world collision map and transport tables, no game client.
 * <p>
 * The usable transports are what a fresh free-to-play account could use under
 * {@link MobileTransportPolicy#DEFAULT}: catalogue rows the policy admits with no quest, item,
 * skill, varbit or currency requirement. In the game, {@code PathfinderConfig.refresh} computes
 * this set from the real account; here it is injected exactly as upstream's
 * {@code WalkerRouteCorpusTest} does. Every step is checked independently of the search: a
 * walking step must be one tile and allowed by the collision map; any other step must be a
 * transport. These tests are planning evidence only, not evidence the walker executes the route.
 */
public class MobileRouteTest
{
	private static final WorldPoint LUMBRIDGE_TOP_FLOOR_BANK = new WorldPoint(3208, 3220, 2);
	private static final WorldPoint LUMBRIDGE_TREES = new WorldPoint(3183, 3227, 0);
	private static final WorldPoint DRAYNOR_BANK = new WorldPoint(3092, 3245, 0);
	private static final WorldPoint DRAYNOR_WILLOWS = new WorldPoint(3087, 3238, 0);
	private static final WorldPoint VARROCK_WEST_BANK = new WorldPoint(3185, 3436, 0);
	private static final WorldPoint GRAND_EXCHANGE = new WorldPoint(3164, 3485, 0);
	private static final WorldPoint FALADOR_EAST_BANK = new WorldPoint(3013, 3355, 0);
	private static final WorldPoint FALADOR_PARK = new WorldPoint(2995, 3376, 0);

	private static SplitFlagMap collisionMap;
	private static Map<WorldPoint, Set<Transport>> allTransports;

	@BeforeClass
	public static void load()
	{
		ensureLoaded();
	}

	/** Loads the bundled collision map and catalogue once, whichever test class asks first. */
	/** The whole catalogue as loaded from resources (shared, do not modify). */
	static Map<WorldPoint, Set<Transport>> catalogue()
	{
		ensureLoaded();
		return allTransports;
	}

	private static synchronized void ensureLoaded()
	{
		if (collisionMap == null)
		{
			collisionMap = SplitFlagMap.fromResources();
			allTransports = Transport.loadAllFromResources();
		}
	}

	private static boolean freshFreeToPlay(Transport t)
	{
		return !t.isMembers()
			&& (t.getQuests() == null || t.getQuests().isEmpty())
			&& (t.getItemIdRequirements() == null || t.getItemIdRequirements().isEmpty())
			&& (t.getVarbits() == null || t.getVarbits().isEmpty())
			&& (t.getVarplayers() == null || t.getVarplayers().isEmpty())
			&& t.getCurrencyAmount() <= 0
			&& Arrays.stream(t.getSkillLevels()).allMatch(level -> level <= 1);
	}

	/** Offline config: the usable set injected as upstream's route corpus does (test-only reflection). */
	static PathfinderConfig offlineConfig(MobileTransportPolicy policy)
	{
		return offlineConfig(policy, MobileRouteTest::freshFreeToPlay);
	}

	/**
	 * Offline config admitting the transports {@code policy} admits and {@code usable} accepts, as if
	 * the player met their requirements; origin-less teleports become usable teleports.
	 */
	@SuppressWarnings("unchecked")
	static PathfinderConfig offlineConfig(MobileTransportPolicy policy, java.util.function.Predicate<Transport> usable)
	{
		ensureLoaded();
		PathfinderConfig config = new PathfinderConfig(
			collisionMap, new HashMap<>(), Collections.emptyList(), null, null, policy);
		try
		{
			Field cutoff = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
			cutoff.setAccessible(true);
			cutoff.setLong(config, 10_000);

			Field transportsField = PathfinderConfig.class.getDeclaredField("transports");
			transportsField.setAccessible(true);
			Map<WorldPoint, Set<Transport>> transports = (Map<WorldPoint, Set<Transport>>) transportsField.get(config);
			Field packedField = PathfinderConfig.class.getDeclaredField("transportsPacked");
			packedField.setAccessible(true);
			PrimitiveIntHashMap<Set<Transport>> packed = (PrimitiveIntHashMap<Set<Transport>>) packedField.get(config);

			Field teleportsField = PathfinderConfig.class.getDeclaredField("usableTeleports");
			teleportsField.setAccessible(true);
			Set<Transport> teleports = (Set<Transport>) teleportsField.get(config);

			for (Map.Entry<WorldPoint, Set<Transport>> entry : allTransports.entrySet())
			{
				Set<Transport> admitted = entry.getValue().stream()
					.filter(policy::isAdmitted)
					.filter(usable)
					.collect(Collectors.toSet());
				if (admitted.isEmpty())
				{
					continue;
				}
				if (entry.getKey() == null)
				{
					teleports.addAll(admitted);
					continue;
				}
				transports.put(entry.getKey(), admitted);
				packed.put(WorldPointUtil.packWorldPoint(entry.getKey()), admitted);
			}
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError("offline transport injection failed", e);
		}
		return config;
	}

	private static PathResult route(WorldPoint from, WorldPoint to)
	{
		PathfinderConfig config = offlineConfig(MobileTransportPolicy.DEFAULT);
		PathResult result = MobilePathfinder.search(config, from, to);
		assertEquals(from + " -> " + to + ": " + result, PathResult.Status.FOUND, result.getStatus());
		List<WorldPoint> tiles = result.getTiles();
		assertEquals(from, tiles.get(0));
		assertEquals(to, tiles.get(tiles.size() - 1));
		assertEveryStepIsLegal(config.getMap(), result);
		return result;
	}

	/** Independent of the search: walking steps are single collision-legal tiles, the rest transports. */
	private static void assertEveryStepIsLegal(CollisionMap map, PathResult result)
	{
		List<WorldPoint> tiles = result.getTiles();
		for (int i = 0; i + 1 < tiles.size(); i++)
		{
			WorldPoint a = tiles.get(i);
			WorldPoint b = tiles.get(i + 1);
			PathResult.TransportStep transport = result.transportAt(i);
			if (transport != null)
			{
				assertEquals("transport origin at step " + i, a, transport.getOrigin());
				assertEquals("transport destination at step " + i, b, transport.getDestination());
				assertTrue("only admitted types: " + transport, MobileTransportPolicy.DEFAULT_TYPES.contains(transport.getType()));
				continue;
			}
			int dx = b.getX() - a.getX();
			int dy = b.getY() - a.getY();
			assertEquals("walking stays on one floor at step " + i + ": " + a + " -> " + b, a.getPlane(), b.getPlane());
			assertTrue("walking step " + i + " is one tile: " + a + " -> " + b, Math.abs(dx) <= 1 && Math.abs(dy) <= 1);
			assertTrue("walking step " + i + " crosses a blocked edge: " + a + " -> " + b,
				map.canStep(a.getX(), a.getY(), a.getPlane(), dx, dy));
		}
	}

	private static List<PathResult.TransportStep> transports(PathResult result)
	{
		return List.copyOf(result.getTransports().values());
	}

	/**
	 * The castle staircase has a direct "Bottom-floor"/"Top-floor" option (objects 56230/56231 in
	 * upstream's catalogue) as well as one-flight climbs, so the route may use one transport or
	 * two. Either way every transport is a staircase and the floors chain without gaps.
	 */
	private static void assertStaircaseChain(PathResult result, int fromPlane, int toPlane)
	{
		List<PathResult.TransportStep> steps = transports(result);
		assertFalse("needs a staircase: " + result, steps.isEmpty());
		int plane = fromPlane;
		for (PathResult.TransportStep step : steps)
		{
			assertEquals("staircase object: " + step, "Staircase", step.getName());
			assertEquals("continuous floors: " + steps, plane, step.getOrigin().getPlane());
			plane = step.getDestination().getPlane();
		}
		assertEquals("ends on the target floor: " + steps, toPlane, plane);
	}

	@Test
	public void lumbridgeTopFloorBankToTreesTakesTheStairsDown()
	{
		assertStaircaseChain(route(LUMBRIDGE_TOP_FLOOR_BANK, LUMBRIDGE_TREES), 2, 0);
	}

	@Test
	public void lumbridgeTreesBackToTopFloorBankTakesTheStairsUp()
	{
		assertStaircaseChain(route(LUMBRIDGE_TREES, LUMBRIDGE_TOP_FLOOR_BANK), 0, 2);
	}

	@Test
	public void draynorBankToWillowsIsAGroundWalk()
	{
		PathResult result = route(DRAYNOR_BANK, DRAYNOR_WILLOWS);
		assertTrue("no transports: " + result, result.getTransports().isEmpty());
	}

	@Test
	public void varrockWestBankToGrandExchange()
	{
		PathResult result = route(VARROCK_WEST_BANK, GRAND_EXCHANGE);
		assertTrue("stays on the surface", result.getTiles().stream().allMatch(p -> p.getPlane() == 0 && p.getY() < 9000));
	}

	@Test
	public void faladorEastBankToPark()
	{
		PathResult result = route(FALADOR_EAST_BANK, FALADOR_PARK);
		assertTrue("stays on the surface", result.getTiles().stream().allMatch(p -> p.getPlane() == 0 && p.getY() < 9000));
	}
}
