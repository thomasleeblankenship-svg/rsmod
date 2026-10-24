package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;

/** Test-only access to {@link MobileRouteTest}'s offline pathfinder config from other packages. */
public final class MobileRouteTestAccess
{
	private MobileRouteTestAccess()
	{
	}

	public static PathfinderConfig offlineConfig(MobileTransportPolicy policy)
	{
		return MobileRouteTest.offlineConfig(policy);
	}

	/** {@link MobileRouteTest#offlineConfig(MobileTransportPolicy, java.util.function.Predicate)}. */
	public static PathfinderConfig offlineConfig(MobileTransportPolicy policy, java.util.function.Predicate<Transport> usable)
	{
		return MobileRouteTest.offlineConfig(policy, usable);
	}

	/** The executor-facing copy of {@code transport}, as a route would carry it. */
	public static PathResult.TransportStep step(Transport transport)
	{
		return new PathResult.TransportStep(transport);
	}

	/** The loaded transport catalogue (shared, do not modify). */
	public static java.util.Map<net.runelite.api.coords.WorldPoint, java.util.Set<Transport>> catalogue()
	{
		return MobileRouteTest.catalogue();
	}
}
