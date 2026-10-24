package net.runelite.client.plugins.microbot.shortestpath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathEdge;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathTerminationReason;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.TransportPlanningPolicy;

/**
 * The walker's single entry point into upstream's pathfinder: {@link #find(WorldPoint, WorldPoint)}.
 * <p>
 * One shared {@link PathfinderConfig} is built lazily from the bundled resources (whole-world
 * collision map, transport tables, restrictions) and published as
 * {@code ShortestPathPlugin.getPathfinderConfig()}. Each search first runs upstream's
 * {@code refresh(goal)} on the client thread, which applies skill, quest, item, varbit and
 * config requirements and the {@link MobileTransportPolicy}; the A* search then runs on the
 * calling thread, never on the client thread. This class computes only: it never clicks, walks
 * or opens interfaces.
 */
@Slf4j
public final class MobilePathfinder
{
	private static final Object LOCK = new Object();
	private static volatile PathfinderConfig sharedConfig;
	private static volatile TransportPlanningPolicy policy = MobileTransportPolicy.DEFAULT;

	private MobilePathfinder()
	{
	}

	/**
	 * Finds a route from {@code start} to {@code goal} for the logged-in player. Blocks for at most
	 * the configured calculation cutoff (default 5 game ticks) plus the requirement refresh.
	 *
	 * @throws IllegalStateException when called on the client thread
	 */
	public static PathResult find(WorldPoint start, WorldPoint goal)
	{
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(goal, "goal");
		PathfinderConfig config = refreshedConfig(start, goal);
		return config == null ? PathResult.failed(start, goal) : search(config, start, goal);
	}

	/**
	 * Finds a route from {@code start} to whichever of {@code goals} the search reaches first (the
	 * nearest by route), as upstream's multi-target planner. On {@link PathResult.Status#FOUND} the
	 * result's goal is the reached target; otherwise it is {@code null}. Same blocking rules as
	 * {@link #find}.
	 *
	 * @throws IllegalStateException when called on the client thread
	 */
	public static PathResult findAny(WorldPoint start, Set<WorldPoint> goals)
	{
		Objects.requireNonNull(start, "start");
		if (goals == null || goals.isEmpty())
		{
			return PathResult.failed(start, null);
		}
		PathfinderConfig config = refreshedConfig(start, null);
		return config == null ? PathResult.failed(start, null) : search(config, start, goals);
	}

	/** The shared config with requirements refreshed on the client thread, or {@code null}. */
	private static PathfinderConfig refreshedConfig(WorldPoint start, WorldPoint goal)
	{
		Client client = Microbot.getClient();
		ClientThread clientThread = Microbot.getClientThread();
		if (client == null || clientThread == null)
		{
			return null;
		}
		if (client.isClientThread())
		{
			throw new IllegalStateException("MobilePathfinder.find must not run on the client thread");
		}

		PathfinderConfig config = sharedConfig(client);
		boolean refreshed = clientThread.runOnClientThreadOptional(() ->
		{
			config.refresh(goal);
			return Boolean.TRUE;
		}).orElse(Boolean.FALSE);
		if (!refreshed)
		{
			log.warn("Pathfinder requirement refresh did not run on the client thread; no route for {} -> {}", start, goal);
			return null;
		}
		return config;
	}

	/** Cancels the running search, if any; it returns {@link PathResult.Status#CANCELLED}. */
	public static void cancel()
	{
		Pathfinder current = ShortestPathPlugin.getPathfinder();
		if (current != null)
		{
			current.cancel();
		}
	}

	/**
	 * Replaces the transport admission policy (for example when the walker learns to execute a new
	 * transport type). The next {@link #find} rebuilds the shared config with it.
	 */
	public static void setTransportPolicy(TransportPlanningPolicy newPolicy)
	{
		synchronized (LOCK)
		{
			policy = Objects.requireNonNull(newPolicy, "policy");
			sharedConfig = null;
		}
	}

