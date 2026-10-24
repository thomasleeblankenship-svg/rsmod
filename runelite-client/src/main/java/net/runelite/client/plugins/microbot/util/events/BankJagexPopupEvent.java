/*
 * Adapted from chsami/Microbot (2.6.38),
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/events/BankJagexPopupEvent.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.events;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/** As upstream: answers "Not now" to the bank's Jagex account popup. */
public class BankJagexPopupEvent extends PopupCloseEvent
{
	@Override
	Widget findButton()
	{
		Widget popup = Microbot.getClient().getWidget(InterfaceID.Popupoverlay.CONTAINER);
		return popup == null || popup.isHidden() ? null : Rs2Widget.searchChildren("Not now", popup, false);
	}

	@Override
	public BlockingEventPriority priority()
	{
		return BlockingEventPriority.NORMAL;
	}
}
