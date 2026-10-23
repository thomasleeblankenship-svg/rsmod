package net.runelite.client.plugins.microbot.util.tile;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Same-plane, collision-reachable tiles from a fresh scene snapshot; never guesses through walls.
 * <p>
 * {@code isWalkable} reads the loaded scene's collision flags on the client thread: a tile is
 * walkable when nothing (object, floor decoration or blocked floor) fills it. Tiles outside the
 * loaded scene, or on a floor without collision data, are not walkable. {@code isTileReachable}
 * asks the walker's in-scene route search, so it agrees with where the walker can go.
 */
public final class Rs2Tile
{
	private Rs2Tile()
	{
	}

	public static List<WorldPoint> getWalkableTilesAroundPlayer(int distance)
	{
		return Rs2Walker.getWalkableTilesAroundPlayer(distance);
	}

	/** Whether nothing fills {@code worldPoint} in the loaded scene. */
	public static boolean isWalkable(WorldPoint worldPoint)
	{
		if (worldPoint == null)
		{
			return false;
		}
		return MobileActions.read(() ->
		{
			WorldView view = Microbot.getClient().getTopLevelWorldView();
			return view != null && walkable(view, worldPoint.getX() - view.getBaseX(),
				worldPoint.getY() - view.getBaseY(), worldPoint.getPlane());
		}, false);
	}

	/** Whether nothing fills the scene tile at {@code localPoint}, on the local player's floor. */
	public static boolean isWalkable(LocalPoint localPoint)
	{
		if (localPoint == null)
		{
			return false;
		}
		return MobileActions.read(() ->
		{
			Client client = Microbot.getClient();
			WorldView view = client.getTopLevelWorldView();
			Player player = client.getLocalPlayer();
			WorldPoint location = player == null ? null : player.getWorldLocation();
			return view != null && location != null
				&& walkable(view, localPoint.getSceneX(), localPoint.getSceneY(), location.getPlane());
		}, false);
	}

	/** Whether nothing fills {@code tile}. */
	public static boolean isWalkable(Tile tile)
	{
		if (tile == null)
		{
			return false;
		}
		return isWalkable(MobileActions.read(tile::getWorldLocation, null));
	}

	/** Whether the local player can walk to {@code targetPoint} on the current floor within the loaded scene. */
	public static boolean isTileReachable(WorldPoint targetPoint)
	{
		if (targetPoint == null)
		{
			return false;
		}
		boolean inScene = MobileActions.read(() ->
		{
			Client client = Microbot.getClient();
			WorldView view = client.getTopLevelWorldView();
			Player player = client.getLocalPlayer();
			WorldPoint location = player == null ? null : player.getWorldLocation();
			return view != null && location != null && location.getPlane() == targetPoint.getPlane()
				&& flags(view, targetPoint.getPlane()) != null
				&& inBounds(flags(view, targetPoint.getPlane()), targetPoint.getX() - view.getBaseX(), targetPoint.getY() - view.getBaseY());
		}, false);
		return inScene && Rs2Walker.canReach(targetPoint);
	}

	private static boolean walkable(WorldView view, int sceneX, int sceneY, int plane)
	{
		int[][] flags = flags(view, plane);
		return flags != null && inBounds(flags, sceneX, sceneY)
			&& (flags[sceneX][sceneY] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0;
	}

	private static int[][] flags(WorldView view, int plane)
	{
		CollisionData[] maps = view.getCollisionMaps();
		if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null)
		{
			return null;
		}
		return maps[plane].getFlags();
	}

	private static boolean inBounds(int[][] flags, int sceneX, int sceneY)
	{
		return sceneX >= 0 && sceneX < flags.length && flags[sceneX] != null && sceneY >= 0 && sceneY < flags[sceneX].length;
	}
}
