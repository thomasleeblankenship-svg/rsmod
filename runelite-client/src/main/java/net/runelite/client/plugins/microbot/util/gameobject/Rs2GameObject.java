package net.runelite.client.plugins.microbot.util.gameobject;

import net.runelite.api.ObjectComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/** Composition access is marshalled, including transformation-dependent Forestry actions. */
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
}
