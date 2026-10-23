package net.runelite.client.plugins.microbot.shortestpath;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.RuntimeFixture;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathTerminationReason;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Entry-point contract: policy, status mapping and threading. No game client. */
public class MobilePathfinderTest
{
	private static final WorldPoint DRAYNOR_BANK = new WorldPoint(3092, 3245, 0);
	/** Walled pocket south of Draynor bank (found by MobileRouteTest's collision probe). */
	private static final WorldPoint DRAYNOR_ENCLOSED = new WorldPoint(3088, 3235, 0);

	@After
	public void tearDown()
	{
		RuntimeFixture.reset();
	}

	@Test
	public void defaultPolicyAdmitsOnlyObjectTransportsAndShortcuts()
	{
		Map<WorldPoint, Set<Transport>> catalogue = Transport.loadAllFromResources();
		Set<TransportType> admittedTypes = catalogue.values().stream()
			.flatMap(Set::stream)
			.filter(MobileTransportPolicy.DEFAULT::isAdmitted)
			.map(Transport::getType)
			.collect(Collectors.toSet());
		assertEquals(MobileTransportPolicy.DEFAULT_TYPES, admittedTypes);

		long excluded = catalogue.values().stream().flatMap(Set::stream)
			.filter(t -> t.getType() == TransportType.TELEPORTATION_SPELL || t.getType() == TransportType.FAIRY_RING
				|| t.getType() == TransportType.SHIP || t.getType() == TransportType.SPIRIT_TREE)
			.filter(MobileTransportPolicy.DEFAULT::isAdmitted)
			.count();
		assertEquals(0, excluded);
		assertTrue(MobileTransportPolicy.admitting(Set.of(TransportType.SHIP)).isAdmitted(
			catalogue.values().stream().flatMap(Set::stream).filter(t -> t.getType() == TransportType.SHIP).findFirst().orElseThrow()));
	}

	@Test
	public void enclosedGoalIsPartialAndDoesNotPretendToArrive()
	{
		PathResult result = MobilePathfinder.search(MobileRouteTest.offlineConfig(MobileTransportPolicy.DEFAULT),
			DRAYNOR_BANK, DRAYNOR_ENCLOSED);
		assertEquals(PathResult.Status.PARTIAL, result.getStatus());
		assertFalse(result.isFound());
		List<WorldPoint> tiles = result.getTiles();
		assertNotEquals(DRAYNOR_ENCLOSED, tiles.get(tiles.size() - 1));
		assertTrue(tiles.get(tiles.size() - 1).distanceTo(DRAYNOR_ENCLOSED) <= 3);
	}

	@Test
	public void terminationReasonsMapToStatuses()
	{
		WorldPoint a = new WorldPoint(3200, 3200, 0);
		WorldPoint b = new WorldPoint(3201, 3200, 0);
		assertEquals(PathResult.Status.CANCELLED, mapped(PathTerminationReason.CANCELLED, List.of(a, b)));
		assertEquals(PathResult.Status.TIMED_OUT, mapped(PathTerminationReason.CUTOFF_REACHED, List.of(a, b)));
		assertEquals(PathResult.Status.FAILED, mapped(PathTerminationReason.FAILED, List.of(a)));
		assertEquals(PathResult.Status.FAILED, mapped(null, List.of(a)));
		assertEquals(PathResult.Status.UNREACHABLE, mapped(PathTerminationReason.SEARCH_EXHAUSTED, List.of(a)));
		assertEquals(PathResult.Status.PARTIAL, mapped(PathTerminationReason.SEARCH_EXHAUSTED, List.of(a, b)));
		assertEquals(PathResult.Status.FOUND, mapped(PathTerminationReason.TARGET_REACHED, List.of(a, b)));
	}

	private static PathResult.Status mapped(PathTerminationReason reason, List<WorldPoint> tiles)
	{
		Pathfinder pathfinder = mock(Pathfinder.class);
		when(pathfinder.getTerminationReason()).thenReturn(reason);
		when(pathfinder.getPath()).thenReturn(tiles);
		when(pathfinder.getPathEdges()).thenReturn(Collections.emptyList());
		return MobilePathfinder.toResult(pathfinder, tiles.get(0), new WorldPoint(3300, 3300, 0)).getStatus();
	}

	@Test
	public void findWithoutAnInstalledRuntimeFails()
	{
		PathResult result = MobilePathfinder.find(DRAYNOR_BANK, DRAYNOR_ENCLOSED);
		assertEquals(PathResult.Status.FAILED, result.getStatus());
		assertTrue(result.getTiles().isEmpty());
	}

	@Test
	public void findRefusesToRunOnTheClientThread()
	{
		RuntimeFixture fixture = new RuntimeFixture().installLoggedIn();
		when(fixture.client.isClientThread()).thenReturn(true);
		try
		{
			MobilePathfinder.find(DRAYNOR_BANK, DRAYNOR_ENCLOSED);
			fail("the search must never run on the client thread");
		}
		catch (IllegalStateException expected)
		{
			// as documented
		}
	}

	@Test
	public void resultIsImmutable()
	{
		PathResult result = MobilePathfinder.search(MobileRouteTest.offlineConfig(MobileTransportPolicy.DEFAULT),
			DRAYNOR_BANK, new WorldPoint(3087, 3238, 0));
		try
		{
			result.getTiles().clear();
			fail("tiles must be unmodifiable");
		}
		catch (UnsupportedOperationException expected)
		{
			// walker cannot mutate a shared route
		}
	}
}
