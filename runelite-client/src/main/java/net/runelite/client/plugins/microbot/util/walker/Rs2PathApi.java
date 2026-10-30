package net.runelite.client.plugins.microbot.util.walker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.MobilePathfinder;
import net.runelite.client.plugins.microbot.shortestpath.PathResult;
import net.runelite.client.plugins.microbot.shortestpath.PlannerSelectionMode;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.shortestpath.TeleportationItem;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportItemRequirement;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;

/**
 * As upstream: the Microbot facade over the shortest-path state, so scripts read routes and
 * planner policy without reaching into {@link ShortestPathPlugin}. Mobile port of the parts that
 * have a mobile counterpart:
 * <ul>
 *   <li>the legacy delegations (pathfinder, config, overrides, transports, {@link #exit()});</li>
 *   <li>the active route, built from the running {@link Rs2Walker} walk's tiles;</li>
 *   <li>{@link #plan(Rs2RouteRequest)}, a synchronous {@link MobilePathfinder} search;</li>
 *   <li>collision and policy queries, and catalogue transport edges.</li>
 * </ul>
 * Upstream's shadow-planner comparison, canary metrics, executor lifecycle and world-map markers
 * have no mobile counterpart and are not provided. Nothing here issues game input except
 * {@link #exit()}, which only cancels.
 */
public final class Rs2PathApi
{
	/** As upstream: the shortest-path config group. */
	public static final String CONFIG_GROUP = ShortestPathPlugin.CONFIG_GROUP;

	private Rs2PathApi()
	{
	}

	// ---- Legacy delegations ----

	/** As upstream: the current pathfinder, or {@code null}. */
	public static Pathfinder getPathfinder()
	{
		return ShortestPathPlugin.getPathfinder();
	}

	/** As upstream: replaces the current pathfinder. */
	public static void setPathfinder(Pathfinder pathfinder)
	{
		ShortestPathPlugin.setPathfinder(pathfinder);
	}

	/** As upstream: the shared pathfinder configuration, or {@code null} before it is built. */
	public static PathfinderConfig getPathfinderConfig()
	{
		return ShortestPathPlugin.getPathfinderConfig();
	}

	/** As upstream: a boolean config override, else {@code defaultValue}. */
	public static boolean override(String configOverrideKey, boolean defaultValue)
	{
		return ShortestPathPlugin.override(configOverrideKey, defaultValue);
	}

	/** As upstream: an int config override, else {@code defaultValue}. */
	public static int override(String configOverrideKey, int defaultValue)
	{
		return ShortestPathPlugin.override(configOverrideKey, defaultValue);
	}

	/** As upstream: a teleportation-item config override, else {@code defaultValue}. */
	public static TeleportationItem override(String configOverrideKey, TeleportationItem defaultValue)
	{
		return ShortestPathPlugin.override(configOverrideKey, defaultValue);
	}

	/** As upstream: a planner-selection config override, else {@code defaultValue}. */
	public static PlannerSelectionMode override(String configOverrideKey, PlannerSelectionMode defaultValue)
	{
		return ShortestPathPlugin.override(configOverrideKey, defaultValue);
	}

	/** As upstream: replaces the per-request config overrides. */
	public static void setConfigOverride(Map<String, Object> overrides)
	{
		ShortestPathPlugin.setConfigOverride(overrides);
	}

	/** As upstream: cancels any search and the running walk (no game input). */
	public static void exit()
	{
		ShortestPathPlugin.exit();
	}

	/** As upstream: the usable transports by origin. */
	public static Map<WorldPoint, Set<Transport>> getTransports()
	{
		return ShortestPathPlugin.getTransports();
	}

	// ---- Active route ----

	/** As upstream: the running walk's route; empty when no walk runs. */
	public static Optional<Rs2RouteResult> getActiveRoute()
	{
		List<WorldPoint> path = Rs2Walker.activeRoutePath();
		WorldPoint target = Rs2Walker.getTarget();
		if (path == null || path.isEmpty() || target == null)
		{
			return Optional.empty();
		}
		return Optional.of(fromTiles(path, Set.of(target), Rs2RouteTermination.TARGET_REACHED, getTransports()));
	}

	/** As upstream: the active route's start, if a walk runs. */
	public static Optional<WorldPoint> getActiveRouteStart()
	{
		return getActiveRoute().map(Rs2RouteResult::getStart);
	}

	/** As upstream: the active route's targets (empty when no walk runs). */
	public static Set<WorldPoint> getActiveRouteTargets()
	{
		return getActiveRoute().map(Rs2RouteResult::getTargets).orElse(Collections.emptySet());
	}

