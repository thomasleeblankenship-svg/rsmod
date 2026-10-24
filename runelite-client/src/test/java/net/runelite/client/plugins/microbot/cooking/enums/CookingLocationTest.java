package net.runelite.client.plugins.microbot.cooking.enums;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import org.junit.Test;

/** The Auto Cooking mobile port's free-to-play split follows upstream's "Free-to-play" section of the enum. */
public class CookingLocationTest
{
	private static final EnumSet<CookingLocation> FREE_TO_PLAY = EnumSet.of(
		CookingLocation.FALADOR_PVP, CookingLocation.FALADOR, CookingLocation.DRAYNOR_VILLAGE,
		CookingLocation.AL_KHARID, CookingLocation.EDGEVILLE, CookingLocation.VARROCK_EAST,
		CookingLocation.VARROCK_WEST, CookingLocation.BARBARBIAN_VILLAGE, CookingLocation.PORT_SARIM,
		CookingLocation.RIMMINGTON, CookingLocation.LUMBRIDGE_TUTOR);

	@Test
	public void upstreamFreeToPlaySectionIsNotMembersOnly()
	{
		for (CookingLocation location : FREE_TO_PLAY)
		{
			assertFalse(location.name(), location.isMembersOnly());
		}
	}

	@Test
	public void everyOtherLocationIsMembersOnly()
	{
		for (CookingLocation location : EnumSet.complementOf(FREE_TO_PLAY))
		{
			assertTrue(location.name(), location.isMembersOnly());
		}
	}
}
