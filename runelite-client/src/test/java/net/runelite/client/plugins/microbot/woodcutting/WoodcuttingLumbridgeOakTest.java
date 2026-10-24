package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTree;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTreeLocations;
import org.junit.Test;
import static org.junit.Assert.*;

/** The Lumbridge oak location targets the oak pair north of the castle, not the lone oak. */
public class WoodcuttingLumbridgeOakTest
{
	private static final WorldPoint PAIR_NORTH = new WorldPoint(3203, 3246, 0);
	private static final WorldPoint PAIR_SOUTH = new WorldPoint(3204, 3239, 0);
	private static final WorldPoint LONE_OAK = new WorldPoint(3188, 3247, 0);

	@Test
	public void lumbridgeOaksStartBetweenThePair()
	{
		ResourceLocationOption lumbridge = WoodcuttingTreeLocations.getLocationsForTree(WoodcuttingTree.OAK).stream()
			.filter(location -> "Lumbridge Oak Trees".equals(location.getName()))
			.findFirst().orElseThrow();
		WorldPoint start = lumbridge.getWorldPoint();
		assertEquals(2, lumbridge.getNumberOfResources());
		assertTrue(start.distanceTo(PAIR_NORTH) <= 4);
		assertTrue(start.distanceTo(PAIR_SOUTH) <= 4);
		assertTrue("the lone oak must be farther than both of the pair",
			start.distanceTo(LONE_OAK) > Math.max(start.distanceTo(PAIR_NORTH), start.distanceTo(PAIR_SOUTH)));
	}
}
