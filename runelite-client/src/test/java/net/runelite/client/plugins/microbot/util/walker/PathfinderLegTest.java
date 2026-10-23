package net.runelite.client.plugins.microbot.util.walker;

import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.MobilePathfinder;
import net.runelite.client.plugins.microbot.shortestpath.PathResult;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.Test;
import org.mockito.MockedStatic;
import static net.runelite.client.plugins.microbot.shortestpath.PathResultFixtures.result;
import static net.runelite.client.plugins.microbot.shortestpath.PathResultFixtures.transport;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/** How the walker turns a pathfinder route into its next leg. */
public class PathfinderLegTest
{
	private static final WorldPoint A = new WorldPoint(3200, 3200, 0);
	private static final WorldPoint B = new WorldPoint(3201, 3200, 0);
	private static final WorldPoint DOOR = new WorldPoint(3202, 3200, 0);
	private static final WorldPoint INSIDE = new WorldPoint(3203, 3200, 0);
	private static final WorldPoint GOAL = new WorldPoint(3204, 3200, 0);
	private static final List<WorldPoint> TILES = List.of(A, B, DOOR, INSIDE, GOAL);

	private static Rs2Walker.WalkLeg legFor(PathResult route)
	{
		try (MockedStatic<MobilePathfinder> pathfinder = mockStatic(MobilePathfinder.class))
		{
			pathfinder.when(() -> MobilePathfinder.find(any(), any())).thenReturn(route);
			return Rs2Walker.pathfinderLeg(A, GOAL, 0);
		}
	}

	@Test
	public void aRouteWithoutTransportsIsOneWalkingLeg()
	{
		Rs2Walker.WalkLeg leg = legFor(result(PathResult.Status.FOUND, TILES, Map.of()));
		assertNotNull(leg);
		assertNull(leg.stairs);
		assertEquals(GOAL, leg.destination);
		assertEquals(TILES, leg.route);
		assertTrue(leg.fromPathfinder);
	}

	@Test
	public void theFirstTransportEndsTheLegAndMustLandWithinOneTile()
	{
		Rs2Walker.WalkLeg leg = legFor(result(PathResult.Status.FOUND, TILES,
			Map.of(2, transport(DOOR, INSIDE, TransportType.TRANSPORT, "Open", "Door", 1530))));
		assertNotNull(leg);
		assertEquals(DOOR, leg.destination);
		assertEquals(List.of(A, B, DOOR), leg.route);
		assertEquals("Open", leg.stairs.action);
		assertEquals(1530, leg.stairs.objectId);
		assertEquals(1, leg.stairs.arrivalRadius);
		assertFalse("two tiles past the door is not arrival", leg.stairs.observedArrival(GOAL.dx(1)));
		assertTrue(leg.stairs.observedArrival(INSIDE));
	}

	@Test
	public void routesNeedingATransportTheWalkerCannotExecuteFallBack()
	{
		assertNull(legFor(result(PathResult.Status.FOUND, TILES,
			Map.of(1, transport(B, GOAL, TransportType.FAIRY_RING, "Use", "Fairy ring", 29495)))));
	}

	@Test
	public void incompleteRoutesFallBack()
	{
		assertNull(legFor(result(PathResult.Status.PARTIAL, TILES, Map.of())));
		assertNull(legFor(result(PathResult.Status.TIMED_OUT, TILES, Map.of())));
	}

	@Test
	public void aBoatBecomesATravelStepThatLandsWithinFiveTiles()
	{
		Rs2Walker.WalkLeg leg = legFor(result(PathResult.Status.FOUND, TILES,
			Map.of(1, transport(B, GOAL, TransportType.SHIP, "Musa Point", "Captain Tobias", 3644))));
		assertNotNull(leg);
		assertTrue(leg.stairs.travel);
		assertEquals(5, leg.stairs.arrivalRadius);
		assertEquals(List.of("Musa Point", "Travel"), Rs2Walker.npcOptions(leg.stairs));
		assertEquals(List.of("Captain Tobias", "Musa Point", "Yes"), List.of(Rs2Walker.travelOptions(leg.stairs)));
	}

	@Test
	public void anExplicitTravelOptionIsNotSupplementedWithTravel()
	{
		ObjectTransports.Step talk = new ObjectTransports.Step(A, GOAL, 601, "Talk-to", "Pirate Pete", 5, true, "Braindeath Island");
		assertEquals(List.of("Talk-to"), Rs2Walker.npcOptions(talk));
		assertEquals(List.of("Braindeath Island", "Yes"), List.of(Rs2Walker.travelOptions(talk)));
	}

