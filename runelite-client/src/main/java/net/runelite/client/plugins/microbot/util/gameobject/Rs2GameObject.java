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
}
