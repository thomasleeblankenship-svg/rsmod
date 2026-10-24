package net.runelite.client.plugins.microbot.util.walker;

import java.util.Collection;
import net.runelite.api.GameObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

/**
 * As upstream: a read-only check of whether an object can be interacted with from here once
 * walking has returned: within 12 tiles on the same floor, on screen, and with a collision-aware
 * approach (a walkable tile within 16 path tiles of a game object, or a reachable tile next to a
 * wall object). Each read goes to the client thread on its own.
 */
public final class Rs2InteractionApproach
{
	private static final int MAX_RANGE = 12;
	private static final int MAX_APPROACH = 16;

	private Rs2InteractionApproach()
	{
	}

	/** As upstream: {@code object} is near, on screen, and its nearest walkable tile is close by path. */
	public static boolean isReady(GameObject object)
	{
		if (object == null)
		{
			return false;
		}
		WorldPoint player = Rs2Player.getWorldLocation();
		WorldPoint target = MobileActions.read(object::getWorldLocation, null);
		if (!withinRange(player, target) || !Rs2Camera.isTileOnScreen(object))
		{
			return false;
		}
		Rs2WorldPoint approach = Rs2Tile.getNearestWalkableTile(object);
		return approach != null && approach.distanceToPath(player) <= MAX_APPROACH;
	}

	/** As upstream: {@code object} is near, on screen, and a reachable tile touches it. */
	public static boolean isReady(WallObject object)
	{
		if (object == null)
		{
			return false;
		}
		WorldPoint player = Rs2Player.getWorldLocation();
		WorldPoint target = MobileActions.read(object::getWorldLocation, null);
		if (!withinRange(player, target) || !Rs2Camera.isTileOnScreen(object))
		{
			return false;
		}
		return hasReachableWallApproach(target, Rs2Tile.getReachableTilesFromTile(player, MAX_APPROACH).keySet());
	}

	static boolean hasReachableWallApproach(WorldPoint target, Collection<WorldPoint> reachable)
	{
		return target != null && reachable != null && reachable.stream()
			.anyMatch(tile -> tile != null && tile.getPlane() == target.getPlane() && tile.distanceTo2D(target) <= 1);
	}

	static boolean withinRange(WorldPoint player, WorldPoint target)
	{
		return player != null && target != null && player.getPlane() == target.getPlane()
			&& player.distanceTo2D(target) <= MAX_RANGE;
	}
}
