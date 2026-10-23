package net.runelite.client.plugins.microbot.util.walker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * Floor-changing object transports (staircases, ladders, trapdoors) usable without requirements.
 * <p>
 * Source: {@code transports.tsv}, copied unchanged from chsami/Microbot
 * {@code shortestpath/transports.tsv} at {@code e01e0d77d79b354472a5737b3aaab2449eb034fc}.
 * Only rows whose menu column is {@code option;target;objectId} and whose skill, item, currency,
 * quest, varbit, varplayer and members columns are all empty are kept, and only those whose
 * origin and destination planes differ. Same-plane transports (doors, dungeon entrances that
 * jump coordinates) and anything with a requirement are not used.
 */
final class ObjectTransports
{
	/** Transports whose origin is farther than this from the player are not considered for a leg. */
	static final int MAX_APPROACH_TILES = 64;
	private static final int MAX_CANDIDATES = 8;

	static final class Step
	{
		final WorldPoint origin;
		final WorldPoint arrival;
		final int objectId;
		final String action;
		final String target;
		/** How close to {@link #arrival} counts as arrived (staircases land loosely, doors do not). */
		final int arrivalRadius;
		/** An NPC or vehicle ride (boat, ship, travel NPC) rather than a single object click. */
		final boolean travel;
		/** The destination as the catalogue names it, for picking a dialogue option; may be empty. */
		final String destinationText;
		/** A teleport item (jewellery, tablets): {@link #destinationText} is "Item name: Destination". */
		final boolean item;
		/** For a teleport item, every id (each charge or variant) that can be used; may be empty. */
		final Set<Integer> itemIds;
		/** A teleport spell: {@link #destinationText} is the spell's name. */
		final boolean spell;
		/** A fairy ring: {@link #destinationText} is the three-letter code, {@link #origin} the ring. */
		final boolean fairyRing;

		Step(WorldPoint origin, WorldPoint arrival, int objectId, String action, String target)
		{
			this(origin, arrival, objectId, action, target, 4);
		}

		Step(WorldPoint origin, WorldPoint arrival, int objectId, String action, String target, int arrivalRadius)
		{
			this(origin, arrival, objectId, action, target, arrivalRadius, false, "");
		}

		Step(WorldPoint origin, WorldPoint arrival, int objectId, String action, String target, int arrivalRadius,
			boolean travel, String destinationText)
		{
			this(origin, arrival, objectId, action, target, arrivalRadius, travel, destinationText, false);
		}

		Step(WorldPoint origin, WorldPoint arrival, int objectId, String action, String target, int arrivalRadius,
			boolean travel, String destinationText, boolean item)
		{
			this(origin, arrival, objectId, action, target, arrivalRadius, travel, destinationText, item, Set.of());
		}

		Step(WorldPoint origin, WorldPoint arrival, int objectId, String action, String target, int arrivalRadius,
			boolean travel, String destinationText, boolean item, Set<Integer> itemIds)
		{
			this(origin, arrival, objectId, action, target, arrivalRadius, travel, destinationText, item, itemIds, false);
		}

		Step(WorldPoint origin, WorldPoint arrival, int objectId, String action, String target, int arrivalRadius,
			boolean travel, String destinationText, boolean item, Set<Integer> itemIds, boolean spell)
		{
			this(origin, arrival, objectId, action, target, arrivalRadius, travel, destinationText, item, itemIds, spell, false);
		}

		Step(WorldPoint origin, WorldPoint arrival, int objectId, String action, String target, int arrivalRadius,
			boolean travel, String destinationText, boolean item, Set<Integer> itemIds, boolean spell, boolean fairyRing)
		{
			this.fairyRing = fairyRing;
			this.spell = spell;
			this.item = item;
			this.itemIds = itemIds == null ? Set.of() : Set.copyOf(itemIds);
			this.origin = origin;
			this.arrival = arrival;
			this.objectId = objectId;
			this.action = action;
			this.target = target == null ? "" : target;
			this.arrivalRadius = arrivalRadius;
			this.travel = travel;
			this.destinationText = destinationText == null ? "" : destinationText;
		}

		/**
		 * A live object near the origin is this transport when it exposes the exact action and
		 * either has the recorded ID or the recorded name, so updated or legacy IDs of the same
		 * staircase still match. The action check keeps an up-staircase from matching a down row.
		 */
		boolean matches(int id, String name, String[] actions)
		{
			if (actions == null || Arrays.stream(actions).noneMatch(action::equalsIgnoreCase))
			{
				return false;
			}
			return id == objectId || (!target.isEmpty() && target.equalsIgnoreCase(name));
		}

		boolean observedArrival(WorldPoint player)
		{
			return player != null && player.getPlane() == arrival.getPlane() && player.distanceTo(arrival) <= arrivalRadius;
		}

		@Override
		public String toString()
		{
			return action + " " + target + " " + origin + " -> " + arrival;
		}
	}

	private static final class Holder
	{
		static final List<Step> ALL = loadPackaged();
	}

	private ObjectTransports()
	{
	}

