/*
 * Adapted from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/skills/slayer/Rs2Slayer.java
 * (the reads). BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.skills.slayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WalkerPathReads;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.npc.MonsterLocation;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import net.runelite.client.plugins.microbot.util.skills.slayer.enums.ProtectiveEquipment;
import net.runelite.client.plugins.microbot.util.skills.slayer.enums.SlayerMaster;
import net.runelite.client.plugins.slayer.Task;

/**
 * Upstream's slayer task reads: the task (varplayers and the slayer DB tables), its size, its
 * monsters and nearest location (from {@link Rs2NpcManager}), its weakness and protective
 * equipment, and the teleport items a route to the task still needs.
 * <p>
 * Adaptations: the DB table reads run on the client thread here (upstream goes through
 * {@code Microbot.getDBRowsByValue}/{@code getDBTableField}); {@code prepareItemTransports} plans on
 * {@link WalkerPathReads} and handles the fairy ring staff without upstream's mutation of the shared
 * catalogue entry; master travel delegates to the existing mobile walker.
 */
@Slf4j
public final class Rs2Slayer
{
	/** As upstream: the monster {@link #getSlayerTaskLocation(int, boolean)} last chose. */
	public static String slayerTaskMonsterTarget;
	/** As upstream: monster names {@link #getSlayerMonsters()} leaves out. */
	public static List<String> blacklistedSlayerMonsters = new ArrayList<>();

	/** The "Bosses" assignment, from {@code [proc,helper_slayer_current_assignment]}. */
	private static final int BOSS_TASK = 98;

	private Rs2Slayer()
	{
	}

	/** Walks to the master's catalogue location; true only when the walker reports arrival. */
	public static boolean walkToSlayerMaster(SlayerMaster master)
	{
		return master != null && Rs2Walker.walkTo(master.getWorldPoint());
	}

	public static boolean hasSlayerTask()
	{
		return getSlayerTaskSize() > 0;
	}

	public static int getSlayerTaskSize()
	{
		return Microbot.getVarbitPlayerValue(VarPlayerID.SLAYER_COUNT);
	}

