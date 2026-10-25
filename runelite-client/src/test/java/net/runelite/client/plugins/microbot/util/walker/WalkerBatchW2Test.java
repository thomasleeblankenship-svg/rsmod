package net.runelite.client.plugins.microbot.util.walker;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.MobilePathfinder;
import net.runelite.client.plugins.microbot.shortestpath.PathResult;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.walker.banking.Rs2WalkerBankingPlanner;
import org.junit.Test;
import org.mockito.MockedStatic;
import static net.runelite.client.plugins.microbot.shortestpath.PathResultFixtures.result;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Walker batch W2: route door actions, the wall-distance nudge, route clears and the banking-route comparison. */
public class WalkerBatchW2Test
{
	private static final WorldPoint T = new WorldPoint(3200, 3200, 0);

	@Test
	public void routeDoorsUseTheirWalkActionButNeverSpendOrClimb()
	{
		assertEquals("Open", Rs2Walker.routeDoorAction(new String[]{"Close", null, "Open"}));
		assertEquals("Walk-through", Rs2Walker.routeDoorAction(new String[]{"Open", "Walk-through"}));
		assertNull("toll gates are transports", Rs2Walker.routeDoorAction(new String[]{"Pay-toll(10gp)"}));
		assertEquals("Open", Rs2Walker.routeDoorAction(new String[]{"Pick-lock", "Open"}));
		assertNull("a stile is a transport", Rs2Walker.routeDoorAction(new String[]{"Climb-over"}));
		assertNull(Rs2Walker.routeDoorAction(new String[]{"Exit", "Force"}));
		assertNull(Rs2Walker.routeDoorAction(new String[]{"Close"}));
		assertNull(Rs2Walker.routeDoorAction(null));
	}

	@Test
	public void theWallNudgePrefersTheCleanNeighbourOnThePlayersSide()
	{
		WorldPoint south = T.dy(-1);
		WorldPoint north = T.dy(1);
		WorldPoint east = T.dx(1);
		Map<WorldPoint, Integer> flags = Map.of(T, CollisionDataFlag.BLOCK_MOVEMENT_NORTH, south, 0, north, 0,
			east, CollisionDataFlag.BLOCK_MOVEMENT_EAST);
		Set<WorldPoint> neighbours = Set.of(T, south, north, east);
		WorldPoint player = new WorldPoint(3200, 3190, 0);
		assertEquals(south, Rs2Walker.chooseWallDistanceNeighbour(T, neighbours, flags, player, Set.of(south)));
		assertEquals("unreachable side loses", south,
			Rs2Walker.chooseWallDistanceNeighbour(T, neighbours, flags, new WorldPoint(3200, 3210, 0), Set.of(south)));
		assertEquals("no clean neighbour keeps the target", T,
			Rs2Walker.chooseWallDistanceNeighbour(T, Set.of(T, east), flags, null, Set.of()));
		assertNull(Rs2Walker.getPointWithWallDistance(null));
	}

	@Test
	public void clearingTheTargetRecordsWhy()
	{
		long before = System.currentTimeMillis();
		Rs2Walker.setTarget(null, "test:clear");
		assertEquals("test:clear", Rs2Walker.getLastRouteClearReason());
		assertTrue(Rs2Walker.getLastRouteClearAtMs() >= before);
		Rs2Walker.setTarget(T, "test:set");
		assertEquals("setting a target keeps the last clear", "test:clear", Rs2Walker.getLastRouteClearReason());
		Rs2Walker.setTarget(null);
	}

	@Test
	public void compareRoutesWeighsTheDirectRouteAgainstTheBankTrip()
	{
		BankLocation bank = BankLocation.VARROCK_WEST;
		WorldPoint bankTile = bank.getWorldPoint();
		WorldPoint start = bankTile.dx(-3);
		WorldPoint target = bankTile.dx(4);
		List<WorldPoint> direct = List.of(start, start.dx(1), start.dx(2), start.dx(3), start.dx(4), start.dx(5), start.dx(6), target);
		List<WorldPoint> toBank = List.of(start, start.dx(1), start.dx(2), bankTile);
		List<WorldPoint> fromBank = List.of(bankTile, bankTile.dx(1), bankTile.dx(2), bankTile.dx(3), target);
		try (MockedStatic<MobilePathfinder> pathfinder = mockStatic(MobilePathfinder.class);
			MockedStatic<Rs2Bank> banks = mockStatic(Rs2Bank.class))
		{
			banks.when(() -> Rs2Bank.getNearestBank(start, 20)).thenReturn(bank);
			pathfinder.when(() -> MobilePathfinder.findAny(eq(start), eq(Set.of(target)), eq(false)))
				.thenReturn(result(PathResult.Status.FOUND, direct, Map.of()));
			pathfinder.when(() -> MobilePathfinder.findAny(eq(start), eq(Set.of(bankTile)), eq(false)))
				.thenReturn(result(PathResult.Status.FOUND, toBank, Map.of()));
			pathfinder.when(() -> MobilePathfinder.findAny(eq(bankTile), eq(Set.of(target)), eq(true)))
				.thenReturn(result(PathResult.Status.FOUND, fromBank, Map.of()));
			TransportRouteAnalysis analysis = Rs2Walker.compareRoutes(start, target);
			assertEquals("path tiles, as upstream", 8, analysis.getDirectDistance());
			assertEquals(9, analysis.getBankingRouteDistance());
			assertEquals(bank, analysis.getNearestBank());
			assertTrue(analysis.isRouteFromBankStepsExact());
			assertTrue(Rs2WalkerBankingPlanner.getRequiredTransportEdgesFromBank(analysis).isEmpty());
		}
	}

	@Test
	public void compareRoutesWithoutATargetSaysSo()
	{
		TransportRouteAnalysis analysis = Rs2Walker.compareRoutes(T, null);
		assertEquals(-1, analysis.getDirectDistance());
		assertEquals("Target location is null", analysis.getAnalysis());
	}
}
