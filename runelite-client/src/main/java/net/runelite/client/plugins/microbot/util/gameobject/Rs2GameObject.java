package net.runelite.client.plugins.microbot.util.gameobject;

import net.runelite.api.ObjectComposition;
import net.runelite.client.plugins.microbot.mobile.MobileActions;

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

}
