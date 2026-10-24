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

	/**
	 * As upstream: the first walkable neighbour (north, south, east, west order of
	 * {@link net.runelite.api.coords.Direction}) of {@code source} that is not the player's tile.
	 */
	public static WorldPoint getNearestWalkableTile(WorldPoint source)
	{
		if (source == null)
		{
			return null;
		}
		WorldPoint player = net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
		for (net.runelite.api.coords.Direction direction : net.runelite.api.coords.Direction.values())
		{
			WorldPoint neighbour = neighbour(direction, source);
			if (!neighbour.equals(player) && isWalkable(neighbour))
			{
				return neighbour;
			}
		}
		return null;
	}

	/**
	 * As upstream: the walkable tile next to {@code gameObject} from which it can be interacted with
	 * (melee-adjacent, else wall-free), nearest to the player; {@code null} on another floor.
	 */
	public static net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint getNearestWalkableTile(net.runelite.api.GameObject gameObject)
	{
		WorldPoint player = net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
		net.runelite.api.coords.WorldArea area = gameObject == null ? null
			: net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.getWorldArea(gameObject);
		if (player == null || area == null || player.getPlane() != area.getPlane())
		{
			return null;
		}
		WorldPoint nearest = MobileActions.read(() ->
		{
			WorldView view = Microbot.getClient().getTopLevelWorldView();
			List<WorldPoint> points = interactablePoints(new net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea(area), view);
			return points.stream().filter(Rs2Tile::isWalkable)
				.min(java.util.Comparator.comparingInt(p -> net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint.quickDistance(player, p)))
				.orElse(null);
		}, null);
		return nearest == null ? null : new net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint(nearest);
	}

	private static List<WorldPoint> interactablePoints(net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea area, WorldView view)
	{
		List<WorldPoint> points = new java.util.ArrayList<>(area.getInteractable());
		if (points.isEmpty())
		{
			points = area.offset(1).toWorldPointList();
			points.removeIf(area::contains);
			points.removeIf(p -> !area.canMelee(view, new net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea(p.toWorldArea())));
			return points;
		}
		List<WorldPoint> melee = new java.util.ArrayList<>();
		for (WorldPoint p : points)
		{
			if (area.canMelee(view, new net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea(p.toWorldArea())))
			{
				melee.add(p);
			}
		}
		if (!melee.isEmpty())
		{
			return melee;
		}
		points.removeIf(p ->
		{
			Tile tile = getTile(p.getX(), p.getY());
			return tile != null && tile.getWallObject() != null;
		});
		return points;
	}

	private static WorldPoint neighbour(net.runelite.api.coords.Direction direction, WorldPoint source)
	{
		switch (direction)
		{
			case NORTH:
				return source.dy(1);
			case SOUTH:
				return source.dy(-1);
			case WEST:
				return source.dx(-1);
			default:
				return source.dx(1);
		}
	}

	/**
	 * As upstream: {@code source} itself when it is a free tile (no wall, no movement flag, or a bank
	 * booth), else the first such neighbour that is not the player's tile.
	 */
	public static WorldPoint getNearestWalkableTileWithLineOfSight(WorldPoint source)
	{
		if (source == null)
		{
			return null;
		}
		if (freeOrBooth(source))
		{
			return source;
		}
		WorldPoint player = net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
		for (net.runelite.api.coords.Direction direction : net.runelite.api.coords.Direction.values())
		{
			WorldPoint neighbour = neighbour(direction, source);
			if (!neighbour.equals(player) && freeOrBooth(neighbour))
			{
				return neighbour;
			}
		}
		return null;
	}

	/** Upstream's line-of-sight tile test: no wall object, no movement flags, and walkable or a bank booth. */
	private static boolean freeOrBooth(WorldPoint point)
	{
		boolean free = MobileActions.read(() ->
		{
			WorldView view = Microbot.getClient().getTopLevelWorldView();
			Tile tile = getTile(point.getX(), point.getY());
			int[][] flags = view == null ? null : flags(view, view.getPlane());
			LocalPoint local = view == null ? null : LocalPoint.fromWorld(view, point);
			return tile != null && tile.getWallObject() == null && flags != null && local != null
				&& inBounds(flags, local.getSceneX(), local.getSceneY()) && flags[local.getSceneX()][local.getSceneY()] == 0;
		}, false);
		if (free)
		{
			return true;
		}
		net.runelite.api.GameObject booth = net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.getGameObject(o -> point.equals(o.getWorldLocation()), point, 1);
		net.runelite.api.ObjectComposition composition = booth == null ? null : net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.convertToObjectComposition(booth);
		return composition != null && "bank booth".equalsIgnoreCase(MobileActions.read(composition::getName, ""));
	}

	/** As upstream: tiles reachable from {@code tile} with their step distance (no distance limit). */
	public static java.util.HashMap<WorldPoint, Integer> getReachableTilesFromTile(WorldPoint tile)
	{
		return getReachableTilesFromTile(tile, Integer.MAX_VALUE, false);
	}

	/** As upstream: tiles reachable from {@code tile} within {@code distance} steps, respecting collision. */
	public static java.util.HashMap<WorldPoint, Integer> getReachableTilesFromTile(WorldPoint tile, int distance)
	{
		return getReachableTilesFromTile(tile, distance, false);
	}

	/**
	 * As upstream: a breadth-first walk over the collision map from {@code tile} (8 directions,
	 * diagonals only when both sides are open), giving each reachable tile its step distance; with
	 * {@code ignoreCollision} every tile in range counts. One client-thread read.
	 */
	public static java.util.HashMap<WorldPoint, Integer> getReachableTilesFromTile(WorldPoint tile, int distance, boolean ignoreCollision)
	{
		return MobileActions.read(() -> reachable(tile, distance, ignoreCollision), new java.util.HashMap<>());
	}

	private static java.util.HashMap<WorldPoint, Integer> reachable(WorldPoint tile, int distance, boolean ignoreCollision)
	{
		java.util.HashMap<WorldPoint, Integer> distances = new java.util.HashMap<>();
		WorldView view = tile == null ? null : Microbot.getClient().getTopLevelWorldView();
		int[][] flags = view == null ? null : flags(view, view.getPlane());
		if (flags == null)
		{
			return distances;
		}
		boolean instance = view.getScene() != null && view.getScene().isInstance();
		java.util.ArrayDeque<WorldPoint> queue = new java.util.ArrayDeque<>();
		distances.put(tile, 0);
		queue.add(tile);
		while (!queue.isEmpty())
		{
			WorldPoint point = queue.poll();
			int dist = distances.get(point);
			WorldPoint scenePoint = instance ? WorldPoint.toLocalInstance(view, point).stream().findFirst().orElse(null) : point;
			LocalPoint local = scenePoint == null ? null : LocalPoint.fromWorld(view, scenePoint);
			if (local == null || !inBounds(flags, local.getSceneX(), local.getSceneY()))
			{
				continue;
			}
			int sx = local.getSceneX();
			int sy = local.getSceneY();
			int data = flags[sx][sy];
			if (!ignoreCollision && !tile.equals(point) && (data & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0)
			{
				distances.remove(point);
				continue;
			}
			if (dist >= distance)
			{
				continue;
			}
			boolean east = ignoreCollision || (data & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0;
			boolean west = ignoreCollision || (data & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0;
			boolean north = ignoreCollision || (data & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0;
			boolean south = ignoreCollision || (data & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0;
			visit(distances, queue, east, point.dx(1), dist);
			visit(distances, queue, west, point.dx(-1), dist);
			visit(distances, queue, north, point.dy(1), dist);
			visit(distances, queue, south, point.dy(-1), dist);
			visit(distances, queue, north && east && diagonalOpen(flags, sx, sy, 1, 1, CollisionDataFlag.BLOCK_MOVEMENT_NORTH, CollisionDataFlag.BLOCK_MOVEMENT_EAST, ignoreCollision), point.dx(1).dy(1), dist);
			visit(distances, queue, north && west && diagonalOpen(flags, sx, sy, -1, 1, CollisionDataFlag.BLOCK_MOVEMENT_NORTH, CollisionDataFlag.BLOCK_MOVEMENT_WEST, ignoreCollision), point.dx(-1).dy(1), dist);
			visit(distances, queue, south && east && diagonalOpen(flags, sx, sy, 1, -1, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH, CollisionDataFlag.BLOCK_MOVEMENT_EAST, ignoreCollision), point.dx(1).dy(-1), dist);
			visit(distances, queue, south && west && diagonalOpen(flags, sx, sy, -1, -1, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH, CollisionDataFlag.BLOCK_MOVEMENT_WEST, ignoreCollision), point.dx(-1).dy(-1), dist);
		}
		return distances;
	}

	private static void visit(java.util.Map<WorldPoint, Integer> distances, java.util.ArrayDeque<WorldPoint> queue, boolean open, WorldPoint next, int dist)
	{
		if (open && distances.putIfAbsent(next, dist + 1) == null)
		{
			queue.add(next);
		}
	}

	/** Upstream's diagonal rule: the horizontal and vertical neighbours and the corner are all passable. */
	private static boolean diagonalOpen(int[][] flags, int sx, int sy, int dx, int dy, int verticalBlock, int horizontalBlock, boolean ignoreCollision)
	{
		if (!inBounds(flags, sx + dx, sy) || !inBounds(flags, sx, sy + dy) || !inBounds(flags, sx + dx, sy + dy))
		{
			return false;
		}
		return ignoreCollision || ((flags[sx + dx][sy] & (CollisionDataFlag.BLOCK_MOVEMENT_FULL | verticalBlock)) == 0
			&& (flags[sx][sy + dy] & (CollisionDataFlag.BLOCK_MOVEMENT_FULL | horizontalBlock)) == 0
			&& (flags[sx + dx][sy + dy] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0);
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

	// ---- Read batch 16e (upstream 2.6.38) ----

	/** As upstream: walkable tiles within {@code radius} of {@code point} (radius clamped to the scene), excluding {@code point}. */
	public static List<WorldPoint> getWalkableTilesAroundTile(WorldPoint point, int radius)
	{
		if (point == null)
		{
			return java.util.Collections.emptyList();
		}
		return MobileActions.read(() ->
		{
			WorldView view = Microbot.getClient().getTopLevelWorldView();
			LocalPoint local = view == null ? null : LocalPoint.fromWorld(view, point);
			int[][] flags = local == null ? null : flags(view, view.getPlane());
			if (flags == null)
			{
				return java.util.Collections.<WorldPoint>emptyList();
			}
			int sceneX = local.getSceneX();
			int sceneY = local.getSceneY();
			int useRadius = radius;
			if (sceneX - useRadius < 0 || sceneX + useRadius >= TileReachability.FLAG_DATA_SIZE)
			{
				useRadius = Math.min(sceneX, TileReachability.FLAG_DATA_SIZE - sceneX - 1);
			}
			if (sceneY - useRadius < 0 || sceneY + useRadius >= TileReachability.FLAG_DATA_SIZE)
			{
				useRadius = Math.min(sceneY, TileReachability.FLAG_DATA_SIZE - sceneY - 1);
			}
			List<WorldPoint> points = new java.util.ArrayList<>();
			for (int dx = -useRadius; dx <= useRadius; dx++)
			{
				for (int dy = -useRadius; dy <= useRadius; dy++)
				{
					if ((dx != 0 || dy != 0) && (flags[sceneX + dx][sceneY + dy] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0)
					{
						points.add(new WorldPoint(point.getX() + dx, point.getY() + dy, point.getPlane()));
					}
				}
			}
			return points;
		}, java.util.Collections.emptyList());
	}

	/** As upstream: {@link #getReachableTilesFromTile(WorldPoint, int, boolean)} ignoring collision. */
	public static java.util.HashMap<WorldPoint, Integer> getReachableTilesFromTileIgnoreCollision(WorldPoint tile, int distance)
	{
		return getReachableTilesFromTile(tile, distance, true);
	}

	/**
	 * As upstream: one step from {@code from} to the adjacent (or same) tile {@code to} is open on the
	 * loaded floor. False off-scene, on another floor, or in an instance (template chunks repeat there).
	 */
	public static boolean isEdgePassable(WorldPoint from, WorldPoint to)
	{
		if (from == null || to == null || from.getPlane() != to.getPlane())
		{
			return false;
		}
		int dx = to.getX() - from.getX();
		int dy = to.getY() - from.getY();
		if (dx == 0 && dy == 0)
		{
			return true;
		}
		if (Math.abs(dx) > 1 || Math.abs(dy) > 1)
		{
			return false;
		}
		return MobileActions.read(() ->
		{
			WorldView view = Microbot.getClient().getTopLevelWorldView();
			if (view == null || view.getPlane() != from.getPlane() || (view.getScene() != null && view.getScene().isInstance()))
			{
				return false;
			}
			int[][] flags = flags(view, view.getPlane());
			int fx = from.getX() - view.getBaseX();
			int fy = from.getY() - view.getBaseY();
			return flags != null && TileReachability.isWithinBounds(fx, fy) && TileReachability.isWithinBounds(fx + dx, fy + dy)
				&& TileReachability.isStepAllowed(flags, fx, fy, dx, dy);
		}, false);
	}

	/** As upstream: {@code tileObject} (a wall from either face) can be used from a tile the player can reach. */
	public static boolean isTileObjectReachable(net.runelite.api.TileObject tileObject)
	{
		if (tileObject == null)
		{
			return false;
		}
		return MobileActions.read(() ->
		{
			Client client = Microbot.getClient();
			WorldView view = client.getTopLevelWorldView();
			Player player = client.getLocalPlayer();
			WorldPoint location = player == null ? null : player.getWorldLocation();
			if (view == null || location == null || tileObject.getPlane() != location.getPlane())
			{
				return false;
			}
			LocalPoint objectLocal = tileObject.getLocalLocation();
			if (objectLocal == null || !objectLocal.isInScene())
			{
				return false;
			}
			if (ignoresCollision(tileObject.getWorldLocation()))
			{
				return true;
			}
			int[][] flags = flags(view, view.getPlane());
			if (flags == null)
			{
				return false;
			}
			int orientation = 0;
			if (tileObject instanceof net.runelite.api.WallObject)
			{
				net.runelite.api.WallObject wall = (net.runelite.api.WallObject) tileObject;
				orientation = wall.getOrientationA() | wall.getOrientationB();
			}
			return TileReachability.canInteractFromReachableTile(flags, reachableFromPlayer(view, player, location, flags),
				objectLocal.getSceneX(), objectLocal.getSceneY(), orientation);
		}, false);
	}

	/** As upstream: any tile of {@code gameObject}'s footprint can be used from a tile the player can reach (top-level scene only). */
	public static boolean isGameObjectInteractableFromReachableTile(net.runelite.api.GameObject gameObject)
	{
		if (gameObject == null)
		{
			return false;
		}
		return MobileActions.read(() ->
		{
			Client client = Microbot.getClient();
			WorldView view = client.getTopLevelWorldView();
			Player player = client.getLocalPlayer();
			if (view == null || player == null)
			{
				return false;
			}
			WorldView objectView = gameObject.getWorldView();
			WorldView playerView = player.getWorldView();
			if (objectView == null || playerView == null || !objectView.isTopLevel() || !playerView.isTopLevel()
				|| gameObject.getPlane() != view.getPlane())
			{
				return false;
			}
			net.runelite.api.Point min = gameObject.getSceneMinLocation();
			net.runelite.api.Point max = gameObject.getSceneMaxLocation();
			LocalPoint playerLocal = player.getLocalLocation();
			if (min == null || max == null || playerLocal == null)
			{
				return false;
			}
			if (ignoresCollision(gameObject.getWorldLocation()))
			{
				return true;
			}
			int[][] flags = flags(view, view.getPlane());
			return flags != null && TileReachability.canInteractWithFootprintFromReachableTile(flags,
				TileReachability.reachableFrom(flags, playerLocal.getSceneX(), playerLocal.getSceneY()),
				min.getX(), min.getY(), max.getX(), max.getY());
		}, false);
	}

	/** As upstream: the game object on {@code source} is named "bank booth" (any case). */
	public static boolean isBankBooth(WorldPoint source)
	{
		if (source == null)
		{
			return false;
		}
		net.runelite.api.GameObject booth = net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.getGameObjects().stream()
			.filter(object -> source.equals(MobileActions.read(object::getWorldLocation, null))).findFirst().orElse(null);
		net.runelite.api.ObjectComposition composition = booth == null ? null
			: net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.convertToObjectComposition(booth);
		return composition != null && "bank booth".equalsIgnoreCase(composition.getName());
	}

	/** As upstream: no movement flag at all is set on {@code tile} (current floor). */
	public static boolean isValidTile(Tile tile)
	{
		if (tile == null)
		{
			return false;
		}
		return MobileActions.read(() ->
		{
			Client client = Microbot.getClient();
			CollisionData[] maps = client.getCollisionMaps();
			int plane = client.getPlane();
			net.runelite.api.Point scene = tile.getSceneLocation();
			if (maps == null || plane < 0 || plane >= maps.length || maps[plane] == null || scene == null)
			{
				return false;
			}
			int[][] flags = maps[plane].getFlags();
			return inBounds(flags, scene.getX(), scene.getY())
				&& (flags[scene.getX()][scene.getY()] & MOVEMENT_FLAGS) == 0;
		}, false);
	}

	/** Every flag in RuneLite's devtools {@code MovementFlag} (upstream's isValidTile test). */
	private static final int MOVEMENT_FLAGS = CollisionDataFlag.BLOCK_MOVEMENT_NORTH_WEST | CollisionDataFlag.BLOCK_MOVEMENT_NORTH
		| CollisionDataFlag.BLOCK_MOVEMENT_NORTH_EAST | CollisionDataFlag.BLOCK_MOVEMENT_EAST | CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_EAST
		| CollisionDataFlag.BLOCK_MOVEMENT_SOUTH | CollisionDataFlag.BLOCK_MOVEMENT_SOUTH_WEST | CollisionDataFlag.BLOCK_MOVEMENT_WEST
		| CollisionDataFlag.BLOCK_MOVEMENT_OBJECT | CollisionDataFlag.BLOCK_MOVEMENT_FLOOR_DECORATION | CollisionDataFlag.BLOCK_MOVEMENT_FLOOR
		| CollisionDataFlag.BLOCK_MOVEMENT_FULL;

	private static boolean ignoresCollision(WorldPoint point)
	{
		return point != null && net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap.ignoreCollisionPacked
			.contains(net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil.packWorldPoint(point));
	}

	// ---- Util action batch AF (Claude 1): scene paths and surroundings ----

	/**
	 * As upstream: the scene path from {@code source} to {@code other} as checkpoint tiles (where it
	 * turns, then its end), at most 25, without the source. When {@code other} cannot be reached the
	 * path ends at the nearest reachable tile within 10 of it. Null on different floors or when there
	 * is no path.
	 */
	public static List<Tile> pathTo(Tile source, Tile other)
	{
		return scenePath(source, other, false);
	}

	/** As upstream: like {@link #pathTo(Tile, Tile)}, but every tile from {@code source} (included) on. */
	public static List<Tile> fullPathTo(Tile source, Tile other)
	{
		return scenePath(source, other, true);
	}

	private static List<Tile> scenePath(Tile source, Tile other, boolean full)
	{
		if (source == null || other == null)
		{
			return null;
		}
		return MobileActions.read(() ->
		{
			int plane = source.getPlane();
			net.runelite.api.Point from = source.getSceneLocation();
			net.runelite.api.Point to = other.getSceneLocation();
			WorldView view = Microbot.getClient().getTopLevelWorldView();
			if (plane != other.getPlane() || from == null || to == null || view == null || view.getScene() == null)
			{
				return null;
			}
			List<int[]> path = TileReachability.scenePath(flags(view, plane), from.getX(), from.getY(), to.getX(), to.getY());
			List<int[]> points = full ? path : TileReachability.checkpoints(path);
			if (points == null)
			{
				return null;
			}
			Tile[][] tiles = view.getScene().getTiles()[plane];
			List<Tile> result = new java.util.ArrayList<>(points.size());
			for (int[] point : points)
			{
				result.add(tiles[point[0]][point[1]]);
			}
			return result;
		}, null);
	}

	/**
	 * As upstream: whether any tile in the ring around an object of {@code sizeX} by {@code sizeY}
	 * centred on {@code worldPoint} is reachable for the player (one scene flood, not one per tile).
	 */
	public static boolean areSurroundingTilesWalkable(WorldPoint worldPoint, int sizeX, int sizeY)
	{
		if (worldPoint == null)
		{
			return false;
		}
		return MobileActions.read(() ->
		{
			Client client = Microbot.getClient();
			WorldView view = client.getTopLevelWorldView();
			Player player = client.getLocalPlayer();
			WorldPoint location = player == null ? null : player.getWorldLocation();
			int[][] flags = view == null ? null : flags(view, worldPoint.getPlane());
			if (flags == null || location == null || location.getPlane() != worldPoint.getPlane())
			{
				return false;
			}
			boolean[][] reachable = reachableFromPlayer(view, player, location, flags);
			int minX = worldPoint.getX() - (sizeX - 1) / 2 - view.getBaseX();
			int minY = worldPoint.getY() - (sizeY - 1) / 2 - view.getBaseY();
			int maxX = minX + sizeX - 1;
			int maxY = minY + sizeY - 1;
			for (int x = minX - 1; x <= maxX + 1; x++)
			{
				for (int y = minY - 1; y <= maxY + 1; y++)
				{
					boolean inside = x >= minX && x <= maxX && y >= minY && y <= maxY;
					if (!inside && TileReachability.isWithinBounds(x, y) && reachable[x][y])
					{
						return true;
					}
				}
			}
			return false;
		}, false);
	}

	/** As upstream: a wall object stands on {@code source}. */
	public static boolean tileHasWalls(WorldPoint source)
	{
		return source != null && net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.getWallObjects().stream()
			.anyMatch(wall -> source.equals(MobileActions.read(wall::getWorldLocation, null)));
	}

	/** As upstream without natural mouse (never on here): hovering is not done; always false. */
	public static boolean hoverOverTile(Tile tile)
	{
		return false;
	}

	/** Upstream's start tile: the local scene tile inside an instance, else the world tile minus the scene base. */
	private static boolean[][] reachableFromPlayer(WorldView view, Player player, WorldPoint location, int[][] flags)
	{
		if (view.getScene() != null && view.getScene().isInstance())
		{
			LocalPoint local = player.getLocalLocation();
			return local == null ? new boolean[TileReachability.FLAG_DATA_SIZE][TileReachability.FLAG_DATA_SIZE]
				: TileReachability.reachableFrom(flags, local.getSceneX(), local.getSceneY());
		}
		return TileReachability.reachableFrom(flags, location.getX() - view.getBaseX(), location.getY() - view.getBaseY());
	}
}
