package net.runelite.client.plugins.microbot.dabrutus;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Set;
import org.junit.Test;

public class BrutusDodgeTest
{
	/** Open field. */
	private static final BrutusDodge.Walls OPEN = (x, y, dx, dy) -> true;

	/** Brutus on 10..12 x 10..12. */
	private static final int MIN = 10;

	@Test
	public void facingFollowsTheGameOrientation()
	{
		assertArrayEquals(new int[]{0, -1}, BrutusDodge.facing(0));
		assertArrayEquals(new int[]{-1, 0}, BrutusDodge.facing(512));
		assertArrayEquals(new int[]{0, 1}, BrutusDodge.facing(1024));
		assertArrayEquals(new int[]{1, 0}, BrutusDodge.facing(1536));
		assertArrayEquals(new int[]{1, -1}, BrutusDodge.facing(1792));
		assertArrayEquals(new int[]{0, -1}, BrutusDodge.facing(2047));
	}

	@Test
	public void chargeAtAPlayerSouthIsDodgedTwoTilesSideways()
	{
		int px = 11, py = 9;
		int[] dir = BrutusDodge.toward(MIN, MIN, px, py);
		assertArrayEquals(new int[]{0, -1}, dir);
		Set<Long> danger = BrutusDodge.chargeDanger(MIN, MIN, dir);
		assertTrue(danger.contains(BrutusDodge.key(11, 9)));
		assertTrue(danger.contains(BrutusDodge.key(10, 5)));
		int[] to = BrutusDodge.pick(px, py, MIN, MIN, danger, 1, BrutusDodge.MAX_CHARGE_STEPS, true, OPEN);
		assertNotNull(to);
		assertFalse(danger.contains(BrutusDodge.key(to[0], to[1])));
		assertEquals(2, Math.max(Math.abs(to[0] - px), Math.abs(to[1] - py)));
		// Out of the 3-wide lane.
		assertTrue(to[0] < 10 || to[0] > 12);
	}

	@Test
	public void diagonalChargeLaneIsSwept()
	{
		Set<Long> danger = BrutusDodge.chargeDanger(MIN, MIN, new int[]{1, 1});
		assertTrue(danger.contains(BrutusDodge.key(17, 17)));
		assertFalse(danger.contains(BrutusDodge.key(10, 17)));
	}

	@Test
	public void slamOnAnEdgeStepsBackOrToTheSide()
	{
		int px = 11, py = 9;
		Set<Long> danger = BrutusDodge.slamDanger(MIN, MIN, px, py);
		assertTrue(danger.contains(BrutusDodge.key(9, 9)));
		assertTrue(danger.contains(BrutusDodge.key(13, 8)));
		assertFalse(danger.contains(BrutusDodge.key(9, 11)));
		int[] to = BrutusDodge.pick(px, py, MIN, MIN, danger, 2, BrutusDodge.MAX_SLAM_STEPS, false, OPEN);
		assertNotNull(to);
		assertFalse(danger.contains(BrutusDodge.key(to[0], to[1])));
		assertTrue(BrutusDodge.ring(MIN, MIN, to[0], to[1]) >= 2);
		assertTrue(Math.max(Math.abs(to[0] - px), Math.abs(to[1] - py)) <= 3);
	}

	@Test
	public void slamOnACornerCoversTheL()
	{
		Set<Long> danger = BrutusDodge.slamDanger(MIN, MIN, 9, 9);
		assertTrue(danger.contains(BrutusDodge.key(9, 11)));
		assertTrue(danger.contains(BrutusDodge.key(11, 9)));
		assertFalse(danger.contains(BrutusDodge.key(13, 13)));
	}

	@Test
	public void alreadySafeStays()
	{
		Set<Long> danger = BrutusDodge.chargeDanger(MIN, MIN, new int[]{0, -1});
		assertArrayEquals(new int[]{14, 11}, BrutusDodge.pick(14, 11, MIN, MIN, danger, 1, 4, true, OPEN));
	}

	@Test
	public void fenceIsRespected()
	{
		// A fence along x = 8.5: no step between x 8 and 9, and nothing east of x 13. The lane covers 10..12, so the only
		// way out within reach is x = 9 (west, before the fence) or x = 13.
		BrutusDodge.Walls fence = (x, y, dx, dy) ->
			!(x == 9 && dx < 0) && !(x == 8 && dx > 0) && !(x == 13 && dx > 0) && !(x == 14 && dx < 0);
		Set<Long> danger = BrutusDodge.chargeDanger(MIN, MIN, new int[]{0, -1});
		int[] to = BrutusDodge.pick(11, 9, MIN, MIN, danger, 1, 4, true, fence);
		assertNotNull(to);
		assertTrue(to[0] == 9 || to[0] == 13);
	}

	@Test
	public void boxedInHasNoDodge()
	{
		BrutusDodge.Walls none = (x, y, dx, dy) -> false;
		Set<Long> danger = BrutusDodge.chargeDanger(MIN, MIN, new int[]{0, -1});
		assertNull(BrutusDodge.pick(11, 9, MIN, MIN, danger, 1, 4, true, none));
	}

	@Test
	public void attackableIsAnEdgeNotACorner()
	{
		assertTrue(BrutusDodge.attackable(MIN, MIN, 11, 9));
		assertTrue(BrutusDodge.attackable(MIN, MIN, 13, 12));
		assertFalse(BrutusDodge.attackable(MIN, MIN, 9, 9));
		assertFalse(BrutusDodge.attackable(MIN, MIN, 11, 8));
	}
}