	/** As upstream: the active route as a status value; mobile routes are planned before walking, so READY or ABSENT. */
	public static Rs2ActiveRouteStatus getActiveRouteStatus()
	{
		Optional<Rs2RouteResult> route = getActiveRoute();
		if (route.isEmpty())
		{
			return Rs2ActiveRouteStatus.absent(0);
		}
		Rs2RouteResult result = route.get();
		return new Rs2ActiveRouteStatus(0, Rs2ActiveRouteStatus.Phase.READY, result.getStart(), result.getTargets(),
			result.getPath(), result.getPath(), result.getTerminationReason(), result.getMetrics());
	}

	/** As upstream: cancels the running walk and any search. */
	public static void cancelAndClearActiveRoute()
	{
		MobilePathfinder.cancel();
		exit();
	}

	// ---- Planning ----

	/**
	 * As upstream: plans {@code request} synchronously (off the client thread) with the mobile
	 * pathfinder; a request without a start plans from the player.
	 */
	public static Rs2RouteResult plan(Rs2RouteRequest request)
	{
		if (request == null || request.getTargets().isEmpty())
		{
			throw new IllegalArgumentException("a route request needs a target");
		}
		WorldPoint start = request.getStart() != null ? request.getStart()
			: net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
		boolean bank = Boolean.TRUE.equals(request.getUseBankItems());
		PathResult result = MobilePathfinder.findAny(start, request.getTargets(), bank);
		return fromPathResult(result, start, request.getTargets());
	}

	static Rs2RouteResult fromPathResult(PathResult result, WorldPoint start, Set<WorldPoint> targets)
	{
		if (result == null || result.getTiles().isEmpty())
		{
			return new Rs2RouteResult(start, targets, List.of(), List.of(),
				result == null ? Rs2RouteTermination.FAILED : termination(result.getStatus()), metrics());
		}
		List<WorldPoint> tiles = result.getTiles();
		List<Rs2RouteStep> steps = new ArrayList<>();
		for (int i = 0; i + 1 < tiles.size(); i++)
		{
			PathResult.TransportStep transport = result.transportAt(i);
			steps.add(transport != null && transport.getTransport() != null
				&& tiles.get(i + 1).equals(transport.getTransport().getDestination())
				? Rs2RouteStep.transport(tiles.get(i), tiles.get(i + 1), toTransportEdge(transport.getTransport()))
				: Rs2RouteStep.walk(tiles.get(i), tiles.get(i + 1)));
		}
		return new Rs2RouteResult(tiles.get(0), targets, tiles, steps, termination(result.getStatus()), metrics());
	}

	/** A route from bare tiles: an edge is a transport when the catalogue has one from its start to its end and it is not a plain step. */
	static Rs2RouteResult fromTiles(List<WorldPoint> tiles, Set<WorldPoint> targets, Rs2RouteTermination termination,
		Map<WorldPoint, Set<Transport>> transports)
	{
		List<Rs2RouteStep> steps = new ArrayList<>();
		for (int i = 0; i + 1 < tiles.size(); i++)
		{
			WorldPoint from = tiles.get(i);
			WorldPoint to = tiles.get(i + 1);
			boolean step = from.getPlane() == to.getPlane() && from.distanceTo2D(to) <= 1;
			Transport transport = step || transports == null ? null : transports.getOrDefault(from, Set.of()).stream()
				.filter(candidate -> to.equals(candidate.getDestination())).findFirst().orElse(null);
			steps.add(transport != null ? Rs2RouteStep.transport(from, to, toTransportEdge(transport)) : Rs2RouteStep.walk(from, to));
		}
		return new Rs2RouteResult(tiles.get(0), targets, tiles, steps, termination, metrics());
	}

	private static Rs2RouteMetrics metrics()
	{
		return new Rs2RouteMetrics(Rs2RouteMetrics.UNAVAILABLE, Rs2RouteMetrics.UNAVAILABLE,
			Rs2RouteMetrics.UNAVAILABLE, Rs2RouteMetrics.UNAVAILABLE);
	}

	static Rs2RouteTermination termination(PathResult.Status status)
	{
		if (status == null)
		{
			return Rs2RouteTermination.FAILED;
		}
		switch (status)
		{
			case FOUND:
				return Rs2RouteTermination.TARGET_REACHED;
			case PARTIAL:
			case UNREACHABLE:
				return Rs2RouteTermination.SEARCH_EXHAUSTED;
			case TIMED_OUT:
				return Rs2RouteTermination.CUTOFF_REACHED;
			case CANCELLED:
				return Rs2RouteTermination.CANCELLED;
			default:
				return Rs2RouteTermination.FAILED;
		}
	}

	// ---- Transport edges ----