	@Test
	public void aTeleportItemIsUsedWhereTheRouteReachesIt()
	{
		Rs2Walker.WalkLeg leg = legFor(result(PathResult.Status.FOUND, TILES,
			Map.of(0, transport(null, GOAL, TransportType.TELEPORTATION_ITEM, null, "Games necklace: Burthorpe", -1))));
		assertNotNull(leg);
		assertTrue(leg.stairs.item);
		assertEquals(A, leg.destination);
		assertEquals(8, leg.stairs.arrivalRadius);
		assertEquals("Games necklace", Rs2Walker.teleportItemName(leg.stairs));
		assertEquals("Burthorpe", Rs2Walker.teleportDestination(leg.stairs));
	}

	@Test
	public void disableTeleportsMakesTeleportRoutesFallBack()
	{
		Rs2Walker.disableTeleports = true;
		try
		{
			assertNull(legFor(result(PathResult.Status.FOUND, TILES,
				Map.of(0, transport(null, GOAL, TransportType.TELEPORTATION_ITEM, null, "Ring of dueling: Castle Wars", -1)))));
		}
		finally
		{
			Rs2Walker.disableTeleports = false;
		}
	}

	private static net.runelite.api.widgets.Widget widget(String text, String[] actions, net.runelite.api.widgets.Widget... children)
	{
		net.runelite.api.widgets.Widget widget = org.mockito.Mockito.mock(net.runelite.api.widgets.Widget.class);
		org.mockito.Mockito.when(widget.getText()).thenReturn(text);
		org.mockito.Mockito.when(widget.getActions()).thenReturn(actions);
		org.mockito.Mockito.when(widget.getDynamicChildren()).thenReturn(children);
		return widget;
	}

	@Test
	public void aCharterDestinationIsFoundAnywhereInTheMapIgnoringColourTags()
	{
		net.runelite.api.widgets.Widget catherby = widget("<col=ffffff>Catherby</col>", null);
		net.runelite.api.widgets.Widget sarim = widget("Port Sarim", null);
		net.runelite.api.widgets.Widget group = widget("", null, sarim, catherby);
		net.runelite.api.widgets.Widget root = widget("", null, group);
		assertSame(catherby, Rs2Walker.findCharterEntry(root, "catherby"));
		assertNull(Rs2Walker.findCharterEntry(root, "musa point"));
	}

	@Test
	public void aCharterShipIsATravelStep()
	{
		Rs2Walker.WalkLeg leg = legFor(result(PathResult.Status.FOUND, TILES,
			Map.of(1, transport(B, GOAL, TransportType.CHARTER_SHIP, "Charter", "Catherby", 9318))));
		assertNotNull(leg);
		assertTrue(leg.stairs.travel);
		assertEquals(List.of("Charter", "Travel"), Rs2Walker.npcOptions(leg.stairs));
	}

	@Test
	public void teleportItemsAreMatchedByIdWhenTheCatalogueListsThem()
	{
		ObjectTransports.Step byIds = new ObjectTransports.Step(A, GOAL, -1, "", "", 8, false, "Games necklace: Burthorpe", true,
			java.util.Set.of(3853, 3855));
		net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel necklace =
			org.mockito.Mockito.mock(net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel.class);
		org.mockito.Mockito.when(necklace.getId()).thenReturn(3855);
		org.mockito.Mockito.when(necklace.getName()).thenReturn("Games necklace(7)");
		net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel other =
			org.mockito.Mockito.mock(net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel.class);
		org.mockito.Mockito.when(other.getId()).thenReturn(3867);
		org.mockito.Mockito.when(other.getName()).thenReturn("Games necklace(1)");
		assertTrue(Rs2Walker.teleportItem(byIds, "Games necklace").test(necklace));
		assertFalse("ids, not the name, decide", Rs2Walker.teleportItem(byIds, "Games necklace").test(other));

		ObjectTransports.Step byName = new ObjectTransports.Step(A, GOAL, -1, "", "", 8, false, "Games necklace: Burthorpe", true);
		assertTrue(Rs2Walker.teleportItem(byName, "Games necklace").test(other));
	}

	@Test
	public void aTeleportSpellIsCastWhereTheRouteReachesIt()
	{
		Rs2Walker.WalkLeg leg = legFor(result(PathResult.Status.FOUND, TILES,
			Map.of(0, transport(null, GOAL, TransportType.TELEPORTATION_SPELL, null, "Varrock Teleport", -1))));
		assertNotNull(leg);
		assertTrue(leg.stairs.spell);
		assertFalse(leg.stairs.item);
		assertEquals("Varrock Teleport", leg.stairs.destinationText);
		assertEquals(A, leg.destination);
		Rs2Walker.disableTeleports = true;
		try
		{
			assertNull(legFor(result(PathResult.Status.FOUND, TILES,
				Map.of(0, transport(null, GOAL, TransportType.TELEPORTATION_SPELL, null, "Varrock Teleport", -1)))));
		}
		finally
		{
			Rs2Walker.disableTeleports = false;
		}
	}
}
