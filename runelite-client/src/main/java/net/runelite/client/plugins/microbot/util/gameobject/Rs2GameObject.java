package net.runelite.client.plugins.microbot.util.gameobject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GroundObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Composition access is marshalled, including transformation-dependent Forestry actions.
 * <p>
 * The Hub read block at the end is upstream's legacy scene API (Microbot 2.6.38), returning live
 * {@link TileObject}s. Adaptation: each scan, its distance filter and the caller's predicate run in
 * one client-thread read (upstream hops once per object), so predicates must not block.
 */
public final class Rs2GameObject
{
	private Rs2GameObject() { }
	public static boolean hasAction(ObjectComposition composition, String action)
	{
		return MobileActions.read(() ->
		{
			if (composition == null || composition.getActions() == null) { return false; }
			for (String candidate : composition.getActions())
			{
				if (candidate != null && candidate.equalsIgnoreCase(action)) { return true; }
			}
			return false;
		}, false);
	}
	public static String getCompositionName(int id)
	{
		return MobileActions.read(() ->
		{
			ObjectComposition definition = net.runelite.client.plugins.microbot.Microbot.getClient().getObjectDefinition(id);
			if (definition != null && definition.getImpostorIds() != null) { definition = definition.getImpostor(); }
			return definition == null ? null : definition.getName();
		}, null);
	}
	public static java.util.Optional<String> getCompositionName(net.runelite.api.GameObject object)
	{
		return java.util.Optional.ofNullable(object == null ? null : getCompositionName(object.getId()));
	}

	// --- upstream lookup-and-interact overloads (nearest match in the tile-object snapshot) ---

	public static boolean interact(int id)
	{
		return interact(nearest(o -> o.getId() == id, -1), "", false);
	}

	public static boolean interact(int id, String action)
	{
		return interact(nearest(o -> o.getId() == id, -1), action, false);
	}

	public static boolean interact(int id, boolean checkCanReach)
	{
		return interact(nearest(o -> o.getId() == id, -1), "", checkCanReach);
	}

	/** Within {@code distance} tiles of the player, as upstream. */
	public static boolean interact(int id, String action, int distance)
	{
		return interact(nearest(o -> o.getId() == id, distance), action, false);
	}

	public static boolean interact(int[] objectIds, String action)
	{
		if (objectIds == null) return false;
		for (int id : objectIds)
		{
			if (interact(id, action)) return true;
		}
		return false;
	}

	public static boolean interact(String name)
	{
		return interact(name, "", true);
	}

	public static boolean interact(String name, boolean exact)
	{
		return interact(name, "", exact);
	}

	public static boolean interact(String name, String action)
	{
		return interact(name, action, false);
	}

	/** Upstream name rule: exact is case-insensitive equality, otherwise a case-insensitive substring. */
	public static boolean interact(String name, String action, boolean exact)
	{
		if (name == null) return false;
		String needle = name.toLowerCase(java.util.Locale.ROOT);
		return interact(nearest(o -> o.getName() != null && (exact ? o.getName().equalsIgnoreCase(name)
			: o.getName().toLowerCase(java.util.Locale.ROOT).contains(needle)), -1), action, false);
	}

	public static boolean interact(WorldPoint location)
	{
		return interact(location, "");
	}

	public static boolean interact(WorldPoint location, String action)
	{
		return location != null && interact(nearest(o -> location.equals(o.getWorldLocation()), -1), action, false);
	}

	private static Rs2TileObjectModel nearest(java.util.function.Predicate<Rs2TileObjectModel> filter, int distance)
	{
		if (net.runelite.client.plugins.microbot.Microbot.getRs2TileObjectCache() == null) return null;
		var query = net.runelite.client.plugins.microbot.Microbot.getRs2TileObjectCache().query().where(filter);
		return distance >= 0 ? query.within(distance).nearest() : query.nearest();
	}

	/**
	 * As upstream: interacts with a live object (a game, wall, decorative or ground object, for
	 * example from a scene read or a spawn event), through a snapshot taken on the client thread so
	 * it is located as the object cache locates it. An empty action means its first action.
	 */
	public static boolean interact(TileObject object, String action, boolean checkCanReach)
	{
		if (object == null)
		{
			return false;
		}
		Rs2TileObjectModel model = MobileActions.read(() -> new Rs2TileObjectModel(MicrobotReadServices.require(), object), null);
		return interact(model, action, checkCanReach);
	}

	/** As upstream: a live object's first action. */
	public static boolean interact(TileObject object)
	{
		return interact(object, "", false);
	}

	/** As upstream: a live object's {@code action}. */
	public static boolean interact(TileObject object, String action)
	{
		return interact(object, action, false);
	}

	/** As upstream: a live object's first action, walking to it first when {@code checkCanReach}. */
	public static boolean interact(TileObject object, boolean checkCanReach)
	{
		return interact(object, "", checkCanReach);
	}

	/** Upstream overload: interacts with the model's first action. */
	public static boolean interact(Rs2TileObjectModel object)
	{
		return interact(object, "", false);
	}

	/** Upstream overload; an empty action means the object's first action. */
	public static boolean interact(Rs2TileObjectModel object, String action)
	{
		return interact(object, action, false);
	}

	/**
	 * Upstream {@code interact(TileObject, String, boolean)} for the mobile snapshot model. With
	 * {@code checkCanReach}, first walks to within 2 tiles (the walker opens doors on the way) and
	 * clicks only after arriving, as upstream does. The click is one exact menu dispatch on the
	 * client thread; {@code true} means dispatched, not that the game action succeeded.
	 */
	public static boolean interact(Rs2TileObjectModel object, String action, boolean checkCanReach)
	{
		if (object == null || object.getWorldLocation() == null)
		{
			return false;
		}
		if (checkCanReach && !Rs2Walker.walkTo(object.getWorldLocation(), 2))
		{
			return false;
		}
		String resolved = action == null || action.isEmpty() ? firstAction(object.getId()) : action;
		return resolved != null && MobileActions.objectAt(object.getId(), object.getWorldLocation(), resolved);
	}

	private static String firstAction(int id)
	{
		return MobileActions.read(() ->
		{
			ObjectComposition definition = net.runelite.client.plugins.microbot.Microbot.getClient().getObjectDefinition(id);
			if (definition != null && definition.getImpostorIds() != null) { definition = definition.getImpostor(); }
			if (definition == null || definition.getActions() == null) { return null; }
			for (String candidate : definition.getActions())
			{
				if (candidate != null && !candidate.isEmpty()) { return candidate; }
			}
			return null;
		}, null);
	}

	// ---- Hub read APIs (upstream 2.6.38; reads only) ----

	private static final Function<Tile, Collection<? extends net.runelite.api.GameObject>> GAMEOBJECT_EXTRACTOR =
		tile -> Arrays.asList(tile.getGameObjects());
	private static final Function<Tile, Collection<? extends GroundObject>> GROUNDOBJECT_EXTRACTOR =
		tile -> Collections.singletonList(tile.getGroundObject());
	private static final Function<Tile, Collection<? extends TileObject>> TILEOBJECT_EXTRACTOR =
		tile -> Arrays.asList(tile.getDecorativeObject(), tile.getGroundObject(), tile.getWallObject());
	private static final Function<Tile, Collection<? extends net.runelite.api.WallObject>> WALLOBJECT_EXTRACTOR =
		tile -> Collections.singletonList(tile.getWallObject());
	private static final Function<Tile, Collection<? extends net.runelite.api.DecorativeObject>> DECORATIVEOBJECT_EXTRACTOR =
		tile -> Collections.singletonList(tile.getDecorativeObject());
	/** Upstream's Grand Exchange booth wall object ids. */
	private static final Set<Integer> GRAND_EXCHANGE_BOOTH_IDS = new HashSet<>(Arrays.asList(10060, 30389));
	private static final Map<String, List<Integer>> NAME_TO_IDS_CACHE = new ConcurrentHashMap<>();

	public static boolean exists(int id)
	{
		return findObjectById(id) != null;
	}

	/** As upstream: the first game, wall, decorative or ground object with {@code id}, nearest first. */
	public static TileObject findObjectById(int id)
	{
		return getAll(o -> o.getId() == id).stream().findFirst().orElse(null);
	}

	/** As upstream: the first object whose world location is {@code worldPoint}. */
	public static TileObject findObjectByLocation(WorldPoint worldPoint)
	{
		return getAll(o -> o.getWorldLocation().equals(worldPoint)).stream().findFirst().orElse(null);
	}

	/** As upstream (deprecated there): the first game object at {@code worldPoint}. */
	@Deprecated
	public static TileObject findGameObjectByLocation(WorldPoint worldPoint)
	{
		return getGameObjects(o -> o.getWorldLocation().equals(worldPoint)).stream().findFirst().orElse(null);
	}

	/** As upstream: {@link #convertToObjectComposition(int)}. */
	public static ObjectComposition findObjectComposition(int id)
	{
		return convertToObjectComposition(id);
	}

	/** As upstream (deprecated there): a game object with {@code id} whose current impostor offers {@code action} exactly. */
	@Deprecated
	public static net.runelite.api.GameObject findObjectByImposter(int id, String action)
	{
		return findObjectByImposter(id, action, true);
	}

