package net.runelite.client.plugins.microbot.util.widget;

import org.junit.Test;
import static org.junit.Assert.*;

public class Rs2WidgetTest
{
	@Test
	public void matchingStripsGameMarkupAndUsesCaseIndependentText()
	{
		assertTrue(Rs2Widget.matches("<col=ff0000>Arrow shafts</col>", "ARROW SHAFTS", true));
		assertTrue(Rs2Widget.matches("How many logs would you like to add?", "logs", false));
		assertFalse(Rs2Widget.matches("Oak logs", "logs", true));
		assertFalse(Rs2Widget.matches(null, "logs", false));
	}
}
