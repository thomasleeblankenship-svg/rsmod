/*
 * Adapted from chsami/Microbot (2.6.38),
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/events/BankTutorialEvent.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.events;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/** As upstream: closes the bank tutorial's information box. */
public class BankTutorialEvent extends PopupCloseEvent
{
	@Override
	Widget findButton()
	{
		Widget box = Microbot.getClient().getWidget(InterfaceID.Screenhighlight.INFORMATION_BOX);
		return box == null || box.isHidden() ? null : Rs2Widget.searchChildren("Close", box, false);
	}

	@Override
	public BlockingEventPriority priority()
	{
		return BlockingEventPriority.HIGH;
	}
}
