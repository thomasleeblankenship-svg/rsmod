package net.runelite.client.plugins.microbot.shortestpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.runelite.api.coords.WorldPoint;

/**
 * Immutable result of one {@link MobilePathfinder} search: the walker's contract.
 * <p>
 * {@link #getTiles()} is the full tile path from the start. When the step from
 * {@code tiles[i]} to {@code tiles[i + 1]} uses a transport (stairs, a ladder, a door, a
 * shortcut), {@link #transportAt(int)} returns it for index {@code i}; ordinary walking steps
 * have none. The walker must observe arrival at {@code tiles[i + 1]} before continuing.
 */
public final class PathResult
{
	/** Why the search stopped and what the tiles mean. */
	public enum Status
	{
		/** The path ends at the goal. */
		FOUND,
		/** The goal is unreachable with the admitted transports; the path ends at the closest tile. */
		PARTIAL,
		/** No step can be taken from the start (no tiles beyond it). */
		UNREACHABLE,
		/** {@link MobilePathfinder#cancel()} or {@code ShortestPathPlugin.exit()} stopped the search. */
		CANCELLED,
		/** The calculation cutoff was reached; the path ends at the best tile found so far. */
		TIMED_OUT,
		/** The search threw, or the client thread could not run the requirement refresh. */
		FAILED
	}

	/** One transport step, copied from the catalogue so it cannot change under the walker. */
	public static final class TransportStep
	{
		private final TransportType type;
		private final WorldPoint origin;
		private final WorldPoint destination;
		private final int objectId;
		private final String action;
		private final String name;
		private final String displayInfo;
		private final List<Set<Integer>> itemRequirements;
		private final Set<Integer> itemIds;
		private final int[] skillLevels;
		private final boolean intoHouse;
		private final PohDevice pohDevice;
		private final String pohOption;
		private final Transport source;

		TransportStep(Transport transport)
		{
			this.source = transport;
			this.type = transport.getType();
			this.origin = transport.getOrigin();
			this.destination = transport.getDestination();
			this.objectId = transport.getObjectId();
			this.action = transport.getAction();
			this.name = transport.getName();
			this.displayInfo = transport.getDisplayInfo();
			List<Set<Integer>> requirements = new ArrayList<>();
			Set<Integer> ids = new TreeSet<>();
			if (transport.getItemRequirements() != null)
			{
				for (TransportItemRequirement requirement : transport.getItemRequirements())
				{
					Set<Integer> alternatives = new TreeSet<>(requirement.getItemIds());
					requirements.add(Collections.unmodifiableSet(alternatives));
					ids.addAll(alternatives);
				}
			}
			this.itemRequirements = Collections.unmodifiableList(requirements);
			this.itemIds = Collections.unmodifiableSet(ids);
			this.skillLevels = transport.getSkillLevels() == null ? new int[0] : transport.getSkillLevels().clone();
			HouseTransport house = transport instanceof HouseTransport ? (HouseTransport) transport : null;
			this.intoHouse = house != null && house.isIntoHouse();
			this.pohDevice = house == null ? null : house.getPohDevice();
			this.pohOption = house == null ? null : house.getPohOption();
		}

		/** The catalogue transport this step was copied from, for the planner's own reads. */
		Transport source()
		{
			return source;
		}

		/**
		 * Whether this step lands in the player-owned house (house teleport spell, tablet, cape or the
		 * outside portal): arrival is being in the house, and the landing tile is synthetic.
		 */
		public boolean isIntoHouse()
		{
			return intoHouse;
		}

		/**
		 * For a {@link TransportType#POH} step out of the house, the device it uses; {@code null}
		 * otherwise. Its {@link #getObjectId()} and {@link #getName()} are the device as detected, and
		 * its origin (the house tile) is never walked to.
		 */
		public PohDevice getPohDevice()
		{
			return pohDevice;
		}

		/**
		 * For a POH step, what the device shows for the destination: the destination name for the
		 * nexus and jewellery box, the code for the fairy ring, the "N: Name" entry for the spirit
		 * tree, and the exact menu option (also {@link #getAction()}) for portals and mounted items.
		 */
		public String getPohOption()
		{
			return pohOption;
		}

