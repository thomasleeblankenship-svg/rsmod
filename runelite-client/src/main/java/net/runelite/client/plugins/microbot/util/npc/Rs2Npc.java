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

	/** As upstream: living NPCs with a combat level that are free to attack (or anything in multi-combat). */
	public static java.util.stream.Stream<Rs2NpcModel> getAttackableNpcs()
	{
		boolean multi = Microbot.getVarbitValue(net.runelite.api.gameval.VarbitID.MULTIWAY_INDICATOR) == 1;
		return getNpcs(npc -> npc.getCombatLevel() > 0 && !npc.isDead() && (multi || !npc.isInteracting()));
	}

	/**
	 * As upstream: living NPCs with a combat level that are free or fighting the player; with
	 * {@code reachable}, only those the player can walk to (here: within the reachable scene tiles).
	 */
	public static java.util.stream.Stream<Rs2NpcModel> getAttackableNpcs(boolean reachable)
	{
		net.runelite.api.Player local = MobileActions.read(() -> Microbot.getClient().getLocalPlayer(), null);
		java.util.stream.Stream<Rs2NpcModel> npcs = getNpcs(npc -> npc.getCombatLevel() > 0 && !npc.isDead()
			&& (!npc.isInteracting() || java.util.Objects.equals(npc.getInteracting(), local)));
		if (!reachable)
		{
			return npcs;
		}
		net.runelite.client.plugins.microbot.util.reachable.Rs2Reachable.PackedTiles tiles =
			net.runelite.client.plugins.microbot.util.reachable.Rs2Reachable.getReachableTiles(
				net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation());
		return npcs.filter(npc -> tiles.contains(npc.getWorldLocation()));
	}

	/** As upstream: {@link #getAttackableNpcs()} whose name contains {@code name}, ignoring case. */
	public static java.util.stream.Stream<Rs2NpcModel> getAttackableNpcs(String name)
	{
		return getAttackableNpcs(name, false);
	}

	public static java.util.stream.Stream<Rs2NpcModel> getAttackableNpcs(String name, boolean exact)
	{
		if (name == null || name.isEmpty())
		{
			return java.util.stream.Stream.empty();
		}
		String lower = name.toLowerCase();
		return getAttackableNpcs().filter(npc -> npc.getName() != null
			&& (exact ? npc.getName().equalsIgnoreCase(name) : npc.getName().toLowerCase().contains(lower)));
	}

	/** As upstream: the first of the NPC definition's actions that is one of {@code possibleActions}, or "". */
	public static String getAvailableAction(Rs2NpcModel npc, java.util.List<String> possibleActions)
	{
		if (npc == null || possibleActions == null || possibleActions.isEmpty())
		{
			return "";
		}
		return MobileActions.read(() ->
		{
			NPCComposition composition = Microbot.getClient().getNpcDefinition(npc.getId());
			String[] actions = composition == null ? null : composition.getActions();
			return actions == null ? "" : java.util.Arrays.stream(actions).filter(java.util.Objects::nonNull)
				.filter(a -> possibleActions.stream().anyMatch(a::equalsIgnoreCase)).findFirst().orElse("");
		}, "");
	}

	/** As upstream: health bar ratio over scale, as a percentage (NaN or infinite without a bar, as upstream). */
	public static double getHealth(net.runelite.client.plugins.microbot.util.ActorModel npc)
	{
		return (double) npc.getHealthRatio() / npc.getHealthScale() * 100;
	}

	/** As upstream: the nearest NPC named {@code name} in line of sight. */
	public static Rs2NpcModel getNpcInLineOfSight(String name)
	{
		return getNpcInLineOfSight(name, false);
	}

	public static Rs2NpcModel getNpcInLineOfSight(String name, boolean exact)
	{
		java.util.List<Rs2NpcModel> seen = getNpcsInLineOfSight(name, exact);
		return seen.isEmpty() ? null : seen.get(0);
	}

	/** As upstream: NPCs named {@code name} in line of sight of the player, nearest first. */
	public static java.util.List<Rs2NpcModel> getNpcsInLineOfSight(String name)
	{
		return getNpcsInLineOfSight(name, false);
	}

	public static java.util.List<Rs2NpcModel> getNpcsInLineOfSight(String name, boolean exact)
	{
		return getNpcs(name, exact).filter(Rs2Npc::hasLineOfSight).collect(java.util.stream.Collectors.toList());
	}

	/** As upstream: the nearest NPC whose base or transformed definition offers {@code action} (exact). */
	public static Rs2NpcModel getNpcWithAction(String action)
	{
		return getNpcs(npc -> offers(npc.getComposition(), action) || offers(npc.getTransformedComposition(), action))
			.findFirst().orElse(null);
	}

	private static boolean offers(NPCComposition composition, String action)
	{
		return composition != null && composition.getActions() != null && java.util.Arrays.asList(composition.getActions()).contains(action);
	}

	/** As upstream: living NPCs interacting with {@code player}. */
	public static java.util.List<Rs2NpcModel> getNpcsAttackingPlayer(net.runelite.api.Player player)
	{
		return getNpcs(npc -> npc.getInteracting() != null && java.util.Objects.equals(npc.getInteracting(), player) && !npc.isDead())
			.collect(java.util.stream.Collectors.toList());
	}

	/** As upstream: NPCs interacting with the local player, nearest first. */
	public static java.util.stream.Stream<Rs2NpcModel> getNpcsForPlayer()
	{
		return getNpcsForPlayer(npc -> true);
	}

	/** As upstream: NPCs interacting with the local player and matching {@code predicate}, nearest first. */
	public static java.util.stream.Stream<Rs2NpcModel> getNpcsForPlayer(java.util.function.Predicate<Rs2NpcModel> predicate)
	{
		net.runelite.api.Player local = MobileActions.read(() -> Microbot.getClient().getLocalPlayer(), null);
		if (local == null)
		{
			return java.util.stream.Stream.empty();
		}
		return getNpcs(npc -> java.util.Objects.equals(npc.getInteracting(), local)).filter(predicate);
	}

	public static java.util.List<Rs2NpcModel> getNpcsForPlayer(String name)
	{
		return getNpcsForPlayer(name, false);
	}

	/** As upstream: NPCs interacting with the local player whose name matches {@code name}. */
	public static java.util.List<Rs2NpcModel> getNpcsForPlayer(String name, boolean exact)
	{
		if (name == null || name.isEmpty())
		{
			return java.util.Collections.emptyList();
		}
		String lower = name.toLowerCase();
		return getNpcsForPlayer(npc -> npc.getName() != null
			&& (exact ? npc.getName().equalsIgnoreCase(name) : npc.getName().toLowerCase().contains(lower)))
			.collect(java.util.stream.Collectors.toList());
	}

	/** As upstream: living Pest Control portals (name contains "portal", health above 0), nearest first. */
	public static Rs2NpcModel[] getPestControlPortals()
	{
		return getNpcs("portal", false).filter(npc -> !npc.isDead() && npc.getHealthRatio() > 0).toArray(Rs2NpcModel[]::new);
	}

	/** As upstream: the NPC's world location. */
	public static WorldPoint getWorldLocation(NPC npc)
	{
		return npc == null ? null : getWorldLocation(new Rs2NpcModel(npc));
	}

	public static WorldPoint getWorldLocation(Rs2NpcModel npc)
	{
		return npc == null ? null : npc.getWorldLocation();
	}

	public static boolean hasLineOfSight(NPC npc)
	{
		return npc != null && hasLineOfSight(new Rs2NpcModel(npc));
	}

	/** As upstream: the NPC's tile has line of sight to the player's (or is the same tile). */
	public static boolean hasLineOfSight(Rs2NpcModel npc)
	{
		if (npc == null)
		{
			return false;
		}
		return MobileActions.read(() ->
		{
			WorldPoint npcLocation = npc.getWorldLocation();
			net.runelite.api.Player local = Microbot.getClient().getLocalPlayer();
			WorldPoint mine = local == null ? null : local.getWorldLocation();
			net.runelite.api.WorldView view = Microbot.getClient().getTopLevelWorldView();
			if (npcLocation == null || mine == null)
			{
				return false;
			}
			return npcLocation.equals(mine) || (view != null && npcLocation.toWorldArea().hasLineOfSightTo(view, mine));
		}, false);
	}

	/** As upstream: the NPC is within the equipped weapon's attack range (tiles, local distance). */
	public static boolean isInAttackRange(Rs2NpcModel npc)
	{
		return isInAttackRange(npc, false, false);
	}

	public static boolean isInAttackRange(Rs2NpcModel npc, boolean includeManualCast, boolean includeSpecialAttack)
	{
		if (npc == null)
		{
			return false;
		}
		Integer tiles = MobileActions.read(() ->
		{
			net.runelite.api.Player local = Microbot.getClient().getLocalPlayer();
			net.runelite.api.coords.LocalPoint mine = local == null ? null : local.getLocalLocation();
			net.runelite.api.coords.LocalPoint theirs = npc.getLocalLocation();
			return mine == null || theirs == null ? null : mine.distanceTo(theirs) / net.runelite.api.Perspective.LOCAL_TILE_SIZE;
		}, null);
		return tiles != null && tiles <= net.runelite.client.plugins.microbot.util.combat.Rs2Combat.getAttackRange(includeManualCast, includeSpecialAttack);
	}

	/** As upstream: the NPC's pose is not its idle pose. */
	public static boolean isMoving(NPC npc)
	{
		return npc != null && MobileActions.read(() -> npc.getPoseAnimation() != npc.getIdlePoseAnimation(), false);
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
	/** As upstream: the NPC's default (first) option. */
	public static boolean interact(Rs2NpcModel npc)
	{
		return interact(npc, "");
	}

	/** As upstream (deprecated there): the default option on a raw NPC. */
	public static boolean interact(NPC npc)
	{
		return npc != null && interact(new Rs2NpcModel(npc), "");
	}

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

	/** As upstream: {@code action} on the nearest NPC with this name. */
	public static boolean interact(String npcName, String action)
	{
		return npcName != null && interact(getNpc(npcName), action);
	}

	/** As upstream: {@code action} on the nearest NPC with this id. */
	public static boolean interact(int npcId, String action)
	{
		return interact(getNpc(npcId), action);
	}

	/**
	 * As upstream, where it hovers only when natural mouse is on: humanised input is not ported, so
	 * this never moves anything and returns false.
	 */
	public static boolean hoverOverActor(net.runelite.api.Actor actor)
	{
		return false;
	}

	/**
	 * As upstream: "Attack" on this NPC when it is in line of sight, the player is not already in
	 * combat, and (outside multi-combat) it is not fighting someone else.
	 */
	public static boolean attack(Rs2NpcModel npc)
	{
		if (npc == null || !hasLineOfSight(npc) || net.runelite.client.plugins.microbot.util.combat.Rs2Combat.inCombat()) return false;
		boolean busyElsewhere = MobileActions.read(() ->
		{
			net.runelite.api.Actor target = npc.getInteracting();
			return npc.isInteracting() && target != null && target != Microbot.getClient().getLocalPlayer()
				&& Microbot.getClient().getVarbitValue(net.runelite.api.gameval.VarbitID.MULTIWAY_INDICATOR) != 1;
		}, true);
		return !busyElsewhere && interact(npc, "Attack");
	}

	/** As upstream (deprecated there): {@link #attack(Rs2NpcModel)} on a raw NPC. */
	public static boolean attack(NPC npc)
	{
		return npc != null && attack(new Rs2NpcModel(npc));
	}

	/** As upstream: {@link #attack(Rs2NpcModel)} on the nearest NPC with this ID. */
	public static boolean attack(int npcId)
	{
		return attack(getNpc(npcId));
	}

	/**
	 * As upstream: walks to the nearest known spawn cluster of {@code name} with at least
	 * {@code minClustering} NPCs; {@code avoidWilderness} skips clusters in the Wilderness.
	 */
	public static boolean walkToNearestMonster(String name, int minClustering, boolean avoidWilderness)
	{
		MonsterLocation location = Rs2NpcManager.getClosestLocation(name, minClustering, avoidWilderness);
		WorldPoint target = location == null ? null : location.getClosestToCenter();
		return target != null && Rs2Walker.walkTo(target);
	}

	/** Mobile adaptation: avoids the Wilderness (upstream does not, by default). */
	public static boolean walkToNearestMonster(String name, int minClustering)
	{
		return walkToNearestMonster(name, minClustering, true);
	}

	/** Mobile adaptation: avoids the Wilderness (upstream does not, by default). */
	public static boolean walkToNearestMonster(String name)
	{
		return walkToNearestMonster(name, 1, true);
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

	/** As upstream: "Pickpocket" on one of the NPC Indicators plugin's highlighted NPCs. */
	public static boolean pickpocket(java.util.Map<NPC, net.runelite.client.game.npcoverlay.HighlightedNpc> highlightedNpcs)
	{
		return highlightedNpcs != null && !highlightedNpcs.isEmpty() && pickpocket(highlightedNpcs.keySet().iterator().next());
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
