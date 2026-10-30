package net.runelite.client.plugins.microbot.util.walker;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.MobilePathfinder;
import net.runelite.client.plugins.microbot.shortestpath.PathResult;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WalkerPathReads;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.util.Global;
import org.junit.Test;
import org.mockito.MockedStatic;
import static net.runelite.client.plugins.microbot.shortestpath.PathResultFixtures.result;
import static net.runelite.client.plugins.microbot.shortestpath.PathResultFixtures.transport;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Walker batch W1: minigame legs, route values, the path facade, Stronghold doors and transport edges. */
public class WalkerBatchW1Test
{
	private static final WorldPoint A = new WorldPoint(3200, 3200, 0);
	private static final WorldPoint B = new WorldPoint(3201, 3200, 0);
	private static final WorldPoint CASTLE_WARS = new WorldPoint(2442, 3093, 0);
	private static final WorldPoint FEROX = new WorldPoint(3150, 3635, 0);

	private static Rs2Walker.WalkLeg legFor(PathResult route, WorldPoint goal)
	{
		try (MockedStatic<MobilePathfinder> pathfinder = mockStatic(MobilePathfinder.class))
		{
			pathfinder.when(() -> MobilePathfinder.find(any(), any())).thenReturn(route);
			return Rs2Walker.pathfinderLeg(A, goal, 0);
		}
	}

	@Test
	public void aMinigameTeleportIsALegUsedWhereTheRouteReachesIt()
	{
		Transport minigame = transport(B, CASTLE_WARS, TransportType.TELEPORTATION_MINIGAME, null, "Castle Wars", -1);
		Rs2Walker.WalkLeg leg = legFor(result(PathResult.Status.FOUND, List.of(A, B, CASTLE_WARS), Map.of(1, minigame)), CASTLE_WARS);
		assertNotNull(leg);
		assertTrue(leg.stairs.minigame);
		assertEquals("Castle Wars", leg.stairs.destinationText);
		assertEquals(B, leg.destination);
		assertTrue(leg.stairs.observedArrival(new WorldPoint(2443, 3092, 0)));
	}

	@Test
	public void aFeroxMinigameTeleportFallsBackToTheLocalPlanner()
	{
		Transport clanWars = transport(A, FEROX, TransportType.TELEPORTATION_MINIGAME, null, "Clan Wars", -1);
		Rs2Walker.WalkLeg leg = legFor(result(PathResult.Status.FOUND, List.of(A, FEROX), Map.of(0, clanWars)), FEROX);
		assertTrue(leg == null || leg.stairs == null || !leg.stairs.minigame);
	}

	@Test
	public void plannedRoutesBecomeRouteValuesWithTransportEdges()
	{
		Transport minigame = transport(B, CASTLE_WARS, TransportType.TELEPORTATION_MINIGAME, null, "Castle Wars", -1);
		Rs2RouteResult route = Rs2PathApi.fromPathResult(
			result(PathResult.Status.FOUND, List.of(A, B, CASTLE_WARS), Map.of(1, minigame)), A, Set.of(CASTLE_WARS));
		assertEquals(List.of(A, B, CASTLE_WARS), route.getPath());
		assertEquals(Rs2RouteTermination.TARGET_REACHED, route.getTerminationReason());
		assertFalse(route.getSteps().get(0).isTransport());
		Rs2TransportEdge edge = route.getSteps().get(1).getTransport().orElseThrow();
		assertEquals(Rs2TransportType.TELEPORTATION_MINIGAME, edge.getType());
		assertEquals(Rs2TransportExecutor.MINIGAME_TELEPORT, edge.getExecutor());
		assertTrue(route.isTargetReached(0));
		assertEquals(Rs2RouteTermination.FAILED, Rs2PathApi.fromPathResult(null, A, Set.of(B)).getTerminationReason());
	}

	@Test
	public void bareTilesFindTheirCatalogueTransports()
	{
		Transport ladder = transport(B, new WorldPoint(3201, 3200, 1), TransportType.TRANSPORT, "Climb-up", "Ladder", 16683);
		WorldPoint up = new WorldPoint(3201, 3200, 1);
		Rs2RouteResult route = Rs2PathApi.fromTiles(List.of(A, B, up), Set.of(up), Rs2RouteTermination.TARGET_REACHED,
			Map.of(B, Set.of(ladder)));
		assertFalse(route.getSteps().get(0).isTransport());
		assertEquals(Rs2TransportExecutor.OBJECT, route.getSteps().get(1).getTransport().orElseThrow().getExecutor());
		assertEquals(Rs2TransportExecutor.WILDERNESS_OBELISK, Rs2PathApi.executor(TransportType.WILDERNESS_OBELISK));
		assertEquals(Rs2TransportExecutor.UNSUPPORTED, Rs2PathApi.executor(TransportType.GRAPPLE_SHORTCUT));
		assertEquals(Rs2RouteTermination.CUTOFF_REACHED, Rs2PathApi.termination(PathResult.Status.TIMED_OUT));
		assertEquals(Rs2RouteTermination.SEARCH_EXHAUSTED, Rs2PathApi.termination(PathResult.Status.PARTIAL));
	}

