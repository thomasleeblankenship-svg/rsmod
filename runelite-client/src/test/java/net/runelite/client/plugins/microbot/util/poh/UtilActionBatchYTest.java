package net.runelite.client.plugins.microbot.util.poh;

import net.runelite.client.plugins.microbot.util.equipment.JewelleryLocationEnum;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.poh.data.NexusPortal;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Util action batch Y: the portal nexus and jewellery box. */
public class UtilActionBatchYTest
{
	@Test
	public void wildernessNexusDestinationsAreRefused()
	{
		NexusPortal wild = java.util.Arrays.stream(NexusPortal.values())
			.filter(p -> p.getLocation() != null && Rs2Pvp.getWildernessLevelFrom(p.getLocation()) > 0)
			.findFirst().orElse(null);
		assertNotNull("the nexus lists Wilderness destinations", wild);
		try (MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class))
		{
			assertFalse(PohTeleports.usePortalNexus(wild));
			assertFalse(PohTeleports.interactWithPortalNexusWidget(wild));
			walker.verifyNoInteractions();
		}
	}

	@Test
	public void devicesNeedTheHouse()
	{
		try (MockedStatic<PohPresence> presence = mockStatic(PohPresence.class))
		{
			presence.when(PohPresence::isInHouse).thenReturn(false);
			assertFalse(PohTeleports.useJewelleryBox(JewelleryLocationEnum.values()[0]));
			assertFalse("no destination", PohTeleports.useJewelleryBox(null));
		}
	}
}
