package net.runelite.client.plugins.microbot.util.walker.banking;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteRequest;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteResult;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteStep;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteTermination;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportLoadout;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.TransportRouteAnalysis;

/**
 * As upstream: plans whether a trip to the nearest bank for transport items (teleports, runes,
 * fares) beats walking straight to a target. Mobile port: every route is a synchronous
 * {@link Rs2PathApi#plan} search (call it off the client thread); the transport-item reads are
 * {@link Rs2Walker}'s. Upstream's per-leg performance log, Leagues handling and rune-pouch fare
 * accounting are not ported.
 */
public final class Rs2WalkerBankingPlanner
{
	private Rs2WalkerBankingPlanner()
	{
	}

	/** As upstream: compares the direct route with one through the nearest bank. */
	public static TransportRouteAnalysis compareRoutes(WorldPoint startPoint, WorldPoint target)
	{
		return compareRoutes(startPoint, target, null);
	}

	/** As upstream: {@link #compareRoutes(WorldPoint, WorldPoint)}, reusing {@code directProbe} when it is this exact leg. */
	public static TransportRouteAnalysis compareRoutes(WorldPoint startPoint, WorldPoint target, Rs2RouteResult directProbe)
	{
		if (target == null)
		{
			return new TransportRouteAnalysis(List.of(), null, null, List.of(), List.of(), "Target location is null");
		}
		WorldPoint start = startPoint != null ? startPoint : Rs2Player.getWorldLocation();
		if (start == null)
		{
			return new TransportRouteAnalysis(List.of(), null, null, List.of(), List.of(), "Cannot determine starting location");
		}
		Rs2RouteResult direct = directProbe != null && start.equals(directProbe.getStart())
			&& directProbe.getTargets().size() == 1 && directProbe.getTargets().contains(target)
			? directProbe : plan(start, target, false);
		int directDistance = comparableRouteDistance(direct, target);
		BankLocation bank = Rs2Bank.getNearestBank(start, 20);
		if (bank == null || bank.getWorldPoint() == null)
		{
			return new TransportRouteAnalysis(direct.getPath(), null, null, List.of(), List.of(), "No reachable bank",
				directDistance, -1, direct.getSteps(), List.of(), List.of());
		}
		WorldPoint bankTile = bank.getWorldPoint();
		Rs2RouteResult toBank = plan(start, bankTile, false);
		Rs2RouteResult fromBank = plan(bankTile, target, true);
		int toBankDistance = comparableRouteDistance(toBank, bankTile);
		int fromBankDistance = comparableRouteDistance(fromBank, target);
		int bankingDistance = toBankDistance < 0 || fromBankDistance < 0 ? -1 : toBankDistance + fromBankDistance;
		String analysis = "Direct " + directDistance + " tiles, via " + bank + " " + bankingDistance + " tiles";
		return new TransportRouteAnalysis(direct.getPath(), bank, bankTile, toBank.getPath(), fromBank.getPath(), analysis,
			directDistance, bankingDistance, direct.getSteps(), toBank.getSteps(), fromBank.getSteps());
	}

	/** As upstream: the transport edges of the route from the bank, when the analysis carries its exact steps. */
	public static List<Rs2TransportEdge> getRequiredTransportEdgesFromBank(TransportRouteAnalysis analysis)
	{
		return analysis == null || !analysis.isRouteFromBankStepsExact() ? List.of() : analysis.getTransportEdgesFromBank();
	}

	/** As upstream: {@link Rs2Walker#getTransportEdgesForDestination}. */
	public static List<Rs2TransportEdge> getTransportEdgesForDestination(WorldPoint destination, boolean useBankItems)
	{
		return Rs2Walker.getTransportEdgesForDestination(destination, useBankItems);
	}

	/** As upstream: {@link Rs2Walker#getTransportsForDestination(WorldPoint, boolean, TransportType)}. */
	public static List<Transport> getTransportsForDestination(WorldPoint destination, boolean useBankItems, TransportType prefTransportType)
	{
		return Rs2Walker.getTransportsForDestination(destination, useBankItems, prefTransportType);
	}

	/** As upstream: {@link Rs2Walker#hasRequiredTransportItems}. */
	public static boolean hasRequiredTransportItems(Transport transport)
	{
		return Rs2Walker.hasRequiredTransportItems(transport);
	}

	/** As upstream: whether the carried items cover {@code transport} (an edge built by {@link Rs2PathApi}). */
	public static boolean hasRequiredTransportEdgeItems(Rs2TransportEdge transport)
	{
		return transport != null && Rs2Walker.getMissingTransportEdges(List.of(transport)).isEmpty();
	}

	/** As upstream: {@link Rs2Walker#getMissingTransports}. */
	public static List<Transport> getMissingTransports(List<Transport> transports)
	{
		return Rs2Walker.getMissingTransports(transports);
	}

	/** As upstream: {@link Rs2Walker#getMissingTransportEdges}. */
	public static List<Rs2TransportEdge> getMissingTransportEdges(List<Rs2TransportEdge> transports)
	{
		return Rs2Walker.getMissingTransportEdges(transports);
	}

	/** As upstream: {@link Rs2Walker#getMissingTransportItemIdsWithQuantities}. */
	public static Map<Integer, Integer> getMissingTransportItemIdsWithQuantities(List<Transport> transports)
	{
		return Rs2Walker.getMissingTransportItemIdsWithQuantities(transports);
	}

	/** As upstream: {@link Rs2Walker#getMissingTransportEdgeItemIdsWithQuantities}. */
	public static Map<Integer, Integer> getMissingTransportEdgeItemIdsWithQuantities(List<Rs2TransportEdge> transports)
	{
		return Rs2Walker.getMissingTransportEdgeItemIdsWithQuantities(transports);
	}

	/** As upstream: {@link Rs2Walker#getMissingTransportEdgeLoadout}. */
	public static Rs2TransportLoadout getMissingTransportEdgeLoadout(List<Rs2TransportEdge> transports)
	{
		return Rs2Walker.getMissingTransportEdgeLoadout(transports);
	}

	/** As upstream: {@link Rs2Walker#getMissingTransportItemIds}. */
	public static List<Integer> getMissingTransportItemIds(List<Transport> transports)
	{
		return Rs2Walker.getMissingTransportItemIds(transports);
	}

	/** The transport edges of {@code route}, in route order. */
	public static List<Rs2TransportEdge> transportEdges(Rs2RouteResult route)
	{
		return route == null ? List.of() : route.getTransportSteps().stream().map(Rs2RouteStep::getTransport)
			.flatMap(java.util.Optional::stream).collect(Collectors.toUnmodifiableList());
	}

	/** As upstream: the route's walking length to {@code target}, or -1 when it does not reach it. */
	static int comparableRouteDistance(Rs2RouteResult route, WorldPoint target)
	{
		if (route == null || target == null || route.getTerminationReason() != Rs2RouteTermination.TARGET_REACHED
			|| !route.isTargetReached(0))
		{
			return -1;
		}
		int distance = Rs2Walker.getTotalTilesFromPath(route.getPath(), target);
		return distance == Integer.MAX_VALUE ? -1 : distance;
	}

	private static Rs2RouteResult plan(WorldPoint start, WorldPoint target, boolean useBankItems)
	{
		return Rs2PathApi.plan(Rs2RouteRequest.to(start, target).withBankItems(useBankItems));
	}
}
