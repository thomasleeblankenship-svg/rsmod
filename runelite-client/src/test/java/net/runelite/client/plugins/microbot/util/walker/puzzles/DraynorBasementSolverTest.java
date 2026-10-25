package net.runelite.client.plugins.microbot.util.walker.puzzles;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** The Draynor Manor basement lever puzzle: rooms and the first action of a shortest solution. */
public class DraynorBasementSolverTest
{
	private static final int ENTRANCE = 0;
	private static final int OIL = 6;

	@Test
	public void basementTilesAndRoomsAreRecognised()
	{
		assertTrue(DraynorBasementSolver.isBasementTarget(new WorldPoint(3093, 9755, 0)));
		assertFalse(DraynorBasementSolver.isBasementTarget(new WorldPoint(3093, 9755, 1)));
		assertFalse(DraynorBasementSolver.isBasementTarget(new WorldPoint(3092, 3361, 0)));
		assertEquals(ENTRANCE, DraynorBasementSolver.roomOf(new WorldPoint(3115, 9752, 0)));
		assertEquals(OIL, DraynorBasementSolver.roomOf(new WorldPoint(3093, 9755, 0)));
		assertEquals(-1, DraynorBasementSolver.roomOf(null));
	}

	@Test
	public void everyRoomIsReachableFromTheEntranceWithAllLeversUp()
	{
		for (int room = 1; room <= OIL; room++)
		{
			assertNotNull("room " + room, DraynorBasementSolver.firstAction(0, ENTRANCE, room));
		}
		assertNull("already there", DraynorBasementSolver.firstAction(0, ENTRANCE, ENTRANCE));
	}

	@Test
	public void theOilRoomStartsWithAnEntranceLever()
	{
		// All levers up: the entrance can only pull A or B (the only levers in its room) first.
		int[] first = DraynorBasementSolver.firstAction(0, ENTRANCE, OIL);
		assertEquals(0, first[0]);
		assertTrue(first[1] == 0 || first[1] == 1);
	}
}
