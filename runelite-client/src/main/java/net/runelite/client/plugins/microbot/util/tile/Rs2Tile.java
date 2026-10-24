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
 * <p>
 * The dangerous-tile block at the end is upstream's tracking of tiles to avoid (projectiles and
 * hazards), counted down every 600 ms after {@link #init()}.
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

	/**
	 * As upstream: the scene tile at world ({@code x}, {@code y}) on the current floor (instance
	 * coordinates converted), or {@code null} outside the loaded scene.
	 */
	public static Tile getTile(int x, int y)
	{
		return MobileActions.read(() ->
		{
			WorldView view = Microbot.getClient().getTopLevelWorldView();
			if (view == null || view.getScene() == null)
			{
				return null;
			}
			WorldPoint point = new WorldPoint(x, y, view.getPlane());
			LocalPoint local = view.getScene().isInstance()
				? net.runelite.client.plugins.microbot.util.coords.Rs2LocalPoint.fromWorldInstance(point)
				: LocalPoint.fromWorld(view, point);
			Tile[][][] tiles = view.getScene().getTiles();
			if (local == null || tiles == null || point.getPlane() < 0 || point.getPlane() >= tiles.length)
			{
				return null;
			}
			Tile[][] plane = tiles[point.getPlane()];
			int sx = local.getSceneX();
			int sy = local.getSceneY();
			return sx < 0 || sy < 0 || sx >= plane.length || sy >= plane[sx].length ? null : plane[sx][sy];
		}, null);
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

	// ---- Hub reads: dangerous tiles (upstream 2.6.38) ----

	private static final java.util.Map<WorldPoint, Integer> DANGEROUS_TILES = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.Map<WorldPoint, Integer> DANGEROUS_WORLD_TILES = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.Map<LocalPoint, Integer> DANGEROUS_LOCAL_TILES = new java.util.concurrent.ConcurrentHashMap<>();
	private static java.util.concurrent.ScheduledExecutorService tileExecutor;

	/**
	 * Called with the tile when a dangerous tile is added where the player stands. Upstream walks
	 * to {@link #getSafeTile()} there (an action); on mobile the walker can register that here.
	 * Called on the thread that added the tile (often the client thread): it must not block.
	 */
	public static volatile java.util.function.Consumer<WorldPoint> onPlayerOnDangerousTile;

	/** As upstream: starts the 600 ms countdown that forgets dangerous tiles when their time runs out. Idempotent. */
	public static synchronized void init()
	{
		if (tileExecutor != null)
		{
			return;
		}
		tileExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r ->
		{
			Thread thread = new Thread(r, "rs2tile-danger");
			thread.setDaemon(true);
			return thread;
		});
		tileExecutor.scheduleWithFixedDelay(Rs2Tile::tickDangerousTiles, 0, 600, java.util.concurrent.TimeUnit.MILLISECONDS);
	}

	/** One countdown step of 600 ms. */
	static void tickDangerousTiles()
	{
		for (java.util.Map<?, Integer> tiles : List.of(DANGEROUS_TILES, DANGEROUS_WORLD_TILES, DANGEROUS_LOCAL_TILES))
		{
			tiles.replaceAll((tile, time) -> time - 600);
			tiles.values().removeIf(time -> time <= 0);
		}
	}

	/** As upstream: marks the graphics object's tile dangerous for {@code time} ms. */
	public static void addDangerousGraphicsObjectTile(net.runelite.api.GraphicsObject graphicsObject, int time)
	{
		if (graphicsObject == null)
		{
			return;
		}
		WorldPoint worldPoint = MobileActions.read(() ->
		{
			Client client = Microbot.getClient();
			LocalPoint location = graphicsObject.getLocation();
			if (location == null)
			{
				return null;
			}
			return client.getTopLevelWorldView().getScene().isInstance()
				? WorldPoint.fromLocalInstance(client, location) : WorldPoint.fromLocal(client, location);
		}, null);
		addDangerous(worldPoint, time);
	}

	/** As upstream: marks the game object's tile dangerous for {@code time} ms. */
	public static void addDangerousGameObjectTile(net.runelite.api.GameObject gameObject, int time)
	{
		if (gameObject != null)
		{
			addDangerous(MobileActions.read(gameObject::getWorldLocation, null), time);
		}
	}

	private static void addDangerous(WorldPoint worldPoint, int time)
	{
		if (worldPoint == null)
		{
			return;
		}
		DANGEROUS_TILES.merge(worldPoint, time, Math::max);
		java.util.function.Consumer<WorldPoint> hook = onPlayerOnDangerousTile;
		if (hook != null && worldPoint.equals(net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation()))
		{
			hook.accept(worldPoint);
		}
	}

	/**
	 * As upstream: in an instance, marks the graphics object's tile dangerous (world tile for
	 * dodging, local tile for overlays), ignoring "ghost" graphics more than 10 tiles away.
	 */
	public static void addDangerousGraphicsObjectTileForInstances(net.runelite.api.GraphicsObject graphicsObject, int time)
	{
		if (graphicsObject == null)
		{
			return;
		}
		LocalPoint local = MobileActions.read(graphicsObject::getLocation, null);
		WorldPoint world = local == null ? null : MobileActions.read(() -> WorldPoint.fromLocalInstance(Microbot.getClient(), local), null);
		WorldPoint player = net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
		if (world == null || player == null || world.distanceTo(player) > 10)
		{
			return;
		}
		DANGEROUS_WORLD_TILES.merge(world, time, Math::max);
		DANGEROUS_LOCAL_TILES.merge(local, time, Math::max);
	}

	/**
	 * The dangerous tiles and their remaining ms. Adaptation: upstream returns only the instance
	 * map here, so tiles from {@link #addDangerousGraphicsObjectTile} and
	 * {@link #addDangerousGameObjectTile} never showed; mobile returns both (the larger time wins).
	 */
	public static java.util.Map<WorldPoint, Integer> getDangerousGraphicsObjectTiles()
	{
		java.util.Map<WorldPoint, Integer> all = new java.util.HashMap<>(DANGEROUS_TILES);
		DANGEROUS_WORLD_TILES.forEach((tile, time) -> all.merge(tile, time, Math::max));
		return java.util.Collections.unmodifiableMap(all);
	}

	/** As upstream: the instance tiles by local point, for overlays. */
	public static java.util.Map<LocalPoint, Integer> getDangerousLocalTiles()
	{
		return java.util.Collections.unmodifiableMap(DANGEROUS_LOCAL_TILES);
	}

	/** As upstream: reachable tiles within {@code radius} that are not dangerous. */
	public static List<WorldPoint> getSafeTiles(int radius)
	{
		java.util.Map<WorldPoint, Integer> dangerous = getDangerousGraphicsObjectTiles();
		List<WorldPoint> safe = new java.util.ArrayList<>();
		for (WorldPoint tile : getWalkableTilesAroundPlayer(radius))
		{
			if (!dangerous.containsKey(tile))
			{
				safe.add(tile);
			}
		}
		return safe;
	}

	/** As upstream: the nearest safe tile within 4, or {@code null}. */
	public static WorldPoint getSafeTile()
	{
		WorldPoint player = net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
		if (player == null)
		{
			return null;
		}
		return getSafeTiles(4).stream().min(java.util.Comparator.comparingInt(tile -> tile.distanceTo(player))).orElse(null);
	}

	/** Forgets every dangerous tile (tests, logout). */
	public static void clearDangerousTiles()
	{
		DANGEROUS_TILES.clear();
		DANGEROUS_WORLD_TILES.clear();
		DANGEROUS_LOCAL_TILES.clear();
	}
}
