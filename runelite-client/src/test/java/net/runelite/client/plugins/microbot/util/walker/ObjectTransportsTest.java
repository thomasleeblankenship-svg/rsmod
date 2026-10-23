package net.runelite.client.plugins.microbot.util.walker;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Parsing and selection of the upstream transport table; pure data, no client. */
public class ObjectTransportsTest
{
	private static List<ObjectTransports.Step> parse(String... lines) throws Exception
	{
		String tsv = "# Origin\tDestination\tmenuOption menuTarget objectID\tSkills\tItem IDs\tCurrency\tQuests\tVarbits\tVarplayers\tisMembers\tDuration\tDisplay info\n"
			+ String.join("\n", lines) + "\n";
		return ObjectTransports.parse(new ByteArrayInputStream(tsv.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void keepsOnlyRequirementFreeFloorChangingObjectRows() throws Exception
	{
		List<ObjectTransports.Step> steps = parse(
			"3205 3228 0\t3206 3229 1\tClimb-up;Staircase;56230\t\t\t\t\t\t\t\t1\t",
			"3097 3107 0\t3098 3107 0\tOpen;Door;9398\t\t\t\t\t\t\t\t1\t",
			"3000 3000 0\t3000 3000 1\tClimb-up;Ladder;1\t30 Agility\t\t\t\t\t\t\t1\t",
			"3000 3000 0\t3000 3000 1\tClimb-up;Ladder;2\t\t\t\tDragon Slayer I\t\t\t\t1\t",
			"3000 3000 0\t3000 3000 1\tClimb-up;Ladder;3\t\t\t\t\t\t\tY\t1\t",
			"3000 3000 0\t3000 3000 1\tTeleport\t\t\t\t\t\t\t\t1\t",
			"bad\t3000 3000 1\tClimb-up;Ladder;4\t\t\t\t\t\t\t\t1\t");
		assertEquals(1, steps.size());
		ObjectTransports.Step step = steps.get(0);
		assertEquals(new WorldPoint(3205, 3228, 0), step.origin);
		assertEquals(new WorldPoint(3206, 3229, 1), step.arrival);
		assertEquals(56230, step.objectId);
		assertEquals("Climb-up", step.action);
		assertEquals("Staircase", step.target);
	}

	@Test
	public void packagedTableContainsLumbridgeCastleStairsAndThousandsOfOthers()
	{
		List<ObjectTransports.Step> all = ObjectTransports.all();
		assertTrue(all.size() > 2000);
		assertTrue(all.stream().anyMatch(s -> s.origin.equals(new WorldPoint(3206, 3229, 2))
			&& s.arrival.getPlane() == 1 && "Climb-down".equals(s.action)));
	}

	@Test
	public void lumbridgeBankDescentPrefersNearbySingleFloorClimbs()
	{
		WorldPoint bank = new WorldPoint(3209, 3220, 2);
		WorldPoint ground = new WorldPoint(3218, 3225, 0);
		List<ObjectTransports.Step> candidates = ObjectTransports.candidates(bank, ground);
		assertFalse(candidates.isEmpty());
		ObjectTransports.Step best = candidates.get(0);
		assertEquals(1, best.arrival.getPlane());
		assertEquals("Climb-down", best.action);
		assertTrue(best.origin.distanceTo(bank) <= 12);
		assertTrue(ObjectTransports.verticalChainExists(bank, ground));
	}

	@Test
	public void candidatesNeverMoveAwayFromTheGoalFloorOrReachTooFar() throws Exception
	{
		List<ObjectTransports.Step> steps = parse(
			"3200 3200 1\t3200 3200 2\tClimb-up;Staircase;1\t\t\t\t\t\t\t\t1\t",
			"3200 3200 1\t3200 3200 0\tClimb-down;Staircase;1\t\t\t\t\t\t\t\t1\t",
			"3400 3400 1\t3400 3400 0\tClimb-down;Ladder;2\t\t\t\t\t\t\t\t1\t");
		WorldPoint start = new WorldPoint(3201, 3201, 1);
		List<ObjectTransports.Step> down = ObjectTransports.candidates(steps, start, new WorldPoint(3205, 3205, 0));
		assertEquals(1, down.size());
		assertEquals("Climb-down", down.get(0).action);
		assertEquals(3200, down.get(0).origin.getX());
	}

	@Test
	public void excludedStepsAreSkippedSoTheNextOneIsTried() throws Exception
	{
		List<ObjectTransports.Step> steps = parse(
			"3200 3200 1\t3200 3200 0\tBottom-floor;Staircase;1\t\t\t\t\t\t\t\t1\t",
			"3202 3202 1\t3202 3202 0\tClimb-down;Staircase;1\t\t\t\t\t\t\t\t1\t");
		WorldPoint start = new WorldPoint(3200, 3200, 1);
		WorldPoint goal = new WorldPoint(3200, 3200, 0);
		List<ObjectTransports.Step> first = ObjectTransports.candidates(steps, start, goal);
		assertEquals(2, first.size());
		List<ObjectTransports.Step> rest = ObjectTransports.candidates(steps, start, goal,
			Collections.singleton(first.get(0)));
		assertEquals(1, rest.size());
		assertTrue(rest.get(0) != first.get(0));
	}

	@Test
	public void matchesRequireTheExactActionAndTheIdOrName()
	{
		ObjectTransports.Step step = new ObjectTransports.Step(new WorldPoint(1, 1, 1), new WorldPoint(1, 1, 0),
			16672, "Climb-down", "Staircase");
		assertTrue(step.matches(16672, "Whatever", new String[]{"Climb-up", "Climb-down"}));
		assertTrue(step.matches(56231, "Staircase", new String[]{"Climb-down"}));
		assertFalse(step.matches(16672, "Staircase", new String[]{"Climb-up"}));
		assertFalse(step.matches(999, "Ladder", new String[]{"Climb-down"}));
		assertFalse(step.matches(16672, "Staircase", null));
	}

	@Test
	public void noChainAcrossOpenSea()
	{
		assertFalse(ObjectTransports.verticalChainExists(new WorldPoint(2000, 2000, 0), new WorldPoint(2000, 2000, 2)));
	}
}
