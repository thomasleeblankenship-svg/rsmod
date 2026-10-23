package net.runelite.client.plugins.microbot.util.coords;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/**
 * A rectangle of world tiles on one floor. Microbot scripts build these for fixed areas (for
 * example the Barrows mounds) and use RuneLite's {@link WorldArea} queries on them.
 */
public class Rs2WorldArea extends WorldArea
{
	public Rs2WorldArea(int x, int y, int width, int height, int plane)
	{
		super(x, y, width, height, plane);
	}

	public Rs2WorldArea(WorldPoint location, int width, int height)
	{
		super(location, width, height);
	}
}
