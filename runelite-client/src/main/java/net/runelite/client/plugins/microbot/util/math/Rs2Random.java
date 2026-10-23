package net.runelite.client.plugins.microbot.util.math;

import java.util.concurrent.ThreadLocalRandom;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.Microbot;

/** Timing variation only; provides no detection-avoidance guarantee. */
public final class Rs2Random
{
	private Rs2Random() { }
	public static int between(int minimum, int maximum)
	{
		if (minimum > maximum) { throw new IllegalArgumentException("minimum exceeds maximum"); }
		return (int) ThreadLocalRandom.current().nextLong(minimum, (long) maximum + 1);
	}
	public static void waitEx(int mean, int deviation)
	{
		if (Microbot.getClient() == null || Microbot.getClient().isClientThread()) { return; }
		long remaining = Math.max(0, Math.round(mean + ThreadLocalRandom.current().nextGaussian() * Math.max(0, deviation)));
		while (remaining > 0 && MobileActions.active() && !Thread.currentThread().isInterrupted())
		{
			long slice = Math.min(remaining, 50);
			try { Thread.sleep(slice); }
			catch (InterruptedException exception) { Thread.currentThread().interrupt(); return; }
			remaining -= slice;
		}
	}
}
