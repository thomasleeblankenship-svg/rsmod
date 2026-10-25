package net.runelite.client.plugins.microbot.api;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.boat.data.Heading;
import net.runelite.client.plugins.microbot.api.boat.models.Rs2BoatModel;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** api batch 3: the boat API's heading maths and its behaviour without a runtime. */
public class ApiBoatTest
{
	@Test
	public void directionMapsToTheSixteenHeadings()
	{
		Rs2BoatModel boat = mock(Rs2BoatModel.class, CALLS_REAL_METHODS);
		doReturn(new WorldPoint(3200, 3200, 0)).when(boat).getPlayerBoatLocation();
		assertEquals(Heading.SOUTH.getValue(), boat.getDirection(new WorldPoint(3200, 3200, 0)));
		int north = boat.getDirection(new WorldPoint(3200, 3300, 0));
		int south = boat.getDirection(new WorldPoint(3200, 3100, 0));
		assertEquals("north and south are opposite", 8, Math.floorMod(north - south, 16));
		for (int value = 0; value < 16; value++)
		{
			assertEquals(value, Heading.getHeading(value).getValue());
		}
	}

	@Test
	public void noBoatWithoutARuntime()
	{
		assertNull(Microbot.getRs2BoatCache());
		Rs2BoatModel boat = new Rs2BoatModel(null);
		assertFalse(boat.isOnBoat());
		assertFalse(boat.trimSails());
		assertFalse(boat.openCargo());
		assertFalse(boat.navigate());
	}
}
