package net.runelite.client.plugins.microbot.util.keyboard;

import java.awt.event.KeyEvent;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;

/**
 * Script-owned key input; manual-key ingress must never be used by scripts.
 * <p>
 * Upstream names. Mobile: each key is one script action, so at most one key per game tick, and
 * only while the script's input session is bound. Holding keys ({@code keyHold}/{@code keyRelease})
 * is not ported.
 */
public final class Rs2Keyboard
{
	/** Longest wait for one key to be accepted, in milliseconds. */
	private static final int KEY_TIMEOUT_MILLIS = 2_000;

	private Rs2Keyboard() { }

	public static boolean keyPress(int code) { return MobileActions.key(code); }

	/** As upstream: types one character. */
	public static boolean keyPress(char character)
	{
		return Global.sleepUntil(() -> MobileActions.type(character), KEY_TIMEOUT_MILLIS);
	}

	/** As upstream: types {@code text}, one character per accepted key; false when a key is refused. */
	public static boolean typeString(String text)
	{
		if (text == null) { return false; }
		for (char character : text.toCharArray())
		{
			if (!keyPress(character)) { return false; }
		}
		return true;
	}

	public static boolean enter()
	{
		return Global.sleepUntil(() -> MobileActions.key(KeyEvent.VK_ENTER), KEY_TIMEOUT_MILLIS);
	}
}