	/**
	 * As upstream (deprecated there): the nearest game object with {@code id} whose current
	 * impostor (for example a farming patch's state) has an action equal to ({@code exact}) or
	 * containing {@code optionName}, ignoring case.
	 */
	@Deprecated
	public static net.runelite.api.GameObject findObjectByImposter(int id, String optionName, boolean exact)
	{
		if (optionName == null)
		{
			return null;
		}
		String lower = optionName.toLowerCase();
		return getGameObjects(o -> o.getId() == id).stream().filter(o ->
		{
			ObjectComposition composition = convertToObjectComposition(o);
			String[] actions = composition == null ? null : MobileActions.read(composition::getActions, null);
			return actions != null && Arrays.stream(actions).anyMatch(a -> a != null
				&& (exact ? a.equalsIgnoreCase(optionName) : a.toLowerCase().contains(lower)));
		}).findFirst().orElse(null);
	}

	/** As upstream: the object's definition, resolved to its current impostor. */
	public static ObjectComposition getObjectComposition(int id)
	{
		return convertToObjectComposition(id);
	}

	/** As upstream: the nearest game object with {@code id} within half a scene. */
	public static net.runelite.api.GameObject getGameObject(int id)
	{
		return getGameObject(o -> o.getId() == id);
	}

	public static net.runelite.api.GameObject getGameObject(int id, int distance)
	{
		return getGameObject(o -> o.getId() == id, playerLocation(), distance);
	}

	public static net.runelite.api.GameObject getGameObject(int id, WorldPoint anchor)
	{
		return getGameObject(o -> o.getId() == id, anchor);
	}

	public static net.runelite.api.GameObject getGameObject(int id, WorldPoint anchor, int distance)
	{
		return getGameObject(o -> o.getId() == id, anchor, distance);
	}

	public static net.runelite.api.GameObject getGameObject(String objectName)
	{
		return getGameObject(objectName, false);
	}

	public static net.runelite.api.GameObject getGameObject(String objectName, boolean exact)
	{
		return getGameObject(nameMatches(objectName, exact));
	}

	public static net.runelite.api.GameObject getGameObject(String objectName, int distance)
	{
		return getGameObject(nameMatches(objectName, false), playerLocation(), distance);
	}

	public static net.runelite.api.GameObject getGameObject(String objectName, boolean exact, int distance)
	{
		return getGameObject(nameMatches(objectName, exact), playerLocation(), distance);
	}

	public static net.runelite.api.GameObject getGameObject(String objectName, WorldPoint anchor)
	{
		return getGameObject(nameMatches(objectName, false), anchor);
	}

	public static net.runelite.api.GameObject getGameObject(String objectName, WorldPoint anchor, int distance)
	{
		return getGameObject(nameMatches(objectName, false), anchor, distance);
	}

	public static net.runelite.api.GameObject getGameObject(String objectName, boolean exact, WorldPoint anchor, int distance)
	{
		return getGameObject(nameMatches(objectName, exact), anchor, distance);
	}

	public static net.runelite.api.GameObject getGameObject(java.util.function.Predicate<net.runelite.api.GameObject> predicate)
	{
		return getGameObject(predicate, playerLocation());
	}

	public static net.runelite.api.GameObject getGameObject(java.util.function.Predicate<net.runelite.api.GameObject> predicate, WorldPoint anchor)
	{
		return getGameObject(predicate, anchor, Constants.SCENE_SIZE / 2);
	}

	/** As upstream: the nearest game object matching {@code predicate} within {@code distance} of {@code anchor}. */
	public static net.runelite.api.GameObject getGameObject(java.util.function.Predicate<net.runelite.api.GameObject> predicate, WorldPoint anchor, int distance)
	{
		return getGameObjects(predicate, anchor, distance).stream().findFirst().orElse(null);
	}

	/** As upstream: {@link #canReach(WorldPoint, int, int, int, int)} for a 2x2 object from a 2x2 path end. */
	public static boolean canReach(WorldPoint target)
	{
		return canReach(target, 2, 2, 2, 2);
	}

	/** As upstream: {@link #canReach(WorldPoint, int, int, int, int)} with a 3x3 path end. */
	public static boolean canReach(WorldPoint target, int objectSizeX, int objectSizeY)
	{
		return canReach(target, objectSizeX, objectSizeY, 3, 3);
	}

	/**
	 * As upstream: whether the end of the player's walkable path toward {@code target} (an area of
	 * {@code pathSizeX} x {@code pathSizeY} around it) touches the object's area grown by one tile.
	 * Adaptation: upstream ends the path with {@code Rs2WorldPoint.pathTo}; here the path end is the
	 * reachable tile (collision BFS over the loaded scene) nearest to {@code target}.
	 */
	public static boolean canReach(WorldPoint target, int objectSizeX, int objectSizeY, int pathSizeX, int pathSizeY)
	{
		WorldPoint player = net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
		if (target == null || player == null || player.getPlane() != target.getPlane())
		{
			return false;
		}
		WorldPoint end = net.runelite.client.plugins.microbot.util.tile.Rs2Tile.getReachableTilesFromTile(player, Constants.SCENE_SIZE).keySet().stream()
			.min(Comparator.comparingInt((WorldPoint p) -> p.distanceTo2D(target)).thenComparingInt(p -> p.distanceTo2D(player)))
			.orElse(null);
		if (end == null)
		{
			return false;
		}
		WorldArea pathArea = new WorldArea(new WorldPoint(end.getX() - pathSizeX / 2, end.getY() - pathSizeY / 2, end.getPlane()), pathSizeX, pathSizeY);
		WorldArea objectArea = new WorldArea(new WorldPoint(target.getX() - (objectSizeX + 2) / 2, target.getY() - (objectSizeY + 2) / 2, target.getPlane()),
			objectSizeX + 2, objectSizeY + 2);
		return pathArea.getX() < objectArea.getX() + objectArea.getWidth() && objectArea.getX() < pathArea.getX() + pathArea.getWidth()
			&& pathArea.getY() < objectArea.getY() + objectArea.getHeight() && objectArea.getY() < pathArea.getY() + pathArea.getHeight();
	}

	/** As upstream: the nearest wall, decorative or ground object with {@code id}. */
	public static TileObject getTileObject(int id)
	{
		return getTileObject(o -> o.getId() == id);
	}

	public static TileObject getTileObject(int id, int distance)
	{
		return getTileObject(o -> o.getId() == id, distance);
	}

	public static TileObject getTileObject(int id, WorldPoint anchor)
	{
		return getTileObject(o -> o.getId() == id, anchor);
	}

	public static TileObject getTileObject(int id, WorldPoint anchor, int distance)
	{
		return getTileObject(o -> o.getId() == id, anchor, distance);
	}

	/** As upstream: the nearest wall, decorative or ground object whose name contains {@code objectName}. */
	public static TileObject getTileObject(String objectName)
	{
		return getTileObject(objectName, false);
	}

	public static TileObject getTileObject(String objectName, boolean exact)
	{
		return getTileObject(nameMatches(objectName, exact));
	}

	public static TileObject getTileObject(String objectName, int distance)
	{
		return getTileObject(nameMatches(objectName, false), distance);
	}

	public static TileObject getTileObject(String objectName, boolean exact, int distance)
	{
		return getTileObject(nameMatches(objectName, exact), distance);
	}

	public static TileObject getTileObject(String objectName, WorldPoint anchor)
	{
		return getTileObject(nameMatches(objectName, false), anchor);
	}

	public static TileObject getTileObject(String objectName, WorldPoint anchor, int distance)
	{
		return getTileObject(objectName, false, anchor, distance);
	}

	public static TileObject getTileObject(String objectName, boolean exact, WorldPoint anchor, int distance)
	{
		return getTileObject(nameMatches(objectName, exact), anchor, distance);
	}

	public static TileObject getTileObject(java.util.function.Predicate<TileObject> predicate)
	{
		return getTileObject(predicate, playerLocation());
	}

	public static TileObject getTileObject(java.util.function.Predicate<TileObject> predicate, int distance)
	{
		return getTileObject(predicate, playerLocation(), distance);
	}

	public static TileObject getTileObject(java.util.function.Predicate<TileObject> predicate, WorldPoint anchor)
	{
		return getTileObject(predicate, anchor, Constants.SCENE_SIZE / 2);
	}

	/** As upstream: the nearest wall, decorative or ground object within {@code distance} of {@code anchor}. */
	public static TileObject getTileObject(java.util.function.Predicate<TileObject> predicate, WorldPoint anchor, int distance)
	{
		return sceneObjects(TILEOBJECT_EXTRACTOR, predicate, anchor, distance).stream().findFirst().orElse(null);
	}

	public static List<TileObject> getAll()
	{
		return getAll(o -> true);
	}

	public static <T extends TileObject> List<TileObject> getAll(java.util.function.Predicate<? super T> predicate)
	{
		return getAll(predicate, Constants.SCENE_SIZE);
	}

	public static <T extends TileObject> List<TileObject> getAll(java.util.function.Predicate<? super T> predicate, int distance)
	{
		return getAll(predicate, playerLocation(), distance);
	}

	public static <T extends TileObject> List<TileObject> getAll(java.util.function.Predicate<? super T> predicate, WorldPoint anchor)
	{
		return getAll(predicate, anchor, Constants.SCENE_SIZE);
	}

	/** As upstream: game objects, then wall, decorative and ground objects, each nearest first. */
	@SuppressWarnings("unchecked")
	public static <T extends TileObject> List<TileObject> getAll(java.util.function.Predicate<? super T> predicate, WorldPoint anchor, int distance)
	{
		java.util.function.Predicate<TileObject> filter = o -> ((java.util.function.Predicate<TileObject>) predicate).test(o);
		List<TileObject> all = new ArrayList<>(sceneObjects(GAMEOBJECT_EXTRACTOR, filter::test, anchor, distance));
		all.addAll(sceneObjects(TILEOBJECT_EXTRACTOR, filter, anchor, distance));
		return all;
	}

