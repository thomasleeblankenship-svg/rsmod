package net.runelite.client.plugins.microbot.util.walker;

import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** Batch AO: the interaction-approach rules. */
public class UtilActionBatchAOTest
{
	@Test
	public void rangeIsTwelveTilesOnTheSameFloor()
	{
		WorldPoint here = new WorldPoint(3200, 3200, 0);
		assertTrue(Rs2InteractionApproach.withinRange(here, new WorldPoint(3212, 3200, 0)));
		assertFalse(Rs2InteractionApproach.withinRange(here, new WorldPoint(3213, 3200, 0)));
		assertFalse(Rs2InteractionApproach.withinRange(here, new WorldPoint(3201, 3200, 1)));
		assertFalse(Rs2InteractionApproach.withinRange(null, here));
		assertFalse(Rs2InteractionApproach.isReady((net.runelite.api.GameObject) null));
	}

	@Test
	public void aWallNeedsAReachableTileNextToIt()
	{
		WorldPoint wall = new WorldPoint(3200, 3200, 0);
		assertTrue(Rs2InteractionApproach.hasReachableWallApproach(wall, List.of(new WorldPoint(3201, 3201, 0))));
		assertFalse(Rs2InteractionApproach.hasReachableWallApproach(wall, List.of(new WorldPoint(3202, 3200, 0))));
		assertFalse(Rs2InteractionApproach.hasReachableWallApproach(wall, List.of(new WorldPoint(3200, 3201, 1))));
	}
}
