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
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
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
	/** How long a found route may be reused for a walk to the same goal. */
	static final long ROUTE_REUSE_MILLIS = 60_000;
	private static volatile CachedRoute lastRoute;
	private static final java.util.concurrent.atomic.AtomicBoolean WARMING = new java.util.concurrent.atomic.AtomicBoolean();

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
		return find(start, goal, false);
	}

	/**
	 * {@link #find(WorldPoint, WorldPoint)}, optionally planning as if the remembered bank's items
	 * were carried (upstream's {@code withBankItems}), for the banking reads. Bank routes are
	 * never reused. A start inside the player-owned house is replaced by {@link PohPanel#HOUSE_TILE}
	 * (after the house is detected), so the result's start and first tile are that tile.
	 */
	public static PathResult find(WorldPoint origin, WorldPoint goal, boolean useBankItems)
	{
		WorldPoint start = origin;
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(goal, "goal");
		Client client = Microbot.getClient();
		if (client != null && client.isClientThread())
		{
			throw new IllegalStateException("MobilePathfinder.find must not run on the client thread");
		}
		if (client != null && net.runelite.client.plugins.microbot.util.poh.PohPresence.isInHouse())
		{
			// Inside the house the player's tile is an instance template: detect the house now and
			// plan from its synthetic tile, as the ways out leave from there.
			net.runelite.client.plugins.microbot.util.poh.PohLayoutStore.refreshIfInHouse();
			start = PohPanel.HOUSE_TILE;
		}
		PathfinderConfig shared = client == null ? null : sharedConfig(client);
		PathResult reused = shared == null || useBankItems ? null
			: reuse(lastRoute, start, goal, shared.getMap(), policy, System.currentTimeMillis());
		if (reused != null)
		{
			log.debug("Pathfinder route reused for {} -> {}", start, goal);
			return reused;
		}
		if (shared != null && useBankItems)
		{
			synchronized (LOCK)
			{
				shared.setUseBankItems(true);
				try
				{
					PathfinderConfig config = refreshedConfig(start, goal);
					return config == null ? PathResult.failed(start, goal) : search(config, start, goal);
				}
				finally
				{
					shared.setUseBankItems(false);
				}
			}
		}
		PathfinderConfig config = refreshedConfig(start, goal);
		if (config == null)
		{
			return PathResult.failed(start, goal);
		}
		PathResult result = search(config, start, goal);
		lastRoute = result.isFound() ? new CachedRoute(result, policy, System.currentTimeMillis()) : null;
		return result;
	}

	/**
	 * Loads the shared pathfinder data (collision map, transports, restrictions) on a background
	 * thread if it is not loaded yet, so the first walk does not wait for it. Idempotent.
	 */
	public static void warmUp()
	{
		Client client = Microbot.getClient();
		if (client == null || sharedConfig != null || !WARMING.compareAndSet(false, true))
		{
			return;
		}
		Thread loader = new Thread(() ->
		{
			try
			{
				sharedConfig(client);
			}
			catch (RuntimeException e)
			{
				log.warn("Pathfinder warm-up failed", e);
			}
			finally
			{
				WARMING.set(false);
			}
		}, "pathfinder-warmup");
		loader.setDaemon(true);
		loader.start();
	}

	/** Forgets the reusable route (logout, hop, or a caller that knows the world changed). */
	public static void clearRouteCache()
	{
		lastRoute = null;
	}

	/** A found route, the policy it was planned under, and when. */
	static final class CachedRoute
	{
		final PathResult result;
		final TransportPlanningPolicy policy;
		final long createdAt;

		CachedRoute(PathResult result, TransportPlanningPolicy policy, long createdAt)
		{
			this.result = result;
			this.policy = policy;
			this.createdAt = createdAt;
		}
	}

	/**
	 * The rest of {@code cached}'s route from {@code start}, when it was found for {@code goal} under
	 * {@code currentPolicy} less than {@link #ROUTE_REUSE_MILLIS} ago and {@code start} is one of its
	 * tiles or one step (on {@code map}) from one; the furthest such tile is used. {@code null} when
	 * the route cannot be reused, including when the rest of it holds an item or spell teleport,
	 * whose runes or charges may since have been spent.
	 */
	static PathResult reuse(CachedRoute cached, WorldPoint start, WorldPoint goal, CollisionMap map,
		TransportPlanningPolicy currentPolicy, long now)
	{
		if (cached == null || cached.policy != currentPolicy || now - cached.createdAt > ROUTE_REUSE_MILLIS
			|| !goal.equals(cached.result.getGoal()))
		{
			return null;
		}
		List<WorldPoint> tiles = cached.result.getTiles();
		int from = -1;
		boolean onRoute = false;
		for (int i = tiles.size() - 1; i >= 0 && from < 0; i--)
		{
			WorldPoint tile = tiles.get(i);
			if (tile.equals(start))
			{
				from = i;
				onRoute = true;
			}
			else if (map != null && tile.getPlane() == start.getPlane()
				&& Math.abs(tile.getX() - start.getX()) <= 1 && Math.abs(tile.getY() - start.getY()) <= 1
				&& map.canStep(start.getX(), start.getY(), start.getPlane(), tile.getX() - start.getX(), tile.getY() - start.getY()))
			{
				from = i;
			}
		}
		if (from < 0)
		{
			return null;
		}
		int shift = onRoute ? from : from - 1;
		List<WorldPoint> rest = new java.util.ArrayList<>();
		if (!onRoute)
		{
			rest.add(start);
		}
		rest.addAll(tiles.subList(from, tiles.size()));
		Map<Integer, PathResult.TransportStep> transports = new HashMap<>();
		for (Map.Entry<Integer, PathResult.TransportStep> entry : cached.result.getTransports().entrySet())
		{
			if (entry.getKey() < from)
			{
				continue;
			}
			TransportType type = entry.getValue().getType();
			if (type == TransportType.TELEPORTATION_ITEM || type == TransportType.TELEPORTATION_SPELL)
			{
				return null;
			}
			transports.put(entry.getKey() - shift, entry.getValue());
		}
		return new PathResult(PathResult.Status.FOUND, start, goal, rest, transports);
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
		return findAny(start, goals, false);
	}

	/** {@link #findAny(WorldPoint, Set)}, optionally planning as if the remembered bank's items were carried. */
	public static PathResult findAny(WorldPoint start, Set<WorldPoint> goals, boolean useBankItems)
	{
		Objects.requireNonNull(start, "start");
		if (goals == null || goals.isEmpty())
		{
			return PathResult.failed(start, null);
		}
		Client client = Microbot.getClient();
		PathfinderConfig shared = client == null || !useBankItems ? null : sharedConfig(client);
		if (shared != null)
		{
			synchronized (LOCK)
			{
				shared.setUseBankItems(true);
				try
				{
					PathfinderConfig config = refreshedConfig(start, null);
					return config == null ? PathResult.failed(start, null) : search(config, start, goals);
				}
				finally
				{
					shared.setUseBankItems(false);
				}
			}
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
			lastRoute = null;
		}
	}

	/** The shared config (loading it on first use), or {@code null} without an installed client. */
	static PathfinderConfig sharedConfig()
	{
		Client client = Microbot.getClient();
		return client == null ? null : sharedConfig(client);
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
