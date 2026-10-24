/*
 * Adapted from chsami/Microbot (2.6.38),
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/events/MembershipPromptEvent.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.events;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;

/** As upstream: closes the bond and membership prompts (redeem, bond, members-world hop). */
public class MembershipPromptEvent extends PopupCloseEvent
{
	static final int[][] PROMPTS = {
		{InterfaceID.F2PBondRedeem.UNIVERSE, InterfaceID.F2PBondRedeem.CLOSE},
		{InterfaceID.BondPrompt.UNIVERSE, InterfaceID.BondPrompt.CLOSE},
		{InterfaceID.MembershipBenefitsPromptHop.UNIVERSE, InterfaceID.MembershipBenefitsPromptHop.CLOSE_BUTTON},
	};

	@Override
	Widget findButton()
	{
		for (int[] prompt : PROMPTS)
		{
			Widget root = Microbot.getClient().getWidget(prompt[0]);
			Widget close = root == null || root.isHidden() ? null : Microbot.getClient().getWidget(prompt[1]);
			if (close != null && !close.isHidden())
			{
				return close;
			}
		}
		return null;
	}

	@Override
	public BlockingEventPriority priority()
	{
		return BlockingEventPriority.NORMAL;
	}
}