	public static List<net.runelite.api.GameObject> getGameObjects()
	{
		return getGameObjects(o -> true);
	}

	public static List<net.runelite.api.GameObject> getGameObjects(int distance)
	{
		return getGameObjects(o -> true, distance);
	}

	public static List<net.runelite.api.GameObject> getGameObjects(java.util.function.Predicate<net.runelite.api.GameObject> predicate, int distance)
	{
		return getGameObjects(predicate, playerLocation(), distance);
	}

	public static List<net.runelite.api.GameObject> getGameObjects(WorldPoint anchor)
	{
		return getGameObjects(o -> true, anchor);
	}

	public static List<net.runelite.api.GameObject> getGameObjects(java.util.function.Predicate<net.runelite.api.GameObject> predicate)
	{
		return getGameObjects(predicate, playerLocation());
	}

	public static List<net.runelite.api.GameObject> getGameObjects(java.util.function.Predicate<net.runelite.api.GameObject> predicate, WorldPoint anchor)
	{
		return getGameObjects(predicate, anchor, Constants.SCENE_SIZE / 2);
	}

	/** As upstream: game objects within {@code distance} tiles of {@code anchor}, nearest first. */
	public static List<net.runelite.api.GameObject> getGameObjects(java.util.function.Predicate<net.runelite.api.GameObject> predicate, WorldPoint anchor, int distance)
	{
		return sceneObjects(GAMEOBJECT_EXTRACTOR, predicate, anchor, distance);
	}

	public static List<GroundObject> getGroundObjects()
	{
		return getGroundObjects(o -> true);
	}

	public static List<GroundObject> getGroundObjects(int distance)
	{
		return getGroundObjects(o -> true, distance);
	}

	public static List<GroundObject> getGroundObjects(java.util.function.Predicate<GroundObject> predicate, int distance)
	{
		return getGroundObjects(predicate, playerLocation(), distance);
	}

	public static List<GroundObject> getGroundObjects(WorldPoint anchor)
	{
		return getGroundObjects(o -> true, anchor);
	}

	public static List<GroundObject> getGroundObjects(java.util.function.Predicate<GroundObject> predicate)
	{
		return getGroundObjects(predicate, playerLocation());
	}

	public static List<GroundObject> getGroundObjects(java.util.function.Predicate<GroundObject> predicate, WorldPoint anchor)
	{
		return getGroundObjects(predicate, anchor, Constants.SCENE_SIZE / 2);
	}

	public static List<GroundObject> getGroundObjects(java.util.function.Predicate<GroundObject> predicate, WorldPoint anchor, int distance)
	{
		return sceneObjects(GROUNDOBJECT_EXTRACTOR, predicate, anchor, distance);
	}

	/**
	 * As upstream: the nearest game object named {@code objectName} within {@code distance} tiles of
	 * {@code anchorPoint} (optionally offering {@code action}) that the player can walk up to.
	 */
	public static net.runelite.api.GameObject findReachableObject(String objectName, boolean exact, int distance, WorldPoint anchorPoint,
		boolean checkAction, String action)
	{
		java.util.function.Predicate<TileObject> namePredicate = nameMatches(objectName, exact);
		List<net.runelite.api.GameObject> candidates = getGameObjects(o -> namePredicate.test(o)
			&& (!checkAction || hasAction(convertToObjectComposition(o), action)), anchorPoint, distance);
		WorldPoint player = playerLocation();
		if (candidates.isEmpty() || player == null)
		{
			return null;
		}
		Map<net.runelite.api.GameObject, WorldPoint> locations = MobileActions.read(() ->
		{
			Map<net.runelite.api.GameObject, WorldPoint> byObject = new java.util.IdentityHashMap<>();
			candidates.forEach(o -> byObject.put(o, o.getWorldLocation()));
			return byObject;
		}, Collections.emptyMap());
		return findNearestReachable(candidates, locations::get, player, Rs2GameObject::isReachable);
	}

	public static net.runelite.api.GameObject findReachableObject(String objectName, boolean exact, int distance, WorldPoint anchorPoint)
	{
		return findReachableObject(objectName, exact, distance, anchorPoint, false, "");
	}

	/**
	 * As upstream: whether a walkable tile next to the object can be reached by the player. The
	 * reachability search runs off the client thread when called from a script.
	 */
	public static boolean isReachable(net.runelite.api.GameObject tileObject)
	{
		WorldArea worldArea = tileObject == null ? null : getWorldArea(tileObject);
		if (worldArea == null)
		{
			return false;
		}
		net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea area =
			new net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea(worldArea);
		List<WorldPoint> interactable = new ArrayList<>(area.getInteractable());
		if (interactable.isEmpty())
		{
			interactable.addAll(area.offset(1).toWorldPointList());
			interactable.removeIf(area::contains);
		}
		return interactable.stream()
			.filter(net.runelite.client.plugins.microbot.util.tile.Rs2Tile::isWalkable)
			.anyMatch(net.runelite.client.plugins.microbot.util.tile.Rs2Tile::isTileReachable);
	}

	/**
	 * As upstream: the tiles the object covers, or {@code null} outside the loaded scene. Unlike
	 * upstream, the corners keep the object's own world view instead of assuming the top level.
	 */
	public static WorldArea getWorldArea(net.runelite.api.GameObject gameObject)
	{
		return MobileActions.read(() ->
		{
			LocalPoint local = gameObject.getLocalLocation();
			if (local == null || !local.isInScene())
			{
				return null;
			}
			int halfX = (gameObject.sizeX() - 1) * Perspective.LOCAL_TILE_SIZE / 2;
			int halfY = (gameObject.sizeY() - 1) * Perspective.LOCAL_TILE_SIZE / 2;
			Client client = net.runelite.client.plugins.microbot.Microbot.getClient();
			WorldPoint southWest = WorldPoint.fromLocal(client, new LocalPoint(local.getX() - halfX, local.getY() - halfY, local.getWorldView()));
			WorldPoint northEast = WorldPoint.fromLocal(client, new LocalPoint(local.getX() + halfX, local.getY() + halfY, local.getWorldView()));
			return new net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea(southWest, northEast);
		}, null);
	}

	public static <T extends TileObject> ObjectComposition convertToObjectComposition(T object)
	{
		return convertToObjectComposition(MobileActions.read(object::getId, -1), false);
	}

	public static ObjectComposition convertToObjectComposition(int objectId)
	{
		return convertToObjectComposition(objectId, false);
	}

	public static <T extends TileObject> ObjectComposition convertToObjectComposition(T object, boolean ignoreImpostor)
	{
		return convertToObjectComposition(MobileActions.read(object::getId, -1), ignoreImpostor);
	}

	/** As upstream: the object's definition, resolved to its current impostor unless {@code ignoreImpostor}. */
	public static ObjectComposition convertToObjectComposition(int objectId, boolean ignoreImpostor)
	{
		return MobileActions.read(() ->
		{
			ObjectComposition composition = net.runelite.client.plugins.microbot.Microbot.getClient().getObjectDefinition(objectId);
			if (composition == null)
			{
				return null;
			}
			return ignoreImpostor || composition.getImpostorIds() == null ? composition : composition.getImpostor();
		}, null);
	}

	public static boolean hasAction(TileObject tileObject, String action)
	{
		return hasAction(tileObject, action, false);
	}

	public static boolean hasAction(TileObject tileObject, String action, boolean exact)
	{
		return hasAction(convertToObjectComposition(tileObject), action, exact);
	}

	/** As upstream: an action equal to ({@code exact}) or containing {@code action}, ignoring case. */
	public static boolean hasAction(ObjectComposition composition, String action, boolean exact)
	{
		if (composition == null || action == null)
		{
			return false;
		}
		return MobileActions.read(() -> composition.getActions() != null && Arrays.stream(composition.getActions())
			.filter(java.util.Objects::nonNull)
			.anyMatch(a -> exact ? a.equalsIgnoreCase(action) : a.toLowerCase().contains(action.toLowerCase())), false);
	}

	/** As upstream: the object's (impostor-resolved) name without colour tags. */
	public static java.util.Optional<String> getCompositionName(TileObject object)
	{
		ObjectComposition composition = convertToObjectComposition(object);
		String name = composition == null ? null : MobileActions.read(composition::getName, null);
		return name == null || name.equals("null") ? java.util.Optional.empty()
			: java.util.Optional.of(net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper.stripColTags(name));
	}

	/**
	 * As upstream: objects whose name equals ({@code exact}) or contains {@code objectName}, ignoring
	 * case; when an {@code ObjectID} constant name contains the name, only those ids are considered.
	 */
	public static <T extends TileObject> java.util.function.Predicate<T> nameMatches(String objectName, boolean exact)
	{
		Set<Integer> ids = new HashSet<>(getObjectIdsByName(objectName.toLowerCase().replace(" ", "_")));
		String lower = objectName.toLowerCase();
		return object ->
		{
			if (!ids.isEmpty() && !ids.contains(object.getId()))
			{
				return false;
			}
			return getCompositionName(object)
				.map(name -> exact ? name.equalsIgnoreCase(objectName) : name.toLowerCase().contains(lower))
				.orElse(false);
		};
	}

	public static <T extends TileObject> java.util.function.Predicate<T> nameMatches(String objectName)
	{
		return nameMatches(objectName, false);
	}

	// ---- Util action batch AE (Claude 1): object click helpers ----

	/** As upstream: objects named {@code objectName} that offer {@code actionName} (exact or contains matching). */
	public static <T extends TileObject> java.util.function.Predicate<T> nameAndActionMatches(String objectName, String actionName, boolean exact)
	{
		java.util.function.Predicate<T> name = nameMatches(objectName, exact);
		return name.and(object -> hasAction(convertToObjectComposition(object), actionName, exact));
	}

