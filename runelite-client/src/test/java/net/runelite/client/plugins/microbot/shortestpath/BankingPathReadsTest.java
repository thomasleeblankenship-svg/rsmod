package net.runelite.client.plugins.microbot.shortestpath;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

/** C2-2 banking reads with upstream's selection rules; bank and carried quantities injected. */
public class BankingPathReadsTest
{
	private static final IntUnaryOperator NONE = id -> 0;

	private static Transport first(TransportType type, java.util.function.Predicate<Transport> filter)
	{
		return MobileRouteTest.catalogue().values().stream().flatMap(Set::stream)
			.filter(t -> t.getType() == type).filter(filter).findFirst().orElseThrow();
	}

	private static Map<Integer, Integer> missing(Transport transport, IntUnaryOperator banked, IntUnaryOperator carried, boolean elite)
	{
		return WalkerPathReads.getMissingTransportItemIdsWithQuantities(List.of(transport), banked, carried, () -> elite);
	}

	@Test
	public void aFareIsWithdrawnAsItsCurrency()
	{
		Transport carpet = first(TransportType.MAGIC_CARPET, t -> t.getCurrencyAmount() > 0);
		assertEquals(Map.of(ItemID.COINS, carpet.getCurrencyAmount()), missing(carpet, NONE, NONE, false));
	}

	@Test
	public void anItemIsWithdrawnOnlyWhenNotCarriedPreferringOneTheBankHasEnoughOf()
	{
		Transport canoe = first(TransportType.CANOE, t -> true);
		assertEquals(Map.of(ItemID.RUNE_AXE, 1), missing(canoe, id -> id == ItemID.RUNE_AXE ? 1 : 0, NONE, false));
		assertTrue("an axe is carried", missing(canoe, id -> 1, id -> id == ItemID.BRONZE_AXE ? 1 : 0, false).isEmpty());
	}

	@Test
	public void aFairyRingNeedsAStaffUntilTheEliteDiary()
	{
		Transport ring = first(TransportType.FAIRY_RING, t -> t.getItemRequirements() == null || t.getItemRequirements().isEmpty());
		Map<Integer, Integer> staff = missing(ring, id -> id == ItemID.DRAMEN_STAFF ? 1 : 0, NONE, false);
		assertEquals(Map.of(ItemID.DRAMEN_STAFF, 1), staff);
		assertTrue("Lumbridge elite done", missing(ring, id -> 1, NONE, true).isEmpty());
		assertTrue("staff wielded", missing(ring, id -> 1, id -> id == ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF ? 1 : 0, false).isEmpty());
	}

	@Test
	public void anUnbankedItemSoldAtItsTransportIsBoughtWithItsFare()
	{
		Transport sold = MobileRouteTest.catalogue().values().stream().flatMap(Set::stream)
			.filter(t -> t.getItemRequirements() != null && t.getItemRequirements().size() == 1)
			.filter(t -> t.getItemRequirements().get(0).getItemIds().stream().anyMatch(id -> PurchasableItemCatalog.byItemId(id) != null))
			.findFirst().orElse(null);
		assertNotNull("the catalogue sells some transport item at its transport (Shantay pass)", sold);
		Map<Integer, Integer> fare = missing(sold, NONE, NONE, true);
		assertEquals(1, fare.size());
		assertTrue(fare.containsKey(ItemID.COINS) || fare.containsKey(ItemID.ECTOTOKEN));
	}

	@Test
	public void onlyBankPlannedTransportsOnTheRouteAreListedInRouteOrder()
	{
		Transport canoe = MobileRouteTest.catalogue().values().stream().flatMap(Set::stream)
			.filter(t -> t.getType() == TransportType.CANOE)
			.max(Comparator.comparingInt(t -> t.getOrigin().distanceTo2D(t.getDestination()))).orElseThrow();
		MobileTransportPolicy policy = MobileTransportPolicy.admitting(
			EnumSet.of(TransportType.TRANSPORT, TransportType.AGILITY_SHORTCUT, TransportType.CANOE));
		PathResult route = MobilePathfinder.search(MobileRouteTest.offlineConfig(policy,
			t -> t.getType() != TransportType.TRANSPORT || t.getItemIdRequirements() == null || t.getItemIdRequirements().isEmpty()),
			canoe.getOrigin(), canoe.getDestination());
		assertTrue(route.isFound());
		List<Transport> planned = WalkerPathReads.bankPlanned(route);
		assertFalse(planned.isEmpty());
		assertTrue(planned.stream().allMatch(t -> t.getType() == TransportType.CANOE));

		PathResult walk = MobilePathfinder.search(MobileRouteTest.offlineConfig(MobileTransportPolicy.DEFAULT),
			new WorldPoint(3092, 3245, 0), new WorldPoint(2965, 3378, 0));
		assertTrue("free gates and doors need no bank trip", WalkerPathReads.bankPlanned(walk).stream()
			.collect(Collectors.toList()).isEmpty());
	}
}