	@Test
	public void noWalkMeansNoActiveRoute()
	{
		Rs2Walker.setTarget(null);
		assertTrue(Rs2PathApi.getActiveRoute().isEmpty());
		assertEquals(Rs2ActiveRouteStatus.Phase.ABSENT, Rs2PathApi.getActiveRouteStatus().getPhase());
		assertTrue(Rs2PathApi.getActiveRouteTargets().isEmpty());
	}

	@Test
	public void planUsesTheMobilePathfinder()
	{
		try (MockedStatic<MobilePathfinder> pathfinder = mockStatic(MobilePathfinder.class))
		{
			pathfinder.when(() -> MobilePathfinder.findAny(eq(A), eq(Set.of(B)), eq(true)))
				.thenReturn(result(PathResult.Status.FOUND, List.of(A, B), Map.of()));
			Rs2RouteResult route = Rs2PathApi.plan(Rs2RouteRequest.to(A, B).withBankItems(true));
			assertEquals(List.of(A, B), route.getPath());
		}
	}

	@Test
	public void walkableTileQueriesAbstainWithoutAMap()
	{
		CollisionMap map = mock(CollisionMap.class);
		when(map.hasRegion(anyInt(), anyInt())).thenReturn(true);
		when(map.isBlocked(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> !invocation.getArgument(0).equals(3202));
		assertTrue(Rs2PathApi.hasWalkableTileWithin(null, A, 1));
		assertFalse(Rs2PathApi.hasWalkableTileWithin(map, A, 1));
		assertTrue(Rs2PathApi.hasWalkableTileWithin(map, A, 2));
		assertEquals(new WorldPoint(3202, 3198, 0), Rs2PathApi.nearestWalkableTile(map, A, 3));
		assertNull(Rs2PathApi.nearestWalkableTile(map, A, 1));
	}

	@Test
	public void strongholdDoorsAndQuestionsAreRecognised()
	{
		assertTrue(StrongholdDoors.isInStronghold(new WorldPoint(1860, 5240, 0)));
		assertFalse(StrongholdDoors.isInStronghold(A));
		assertTrue(StrongholdDoors.isStrongholdDoor("Gate of War"));
		assertTrue(StrongholdDoors.isStrongholdDoor("oozing barrier"));
		assertFalse(StrongholdDoors.isStrongholdDoor("Door"));
		assertTrue(StrongholdDoors.isIntroduction("Hopefully you will learn<br>much from us."));
		assertEquals("Nobody.", StrongholdAnswer.findAnswer("To pass you must answer me this: Who can I give my<br>password to?"));
		assertNull(StrongholdAnswer.findAnswer("Nice weather today."));
	}

	@Test
	public void aDoorThatAsksNothingNeedsNoAnswer()
	{
		try (MockedStatic<Global> global = mockStatic(Global.class))
		{
			global.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt())).thenReturn(false);
			assertTrue(StrongholdDoors.answerIfAsked());
		}
	}

	@Test
	public void missingTransportEdgesFollowTheirCatalogueTransports()
	{
		Transport carried = transport(A, B, TransportType.TELEPORTATION_ITEM, null, "Varrock teleport", -1);
		Transport missing = transport(A, CASTLE_WARS, TransportType.TELEPORTATION_ITEM, null, "Ring of dueling: Castle Wars", -1);
		List<Rs2TransportEdge> edges = List.of(Rs2PathApi.toTransportEdge(carried), Rs2PathApi.toTransportEdge(missing));
		try (MockedStatic<WalkerPathReads> reads = mockStatic(WalkerPathReads.class))
		{
			reads.when(() -> WalkerPathReads.getMissingTransports(anyList())).thenReturn(List.of(missing));
			reads.when(() -> WalkerPathReads.getMissingTransportItemIdsWithQuantities(anyList())).thenReturn(Map.of(2552, 1));
			assertEquals(List.of(edges.get(1)), Rs2Walker.getMissingTransportEdges(edges));
			assertEquals(Map.of(2552, 1), Rs2Walker.getMissingTransportEdgeItemIdsWithQuantities(edges));
			Rs2TransportLoadout loadout = Rs2Walker.getMissingTransportEdgeLoadout(edges);
			assertTrue(loadout.isSatisfiable());
			assertEquals(Map.of(2552, 1), loadout.getWithdrawals());
			reads.when(() -> WalkerPathReads.getMissingTransports(anyList())).thenReturn(List.of());
			assertTrue(Rs2Walker.getMissingTransportEdgeLoadout(edges).isEmpty());
		}
	}
}
