package net.runelite.client.plugins.microbot.util.keyboard;

import java.awt.event.KeyEvent;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;

/**
 * Script-owned key input; manual-key ingress must never be used by scripts.
 * <p>
 * Upstream names. Mobile: each key is one script action, so at most one key per game tick, and
 * only while the script's input session is bound. A held key ({@code keyHold}) stays down until
 * {@code keyRelease}, and is released by itself when the script stops or the player touches the
 * screen, so a script never leaves a key down.
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

	/** As upstream: holds {@code key} down until {@link #keyRelease(int)} (camera arrows, Space). */
	public static void keyHold(int key)
	{
		if (Global.sleepUntil(() -> MobileActions.keyHold(key), KEY_TIMEOUT_MILLIS))
		{
			HELD_KEYS.add(key);
		}
	}

	/** As upstream: releases a key this script holds; nothing when it holds none. */
	public static void keyRelease(int key)
	{
		HELD_KEYS.remove(key);
		MobileActions.keyRelease(key);
	}

	/** As upstream: holds Shift (for example, for shift-click dropping) until {@link #releaseShift()}. */
	public static void holdShift()
	{
		keyHold(KeyEvent.VK_SHIFT);
	}

	/** As upstream: releases a held Shift. */
	public static void releaseShift()
	{
		keyRelease(KeyEvent.VK_SHIFT);
	}

	/** As upstream: releases every key held through {@link #keyHold(int)}. Safe to call repeatedly. */
	public static void releaseHeldKeys()
	{
		for (Integer key : new java.util.ArrayList<>(HELD_KEYS))
		{
			if (HELD_KEYS.remove(key))
			{
				MobileActions.keyRelease(key);
			}
		}
	}

	/** Keys this script holds through {@link #keyHold(int)} (the bridge also releases them when the script stops). */
	private static final java.util.Set<Integer> HELD_KEYS = java.util.concurrent.ConcurrentHashMap.newKeySet();

	public static boolean enter()
	{
		return Global.sleepUntil(() -> MobileActions.key(KeyEvent.VK_ENTER), KEY_TIMEOUT_MILLIS);
	}
}
