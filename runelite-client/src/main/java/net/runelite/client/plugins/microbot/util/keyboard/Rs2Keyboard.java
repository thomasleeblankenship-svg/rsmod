package net.runelite.client.plugins.microbot.util.keyboard;

import net.runelite.client.plugins.microbot.mobile.MobileActions;

/** Script-owned key input; manual-key ingress must never be used by scripts. */
public final class Rs2Keyboard
{
	private Rs2Keyboard() { }
	public static boolean keyPress(int code) { return MobileActions.key(code); }
}