	static List<Step> all()
	{
		return Holder.ALL;
	}

	private static List<Step> loadPackaged()
	{
		InputStream resource = ObjectTransports.class.getResourceAsStream("transports.tsv");
		if (resource == null)
		{
			throw new IllegalStateException("Packaged transports.tsv is missing");
		}
		try (InputStream in = resource)
		{
			return parse(in);
		}
		catch (IOException exception)
		{
			throw new IllegalStateException("Unable to load transports.tsv", exception);
		}
	}

	/** Parses upstream's TSV layout, keeping only requirement-free floor-changing object rows. */
	static List<Step> parse(InputStream in) throws IOException
	{
		List<Step> steps = new ArrayList<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] columns = line.split("\t", -1);
			if (columns.length < 3)
			{
				continue;
			}
			boolean requirementFree = true;
			for (int column = 3; column <= 9 && column < columns.length; column++)
			{
				if (!columns[column].trim().isEmpty())
				{
					requirementFree = false;
					break;
				}
			}
			String[] menu = columns[2].split(";");
			WorldPoint origin = point(columns[0]);
			WorldPoint arrival = point(columns[1]);
			if (!requirementFree || menu.length != 3 || origin == null || arrival == null
				|| origin.getPlane() == arrival.getPlane())
			{
				continue;
			}
			int objectId;
			try
			{
				objectId = Integer.parseInt(menu[2].trim());
			}
			catch (NumberFormatException exception)
			{
				continue;
			}
			steps.add(new Step(origin, arrival, objectId, menu[0].trim(), menu[1].trim()));
		}
		return Collections.unmodifiableList(steps);
	}

	private static WorldPoint point(String column)
	{
		String[] parts = column.trim().split(" ");
		if (parts.length != 3)
		{
			return null;
		}
		try
		{
			return new WorldPoint(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		}
		catch (NumberFormatException exception)
		{
			return null;
		}
	}

	/**
	 * Transports on the player's plane that move toward the goal's plane without overshooting,
	 * within {@link #MAX_APPROACH_TILES}, best first: approach distance, then the landing's
	 * distance to the goal, then floors still to go.
	 */
	static List<Step> candidates(WorldPoint start, WorldPoint goal)
	{
		return candidates(all(), start, goal);
	}

	static List<Step> candidates(List<Step> steps, WorldPoint start, WorldPoint goal)
	{
		return candidates(steps, start, goal, Collections.emptySet());
	}

	/** As {@link #candidates(List, WorldPoint, WorldPoint)}, skipping steps already found unusable. */
	static List<Step> candidates(List<Step> steps, WorldPoint start, WorldPoint goal, Set<Step> excluded)
	{
		List<Step> found = new ArrayList<>();
		if (start == null || goal == null || start.getPlane() == goal.getPlane())
		{
			return found;
		}
		int remaining = Math.abs(goal.getPlane() - start.getPlane());
		int direction = Integer.signum(goal.getPlane() - start.getPlane());
		for (Step step : steps)
		{
			int moved = step.arrival.getPlane() - start.getPlane();
			if (excluded.contains(step) || step.origin.getPlane() != start.getPlane() || Integer.signum(moved) != direction
				|| Math.abs(moved) > remaining || distance2d(start, step.origin) > MAX_APPROACH_TILES)
			{
				continue;
			}
			found.add(step);
		}
		// Single-floor climbs first: multi-floor shortcuts ("Top-floor") are not on every variant.
		found.sort(Comparator.comparingInt((Step step) -> distance2d(start, step.origin)
			+ distance2d(step.arrival, goal) + 10 * Math.abs(goal.getPlane() - step.arrival.getPlane())
			+ (Math.abs(step.arrival.getPlane() - start.getPlane()) == 1 ? 0 : 30)));
		return found.size() > MAX_CANDIDATES ? new ArrayList<>(found.subList(0, MAX_CANDIDATES)) : found;
	}

	/**
	 * Structural check used by bank selection: a chain of transports leads from {@code origin}'s
	 * floor to {@code destination}'s floor nearby. Collision is validated later, when walking.
	 */
	static boolean verticalChainExists(WorldPoint origin, WorldPoint destination)
	{
		return verticalChainExists(all(), origin, destination);
	}

	static boolean verticalChainExists(List<Step> steps, WorldPoint origin, WorldPoint destination)
	{
		if (origin == null || destination == null)
		{
			return false;
		}
		WorldPoint current = origin;
		for (int guard = 0; guard < 4 && current.getPlane() != destination.getPlane(); guard++)
		{
			List<Step> next = candidates(steps, current, destination);
			if (next.isEmpty())
			{
				return false;
			}
			current = next.get(0).arrival;
		}
		// The chain must land near the destination, not just on its floor somewhere else.
		return current.getPlane() == destination.getPlane()
			&& distance2d(current, destination) <= MAX_APPROACH_TILES;
	}

	private static int distance2d(WorldPoint first, WorldPoint second)
	{
		return Math.max(Math.abs(first.getX() - second.getX()), Math.abs(first.getY() - second.getY()));
	}
}
