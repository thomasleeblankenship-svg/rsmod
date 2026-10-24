/*
 * Adapted from chsami/Microbot (2.6.38),
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/events/DeathEvent.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.events;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * As upstream: the first-death tutorial in Death's Domain. While it runs (death counter 1, inside
 * the domain), steps Death's dialogue: continue, then each option not yet struck through; once four
 * are done, leaves through the portal.
 */
public class DeathEvent implements BlockingEvent
{
	private static final int DEATH_COUNTER_VARP = 4517;
	private static final int DEATH_DOMAIN_REGION_ID = 12633;
	private static final int DEATHS_PORTAL = net.runelite.api.ObjectID.PORTAL_39549;

	@Override
	public boolean validate()
	{
		if (!MobileActions.active() || !Microbot.isLoggedIn() || Microbot.getVarbitPlayerValue(DEATH_COUNTER_VARP) != 1) return false;
		WorldPoint location = Rs2Player.getWorldLocation();
		return location != null && location.getRegionID() == DEATH_DOMAIN_REGION_ID;
	}

	@Override
	public boolean execute()
	{
		if (Rs2Player.isMoving()) return false;
		if (Rs2Dialogue.hasContinue())
		{
			Rs2Dialogue.clickContinue();
			return true;
		}
		if (Rs2Dialogue.hasSelectAnOption())
		{
			int[] options = optionState();
			if (options[0] >= 4)
			{
				Rs2GameObject.interact(DEATHS_PORTAL, "Use");
				return Global.sleepUntil(() ->
				{
					WorldPoint location = Rs2Player.getWorldLocation();
					return location != null && location.getRegionID() != DEATH_DOMAIN_REGION_ID;
				}, 10_000);
			}
			if (options[1] > 0)
			{
				return Rs2Dialogue.keyPressForDialogueOption(options[1]);
			}
		}
		return !validate();
	}

	/** {@code {struck-through options, number of the first not struck through (0 for none)}}. */
	private static int[] optionState()
	{
		return MobileActions.read(() ->
		{
			Widget menu = Microbot.getClient().getWidget(net.runelite.api.widgets.ComponentID.DIALOG_OPTION_OPTIONS);
			Widget[] rows = menu == null || menu.isHidden() ? null : menu.getDynamicChildren();
			int done = 0;
			int firstOpen = 0;
			int number = 0;
			for (int i = 1; rows != null && i < rows.length; i++)
			{
				String text = rows[i] == null || rows[i].isHidden() ? null : rows[i].getText();
				if (text == null || text.isBlank()) continue;
				number++;
				if (text.matches("<str>.*</str>")) done++;
				else if (firstOpen == 0) firstOpen = number;
			}
			return new int[]{done, firstOpen};
		}, new int[]{0, 0});
	}

	@Override
	public BlockingEventPriority priority()
	{
		return BlockingEventPriority.HIGH;
	}
}
