/*
 * Shared base for upstream Microbot's popup-closing blocking events (BSD 2-Clause; see LICENSE).
 */
package net.runelite.client.plugins.microbot.util.events;

import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;

/**
 * A blocking event that closes one popup: {@link #validate()} while its button shows, and
 * {@link #execute()} presses it (one script action) and waits up to 10 s for it to go. The button
 * is found on the client thread by {@link #findButton()}, and only while a script holds input.
 */
abstract class PopupCloseEvent implements BlockingEvent
{
	private static final int CLOSE_TIMEOUT_MILLIS = 10_000;

	/** The button to press, read on the client thread; null when the popup is not showing. */
	abstract Widget findButton();

	/** The button's option to dispatch ("" for its first). */
	String option()
	{
		return "";
	}

	private int[] target()
	{
		return MobileActions.active() && Microbot.isLoggedIn() ? MobileActions.read(() ->
		{
			Widget button = findButton();
			return button == null ? null : new int[]{button.getId(), button.getIndex()};
		}, null) : null;
	}

	@Override
	public boolean validate()
	{
		return target() != null;
	}

	@Override
	public boolean execute()
	{
		int[] button = target();
		if (button == null)
		{
			return true;
		}
		return MobileActions.widget(button[0], button[1], option()) && Global.sleepUntil(() -> target() == null, CLOSE_TIMEOUT_MILLIS);
	}
}