	/** As upstream: {@link #nameAndActionMatches(String, String, boolean)} with contains matching. */
	public static <T extends TileObject> java.util.function.Predicate<T> nameAndActionMatches(String objectName, String actionName)
	{
		return nameAndActionMatches(objectName, actionName, false);
	}

	/** As upstream (deprecated there): {@link #convertToObjectComposition(TileObject)}. */
	@Deprecated
	public static ObjectComposition convertGameObjectToObjectComposition(TileObject tileObject)
	{
		return convertToObjectComposition(tileObject);
	}

	/** As upstream (deprecated there): {@link #convertToObjectComposition(int)}. */
	@Deprecated
	public static ObjectComposition convertGameObjectToObjectComposition(int objectId)
	{
		return convertToObjectComposition(objectId);
	}

	/** As upstream: the non-null candidate whose location is nearest {@code anchor}. */
	public static <T> java.util.Optional<T> pickClosest(Collection<T> candidates, Function<T, WorldPoint> locFn, WorldPoint anchor)
	{
		if (candidates == null || anchor == null)
		{
			return java.util.Optional.empty();
		}
		return candidates.stream()
			.filter(java.util.Objects::nonNull)
			.filter(candidate -> locFn.apply(candidate) != null)
			.min(Comparator.comparingInt(candidate -> locFn.apply(candidate).distanceTo(anchor)));
	}

	/** Upstream's limit for clicking an object without walking towards it first. */
	private static final int CLICK_OBJECT_MAX_DISTANCE = 51;

	/**
	 * As upstream: clicks {@code action} on a live object. An object more than 51 tiles away is walked
	 * towards instead and {@code false} returned (call again), as upstream. Mobile adaptation: the click
	 * is the {@link #interact(TileObject, String)} dispatch; upstream's can't-reach retry counter is not
	 * a separate path here.
	 */
	public static boolean clickObject(TileObject object, String action)
	{
		if (object == null)
		{
			return false;
		}
		WorldPoint objectLocation = MobileActions.read(object::getWorldLocation, null);
		WorldPoint here = net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
		if (objectLocation == null || here == null)
		{
			return false;
		}
		if (here.distanceTo2D(objectLocation) > CLICK_OBJECT_MAX_DISTANCE)
		{
			Rs2Walker.walkTo(objectLocation);
			return false;
		}
		return interact(object, action == null ? "" : action);
	}

	/**
	 * As upstream: ids of the {@code ObjectID} constants (legacy and gameval) whose name contains
	 * {@code name}, ignoring case; cached. Adaptation: Microbot's own {@code ObjectID} is not ported.
	 */
	public static List<Integer> getObjectIdsByName(String name)
	{
		return NAME_TO_IDS_CACHE.computeIfAbsent(name, key ->
		{
			List<Integer> ids = new ArrayList<>();
			String lowerName = key.toLowerCase();
			@SuppressWarnings("deprecation")
			Class<?>[] classesToScan = {net.runelite.api.ObjectID.class, net.runelite.api.gameval.ObjectID.class};
			for (Class<?> type : classesToScan)
			{
				for (Field field : type.getFields())
				{
					if (field.getType() == int.class && field.getName().toLowerCase().contains(lowerName))
					{
						try
						{
							ids.add(field.getInt(null));
						}
						catch (IllegalAccessException ignored)
						{
							// public constants: not reached
						}
					}
				}
			}
			return Collections.unmodifiableList(ids);
		});
	}

	/**
	 * As upstream: the nearest bank booth or chest (by {@link Rs2BankID}) within
	 * {@code maxSearchRadius} tiles that offers "Bank" or "Collect", skipping the Cooks' Guild banks
	 * without its requirements, the northern Farming Guild bank below 85 Farming, the Lunar Isle
	 * booth before Dream Mentor and its two neighbours without a seal of passage.
	 */
	public static net.runelite.api.GameObject findBank(int maxSearchRadius)
	{
		java.util.function.Predicate<net.runelite.api.GameObject> bankable = gameObject ->
		{
			WorldPoint loc = gameObject.getWorldLocation();
			if ((loc.equals(new WorldPoint(3147, 3449, 0)) || loc.equals(new WorldPoint(3148, 3449, 0)))
				&& !net.runelite.client.plugins.microbot.util.bank.enums.BankLocation.COOKS_GUILD.hasRequirements())
			{
				return false;
			}
			if ((loc.equals(new WorldPoint(1248, 3759, 0)) || loc.equals(new WorldPoint(1249, 3759, 0)))
				&& !net.runelite.client.plugins.microbot.util.player.Rs2Player.getSkillRequirement(net.runelite.api.Skill.FARMING, 85, true))
			{
				return false;
			}
			if (loc.equals(new WorldPoint(2099, 3920, 0))
				&& net.runelite.client.plugins.microbot.util.player.Rs2Player.getQuestState(net.runelite.api.Quest.DREAM_MENTOR)
				!= net.runelite.api.QuestState.FINISHED)
			{
				return false;
			}
			if ((loc.equals(new WorldPoint(2098, 3920, 0)) || loc.equals(new WorldPoint(2097, 3920, 0)))
				&& !(net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.hasItem(net.runelite.api.gameval.ItemID.LUNAR_SEAL_OF_PASSAGE)
				|| net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.isWearing(net.runelite.api.gameval.ItemID.LUNAR_SEAL_OF_PASSAGE)))
			{
				return false;
			}
			ObjectComposition composition = convertToObjectComposition(gameObject);
			return composition != null && (hasAction(composition, "Bank", false) || hasAction(composition, "Collect", false));
		};
		// Unlike upstream the filter runs inside the scan, so its live reads stay on the client thread.
		return getGameObjects(o -> Rs2BankID.BANK_ID_SET.contains(o.getId()) && bankable.test(o), maxSearchRadius).stream()
			.findFirst()
			.orElse(null);
	}

	public static net.runelite.api.GameObject findBank()
	{
		return findBank(20);
	}

	static <T> T findNearestReachable(List<T> candidates, Function<? super T, WorldPoint> location, WorldPoint from,
		java.util.function.Predicate<? super T> reachable)
	{
		List<T> sorted = new ArrayList<>(candidates);
		sorted.removeIf(c -> location.apply(c) == null);
		sorted.sort(Comparator.comparingInt(c -> net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint.quickDistance(from, location.apply(c))));
		for (T candidate : sorted)
		{
			if (reachable.test(candidate))
			{
				return candidate;
			}
		}
		return null;
	}

	private static WorldPoint playerLocation()
	{
		return net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
	}

	/**
	 * As upstream's scene scan: objects on the player's floor and below whose origin is on the tile,
	 * within {@code distance} tiles of {@code anchor} (0: the four neighbours), matching
	 * {@code predicate}, nearest first. Runs in one client-thread read.
	 */
	private static <T extends TileObject> List<T> sceneObjects(Function<Tile, Collection<? extends T>> extractor,
		java.util.function.Predicate<? super T> predicate, WorldPoint anchor, int distance)
	{
		if (anchor == null)
		{
			return Collections.emptyList();
		}
		return MobileActions.read(() ->
		{
			Client client = net.runelite.client.plugins.microbot.Microbot.getClient();
			Player player = client.getLocalPlayer();
			if (player == null || player.getWorldView() == null || player.getWorldView().getScene() == null)
			{
				return Collections.<T>emptyList();
			}
			LocalPoint anchorLocal = LocalPoint.fromWorld(client.getTopLevelWorldView(), anchor);
			if (anchorLocal == null)
			{
				// As upstream (POH fix): an anchor outside the scene falls back to the player.
				anchorLocal = player.getLocalLocation();
			}
			Tile[][][] tiles = player.getWorldView().getScene().getTiles();
			if (anchorLocal == null || tiles == null)
			{
				return Collections.<T>emptyList();
			}
			int localDistance = Math.min(distance, Constants.SCENE_SIZE) * Perspective.LOCAL_TILE_SIZE;
			int plane = player.getWorldView().getPlane();
			final LocalPoint origin = anchorLocal;
			List<T> result = new ArrayList<>();
			for (int x = 0; x < tiles[plane].length; x++)
			{
				for (int y = 0; y < tiles[plane][x].length; y++)
				{
					for (int h = 0; h <= plane; h++)
					{
						Tile tile = tiles[h][x][y];
						Collection<? extends T> objects = tile == null ? null : extractor.apply(tile);
						if (objects == null)
						{
							continue;
						}
						for (T object : objects)
						{
							if (object != null && originatesOn(object, tile)
								&& isWithinTiles(origin, object.getLocalLocation(), localDistance) && predicate.test(object))
							{
								result.add(object);
							}
						}
					}
				}
			}
			result.sort(Comparator.comparingInt(o -> o.getLocalLocation().distanceTo(origin)));
			return result;
		}, Collections.<T>emptyList());
	}

	private static boolean originatesOn(TileObject object, Tile tile)
	{
		if (object instanceof net.runelite.api.GameObject)
		{
			return ((net.runelite.api.GameObject) object).getSceneMinLocation().equals(tile.getSceneLocation());
		}
		return object.getLocalLocation().equals(tile.getLocalLocation());
	}

	private static boolean isWithinTiles(LocalPoint anchor, LocalPoint location, int distance)
	{
		if (location == null)
		{
			return false;
		}
		int dx = Math.abs(anchor.getX() - location.getX());
		int dy = Math.abs(anchor.getY() - location.getY());
		if (distance == 0)
		{
			return (dx == Perspective.LOCAL_TILE_SIZE && dy == 0) || (dy == Perspective.LOCAL_TILE_SIZE && dx == 0);
		}
		return location.distanceTo(anchor) <= distance;
	}

