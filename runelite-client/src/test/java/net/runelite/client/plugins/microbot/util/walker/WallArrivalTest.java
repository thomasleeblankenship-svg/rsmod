package net.runelite.client.plugins.microbot.util.walker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.runelite.api.CollisionDataFlag;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

/**
 * The walker's arrival rule on mobile: within the radius in a straight line is not "arrived" when the walk
 * there goes round a wall (user report: the bank walk stopped behind Draynor bank's south wall).
 */
public class WallArrivalTest
{
	private static WorldPoint point(int x, int y)
	{
		return new WorldPoint(x, y, 0);
	}

	private static MobileRoutePlanner.Scene scene(int[][] flags, WorldPoint player)
	{
		return new MobileRoutePlanner.Scene(0, 0, 0, flags, player, false);
	}

	/** A 30x30 area with a solid wall along y = 10 from x = 0 to x = 24 (the gap is at x 25-29). */
	private static int[][] wallAcross()
	{
		int[][] flags = new int[30][30];
		for (int x = 0; x <= 24; x++)
		{
			flags[x][10] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		}
		return flags;
	}

	@Test
	public void goalBehindAWallIsNotArrived()
	{
		WorldPoint player = point(10, 7);
		WorldPoint goal = point(10, 13);
		assertTrue("straight-line within a 6-tile radius", player.distanceTo(goal) <= 6);
		assertTrue(Rs2Walker.walledOff(scene(wallAcross(), player), player, goal));
	}

	@Test
	public void openGroundIsArrived()
	{
		WorldPoint player = point(10, 7);
		assertFalse(Rs2Walker.walledOff(scene(new int[30][30], player), player, point(10, 13)));
	}

	@Test
	public void aShortWalkRoundAnObstacleIsNotAWall()
	{
		int[][] flags = new int[30][30];
		flags[10][10] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		flags[11][10] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
		WorldPoint player = point(10, 7);
		assertFalse(Rs2Walker.walledOff(scene(flags, player), player, point(10, 13)));
	}

	@Test
	public void detourRuleMatchesTheRealBanks()
	{
		assertTrue("Draynor south", Rs2Walker.isWallDetour(20, 7));
		assertTrue("Draynor east", Rs2Walker.isWallDetour(12, 5));
		assertFalse("Lumbridge top floor booths", Rs2Walker.isWallDetour(10, 6));
		assertFalse("Draynor entrance", Rs2Walker.isWallDetour(4, 3));
	}
}
