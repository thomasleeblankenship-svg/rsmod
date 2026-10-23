package net.runelite.client.plugins.microbot.util.gameobject;

import net.runelite.api.ObjectComposition;
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