	/**
	 * As upstream, where it hovers only when natural mouse is on: humanised input is not ported, so
	 * this never moves anything and returns false.
	 */
	public static boolean hoverOverObject(TileObject object)
	{
		return false;
	}

	// ---- Read batch 16a (Claude 2): upstream 2.6.38 scene object overload families ----

	public static List<net.runelite.api.WallObject> getWallObjects()
	{
		return getWallObjects(o -> true);
	}

	public static List<net.runelite.api.WallObject> getWallObjects(int distance)
	{
		return getWallObjects(o -> true, distance);
	}

	public static List<net.runelite.api.WallObject> getWallObjects(java.util.function.Predicate<net.runelite.api.WallObject> predicate, int distance)
	{
		return getWallObjects(predicate, playerLocation(), distance);
	}

	public static List<net.runelite.api.WallObject> getWallObjects(WorldPoint anchor)
	{
		return getWallObjects(o -> true, anchor);
	}

	public static List<net.runelite.api.WallObject> getWallObjects(LocalPoint anchorLocal)
	{
		return getWallObjects(o -> true, anchorLocal);
	}

	public static List<net.runelite.api.WallObject> getWallObjects(java.util.function.Predicate<net.runelite.api.WallObject> predicate)
	{
		return getWallObjects(predicate, playerLocation());
	}

	public static List<net.runelite.api.WallObject> getWallObjects(java.util.function.Predicate<net.runelite.api.WallObject> predicate, WorldPoint anchor)
	{
		return getWallObjects(predicate, anchor, Constants.SCENE_SIZE / 2);
	}

	public static List<net.runelite.api.WallObject> getWallObjects(java.util.function.Predicate<net.runelite.api.WallObject> predicate, LocalPoint anchorLocal)
	{
		return getWallObjects(predicate, anchorLocal, (Constants.SCENE_SIZE / 2) * Perspective.LOCAL_TILE_SIZE);
	}

	/** As upstream: WallObjects matching {@code predicate} within {@code distance} tiles of {@code anchor}, nearest first. */
	public static List<net.runelite.api.WallObject> getWallObjects(java.util.function.Predicate<net.runelite.api.WallObject> predicate, WorldPoint anchor, int distance)
	{
		return sceneObjects(WALLOBJECT_EXTRACTOR, predicate, anchor, distance);
	}

	/** As upstream: WallObjects matching {@code predicate} within {@code distance} local units of {@code anchorLocal}, nearest first. */
	public static List<net.runelite.api.WallObject> getWallObjects(java.util.function.Predicate<net.runelite.api.WallObject> predicate, LocalPoint anchorLocal, int distance)
	{
		return sceneObjects(WALLOBJECT_EXTRACTOR, predicate, toWorld(anchorLocal), distance / Perspective.LOCAL_TILE_SIZE);
	}

	public static net.runelite.api.WallObject getWallObject(int id)
	{
		return getWallObject(o -> o.getId() == id);
	}

	public static net.runelite.api.WallObject getWallObject(int id, int distance)
	{
		return getWallObject(o -> o.getId() == id, distance);
	}

	public static net.runelite.api.WallObject getWallObject(int id, WorldPoint anchor)
	{
		return getWallObject(o -> o.getId() == id, anchor);
	}

	public static net.runelite.api.WallObject getWallObject(int id, WorldPoint anchor, int distance)
	{
		return getWallObject(o -> o.getId() == id, anchor, distance);
	}

	public static net.runelite.api.WallObject getWallObject(Integer[] ids)
	{
		Set<Integer> idSet = new HashSet<>(Arrays.asList(ids));
		return getWallObject(o -> idSet.contains(o.getId()));
	}

	public static net.runelite.api.WallObject getWallObject(Integer[] ids, int distance)
	{
		Set<Integer> idSet = new HashSet<>(Arrays.asList(ids));
		return getWallObject(o -> idSet.contains(o.getId()), distance);
	}

	public static net.runelite.api.WallObject getWallObject(String objectName, boolean exact)
	{
		return getWallObject(nameMatches(objectName, exact));
	}

	public static net.runelite.api.WallObject getWallObject(String objectName, boolean exact, int distance)
	{
		return getWallObject(nameMatches(objectName, exact), distance);
	}

	public static net.runelite.api.WallObject getWallObject(String objectName, boolean exact, WorldPoint anchor)
	{
		return getWallObject(nameMatches(objectName, exact), anchor);
	}

	public static net.runelite.api.WallObject getWallObject(String objectName, boolean exact, LocalPoint anchorLocal)
	{
		return getWallObject(nameMatches(objectName, exact), anchorLocal);
	}

	public static net.runelite.api.WallObject getWallObject(String objectName, boolean exact, WorldPoint anchor, int distance)
	{
		return getWallObject(nameMatches(objectName, exact), anchor, distance);
	}

	public static net.runelite.api.WallObject getWallObject(String objectName, boolean exact, LocalPoint anchorLocal, int distance)
	{
		return getWallObject(nameMatches(objectName, exact), anchorLocal, distance);
	}

	public static net.runelite.api.WallObject getWallObject(String objectName)
	{
		return getWallObject(nameMatches(objectName, false));
	}

	public static net.runelite.api.WallObject getWallObject(String objectName, int distance)
	{
		return getWallObject(nameMatches(objectName, false), distance);
	}

	public static net.runelite.api.WallObject getWallObject(String objectName, WorldPoint anchor)
	{
		return getWallObject(nameMatches(objectName, false), anchor);
	}

	public static net.runelite.api.WallObject getWallObject(String objectName, LocalPoint anchorLocal)
	{
		return getWallObject(nameMatches(objectName, false), anchorLocal);
	}

	public static net.runelite.api.WallObject getWallObject(String objectName, WorldPoint anchor, int distance)
	{
		return getWallObject(nameMatches(objectName, false), anchor, distance);
	}

	public static net.runelite.api.WallObject getWallObject(String objectName, LocalPoint anchorLocal, int distance)
	{
		return getWallObject(nameMatches(objectName, false), anchorLocal, distance);
	}

	public static net.runelite.api.WallObject getWallObject(java.util.function.Predicate<net.runelite.api.WallObject> predicate)
	{
		return getWallObjects(predicate).stream().findFirst().orElse(null);
	}

	public static net.runelite.api.WallObject getWallObject(WorldPoint anchor)
	{
		return getWallObject(o -> true, anchor);
	}

	public static net.runelite.api.WallObject getWallObject(LocalPoint anchorLocal)
	{
		return getWallObject(o -> true, anchorLocal);
	}

	public static net.runelite.api.WallObject getWallObject(WorldPoint anchor, int distance)
	{
		return getWallObject(o -> true, anchor, distance);
	}

	public static net.runelite.api.WallObject getWallObject(LocalPoint anchorLocal, int distance)
	{
		return getWallObject(o -> true, anchorLocal, distance);
	}

	public static net.runelite.api.WallObject getWallObject(java.util.function.Predicate<net.runelite.api.WallObject> predicate, int distance)
	{
		return getWallObjects(predicate, distance).stream().findFirst().orElse(null);
	}

	public static net.runelite.api.WallObject getWallObject(java.util.function.Predicate<net.runelite.api.WallObject> predicate, WorldPoint anchor)
	{
		return getWallObjects(predicate, anchor).stream().findFirst().orElse(null);
	}

	public static net.runelite.api.WallObject getWallObject(java.util.function.Predicate<net.runelite.api.WallObject> predicate, LocalPoint anchorLocal)
	{
		return getWallObjects(predicate, anchorLocal).stream().findFirst().orElse(null);
	}

	/** As upstream: the nearest WallObject matching {@code predicate} within {@code distance} tiles of {@code anchor}. */
	public static net.runelite.api.WallObject getWallObject(java.util.function.Predicate<net.runelite.api.WallObject> predicate, WorldPoint anchor, int distance)
	{
		return getWallObjects(predicate, anchor, distance).stream().findFirst().orElse(null);
	}

	/** As upstream: the nearest WallObject matching {@code predicate} within {@code distance} local units of {@code anchorLocal}. */
	public static net.runelite.api.WallObject getWallObject(java.util.function.Predicate<net.runelite.api.WallObject> predicate, LocalPoint anchorLocal, int distance)
	{
		return getWallObjects(predicate, anchorLocal, distance).stream().findFirst().orElse(null);
	}

	public static List<net.runelite.api.DecorativeObject> getDecorativeObjects()
	{
		return getDecorativeObjects(o -> true);
	}

	public static List<net.runelite.api.DecorativeObject> getDecorativeObjects(int distance)
	{
		return getDecorativeObjects(o -> true, distance);
	}

	public static List<net.runelite.api.DecorativeObject> getDecorativeObjects(java.util.function.Predicate<net.runelite.api.DecorativeObject> predicate, int distance)
	{
		return getDecorativeObjects(predicate, playerLocation(), distance);
	}

	public static List<net.runelite.api.DecorativeObject> getDecorativeObjects(WorldPoint anchor)
	{
		return getDecorativeObjects(o -> true, anchor);
	}

	public static List<net.runelite.api.DecorativeObject> getDecorativeObjects(LocalPoint anchorLocal)
	{
		return getDecorativeObjects(o -> true, anchorLocal);
	}

	public static List<net.runelite.api.DecorativeObject> getDecorativeObjects(java.util.function.Predicate<net.runelite.api.DecorativeObject> predicate)
	{
		return getDecorativeObjects(predicate, playerLocation());
	}

