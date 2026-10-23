package net.runelite.client.plugins.microbot.util.tile;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/** Same-plane, collision-reachable tiles from a fresh scene snapshot; never guesses through walls. */
public final class Rs2Tile
{
	private Rs2Tile()
	{
	}

	public static List<WorldPoint> getWalkableTilesAroundPlayer(int distance)
	{
		return Rs2Walker.getWalkableTilesAroundPlayer(distance);
	}
}