	/** As upstream's {@code toTransportEdge}: the immutable description of a catalogue transport. */
	static Rs2TransportEdge toTransportEdge(Transport transport)
	{
		List<Rs2TransportItemRequirement> items = new ArrayList<>();
		for (TransportItemRequirement requirement : transport.getItemRequirements())
		{
			items.add(new Rs2TransportItemRequirement(requirement.getAlternatives(), requirement.getStaffAlternatives(),
				requirement.getOffhandAlternatives(), requirement.isRuneOnly()));
		}
		return new Rs2TransportEdge(transport.getOrigin(), transport.getDestination(), transportType(transport.getType()),
			executor(transport.getType()), Rs2TerminalTravelMode.UNSUPPORTED, transport.getDisplayInfo(), transport.getAction(),
			transport.getName(), transport.getObjectId(), transport.getDuration(),
			TransportType.isTeleport(transport.getType(), transport.getOrigin()), transport.isConsumable(), transport.isMembers(),
			transport.getMaxWildernessLevel(), transport.getCurrencyName(), transport.getCurrencyAmount(), items,
			Arrays.stream(transport.getSkillLevels()).anyMatch(level -> level > 0), transport.isQuestLocked(),
			!transport.getVarbits().isEmpty() || !transport.getVarplayers().isEmpty(), transport);
	}

	static Rs2TransportType transportType(TransportType type)
	{
		if (type == null)
		{
			return Rs2TransportType.UNKNOWN;
		}
		try
		{
			return Rs2TransportType.valueOf(type.name());
		}
		catch (IllegalArgumentException unknown)
		{
			return Rs2TransportType.UNKNOWN;
		}
	}

	/** The mobile walker's executor for {@code type}: UNSUPPORTED for what {@link Rs2Walker} does not run. */
	static Rs2TransportExecutor executor(TransportType type)
	{
		if (type == null || !Rs2Walker.EXECUTABLE_TRANSPORTS.contains(type))
		{
			return Rs2TransportExecutor.UNSUPPORTED;
		}
		switch (type)
		{
			case CANOE: return Rs2TransportExecutor.CANOE;
			case CHARTER_SHIP: return Rs2TransportExecutor.CHARTER_SHIP;
			case FAIRY_RING: return Rs2TransportExecutor.FAIRY_RING;
			case GNOME_GLIDER: return Rs2TransportExecutor.GNOME_GLIDER;
			case HOT_AIR_BALLOON: return Rs2TransportExecutor.HOT_AIR_BALLOON;
			case TELEPORTATION_ITEM: return Rs2TransportExecutor.ITEM_TELEPORT;
			case MAGIC_CARPET: return Rs2TransportExecutor.MAGIC_CARPET;
			case MAGIC_MUSHTREE: return Rs2TransportExecutor.MAGIC_MUSHTREE;
			case TELEPORTATION_MINIGAME: return Rs2TransportExecutor.MINIGAME_TELEPORT;
			case POH: return Rs2TransportExecutor.POH;
			case QUETZAL: return Rs2TransportExecutor.QUETZAL;
			case TELEPORTATION_SPELL: return Rs2TransportExecutor.SPELL_TELEPORT;
			case SPIRIT_TREE: return Rs2TransportExecutor.SPIRIT_TREE;
			case WILDERNESS_OBELISK: return Rs2TransportExecutor.WILDERNESS_OBELISK;
			default: return Rs2TransportExecutor.OBJECT;
		}
	}

	/** As upstream: whether the full catalogue has a transport leaving {@code origin}. */
	public static boolean hasCatalogTransportOrigin(WorldPoint origin)
	{
		return !catalog(origin).isEmpty();
	}

	/** As upstream: whether the full catalogue has a transport from {@code origin} to {@code destination}. */
	public static boolean hasCatalogTransportEdge(WorldPoint origin, WorldPoint destination)
	{
		return destination != null && catalog(origin).stream().anyMatch(transport -> destination.equals(transport.getDestination()));
	}

	/** As upstream: the full catalogue's transports leaving {@code origin}, as edges. */
	public static List<Rs2TransportEdge> getCatalogTransportEdges(WorldPoint origin)
	{
		return catalog(origin).stream().filter(transport -> transport.getDestination() != null)
			.map(Rs2PathApi::toTransportEdge).collect(Collectors.toUnmodifiableList());
	}

	private static Set<Transport> catalog(WorldPoint origin)
	{
		PathfinderConfig config = getPathfinderConfig();
		if (origin == null || config == null || config.getAllTransports() == null)
		{
			return Set.of();
		}
		synchronized (config.getAllTransports())
		{
			Set<Transport> transports = config.getAllTransports().get(origin);
			return transports == null ? Set.of() : new LinkedHashSet<>(transports);
		}
	}

