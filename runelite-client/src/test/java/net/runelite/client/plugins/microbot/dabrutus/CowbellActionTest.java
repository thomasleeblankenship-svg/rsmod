package net.runelite.client.plugins.microbot.dabrutus;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class CowbellActionTest
{
	@Test
	public void takesTheTeleportActionFromTheMenu()
	{
		assertEquals("Teleport", DaBrutusScript.pickAction(Arrays.asList(null, "Wear", "Ring", "Teleport", null), null));
		assertEquals("Rub", DaBrutusScript.pickAction(Arrays.asList("Wear", "Rub", "Ring"), null));
	}

	@Test
	public void findsATeleportInASubMenu()
	{
		String[][] subops = {null, {"Cow field", "Check"}};
		assertEquals("Cow field", DaBrutusScript.pickAction(Arrays.asList("Wear", "Rub"), subops));
	}

	@Test
	public void fallsBackToTeleport()
	{
		assertEquals("Teleport", DaBrutusScript.pickAction(Collections.emptyList(), null));
	}
}
