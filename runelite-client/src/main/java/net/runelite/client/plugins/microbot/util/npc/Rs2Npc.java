package net.runelite.client.plugins.microbot.util.npc;

import java.util.HashSet;
import java.util.Set;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Static NPC helpers with Microbot's names. Reads only (written for RuneMobile); interaction lives
 * with the NPC cache and queryables.
 */
public final class Rs2Npc
{
	private Rs2Npc()
	{
	}

	/** Whether NPC type {@code npcId} offers {@code action} (ignoring case) in its menu. */
	public static boolean hasAction(int npcId, String action)
	{
		if (action == null)
		{
			return false;
		}
		return MobileActions.read(() ->
		{
			NPCComposition definition = Microbot.getClient().getNpcDefinition(npcId);
			String[] actions = definition == null ? null : definition.getActions();
			if (actions == null)
			{
				return false;
			}
			for (String candidate : actions)
			{
				if (action.equalsIgnoreCase(candidate))
				{
					return true;
				}
			}
			return false;
		}, false);
	}

	/**
	 * Whether the player can walk, within {@code distance} tiles on the current floor, to the NPC's
	 * tile or a tile beside it.
	 */
	public static boolean canWalkTo(NPC npc, int distance)
	{
		if (npc == null)
		{
			return false;
		}
		WorldPoint location = MobileActions.read(npc::getWorldLocation, null);
		if (location == null)
		{
			return false;
		}
		Set<WorldPoint> reachable = new HashSet<>(Rs2Walker.getWalkableTilesAroundPlayer(distance));
		return reachable.contains(location) || reachable.contains(location.dx(1)) || reachable.contains(location.dx(-1))
			|| reachable.contains(location.dy(1)) || reachable.contains(location.dy(-1));
	}

	// ---- Actions (Claude 1) ----

	/** As upstream: walks to the NPC and turns the camera to it; the same model, or null. */
	public static Rs2NpcModel validateInteractable(Rs2NpcModel npc)
	{
		if (npc == null)
		{
			return null;
		}
		WorldPoint location = npc.getWorldLocation();
		if (location != null)
		{
			Rs2Walker.walkTo(location);
		}
		net.runelite.client.plugins.microbot.util.camera.Rs2Camera.turnTo(npc);
		return npc;
	}

	/** As upstream, for a live NPC. */
	public static NPC validateInteractable(NPC npc)
	{
		return npc == null || validateInteractable(new Rs2NpcModel(npc)) == null ? null : npc;
	}
}
