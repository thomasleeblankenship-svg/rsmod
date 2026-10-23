/*
 * Adapted from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/shortestpath/ShortestPathPlugin.java
 * (static state, config overrides, getTransports, exit). BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.shortestpath;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Headless static facade with upstream's {@code ShortestPathPlugin} names.
 * <p>
 * Adaptation: upstream is a RuneLite plugin with a side panel, overlays, map markers and its
 * own walking script. Mobile keeps only the static state the pathfinder and Hub plugins read
 * ({@link #getPathfinderConfig()}, {@link #getPathfinder()}, {@link #getTransports()},
 * {@link #exit()}, the config overrides) and has no UI. {@link MobilePathfinder} installs the
 * config and runs searches; walking stays in {@code Rs2Walker}.
 */
public final class ShortestPathPlugin
{
	public static final String CONFIG_GROUP = "shortestpath";

	/** Per-request overrides of config values, as upstream's plugin-message {@code config} map. */
	private static final Map<String, Object> configOverride = new ConcurrentHashMap<>();

	@Getter
	@Setter
	public static volatile Pathfinder pathfinder;

	@Getter
	public static volatile PathfinderConfig pathfinderConfig;

	/**
	 * Global teleport switch read by {@link PathfinderConfig}. Upstream keeps it as
	 * {@code Rs2Walker.disableTeleports}; it moves there once the walker owns it.
	 */
	public static volatile boolean disableTeleports;

	private ShortestPathPlugin()
	{
	}

	static void setPathfinderConfig(PathfinderConfig config)
	{
		pathfinderConfig = config;
	}

	/** Replaces the config overrides; {@code null} or empty clears them. Keys are upstream's. */
	public static void setConfigOverride(Map<String, Object> overrides)
	{
		configOverride.clear();
		if (overrides != null)
		{
			overrides.forEach((key, value) ->
			{
				if (key != null && value != null)
				{
					configOverride.put(key, value);
				}
			});
		}
	}

	public static Map<WorldPoint, Set<Transport>> getTransports()
	{
		PathfinderConfig config = pathfinderConfig;
		return config == null ? Map.of() : config.getTransports();
	}

	/** As upstream: cancels any search and clears the walker's target. */
	public static void exit()
	{
		Pathfinder current = pathfinder;
		if (current != null)
		{
			current.cancel();
		}
		Rs2Walker.setTarget(null);
	}

	public static boolean override(String configOverrideKey, boolean defaultValue)
	{
		Object value = configOverride.get(configOverrideKey);
		return value instanceof Boolean ? (boolean) value : defaultValue;
	}

	public static int override(String configOverrideKey, int defaultValue)
	{
		Object value = configOverride.get(configOverrideKey);
		return value instanceof Integer ? (int) value : defaultValue;
	}

	public static TeleportationItem override(String configOverrideKey, TeleportationItem defaultValue)
	{
		Object value = configOverride.get(configOverrideKey);
		if (value instanceof String)
		{
			TeleportationItem teleportationItem = TeleportationItem.fromType((String) value);
			if (teleportationItem != null)
			{
				return teleportationItem;
			}
		}
		return defaultValue;
	}

	public static PlannerSelectionMode override(String configOverrideKey, PlannerSelectionMode defaultValue)
	{
		if (configOverride.isEmpty())
		{
			return defaultValue;
		}
		return PlannerSelectionMode.fromConfigValue(configOverride.get(configOverrideKey), defaultValue);
	}
}