		/**
		 * The level this step requires in each skill, indexed by {@link net.runelite.api.Skill#ordinal()}
		 * (0 when none), as the catalogue states it. A canoe's Woodcutting level names the canoe that
		 * makes the trip (12 log, 27 dugout, 42 stable dugout, 57 waka). A copy on every call.
		 */
		public int[] getSkillLevels()
		{
			return skillLevels.clone();
		}

		/** {@link #getSkillLevels()} for one skill; 0 when the step needs no level in it. */
		public int getSkillLevel(net.runelite.api.Skill skill)
		{
			return skill.ordinal() < skillLevels.length ? skillLevels[skill.ordinal()] : 0;
		}

		/**
		 * Every item id that can satisfy one of this transport's item requirements (for a teleport
		 * item: each charged or variant id of that item). Empty when no item is needed.
		 */
		public Set<Integer> getItemIds()
		{
			return itemIds;
		}

		/**
		 * The item requirements as upstream's catalogue states them: all entries are needed, and each
		 * entry is satisfied by any one of its ids.
		 */
		public List<Set<Integer>> getItemRequirements()
		{
			return itemRequirements;
		}

		public TransportType getType()
		{
			return type;
		}

		/** Tile the player stands on (or next to) before using the transport. */
		public WorldPoint getOrigin()
		{
			return origin;
		}

		/** Tile the player should be on afterwards; the walker confirms arrival here. */
		public WorldPoint getDestination()
		{
			return destination;
		}

		/** Object to interact with, as in {@code transports.tsv}; {@code -1} or 0 when none. */
		public int getObjectId()
		{
			return objectId;
		}

		/** Menu action to use on the object, for example {@code "Climb-down"} or {@code "Open"}. */
		public String getAction()
		{
			return action;
		}

		/** Object name from the catalogue, for menu matching. */
		public String getName()
		{
			return name;
		}

		public String getDisplayInfo()
		{
			return displayInfo;
		}

		@Override
		public String toString()
		{
			return type + " " + action + " " + name + " (" + objectId + ") " + origin + " -> " + destination;
		}
	}

	private final Status status;
	private final WorldPoint start;
	private final WorldPoint goal;
	private final List<WorldPoint> tiles;
	private final Map<Integer, TransportStep> transports;

	PathResult(Status status, WorldPoint start, WorldPoint goal, List<WorldPoint> tiles,
		Map<Integer, TransportStep> transports)
	{
		this.status = Objects.requireNonNull(status, "status");
		this.start = start;
		this.goal = goal;
		this.tiles = tiles == null ? Collections.emptyList() : Collections.unmodifiableList(List.copyOf(tiles));
		this.transports = Collections.unmodifiableMap(new TreeMap<>(transports == null ? Map.of() : transports));
	}

	static PathResult failed(WorldPoint start, WorldPoint goal)
	{
		return new PathResult(Status.FAILED, start, goal, null, null);
	}

	public Status getStatus()
	{
		return status;
	}

	/** {@code true} only for {@link Status#FOUND}. */
	public boolean isFound()
	{
		return status == Status.FOUND;
	}

	public WorldPoint getStart()
	{
		return start;
	}

	public WorldPoint getGoal()
	{
		return goal;
	}

	/** Every tile of the path, starting at the start tile. */
	public List<WorldPoint> getTiles()
	{
		return tiles;
	}

	/** The transport used from {@code tiles[index]} to {@code tiles[index + 1]}, or {@code null}. */
	public TransportStep transportAt(int index)
	{
		return transports.get(index);
	}

	/** Transport steps keyed by tile index, in path order. */
	public Map<Integer, TransportStep> getTransports()
	{
		return transports;
	}

	@Override
	public String toString()
	{
		return "PathResult{" + status + ", " + start + " -> " + goal + ", tiles=" + tiles.size()
			+ ", transports=" + transports + '}';
	}
}
