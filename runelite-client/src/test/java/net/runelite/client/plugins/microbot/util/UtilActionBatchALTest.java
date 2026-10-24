package net.runelite.client.plugins.microbot.util;

import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import org.junit.Test;
import static org.junit.Assert.*;

/** Util action batch AL: rune pouch refresh hooks are safe to call without a client. */
public class UtilActionBatchALTest
{
	@Test
	public void refreshHooksDoNothingAndReadsStayLive()
	{
		Rs2RunePouch.fullUpdate();
		Rs2RunePouch.onVarbitChanged(null);
		Rs2RunePouch.onWidgetLoaded(null);
		assertEquals(0, Rs2RunePouch.getQuantity(556));
	}
}