	// ---- Collision and policy queries ----

	/** As upstream: whether a tile within {@code distance} of {@code target} is standable; abstains (true) without a map or region. */
	public static boolean hasWalkableTileWithin(WorldPoint target, int distance)
	{
		PathfinderConfig config = getPathfinderConfig();
		return hasWalkableTileWithin(config == null ? null : config.getMap(), target, distance);
	}

	static boolean hasWalkableTileWithin(CollisionMap map, WorldPoint target, int distance)
	{
		if (map == null || target == null || !map.hasRegion(target.getX(), target.getY()))
		{
			return true;
		}
		int radius = Math.max(0, distance);
		for (int dx = -radius; dx <= radius; dx++)
		{
			for (int dy = -radius; dy <= radius; dy++)
			{
				int x = target.getX() + dx;
				int y = target.getY() + dy;
				if (!map.hasRegion(x, y) || !map.isBlocked(x, y, target.getPlane()))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** As upstream: the nearest mapped standable tile to {@code target} within {@code maxRadius}, or {@code null}. */
	public static WorldPoint nearestWalkableTile(WorldPoint target, int maxRadius)
	{
		PathfinderConfig config = getPathfinderConfig();
		return nearestWalkableTile(config == null ? null : config.getMap(), target, maxRadius);
	}

	static WorldPoint nearestWalkableTile(CollisionMap map, WorldPoint target, int maxRadius)
	{
		if (map == null || target == null)
		{
			return null;
		}
		for (int radius = 1; radius <= Math.max(0, maxRadius); radius++)
		{
			for (int dx = -radius; dx <= radius; dx++)
			{
				for (int dy = -radius; dy <= radius; dy++)
				{
					if (Math.max(Math.abs(dx), Math.abs(dy)) != radius)
					{
						continue;
					}
					int x = target.getX() + dx;
					int y = target.getY() + dy;
					if (map.hasRegion(x, y) && !map.isBlocked(x, y, target.getPlane()))
					{
						return new WorldPoint(x, y, target.getPlane());
					}
				}
			}
		}
		return null;
	}

	/** As upstream: records a walking edge that failed, so the planner avoids it. */
	public static boolean learnBlockedEdge(WorldPoint origin, WorldPoint destination, String reason)
	{
		PathfinderConfig config = getPathfinderConfig();
		return config != null && config.learnBlockedEdge(origin, destination, reason);
	}

	/** As upstream: removes a learned blocked edge again. */
	public static boolean unlearnBlockedEdge(WorldPoint origin, WorldPoint destination, String reason)
	{
		PathfinderConfig config = getPathfinderConfig();
		return config != null && config.unlearnBlockedEdge(origin, destination, reason);
	}

	/** As upstream: whether the planner avoids {@code tile} as next to a dangerous NPC. */
	public static boolean shouldAvoidDangerousTile(WorldPoint tile)
	{
		PathfinderConfig config = getPathfinderConfig();
		return config != null && config.isAvoidDangerousNpcs() && tile != null
			&& config.isDangerousAdjacentTile(WorldPointUtil.packWorldPoint(tile));
	}

	/** As upstream: whether the last refreshed policy allows spirit-tree travel. */
	public static boolean isSpiritTreeTravelEnabled()
	{
		PathfinderConfig config = getPathfinderConfig();
		return config != null && config.isUseSpiritTrees();
	}

	/** As upstream: the planner's Wilderness classification of {@code point}. */
	public static boolean isInWilderness(WorldPoint point)
	{
		return point != null && PathfinderConfig.isInWilderness(point);
	}

	/**
	 * As upstream: whether {@code itemId} is needed by a catalogue teleport, or is one of
	 * {@code additionalItemIds}. Mobile: an unbuilt catalogue answers from the extra ids only
	 * (refreshing it reads the client and is the pathfinder's job).
	 */
	public static boolean isTeleportItem(int itemId, int... additionalItemIds)
	{
		if (additionalItemIds != null && Arrays.stream(additionalItemIds).anyMatch(id -> id == itemId))
		{
			return true;
		}
		PathfinderConfig config = getPathfinderConfig();
		if (config == null || config.getAllTransports() == null)
		{
			return false;
		}
		synchronized (config.getAllTransports())
		{
			return config.getAllTransports().values().stream().flatMap(Set::stream)
				.filter(transport -> TransportType.isTeleport(transport.getType(), transport.getOrigin()))
				.flatMap(transport -> transport.getItemIdRequirements().stream())
				.flatMap(Set::stream)
				.anyMatch(required -> required == itemId);
		}
	}
}
