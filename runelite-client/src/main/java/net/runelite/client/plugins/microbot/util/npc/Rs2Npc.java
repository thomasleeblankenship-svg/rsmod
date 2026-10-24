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

	// ---- Hub read APIs (upstream 2.6.38) ----

	/**
	 * As upstream: top-level NPCs with a name and location that match {@code predicate}, nearest to
	 * the player first, as live legacy models. Adaptation: the predicate runs on the client thread,
	 * inside the one read that builds the list, so it must not block.
	 */
	public static java.util.stream.Stream<Rs2NpcModel> getNpcs(java.util.function.Predicate<Rs2NpcModel> predicate)
	{
		java.util.function.Predicate<Rs2NpcModel> filter = predicate == null ? npc -> true : predicate;
		java.util.List<Rs2NpcModel> npcs = MobileActions.read(() ->
		{
			net.runelite.api.Client client = Microbot.getClient();
			net.runelite.api.WorldView view = client.getTopLevelWorldView();
			net.runelite.api.Player player = client.getLocalPlayer();
			net.runelite.api.coords.LocalPoint from = player == null ? null : player.getLocalLocation();
			if (view == null || view.npcs() == null || from == null)
			{
				return java.util.Collections.<Rs2NpcModel>emptyList();
			}
			java.util.List<Rs2NpcModel> matches = new java.util.ArrayList<>();
			for (NPC npc : view.npcs())
			{
				Rs2NpcModel model = npc == null ? null : new Rs2NpcModel(npc);
				if (model != null && model.getName() != null && model.getLocalLocation() != null && filter.test(model))
				{
					matches.add(model);
				}
			}
			matches.sort(java.util.Comparator.comparingInt(npc -> npc.getLocalLocation().distanceTo(from)));
			return matches;
		}, java.util.Collections.emptyList());
		return npcs.stream();
	}

	public static java.util.stream.Stream<Rs2NpcModel> getNpcs()
	{
		return getNpcs(npc -> true);
	}

	/** As upstream: names equal to ({@code exact}) or containing {@code name}, ignoring case. */
	public static java.util.stream.Stream<Rs2NpcModel> getNpcs(String name, boolean exact)
	{
		if (name == null || name.isEmpty())
		{
			return java.util.stream.Stream.empty();
		}
		String lower = name.toLowerCase();
		return getNpcs(npc ->
		{
			String npcName = npc.getName();
			return npcName != null && !npcName.isEmpty()
				&& (exact ? npcName.equalsIgnoreCase(name) : npcName.toLowerCase().contains(lower));
		});
	}

	public static java.util.stream.Stream<Rs2NpcModel> getNpcs(String name)
	{
		return getNpcs(name, false);
	}

	public static java.util.stream.Stream<Rs2NpcModel> getNpcs(int id)
	{
		return getNpcs(npc -> npc.getId() == id);
	}

	/** As upstream: the nearest NPC whose name contains {@code name}, or {@code null}. */
	public static Rs2NpcModel getNpc(String name)
	{
		return getNpc(name, false);
	}

	public static Rs2NpcModel getNpc(String name, boolean exact)
	{
		return getNpcs(name, exact).findFirst().orElse(null);
	}

	public static Rs2NpcModel getNpc(int id)
	{
		return getNpcs(id).findFirst().orElse(null);
	}

	/** As upstream: the nearest NPC with {@code id} whose index is not in {@code excludedIndexes}. */
	public static java.util.Optional<Rs2NpcModel> getNpc(int id, java.util.List<Integer> excludedIndexes)
	{
		return getNpcs(npc -> npc.getId() == id && !excludedIndexes.contains(npc.getIndex())).findFirst();
	}

	/** As upstream: the NPC with scene index {@code index}, or {@code null}. */
	public static Rs2NpcModel getNpcByIndex(int index)
	{
		return getNpcs(npc -> npc.getIndex() == index).findFirst().orElse(null);
	}

	/** As upstream: the nearest NPC whose definition offers {@code action} (exact, case-sensitive). */
	public static Rs2NpcModel getNearestNpcWithAction(String action)
	{
		return getNpcs(npc ->
		{
			NPCComposition composition = npc.getComposition();
			return composition != null && composition.getActions() != null
				&& java.util.Arrays.asList(composition.getActions()).contains(action);
		}).findFirst().orElse(null);
	}

	/** As upstream: the random-event NPC: one offering "Dismiss" that is interacting with the local player. */
	public static Rs2NpcModel getRandomEventNPC()
	{
		return getNpcs(npc ->
		{
			NPCComposition composition = npc.getComposition();
			return composition != null && composition.getActions() != null
				&& java.util.Arrays.asList(composition.getActions()).contains("Dismiss")
				&& java.util.Objects.equals(npc.getInteracting(), Microbot.getClient().getLocalPlayer());
		}).findFirst().orElse(null);
	}

	/**
	 * As upstream: the nearest NPC offering "Bank" (base or transformed definition); the Hunter
	 * Guild banker counts only with 46 Hunter.
	 */
	public static Rs2NpcModel getBankerNPC()
	{
		WorldPoint hunterGuild = new WorldPoint(1542, 3041, 0);
		return getNpcs(npc ->
		{
			if (java.util.Objects.equals(npc.getWorldLocation(), hunterGuild))
			{
				return net.runelite.client.plugins.microbot.util.player.Rs2Player.getSkillRequirement(net.runelite.api.Skill.HUNTER, 46, false);
			}
			return offersBank(npc.getComposition()) || offersBank(npc.getTransformedComposition());
		}).findFirst().orElse(null);
	}

	private static boolean offersBank(NPCComposition composition)
	{
		return composition != null && composition.getActions() != null
			&& java.util.Arrays.asList(composition.getActions()).contains("Bank");
	}

	// ---- Actions (Claude 1). Claude 2's reads above are unchanged. ----

	/**
	 * As upstream: {@code action} on this NPC (its exact menu option, matched case-insensitively),
	 * as one script action on the NPC's index; tried for at most 1.2 s while another action holds
	 * the tick. Mobile: no "can't reach" recovery here; walks that need doors go through the walker.
	 */
	public static boolean interact(Rs2NpcModel npc, String action)
	{
		if (npc == null || action == null || action.isBlank())
		{
			return false;
		}
		Microbot.status = action + " " + npc.getName();
		int id = npc.getId();
		int index = npc.getIndex();
		return MobileActions.npcAt(id, index, action)
			|| net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> MobileActions.npcAt(id, index, action), 1_200);
	}

	/** As upstream: "Pickpocket" on this NPC. */
	public static boolean pickpocket(Rs2NpcModel npc)
	{
		return interact(npc, "Pickpocket");
	}

	/** As upstream: "Pickpocket" on this NPC. */
	public static boolean pickpocket(NPC npc)
	{
		return npc != null && pickpocket(new Rs2NpcModel(npc));
	}

	/** As upstream: "Pickpocket" on the nearest NPC named {@code npcName}. */
	public static boolean pickpocket(String npcName)
	{
		return pickpocket(getNpc(npcName));
	}

	/** As upstream (deprecated there): {@link #interact(Rs2NpcModel, String)} on a raw NPC. */
	public static boolean interact(NPC npc, String action)
	{
		return npc != null && interact(new Rs2NpcModel(npc), action);
	}
}
