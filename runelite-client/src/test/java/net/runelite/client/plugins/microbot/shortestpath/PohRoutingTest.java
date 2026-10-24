package net.runelite.client.plugins.microbot.shortestpath;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.poh.PohLayout;
import net.runelite.client.plugins.microbot.util.poh.data.HouseLocation;
import net.runelite.client.plugins.microbot.util.poh.data.JewelleryBox;
import net.runelite.client.plugins.microbot.util.poh.data.JewelleryBoxType;
import net.runelite.client.plugins.microbot.util.poh.data.NexusPortal;
import net.runelite.client.plugins.microbot.util.poh.data.PohPortal;
import org.junit.Test;
import static org.junit.Assert.*;

/** C2-1: POH transports built from a detected house, and routes through the house. No game client. */
public class PohRoutingTest
{
	private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);

	private static PohLayout layout(boolean withFairyRing)
	{
		Map<PohPortal, PohLayout.Device> portals = new EnumMap<>(PohPortal.class);
		portals.put(PohPortal.CAMELOT, new PohLayout.Device(13625, "Camelot Portal"));
		Map<PohDevice, PohLayout.Device> devices = new EnumMap<>(PohDevice.class);
		devices.put(PohDevice.NEXUS, new PohLayout.Device(NexusPortal.PORTAL_IDS[0], "Portal Nexus"));
		devices.put(PohDevice.JEWELLERY_BOX, new PohLayout.Device(JewelleryBoxType.ORNATE.getObjectId(), "Ornate jewellery box"));
		if (withFairyRing)
		{
			devices.put(PohDevice.FAIRY_RING, new PohLayout.Device(29228, "Fairy ring"));
		}
		return new PohLayout(HouseLocation.RIMMINGTON, portals, Set.of(NexusPortal.FALADOR), JewelleryBoxType.ORNATE, devices);
	}

	private static List<PathResult.TransportStep> steps(Map<WorldPoint, Set<Transport>> transports)
	{
		return transports.values().stream().flatMap(Set::stream).map(PathResult.TransportStep::new).collect(Collectors.toList());
	}

	@Test
	public void theHouseTileIsSearchableButNotWalkable()
	{
		net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap map =
			MobileRouteTest.offlineConfig(MobileTransportPolicy.DEFAULT).getMap();
		WorldPoint house = PohPanel.HOUSE_TILE;
		assertFalse("no collision data: no walking in or out", map.hasRegion(house.getX(), house.getY()));
		assertTrue(map.isBlocked(house.getX(), house.getY(), house.getPlane()));
	}

	@Test
	public void waysInLandOnTheHouseTile()
	{
		Map<WorldPoint, Set<Transport>> in = PohPanel.getTransportsToPoh(layout(false));
		assertEquals(3, in.get(null).size());
		WorldPoint outside = HouseLocation.RIMMINGTON.getPortalLocation();
		PathResult.TransportStep portal = new PathResult.TransportStep(in.get(outside).iterator().next());
		assertEquals("Home", portal.getAction());
		assertEquals(HouseLocation.RIMMINGTON.getPortalId(), portal.getObjectId());
		assertTrue(steps(in).stream().allMatch(PathResult.TransportStep::isIntoHouse));
		assertTrue(PohPanel.getTransportsToPoh(PohLayout.EMPTY).isEmpty());
	}

	@Test
	public void waysOutNameTheirDeviceObjectAndOption()
	{
		List<PathResult.TransportStep> out = steps(PohPanel.getAvailableTransports(layout(false), Map.of(), true));
		PathResult.TransportStep camelot = out.stream().filter(s -> s.getPohDevice() == PohDevice.PORTAL).findFirst().orElseThrow();
		assertEquals("Enter", camelot.getAction());
		assertEquals("Camelot Portal", camelot.getName());
		assertEquals(13625, camelot.getObjectId());
		assertEquals(PohPanel.HOUSE_TILE, camelot.getOrigin());
		assertEquals(PohPortal.CAMELOT.getDestination(), camelot.getDestination());

		List<PathResult.TransportStep> nexus = out.stream().filter(s -> s.getPohDevice() == PohDevice.NEXUS).collect(Collectors.toList());
		assertEquals(1, nexus.size());
		assertEquals("Falador", nexus.get(0).getPohOption());
		assertEquals("Teleport Menu", nexus.get(0).getAction());
		assertEquals(JewelleryBox.getAvailableTeleports(JewelleryBoxType.ORNATE).size(),
			out.stream().filter(s -> s.getPohDevice() == PohDevice.JEWELLERY_BOX).count());
		assertTrue("every way out names its device object", out.stream().allMatch(s -> s.getObjectId() > 0 && !s.getName().isEmpty()));
		assertTrue(PohPanel.getAvailableTransports(layout(false), Map.of(), false).values().stream().flatMap(Set::stream)
			.map(PathResult.TransportStep::new).noneMatch(s -> s.getPohDevice() == PohDevice.NEXUS));
		assertTrue(PohPanel.getAvailableTransports(PohLayout.EMPTY, Map.of(), true).isEmpty());
	}

	@Test
	public void theHouseFairyRingJoinsEveryRingBothWays()
	{
		Map<WorldPoint, Set<Transport>> catalogue = MobileRouteTest.catalogue();
		Map<WorldPoint, Set<Transport>> house = PohPanel.getAvailableTransports(layout(true), catalogue, true);
		List<PathResult.TransportStep> out = steps(Map.of(PohPanel.HOUSE_TILE, house.get(PohPanel.HOUSE_TILE))).stream()
			.filter(s -> s.getPohDevice() == PohDevice.FAIRY_RING).collect(Collectors.toList());
		assertFalse(out.isEmpty());
		assertTrue(out.stream().allMatch(s -> s.getPohOption() != null && s.getPohOption().matches("[A-D][I-L][P-S].*")));
		long into = house.entrySet().stream().filter(e -> e.getKey() != PohPanel.HOUSE_TILE).flatMap(e -> e.getValue().stream())
			.filter(t -> t.getType() == TransportType.FAIRY_RING && PohPanel.HOUSE_TILE.equals(t.getDestination())).count();
		assertTrue("every ring can dial the house (DIQ)", into > 10);
	}

	@Test
	public void aRouteGoesInBySpellAndOutThroughAPortal()
	{
		Map<WorldPoint, Set<Transport>> extra = new HashMap<>(PohPanel.getTransportsToPoh(layout(false)));
		PohPanel.getAvailableTransports(layout(false), Map.of(), true)
			.forEach((k, v) -> extra.computeIfAbsent(k, x -> new java.util.HashSet<>()).addAll(v));
		MobileTransportPolicy policy = MobileTransportPolicy.admitting(EnumSet.of(TransportType.TRANSPORT,
			TransportType.AGILITY_SHORTCUT, TransportType.TELEPORTATION_SPELL, TransportType.POH));
		PathResult route = MobilePathfinder.search(MobileRouteTest.offlineConfig(policy,
			t -> t.getType() == TransportType.POH || "Teleport to House".equals(t.getDisplayInfo())
				|| (t.getType() != TransportType.TELEPORTATION_SPELL && MobileRouteTest.freshFreeToPlayForTest(t)), extra),
			LUMBRIDGE, PohPortal.CAMELOT.getDestination());
		assertTrue(route.toString(), route.isFound());
		List<PathResult.TransportStep> used = List.copyOf(route.getTransports().values());
		assertEquals(2, used.size());
		assertTrue(used.get(0).isIntoHouse());
		assertEquals("Teleport to House", used.get(0).getDisplayInfo());
		assertEquals(PohDevice.PORTAL, used.get(1).getPohDevice());

		PathResult fromHouse = MobilePathfinder.search(MobileRouteTest.offlineConfig(policy,
			t -> t.getType() == TransportType.POH, extra), PohPanel.HOUSE_TILE, PohPortal.CAMELOT.getDestination());
		assertTrue(fromHouse.isFound());
		assertEquals(PohDevice.PORTAL, fromHouse.transportAt(0).getPohDevice());
	}
}
