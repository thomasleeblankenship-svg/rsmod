/*
 * Adapted from chsami/Microbot (2.6.38),
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/events/WelcomeScreenEvent.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.events;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * As upstream: presses Play on the welcome screen shown after logging in. Mobile adaptation: the
 * button's own option is dispatched, so upstream's clearing of the news banner and update ribbon
 * (against a misplaced click opening a web page) is not needed.
 */
public class WelcomeScreenEvent extends PopupCloseEvent
{
	@Override
	Widget findButton()
	{
		Widget play = Microbot.getClient().getWidget(InterfaceID.WelcomeScreen.PLAY);
		return play == null || play.isHidden() ? null : play;
	}

	@Override
	public BlockingEventPriority priority()
	{
		return BlockingEventPriority.HIGHEST;
	}
}
