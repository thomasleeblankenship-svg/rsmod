package net.runelite.client.plugins.mobilemicrobot;

import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
	* LIMITED same-scene minimap movement (no shortest path, doors, teleports,
	* banking, cross-region travel, collision/pathfinding or arrival guarantees).
	*/
public final class Rs2Walker
{
	private final MobileScriptContext context;

	Rs2Walker(MobileScriptContext context)
	{
		this.context = context;
	}

	public int distanceTo(WorldPoint destination)
	{
		WorldPoint position = context.player().location();
		if (position == null || destination == null || position.getPlane() != destination.getPlane())
		{
			return Integer.MAX_VALUE;
		}
		return position.distanceTo(destination);
	}

	public boolean isAt(WorldPoint destination, int withinTiles)
	{
		return distanceTo(destination) <= Math.max(0, withinTiles);
	}

	/** One minimap tap only. Must be called on RuneLite's client thread. */
	public boolean walkToVisible(WorldPoint destination)
	{
		if (!context.loggedIn() || destination == null || distanceTo(destination) > 15)
		{
			return false;
		}
		LocalPoint local = LocalPoint.fromWorld(context.client(), destination);
		if (local == null)
		{
			return false;
		}
		Point point = Perspective.localToMinimap(context.client(), local);
		return context.queueClick(point);
	}
}