	public static List<net.runelite.api.DecorativeObject> getDecorativeObjects(java.util.function.Predicate<net.runelite.api.DecorativeObject> predicate, WorldPoint anchor)
	{
		return getDecorativeObjects(predicate, anchor, Constants.SCENE_SIZE / 2);
	}

	public static List<net.runelite.api.DecorativeObject> getDecorativeObjects(java.util.function.Predicate<net.runelite.api.DecorativeObject> predicate, LocalPoint anchorLocal)
	{
		return getDecorativeObjects(predicate, anchorLocal, (Constants.SCENE_SIZE / 2) * Perspective.LOCAL_TILE_SIZE);
	}

	/** As upstream: DecorativeObjects matching {@code predicate} within {@code distance} tiles of {@code anchor}, nearest first. */
	public static List<net.runelite.api.DecorativeObject> getDecorativeObjects(java.util.function.Predicate<net.runelite.api.DecorativeObject> predicate, WorldPoint anchor, int distance)
	{
		return sceneObjects(DECORATIVEOBJECT_EXTRACTOR, predicate, anchor, distance);
	}

	/** As upstream: DecorativeObjects matching {@code predicate} within {@code distance} local units of {@code anchorLocal}, nearest first. */
	public static List<net.runelite.api.DecorativeObject> getDecorativeObjects(java.util.function.Predicate<net.runelite.api.DecorativeObject> predicate, LocalPoint anchorLocal, int distance)
	{
		return sceneObjects(DECORATIVEOBJECT_EXTRACTOR, predicate, toWorld(anchorLocal), distance / Perspective.LOCAL_TILE_SIZE);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(int id)
	{
		return getDecorativeObject(o -> o.getId() == id);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(int id, int distance)
	{
		return getDecorativeObject(o -> o.getId() == id, distance);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(int id, WorldPoint anchor)
	{
		return getDecorativeObject(o -> o.getId() == id, anchor);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(int id, WorldPoint anchor, int distance)
	{
		return getDecorativeObject(o -> o.getId() == id, anchor, distance);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(Integer[] ids)
	{
		Set<Integer> idSet = new HashSet<>(Arrays.asList(ids));
		return getDecorativeObject(o -> idSet.contains(o.getId()));
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(Integer[] ids, int distance)
	{
		Set<Integer> idSet = new HashSet<>(Arrays.asList(ids));
		return getDecorativeObject(o -> idSet.contains(o.getId()), distance);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(String objectName, boolean exact)
	{
		return getDecorativeObject(nameMatches(objectName, exact));
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(String objectName, boolean exact, int distance)
	{
		return getDecorativeObject(nameMatches(objectName, exact), distance);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(String objectName, boolean exact, WorldPoint anchor)
	{
		return getDecorativeObject(nameMatches(objectName, exact), anchor);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(String objectName, boolean exact, LocalPoint anchorLocal)
	{
		return getDecorativeObject(nameMatches(objectName, exact), anchorLocal);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(String objectName, boolean exact, WorldPoint anchor, int distance)
	{
		return getDecorativeObject(nameMatches(objectName, exact), anchor, distance);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(String objectName, boolean exact, LocalPoint anchorLocal, int distance)
	{
		return getDecorativeObject(nameMatches(objectName, exact), anchorLocal, distance);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(String objectName)
	{
		return getDecorativeObject(nameMatches(objectName, false));
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(String objectName, int distance)
	{
		return getDecorativeObject(nameMatches(objectName, false), distance);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(String objectName, WorldPoint anchor)
	{
		return getDecorativeObject(nameMatches(objectName, false), anchor);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(String objectName, LocalPoint anchorLocal)
	{
		return getDecorativeObject(nameMatches(objectName, false), anchorLocal);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(String objectName, WorldPoint anchor, int distance)
	{
		return getDecorativeObject(nameMatches(objectName, false), anchor, distance);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(String objectName, LocalPoint anchorLocal, int distance)
	{
		return getDecorativeObject(nameMatches(objectName, false), anchorLocal, distance);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(java.util.function.Predicate<net.runelite.api.DecorativeObject> predicate)
	{
		return getDecorativeObjects(predicate).stream().findFirst().orElse(null);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(WorldPoint anchor)
	{
		return getDecorativeObject(o -> true, anchor);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(LocalPoint anchorLocal)
	{
		return getDecorativeObject(o -> true, anchorLocal);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(WorldPoint anchor, int distance)
	{
		return getDecorativeObject(o -> true, anchor, distance);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(LocalPoint anchorLocal, int distance)
	{
		return getDecorativeObject(o -> true, anchorLocal, distance);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(java.util.function.Predicate<net.runelite.api.DecorativeObject> predicate, int distance)
	{
		return getDecorativeObjects(predicate, distance).stream().findFirst().orElse(null);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(java.util.function.Predicate<net.runelite.api.DecorativeObject> predicate, WorldPoint anchor)
	{
		return getDecorativeObjects(predicate, anchor).stream().findFirst().orElse(null);
	}

	public static net.runelite.api.DecorativeObject getDecorativeObject(java.util.function.Predicate<net.runelite.api.DecorativeObject> predicate, LocalPoint anchorLocal)
	{
		return getDecorativeObjects(predicate, anchorLocal).stream().findFirst().orElse(null);
	}

	/** As upstream: the nearest DecorativeObject matching {@code predicate} within {@code distance} tiles of {@code anchor}. */
	public static net.runelite.api.DecorativeObject getDecorativeObject(java.util.function.Predicate<net.runelite.api.DecorativeObject> predicate, WorldPoint anchor, int distance)
	{
		return getDecorativeObjects(predicate, anchor, distance).stream().findFirst().orElse(null);
	}

	/** As upstream: the nearest DecorativeObject matching {@code predicate} within {@code distance} local units of {@code anchorLocal}. */
	public static net.runelite.api.DecorativeObject getDecorativeObject(java.util.function.Predicate<net.runelite.api.DecorativeObject> predicate, LocalPoint anchorLocal, int distance)
	{
		return getDecorativeObjects(predicate, anchorLocal, distance).stream().findFirst().orElse(null);
	}

	public static List<GroundObject> getGroundObjects(LocalPoint anchorLocal)
	{
		return getGroundObjects(o -> true, anchorLocal);
	}

	public static List<GroundObject> getGroundObjects(java.util.function.Predicate<GroundObject> predicate, LocalPoint anchorLocal)
	{
		return getGroundObjects(predicate, anchorLocal, (Constants.SCENE_SIZE / 2) * Perspective.LOCAL_TILE_SIZE);
	}

	/** As upstream: GroundObjects matching {@code predicate} within {@code distance} local units of {@code anchorLocal}, nearest first. */
	public static List<GroundObject> getGroundObjects(java.util.function.Predicate<GroundObject> predicate, LocalPoint anchorLocal, int distance)
	{
		return sceneObjects(GROUNDOBJECT_EXTRACTOR, predicate, toWorld(anchorLocal), distance / Perspective.LOCAL_TILE_SIZE);
	}

	public static GroundObject getGroundObject(int id)
	{
		return getGroundObject(o -> o.getId() == id);
	}

	public static GroundObject getGroundObject(int id, int distance)
	{
		return getGroundObject(o -> o.getId() == id, distance);
	}

	public static GroundObject getGroundObject(int id, WorldPoint anchor)
	{
		return getGroundObject(o -> o.getId() == id, anchor);
	}

	public static GroundObject getGroundObject(int id, WorldPoint anchor, int distance)
	{
		return getGroundObject(o -> o.getId() == id, anchor, distance);
	}

	public static GroundObject getGroundObject(Integer[] ids)
	{
		Set<Integer> idSet = new HashSet<>(Arrays.asList(ids));
		return getGroundObject(o -> idSet.contains(o.getId()));
	}

	public static GroundObject getGroundObject(Integer[] ids, int distance)
	{
		Set<Integer> idSet = new HashSet<>(Arrays.asList(ids));
		return getGroundObject(o -> idSet.contains(o.getId()), distance);
	}

	public static GroundObject getGroundObject(String objectName, boolean exact)
	{
		return getGroundObject(nameMatches(objectName, exact));
	}

	public static GroundObject getGroundObject(String objectName, boolean exact, int distance)
	{
		return getGroundObject(nameMatches(objectName, exact), distance);
	}

	public static GroundObject getGroundObject(String objectName, boolean exact, WorldPoint anchor)
	{
		return getGroundObject(nameMatches(objectName, exact), anchor);
	}

	public static GroundObject getGroundObject(String objectName, boolean exact, LocalPoint anchorLocal)
	{
		return getGroundObject(nameMatches(objectName, exact), anchorLocal);
	}

	public static GroundObject getGroundObject(String objectName, boolean exact, WorldPoint anchor, int distance)
	{
		return getGroundObject(nameMatches(objectName, exact), anchor, distance);
	}

	public static GroundObject getGroundObject(String objectName, boolean exact, LocalPoint anchorLocal, int distance)
	{
		return getGroundObject(nameMatches(objectName, exact), anchorLocal, distance);
	}

	public static GroundObject getGroundObject(String objectName)
	{
		return getGroundObject(nameMatches(objectName, false));
	}

	public static GroundObject getGroundObject(String objectName, int distance)
	{
		return getGroundObject(nameMatches(objectName, false), distance);
	}

	public static GroundObject getGroundObject(String objectName, WorldPoint anchor)
	{
		return getGroundObject(nameMatches(objectName, false), anchor);
	}

	public static GroundObject getGroundObject(String objectName, LocalPoint anchorLocal)
	{
		return getGroundObject(nameMatches(objectName, false), anchorLocal);
	}

	public static GroundObject getGroundObject(String objectName, WorldPoint anchor, int distance)
	{
		return getGroundObject(nameMatches(objectName, false), anchor, distance);
	}

	public static GroundObject getGroundObject(String objectName, LocalPoint anchorLocal, int distance)
	{
		return getGroundObject(nameMatches(objectName, false), anchorLocal, distance);
	}

	public static GroundObject getGroundObject(java.util.function.Predicate<GroundObject> predicate)
	{
		return getGroundObjects(predicate).stream().findFirst().orElse(null);
	}

	public static GroundObject getGroundObject(WorldPoint anchor)
	{
		return getGroundObject(o -> true, anchor);
	}

	public static GroundObject getGroundObject(LocalPoint anchorLocal)
	{
		return getGroundObject(o -> true, anchorLocal);
	}

	public static GroundObject getGroundObject(WorldPoint anchor, int distance)
	{
		return getGroundObject(o -> true, anchor, distance);
	}

	public static GroundObject getGroundObject(LocalPoint anchorLocal, int distance)
	{
		return getGroundObject(o -> true, anchorLocal, distance);
	}

	public static GroundObject getGroundObject(java.util.function.Predicate<GroundObject> predicate, int distance)
	{
		return getGroundObjects(predicate, distance).stream().findFirst().orElse(null);
	}

	public static GroundObject getGroundObject(java.util.function.Predicate<GroundObject> predicate, WorldPoint anchor)
	{
		return getGroundObjects(predicate, anchor).stream().findFirst().orElse(null);
	}

	public static GroundObject getGroundObject(java.util.function.Predicate<GroundObject> predicate, LocalPoint anchorLocal)
	{
		return getGroundObjects(predicate, anchorLocal).stream().findFirst().orElse(null);
	}

	/** As upstream: the nearest GroundObject matching {@code predicate} within {@code distance} tiles of {@code anchor}. */
	public static GroundObject getGroundObject(java.util.function.Predicate<GroundObject> predicate, WorldPoint anchor, int distance)
	{
		return getGroundObjects(predicate, anchor, distance).stream().findFirst().orElse(null);
	}

	/** As upstream: the nearest GroundObject matching {@code predicate} within {@code distance} local units of {@code anchorLocal}. */
	public static GroundObject getGroundObject(java.util.function.Predicate<GroundObject> predicate, LocalPoint anchorLocal, int distance)
	{
		return getGroundObjects(predicate, anchorLocal, distance).stream().findFirst().orElse(null);
	}

	public static List<TileObject> getTileObjects()
	{
		return getTileObjects(o -> true);
	}

	public static List<TileObject> getTileObjects(int distance)
	{
		return getTileObjects(o -> true, distance);
	}

	public static List<TileObject> getTileObjects(java.util.function.Predicate<TileObject> predicate, int distance)
	{
		return getTileObjects(predicate, playerLocation(), distance);
	}

	public static List<TileObject> getTileObjects(WorldPoint anchor)
	{
		return getTileObjects(o -> true, anchor);
	}

	public static List<TileObject> getTileObjects(LocalPoint anchorLocal)
	{
		return getTileObjects(o -> true, anchorLocal);
	}

	public static List<TileObject> getTileObjects(java.util.function.Predicate<TileObject> predicate)
	{
		return getTileObjects(predicate, playerLocation());
	}

	public static List<TileObject> getTileObjects(java.util.function.Predicate<TileObject> predicate, WorldPoint anchor)
	{
		return getTileObjects(predicate, anchor, Constants.SCENE_SIZE / 2);
	}

	public static List<TileObject> getTileObjects(java.util.function.Predicate<TileObject> predicate, LocalPoint anchorLocal)
	{
		return getTileObjects(predicate, anchorLocal, (Constants.SCENE_SIZE / 2) * Perspective.LOCAL_TILE_SIZE);
	}

	/** As upstream: TileObjects matching {@code predicate} within {@code distance} tiles of {@code anchor}, nearest first. */
	public static List<TileObject> getTileObjects(java.util.function.Predicate<TileObject> predicate, WorldPoint anchor, int distance)
	{
		return sceneObjects(TILEOBJECT_EXTRACTOR, predicate, anchor, distance);
	}

	/** As upstream: TileObjects matching {@code predicate} within {@code distance} local units of {@code anchorLocal}, nearest first. */
	public static List<TileObject> getTileObjects(java.util.function.Predicate<TileObject> predicate, LocalPoint anchorLocal, int distance)
	{
		return sceneObjects(TILEOBJECT_EXTRACTOR, predicate, toWorld(anchorLocal), distance / Perspective.LOCAL_TILE_SIZE);
	}

	public static TileObject getTileObject(Integer[] ids)
	{
		Set<Integer> idSet = new HashSet<>(Arrays.asList(ids));
		return getTileObject(o -> idSet.contains(o.getId()));
	}

	public static TileObject getTileObject(Integer[] ids, int distance)
	{
		Set<Integer> idSet = new HashSet<>(Arrays.asList(ids));
		return getTileObject(o -> idSet.contains(o.getId()), distance);
	}

	public static TileObject getTileObject(String objectName, boolean exact, WorldPoint anchor)
	{
		return getTileObject(nameMatches(objectName, exact), anchor);
	}

	public static TileObject getTileObject(String objectName, boolean exact, LocalPoint anchorLocal)
	{
		return getTileObject(nameMatches(objectName, exact), anchorLocal);
	}

	public static TileObject getTileObject(String objectName, boolean exact, LocalPoint anchorLocal, int distance)
	{
		return getTileObject(nameMatches(objectName, exact), anchorLocal, distance);
	}

	public static TileObject getTileObject(String objectName, LocalPoint anchorLocal)
	{
		return getTileObject(nameMatches(objectName, false), anchorLocal);
	}

	public static TileObject getTileObject(String objectName, LocalPoint anchorLocal, int distance)
	{
		return getTileObject(nameMatches(objectName, false), anchorLocal, distance);
	}

	public static TileObject getTileObject(WorldPoint anchor)
	{
		return getTileObject(o -> true, anchor);
	}

	public static TileObject getTileObject(LocalPoint anchorLocal)
	{
		return getTileObject(o -> true, anchorLocal);
	}

	public static TileObject getTileObject(WorldPoint anchor, int distance)
	{
		return getTileObject(o -> true, anchor, distance);
	}

	public static TileObject getTileObject(LocalPoint anchorLocal, int distance)
	{
		return getTileObject(o -> true, anchorLocal, distance);
	}

	public static TileObject getTileObject(java.util.function.Predicate<TileObject> predicate, LocalPoint anchorLocal)
	{
		return getTileObjects(predicate, anchorLocal).stream().findFirst().orElse(null);
	}

	/** As upstream: the nearest TileObject matching {@code predicate} within {@code distance} local units of {@code anchorLocal}. */
	public static TileObject getTileObject(java.util.function.Predicate<TileObject> predicate, LocalPoint anchorLocal, int distance)
	{
		return getTileObjects(predicate, anchorLocal, distance).stream().findFirst().orElse(null);
	}

	public static List<net.runelite.api.GameObject> getGameObjects(LocalPoint anchorLocal)
	{
		return getGameObjects(o -> true, anchorLocal);
	}

	public static List<net.runelite.api.GameObject> getGameObjects(java.util.function.Predicate<net.runelite.api.GameObject> predicate, LocalPoint anchorLocal)
	{
		return getGameObjects(predicate, anchorLocal, (Constants.SCENE_SIZE / 2) * Perspective.LOCAL_TILE_SIZE);
	}

	/** As upstream: GameObjects matching {@code predicate} within {@code distance} local units of {@code anchorLocal}, nearest first. */
	public static List<net.runelite.api.GameObject> getGameObjects(java.util.function.Predicate<net.runelite.api.GameObject> predicate, LocalPoint anchorLocal, int distance)
	{
		return sceneObjects(GAMEOBJECT_EXTRACTOR, predicate, toWorld(anchorLocal), distance / Perspective.LOCAL_TILE_SIZE);
	}

	public static net.runelite.api.GameObject getGameObject(Integer[] ids)
	{
		Set<Integer> idSet = new HashSet<>(Arrays.asList(ids));
		return getGameObject(o -> idSet.contains(o.getId()));
	}

	public static net.runelite.api.GameObject getGameObject(Integer[] ids, int distance)
	{
		Set<Integer> idSet = new HashSet<>(Arrays.asList(ids));
		return getGameObject(o -> idSet.contains(o.getId()), distance);
	}

	public static net.runelite.api.GameObject getGameObject(String objectName, boolean exact, WorldPoint anchor)
	{
		return getGameObject(nameMatches(objectName, exact), anchor);
	}

	public static net.runelite.api.GameObject getGameObject(String objectName, boolean exact, LocalPoint anchorLocal)
	{
		return getGameObject(nameMatches(objectName, exact), anchorLocal);
	}

	public static net.runelite.api.GameObject getGameObject(String objectName, boolean exact, LocalPoint anchorLocal, int distance)
	{
		return getGameObject(nameMatches(objectName, exact), anchorLocal, distance);
	}

	public static net.runelite.api.GameObject getGameObject(String objectName, LocalPoint anchorLocal)
	{
		return getGameObject(nameMatches(objectName, false), anchorLocal);
	}

	public static net.runelite.api.GameObject getGameObject(String objectName, LocalPoint anchorLocal, int distance)
	{
		return getGameObject(nameMatches(objectName, false), anchorLocal, distance);
	}

	public static net.runelite.api.GameObject getGameObject(WorldPoint anchor)
	{
		return getGameObject(o -> true, anchor);
	}

	public static net.runelite.api.GameObject getGameObject(LocalPoint anchorLocal)
	{
		return getGameObject(o -> true, anchorLocal);
	}

	public static net.runelite.api.GameObject getGameObject(WorldPoint anchor, int distance)
	{
		return getGameObject(o -> true, anchor, distance);
	}

	public static net.runelite.api.GameObject getGameObject(LocalPoint anchorLocal, int distance)
	{
		return getGameObject(o -> true, anchorLocal, distance);
	}

	public static net.runelite.api.GameObject getGameObject(java.util.function.Predicate<net.runelite.api.GameObject> predicate, int distance)
	{
		return getGameObjects(predicate, distance).stream().findFirst().orElse(null);
	}

	public static net.runelite.api.GameObject getGameObject(java.util.function.Predicate<net.runelite.api.GameObject> predicate, LocalPoint anchorLocal)
	{
		return getGameObjects(predicate, anchorLocal).stream().findFirst().orElse(null);
	}

	/** As upstream: the nearest GameObject matching {@code predicate} within {@code distance} local units of {@code anchorLocal}. */
	public static net.runelite.api.GameObject getGameObject(java.util.function.Predicate<net.runelite.api.GameObject> predicate, LocalPoint anchorLocal, int distance)
	{
		return getGameObjects(predicate, anchorLocal, distance).stream().findFirst().orElse(null);
	}

	/** The world tile of {@code anchorLocal} on the top-level view (client thread), or the player's tile. */
	private static WorldPoint toWorld(LocalPoint anchorLocal)
	{
		if (anchorLocal == null)
		{
			return null;
		}
		WorldPoint point = MobileActions.read(() -> WorldPoint.fromLocal(net.runelite.client.plugins.microbot.Microbot.getClient(), anchorLocal), null);
		return point == null ? playerLocation() : point;
	}

	/** As upstream: "WallObject", "DecorativeObject", "GameObject", "GroundObject" or "TileObject". */
	public static String getObjectType(TileObject object)
	{
		if (object instanceof net.runelite.api.WallObject)
		{
			return "WallObject";
		}
		if (object instanceof net.runelite.api.DecorativeObject)
		{
			return "DecorativeObject";
		}
		if (object instanceof net.runelite.api.GameObject)
		{
			return "GameObject";
		}
		return object instanceof GroundObject ? "GroundObject" : "TileObject";
	}

	/** As upstream: the ground object at {@code worldPoint}. */
	public static TileObject findGroundObjectByLocation(WorldPoint worldPoint)
	{
		return getGroundObject(o -> o.getWorldLocation().equals(worldPoint));
	}

	/** As upstream: the nearest game object with one of {@code ids}. */
	public static net.runelite.api.GameObject findObject(Integer[] ids)
	{
		Set<Integer> idSet = new HashSet<>(Arrays.asList(ids));
		return getGameObject(o -> idSet.contains(o.getId()));
	}

	/** As upstream: the game object with {@code id} at {@code worldPoint}. */
	public static net.runelite.api.GameObject findObject(int id, WorldPoint worldPoint)
	{
		return getGameObject(o -> o.getId() == id && o.getWorldLocation().equals(worldPoint));
	}

	/** As upstream: the nearest game object named {@code objectName} near {@code anchorPoint}, optionally in line of sight. */
	public static net.runelite.api.GameObject findObject(String objectName, boolean exact, int distance, boolean checkLineOfSight, WorldPoint anchorPoint)
	{
		return getGameObjects(nameMatches(objectName, exact), anchorPoint, distance).stream()
			.filter(o -> !checkLineOfSight || hasLineOfSight(o)).findFirst().orElse(null);
	}

	/** As upstream: the game object with {@code id} whose world x is {@code x}. */
	public static net.runelite.api.GameObject findObjectById(int id, int x)
	{
		return getGameObject(o -> o.getId() == id && o.getWorldLocation().getX() == x);
	}

	/** As upstream: the nearest object of any kind with {@code id} within {@code distance} tiles of the player. */
	public static TileObject findObjectByIdAndDistance(int id, int distance)
	{
		return getAll(o -> o.getId() == id, distance).stream().findFirst().orElse(null);
	}

	/** As upstream: the nearest game object whose name contains {@code name}. */
	public static net.runelite.api.GameObject get(String name)
	{
		return get(name, false);
	}

	public static net.runelite.api.GameObject get(String name, boolean exact)
	{
		return getGameObject(name, exact);
	}

	/** As upstream: the nearest bank-id game object offering "Deposit" within 20 tiles. */
	public static net.runelite.api.GameObject findDepositBox()
	{
		return findDepositBox(20);
	}

	/** As upstream: the nearest bank-id game object offering "Deposit" within {@code maxSearchRadius} tiles. */
	public static net.runelite.api.GameObject findDepositBox(int maxSearchRadius)
	{
		return getGameObjects(o -> Rs2BankID.BANK_ID_SET.contains(o.getId()), maxSearchRadius).stream()
			.filter(o -> hasAction(convertToObjectComposition(o), "Deposit", false)).findFirst().orElse(null);
	}

	/** As upstream: the nearest reachable Grand Exchange booth within 20 tiles. */
	public static net.runelite.api.WallObject findGrandExchangeBooth()
	{
		return findGrandExchangeBooth(20);
	}

	public static net.runelite.api.WallObject findGrandExchangeBooth(int maxSearchRadius)
	{
		return getWallObjects(o -> GRAND_EXCHANGE_BOOTH_IDS.contains(o.getId()), maxSearchRadius).stream()
			.filter(o -> net.runelite.client.plugins.microbot.util.tile.Rs2Tile.isTileReachable(MobileActions.read(o::getWorldLocation, null)))
			.findFirst().orElse(null);
	}

	/** As upstream: the loaded tiles on the player's floor within 2400 local units (about 19 tiles). */
	public static List<Tile> getTiles()
	{
		return getTiles(2400);
	}

	/** As upstream: the loaded tiles on the player's floor within {@code max(2400, maxTileDistance * 128)} local units. */
	public static List<Tile> getTiles(int maxTileDistance)
	{
		int maxDistance = Math.max(2400, maxTileDistance * 128);
		return MobileActions.read(() ->
		{
			Client client = net.runelite.client.plugins.microbot.Microbot.getClient();
			Player player = client.getLocalPlayer();
			net.runelite.api.WorldView view = client.getTopLevelWorldView();
			Tile[][][] tiles = view == null || view.getScene() == null ? null : view.getScene().getTiles();
			if (player == null || player.getLocalLocation() == null || tiles == null)
			{
				return Collections.<Tile>emptyList();
			}
			List<Tile> result = new ArrayList<>();
			for (Tile[] column : tiles[view.getPlane()])
			{
				for (Tile tile : column)
				{
					if (tile != null && player.getLocalLocation().distanceTo(tile.getLocalLocation()) <= maxDistance)
					{
						result.add(tile);
					}
				}
			}
			return result;
		}, Collections.emptyList());
	}

	/** As upstream: line of sight from the player to {@code tileObject}. */
	public static boolean hasLineOfSight(TileObject tileObject)
	{
		return hasLineOfSight(net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation(), tileObject);
	}

	/** As upstream: line of sight from {@code point} to {@code tileObject}'s area (2x2 for non-game objects). */
	public static boolean hasLineOfSight(WorldPoint point, TileObject tileObject)
	{
		if (point == null || tileObject == null)
		{
			return false;
		}
		return MobileActions.read(() ->
		{
			net.runelite.api.WorldView view = net.runelite.client.plugins.microbot.Microbot.getClient().getTopLevelWorldView();
			if (tileObject instanceof net.runelite.api.GameObject)
			{
				net.runelite.api.GameObject object = (net.runelite.api.GameObject) tileObject;
				WorldPoint corner = WorldPoint.fromScene(view, object.getSceneMinLocation().getX(), object.getSceneMinLocation().getY(), object.getPlane());
				return new WorldArea(corner, object.sizeX(), object.sizeY()).hasLineOfSightTo(view, point.toWorldArea());
			}
			return new WorldArea(tileObject.getWorldLocation(), 2, 2)
				.hasLineOfSightTo(view, new WorldArea(point.getX(), point.getY(), 2, 2, point.getPlane()));
		}, false);
	}

	/** As upstream: some tile within {@code distance} steps of the player is next to {@code tileObject}'s area. */
	public static boolean canWalkTo(TileObject tileObject, int distance)
	{
		if (tileObject == null)
		{
			return false;
		}
		WorldArea area = MobileActions.read(() ->
		{
			if (tileObject instanceof net.runelite.api.GameObject)
			{
				net.runelite.api.GameObject object = (net.runelite.api.GameObject) tileObject;
				net.runelite.api.WorldView view = net.runelite.client.plugins.microbot.Microbot.getClient().getTopLevelWorldView();
				WorldPoint corner = WorldPoint.fromScene(view, object.getSceneMinLocation().getX(), object.getSceneMinLocation().getY(), object.getPlane());
				return new WorldArea(corner, object.sizeX(), object.sizeY());
			}
			return new WorldArea(tileObject.getWorldLocation(), 2, 2);
		}, null);
		if (area == null)
		{
			return false;
		}
		return net.runelite.client.plugins.microbot.util.tile.Rs2Tile.getReachableTilesFromTile(
			net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation(), distance).keySet().stream()
			.anyMatch(tile -> tile.distanceTo(area) < 2);
	}
}
