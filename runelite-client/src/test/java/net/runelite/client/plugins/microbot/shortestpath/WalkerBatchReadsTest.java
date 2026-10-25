package net.runelite.client.plugins.microbot.shortestpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import org.junit.Test;

/** The 13-task walker batch's path reads (upstream Rs2Walker 2.6.38), on the real transport catalogue and map. */
public class WalkerBatchReadsTest
{
	private static final IntUnaryOperator NOTHING = id -> 0;

	private static Transport first(TransportType type, java.util.function.Predicate<Transport> extra)
	{
		return MobileRouteTest.catalogue().values().stream().flatMap(Set::stream)
			.filter(t -> t.getType() == type).filter(extra).findFirst().orElseThrow();
	}

	@Test
	public void routeEndTouchingTheObjectFootprintCountsAsReachable()
	{
		WorldPoint booth = new WorldPoint(3200, 3200, 0);
		assertTrue(WalkerPathReads.endpointReaches(new WorldPoint(3201, 3200, 0), booth, 1, 1, 2, 2));
		assertTrue(WalkerPathReads.endpointReaches(new WorldPoint(3202, 3200, 0), booth, 1, 1, 3, 3));
		assertFalse(WalkerPathReads.endpointReaches(new WorldPoint(3206, 3200, 0), booth, 1, 1, 2, 2));
		// As upstream, the area test is 2D: the floor is not compared.
		assertTrue(WalkerPathReads.endpointReaches(new WorldPoint(3201, 3200, 1), booth, 1, 1, 2, 2));
	}

	@Test
	public void nearestAccessibleTargetMatchesTheRouteEnd()
	{
		List<WorldPoint> targets = List.of(BankLocation.LUMBRIDGE_TOP.getWorldPoint(), BankLocation.DRAYNOR_VILLAGE.getWorldPoint(),
			BankLocation.VARROCK_WEST.getWorldPoint());
		WorldPoint draynorWillows = new WorldPoint(3087, 3235, 0);
		PathResult route = MobilePathfinder.search(MobileRouteTest.offlineConfig(MobileTransportPolicy.DEFAULT), draynorWillows,
			new java.util.HashSet<>(targets));
		assertTrue(route.getStatus().toString(), route.isFound());
		WorldPoint end = route.getTiles().get(route.getTiles().size() - 1);
		assertEquals("Draynor is the nearest from its willows", 1, WalkerPathReads.matchTarget(end, targets, 2));
		assertEquals(-1, WalkerPathReads.matchTarget(new WorldPoint(3000, 3000, 0), targets, 2));
	}

	@Test
	public void transportsAlongAPathAreFoundInOrderAndJumpToTheirDestination()
	{
		Transport ladder = first(TransportType.TRANSPORT, t -> t.getOrigin() != null && t.getDestination() != null
			&& t.getOrigin().getPlane() == 0);
		WorldPoint before = new WorldPoint(ladder.getOrigin().getX() - 1, ladder.getOrigin().getY(), 0);
		WorldPoint after = new WorldPoint(ladder.getDestination().getX() + 1, ladder.getDestination().getY(), ladder.getDestination().getPlane());
		List<WorldPoint> path = List.of(before, ladder.getOrigin(), ladder.getDestination(), after);
		Map<WorldPoint, Set<Transport>> map = Map.of(ladder.getOrigin(), Set.of(ladder));
		assertEquals(List.of(ladder), WalkerPathReads.transportsForPath(path, 0, TransportType.TELEPORTATION_ITEM, map, 0));
		assertTrue("start index past the origin", WalkerPathReads.transportsForPath(path, 2, TransportType.TELEPORTATION_ITEM, map, 0).isEmpty());
		assertTrue("player on another plane", WalkerPathReads.transportsForPath(path, 0, TransportType.TELEPORTATION_ITEM, map, 3).isEmpty());
		assertTrue("bad index", WalkerPathReads.transportsForPath(path, 9, TransportType.TELEPORTATION_ITEM, map, 0).isEmpty());
	}

	@Test
	public void requiredItemsFollowUpstreamPerType()
	{
		Transport fairyRing = first(TransportType.FAIRY_RING, t -> true);
		assertFalse(WalkerPathReads.hasRequiredTransportItems(fairyRing, NOTHING, NOTHING, () -> false));
		assertTrue("elite diary", WalkerPathReads.hasRequiredTransportItems(fairyRing, NOTHING, NOTHING, () -> true));
		assertTrue("dramen staff", WalkerPathReads.hasRequiredTransportItems(fairyRing,
			id -> id == net.runelite.api.gameval.ItemID.DRAMEN_STAFF ? 1 : 0, NOTHING, () -> false));

		Transport plain = first(TransportType.TRANSPORT, t -> t.getCurrencyAmount() == 0
			&& (t.getItemIdRequirements() == null || t.getItemIdRequirements().isEmpty()));
		assertTrue("a free plain transport needs nothing", WalkerPathReads.hasRequiredTransportItems(plain, NOTHING, NOTHING, () -> false));

		Transport item = first(TransportType.TELEPORTATION_ITEM, t -> t.getItemRequirements() != null && !t.getItemRequirements().isEmpty());
		assertFalse(WalkerPathReads.hasRequiredTransportItems(item, NOTHING, NOTHING, () -> false));
		assertTrue(WalkerPathReads.hasRequiredTransportItems(item, id -> 1000, NOTHING, () -> false));

		assertFalse(WalkerPathReads.hasRequiredTransportItems(null, NOTHING, NOTHING, () -> true));
	}

	@Test
	public void fareOnlyTransportNeedsTheCurrencyInTheInventory()
	{
		List<Transport> fares = MobileRouteTest.catalogue().values().stream().flatMap(Set::stream)
			.filter(t -> (t.getType() == TransportType.BOAT || t.getType() == TransportType.CHARTER_SHIP || t.getType() == TransportType.SHIP)
				&& t.getCurrencyAmount() > 0 && "coins".equalsIgnoreCase(t.getCurrencyName())
				&& (t.getItemIdRequirements() == null || t.getItemIdRequirements().isEmpty()))
			.limit(1).collect(Collectors.toList());
		assertFalse("the catalogue has a coin fare", fares.isEmpty());
		Transport fare = fares.get(0);
		int coins = net.runelite.api.gameval.ItemID.COINS;
		assertFalse(WalkerPathReads.hasRequiredTransportItems(fare, NOTHING, NOTHING, () -> false));
		assertTrue(WalkerPathReads.hasRequiredTransportItems(fare, NOTHING, id -> id == coins ? fare.getCurrencyAmount() : 0, () -> false));
	}
}
