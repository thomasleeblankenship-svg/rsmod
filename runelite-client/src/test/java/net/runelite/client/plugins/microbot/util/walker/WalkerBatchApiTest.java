package net.runelite.client.plugins.microbot.util.walker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/** The 13-task walker batch's own logic in Rs2Walker (upstream 2.6.38): isNear over a route, walkWithStateTry guards. */
public class WalkerBatchApiTest
{
	private static List<WorldPoint> line(int length)
	{
		List<WorldPoint> path = new ArrayList<>();
		for (int i = 0; i < length; i++)
		{
			path.add(new WorldPoint(3200 + i, 3200, 0));
		}
		return path;
	}

	@Test
	public void nearOnlyOnTheLastTenRouteTiles()
	{
		List<WorldPoint> path = line(40);
		assertTrue(Rs2Walker.isNear(path, new WorldPoint(3238, 3200, 0)));
		// The first route tile within 2 decides: from (3233, 3201) that is tile 31 of 40, inside the last 10.
		assertTrue(Rs2Walker.isNear(path, new WorldPoint(3233, 3201, 0)));
		assertFalse("first match is tile 29, before the last 10", Rs2Walker.isNear(path, new WorldPoint(3231, 3201, 0)));
		assertFalse(Rs2Walker.isNear(path, new WorldPoint(3210, 3200, 0)));
		assertFalse("other plane", Rs2Walker.isNear(path, new WorldPoint(3238, 3200, 1)));
		assertFalse("no route", Rs2Walker.isNear(List.of(), new WorldPoint(3238, 3200, 0)));
		assertTrue("a short route is near anywhere on it", Rs2Walker.isNear(line(5), new WorldPoint(3200, 3200, 0)));
	}

	@Test
	public void walkWithStateTryRejectsBadInput()
	{
		assertEquals(WalkerState.EXIT, Rs2Walker.walkWithStateTry(null, 1, 0));
		try
		{
			Rs2Walker.walkWithStateTry(new WorldPoint(3200, 3200, 0), 1, -1);
			throw new AssertionError("negative wait accepted");
		}
		catch (IllegalArgumentException expected)
		{
			assertTrue(expected.getMessage().contains("lockWaitMs"));
		}
	}

	@Test
	public void noRunningWalkMeansNotNearAndOnPath()
	{
		assertFalse(Rs2Walker.isNear());
		assertTrue(Rs2Walker.isNearPath());
	}
}
