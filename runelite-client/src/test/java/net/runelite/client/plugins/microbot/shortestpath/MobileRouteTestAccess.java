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
}
