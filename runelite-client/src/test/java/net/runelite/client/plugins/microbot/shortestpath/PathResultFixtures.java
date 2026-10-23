package net.runelite.client.plugins.microbot.shortestpath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/** Builds {@link PathResult}s for walker tests without running a search. */
public final class PathResultFixtures
{
	private PathResultFixtures()
	{
	}

	public static PathResult result(PathResult.Status status, List<WorldPoint> tiles, Map<Integer, Transport> transports)
	{
		Map<Integer, PathResult.TransportStep> steps = new HashMap<>();
		transports.forEach((index, transport) -> steps.put(index, new PathResult.TransportStep(transport)));
		return new PathResult(status, tiles.get(0), tiles.get(tiles.size() - 1), tiles, steps);
	}

	public static Transport transport(WorldPoint origin, WorldPoint destination, TransportType type, String action, String name, int objectId)
	{
		return new Transport(origin, destination, name, type, false, action, name, objectId);
	}
}
