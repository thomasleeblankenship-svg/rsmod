package net.runelite.client.plugins.microbot.util.walker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.runelite.api.coords.WorldPoint;

/**
 * Explicit Lumbridge castle staircase edges from Microbot shortestpath/transports.tsv
 * at e01e0d77d79b354472a5737b3aaab2449eb034fc (north and south staircase rows).
 * Origins are interaction tiles, not guesses at the multi-tile object's anchor.
 */
final class LumbridgeStairs
{
	static final class Step
	{
		final WorldPoint origin;
		final WorldPoint arrival;
		final int objectId;
		final String action;

		Step(int x, int y, int plane, int arrivalX, int arrivalY, int arrivalPlane, int objectId, String action)
		{
			this.origin = new WorldPoint(x, y, plane);
			this.arrival = new WorldPoint(arrivalX, arrivalY, arrivalPlane);
			this.objectId = objectId;
			this.action = action;
		}

		/** Restrict fallback IDs to a nearby live staircase exposing the exact requested direction. */
		boolean matches(int id, String name, String[] actions)
		{
			if (actions == null || !java.util.Arrays.stream(actions).anyMatch(action::equalsIgnoreCase))
			{
				return false;
			}
			return id == objectId || id == 16671 || id == 16672 || id == 16673
				|| id == 56230 || id == 56231 || "Staircase".equalsIgnoreCase(name) || "Stairs".equalsIgnoreCase(name);
		}

		boolean observedArrival(WorldPoint player)
		{
			return player != null && player.getPlane() == arrival.getPlane() && player.distanceTo(arrival) <= 4;
		}
	}

	private static final Step[] STEPS = {
		new Step(3205, 3228, 0, 3206, 3229, 1, 56230, "Climb-up"),
		new Step(3206, 3229, 1, 3205, 3228, 0, 16672, "Climb-down"),
		new Step(3206, 3229, 1, 3206, 3229, 2, 16672, "Climb-up"),
		new Step(3206, 3229, 2, 3205, 3228, 1, 56231, "Climb-down"),
		new Step(3206, 3208, 0, 3205, 3209, 1, 56230, "Climb-up"),
		new Step(3205, 3209, 1, 3206, 3208, 0, 16672, "Climb-down"),
		new Step(3205, 3209, 1, 3205, 3209, 2, 16672, "Climb-up"),
		new Step(3205, 3209, 2, 3206, 3208, 1, 56231, "Climb-down")
	};

	private LumbridgeStairs()
	{
	}

	static boolean supported(WorldPoint start, WorldPoint goal)
	{
		if (start == null || goal == null || start.getPlane() == goal.getPlane()
			|| start.getPlane() < 0 || start.getPlane() > 2 || goal.getPlane() < 0 || goal.getPlane() > 2)
		{
			return false;
		}
		return (start.getPlane() == 0 || insideCastle(start))
			&& (goal.getPlane() == 0 || insideCastle(goal));
	}

	static List<Step> candidates(WorldPoint start, WorldPoint goal)
	{
		List<Step> candidates = new ArrayList<>();
		if (!supported(start, goal))
		{
			return candidates;
		}
		int nextPlane = start.getPlane() + Integer.signum(goal.getPlane() - start.getPlane());
		for (Step step : STEPS)
		{
			if (step.origin.getPlane() == start.getPlane() && step.arrival.getPlane() == nextPlane)
			{
				candidates.add(step);
			}
		}
		candidates.sort(Comparator.comparingInt(step -> distance2d(start, step.origin)
			+ distance2d(step.arrival, goal)));
		return candidates;
	}

	private static boolean insideCastle(WorldPoint point)
	{
		return point.getX() >= 3195 && point.getX() <= 3216 && point.getY() >= 3200 && point.getY() <= 3238;
	}

	private static int distance2d(WorldPoint first, WorldPoint second)
	{
		return Math.max(Math.abs(first.getX() - second.getX()), Math.abs(first.getY() - second.getY()));
	}
}