	/** As upstream: the task's name from the slayer DB tables (a boss task by its sublist), or {@code null}. */
	public static String getSlayerTask()
	{
		int taskId = Microbot.getVarbitPlayerValue(VarPlayerID.SLAYER_TARGET);
		if (taskId == 0)
		{
			return null;
		}
		return MobileActions.read(() ->
		{
			Client client = Microbot.getClient();
			int taskRow;
			if (taskId == BOSS_TASK)
			{
				List<Integer> bossRows = client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID,
					DBTableID.SlayerTaskSublist.COL_SUBTABLE_ID, 0, taskId);
				Object[] bossTask = bossRows == null || bossRows.isEmpty() ? null
					: client.getDBTableField(bossRows.get(0), DBTableID.SlayerTaskSublist.COL_TASK, 0);
				if (bossTask == null || bossTask.length == 0)
				{
					return null;
				}
				taskRow = (Integer) bossTask[0];
			}
			else
			{
				List<Integer> rows = client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, taskId);
				if (rows == null || rows.isEmpty())
				{
					return null;
				}
				taskRow = rows.get(0);
			}
			Object[] name = client.getDBTableField(taskRow, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0);
			return name == null || name.length == 0 ? null : (String) name[0];
		}, null);
	}

	/** As upstream: the task's monsters (by category in {@link Rs2NpcManager}) minus the blacklist; {@code null} without a task. */
	public static List<String> getSlayerMonsters()
	{
		String taskName = getSlayerTask();
		if (taskName == null)
		{
			return null;
		}
		return Rs2NpcManager.getSlayerMonstersByCategory(taskName).stream()
			.filter(monster -> !blacklistedSlayerMonsters.contains(monster))
			.collect(Collectors.toList());
	}

	/**
	 * As upstream: the nearest location of the first task monster that has one (recording it in
	 * {@link #slayerTaskMonsterTarget}); {@code null} without a task or location.
	 */
	public static MonsterLocation getSlayerTaskLocation(int minClustering, boolean avoidWilderness)
	{
		List<String> names = getSlayerMonsters();
		if (names == null || names.isEmpty())
		{
			log.debug("No Slayer task found or no monsters available for the task.");
			return null;
		}
		for (String name : names)
		{
			MonsterLocation location = Rs2NpcManager.getClosestLocation(name, minClustering, avoidWilderness);
			if (location != null)
			{
				slayerTaskMonsterTarget = name;
				return location;
			}
		}
		slayerTaskMonsterTarget = names.get(0);
		return null;
	}

	public static MonsterLocation getSlayerTaskLocation(int minClustering)
	{
		return getSlayerTaskLocation(minClustering, true);
	}

	public static MonsterLocation getSlayerTaskLocation()
	{
		return getSlayerTaskLocation(3, true);
	}

	private static Task task()
	{
		String taskName = getSlayerTask();
		return taskName == null ? null : Task.getTask(taskName);
	}

	/** As upstream: the task's monster has a finishing item (rock hammer, ice coolers...). */
	public static boolean hasSlayerTaskWeakness()
	{
		Task task = task();
		return task != null && task.getWeaknessItem() != -1;
	}

	public static String getSlayerTaskWeaknessName()
	{
		Task task = task();
		if (task == null)
		{
			return null;
		}
		int itemId = task.getWeaknessItem();
		return MobileActions.read(() -> Microbot.getClient().getItemDefinition(itemId).getName(), "");
	}

	public static int getSlayerTaskWeakness()
	{
		Task task = task();
		return task == null ? -1 : task.getWeaknessItem();
	}

	public static int getSlayerTaskWeaknessThreshold()
	{
		Task task = task();
		return task == null ? -1 : task.getWeaknessThreshold();
	}

	/** As upstream: the protective item the task needs, "None" when none; {@code null} without a task. */
	public static String getSlayerTaskProtectiveEquipment()
	{
		String taskName = getSlayerTask();
		if (taskName == null)
		{
			return null;
		}
		String itemName = ProtectiveEquipment.getItemNameByCreature(taskName);
		return itemName == null ? "None" : itemName;
	}

	/**
	 * As upstream: the teleport items and fairy rings on a bank-aware route to
	 * {@code cachedMonsterLocation} whose items the player does not carry.
	 */
	public static List<Transport> prepareItemTransports(WorldPoint cachedMonsterLocation)
	{
		return WalkerPathReads.getTransportsForDestination(cachedMonsterLocation, true).stream()
			.filter(t -> t.getType() == TransportType.TELEPORTATION_ITEM || t.getType() == TransportType.FAIRY_RING)
			.filter(t -> !hasRequiredTeleportItem(t))
			.collect(Collectors.toList());
	}

	/** As upstream: for each requirement of {@code transports}, the first alternative the bank holds enough of. */
	public static List<Integer> getMissingItemIds(List<Transport> transports)
	{
		List<Integer> ids = new ArrayList<>();
		for (Transport transport : Objects.requireNonNull(transports, "transports"))
		{
			if (transport.getType() == TransportType.FAIRY_RING && transport.getItemRequirements().isEmpty())
			{
				// Upstream rewrote the catalogue entry to require a staff; read it instead.
				if (Rs2Bank.count(ItemID.DRAMEN_STAFF) >= 1)
				{
					ids.add(ItemID.DRAMEN_STAFF);
				}
				else if (Rs2Bank.count(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF) >= 1)
				{
					ids.add(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF);
				}
				continue;
			}
			transport.getItemRequirements().forEach(requirement -> requirement.getItemIds().stream()
				.filter(itemId -> Rs2Bank.count(itemId) >= requirement.getRequiredQuantity(itemId))
				.findFirst()
				.ifPresent(ids::add));
		}
		return ids;
	}

	private static boolean hasRequiredTeleportItem(Transport transport)
	{
		if (transport.getType() == TransportType.FAIRY_RING)
		{
			return Rs2Inventory.hasItem(ItemID.DRAMEN_STAFF) || Rs2Equipment.isWearing(ItemID.DRAMEN_STAFF)
				|| Rs2Inventory.hasItem(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)
				|| Rs2Equipment.isWearing(ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)
				|| Microbot.getVarbitValue(VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) == 1;
		}
		if (transport.getType() == TransportType.TELEPORTATION_ITEM)
		{
			return transport.getItemRequirements().stream().allMatch(requirement -> requirement.isSatisfiedBy(Rs2Slayer::carriedItemQuantity));
		}
		return false;
	}

	private static int carriedItemQuantity(int itemId)
	{
		int quantity = Rs2Inventory.itemQuantity(itemId);
		net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel equipped = Rs2Equipment.get(itemId);
		return equipped == null ? quantity : quantity + Math.max(1, equipped.getQuantity());
	}
}