	private static PathfinderConfig sharedConfig(Client client)
	{
		PathfinderConfig config = sharedConfig;
		if (config != null)
		{
			return config;
		}
		synchronized (LOCK)
		{
			if (sharedConfig == null)
			{
				long started = System.currentTimeMillis();
				sharedConfig = new PathfinderConfig(
					SplitFlagMap.fromResources(),
					Transport.loadAllFromResources(),
					Restriction.loadAllFromResources(),
					client,
					shortestPathConfig(),
					policy);
				ShortestPathPlugin.setPathfinderConfig(sharedConfig);
				log.info("Pathfinder data loaded in {} ms", System.currentTimeMillis() - started);
			}
			return sharedConfig;
		}
	}

	/** Upstream's settings from the {@code shortestpath} config group, with upstream's defaults. */
	private static ShortestPathConfig shortestPathConfig()
	{
		ConfigManager configManager = Microbot.getConfigManager();
		return configManager == null ? null : configManager.getConfig(ShortestPathConfig.class);
	}

	/**
	 * Runs one search over {@code config} as it stands (no refresh) on the calling thread and
	 * maps the outcome. Route tests call this with a config prepared offline.
	 */
	public static PathResult search(PathfinderConfig config, WorldPoint start, WorldPoint goal)
	{
		Pathfinder pathfinder = new Pathfinder(config, start, goal);
		ShortestPathPlugin.setPathfinder(pathfinder);
		try
		{
			pathfinder.run();
		}
		catch (RuntimeException e)
		{
			log.warn("Pathfinder failed for {} -> {}", start, goal, e);
			return PathResult.failed(start, goal);
		}
		return toResult(pathfinder, start, goal);
	}

	/** {@link #search(PathfinderConfig, WorldPoint, WorldPoint)} towards any of {@code goals}. */
	public static PathResult search(PathfinderConfig config, WorldPoint start, Set<WorldPoint> goals)
	{
		Pathfinder pathfinder = new Pathfinder(config, start, goals);
		ShortestPathPlugin.setPathfinder(pathfinder);
		try
		{
			pathfinder.run();
		}
		catch (RuntimeException e)
		{
			log.warn("Pathfinder failed for {} -> any of {}", start, goals.size(), e);
			return PathResult.failed(start, null);
		}
		List<WorldPoint> tiles = pathfinder.getPath();
		WorldPoint end = tiles == null || tiles.isEmpty() ? null : tiles.get(tiles.size() - 1);
		boolean reached = pathfinder.getTerminationReason() == PathTerminationReason.TARGET_REACHED && goals.contains(end);
		return toResult(pathfinder, start, reached ? end : null);
	}

	static PathResult toResult(Pathfinder pathfinder, WorldPoint start, WorldPoint goal)
	{
		List<WorldPoint> tiles = pathfinder.getPath();
		Map<Integer, PathResult.TransportStep> transports = new HashMap<>();
		List<PathEdge> edges = pathfinder.getPathEdges();
		for (int i = 0; edges != null && i < edges.size() && i + 1 < tiles.size(); i++)
		{
			PathEdge edge = edges.get(i);
			if (edge.getTransport() == null)
			{
				continue;
			}
			// Edges follow the tile path one to one; refuse to guess if they ever disagree.
			if (!tiles.get(i).equals(edge.getFrom()) || !tiles.get(i + 1).equals(edge.getTo()))
			{
				log.warn("Pathfinder edge {} does not match tiles {} -> {}", i, tiles.get(i), tiles.get(i + 1));
				return PathResult.failed(start, goal);
			}
			transports.put(i, new PathResult.TransportStep(edge.getTransport()));
		}
		return new PathResult(status(pathfinder.getTerminationReason(), tiles), start, goal, tiles, transports);
	}

	private static PathResult.Status status(PathTerminationReason reason, List<WorldPoint> tiles)
	{
		if (reason == null)
		{
			return PathResult.Status.FAILED;
		}
		switch (reason)
		{
			case TARGET_REACHED:
				return PathResult.Status.FOUND;
			case CUTOFF_REACHED:
				return PathResult.Status.TIMED_OUT;
			case CANCELLED:
				return PathResult.Status.CANCELLED;
			case SEARCH_EXHAUSTED:
				return tiles.size() > 1 ? PathResult.Status.PARTIAL : PathResult.Status.UNREACHABLE;
			default:
				return PathResult.Status.FAILED;
		}
	}
}
