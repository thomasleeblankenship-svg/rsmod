package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

/** C2-1: what a POH step tells the walker's POH executor (agreed with Claude 1 in COORDINATION.md). */
public class HouseTransportStepTest
{
	private static final WorldPoint HOUSE = new WorldPoint(1, 1, 0);
	private static final WorldPoint VARROCK = new WorldPoint(3213, 3424, 0);

	@Test
	public void aWayOutNamesItsDeviceOptionAndObject()
	{
		HouseTransport portal = new HouseTransport(HOUSE, VARROCK, "PoHPortal -> Varrock", PohDevice.PORTAL,
			"Enter", "Enter", "Varrock Portal", 13615, 4);
		PathResult.TransportStep step = new PathResult.TransportStep(portal);
		assertEquals(TransportType.POH, step.getType());
		assertEquals(PohDevice.PORTAL, step.getPohDevice());
		assertEquals("Enter", step.getPohOption());
		assertEquals("Enter", step.getAction());
		assertEquals("Varrock Portal", step.getName());
		assertEquals(13615, step.getObjectId());
		assertEquals(HOUSE, step.getOrigin());
		assertEquals(VARROCK, step.getDestination());
		assertFalse(step.isIntoHouse());

		PathResult.TransportStep nexus = new PathResult.TransportStep(new HouseTransport(HOUSE, VARROCK,
			"NexusTeleport -> Varrock", PohDevice.NEXUS, "Varrock", "Teleport Menu", "Portal Nexus", 33408, 6));
		assertEquals(PohDevice.NEXUS, nexus.getPohDevice());
		assertEquals("the destination name, matched by contains", "Varrock", nexus.getPohOption());
		assertTrue("every POH step names its device object", nexus.getObjectId() > 0);
	}

	@Test
	public void aWayInLandsInTheHouseWithoutADevice()
	{
		PathResult.TransportStep spell = new PathResult.TransportStep(new HouseTransport(null, HOUSE,
			"Teleport to House", TransportType.TELEPORTATION_SPELL, null, null, 0, 19));
		assertTrue(spell.isIntoHouse());
		assertEquals(TransportType.TELEPORTATION_SPELL, spell.getType());
		assertNull(spell.getPohDevice());
		assertNull(spell.getPohOption());
		assertEquals(HOUSE, spell.getDestination());

		PathResult.TransportStep plain = new PathResult.TransportStep(
			new Transport(VARROCK, HOUSE, "stairs", TransportType.TRANSPORT, false, "Climb-up", "Staircase", 11));
		assertFalse("a catalogue transport is never into the house", plain.isIntoHouse());
		assertNull(plain.getPohDevice());
	}
}
