/*
 * Copied from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/math/RateCalculator.java.
 * BSD 2-Clause License; see LICENSE. Mobile adaptation: no time elapsed yet gives 0, not a division by zero.
 */
package net.runelite.client.plugins.microbot.util.math;

/**
 * Per-hour rates for overlays, as upstream: the clock starts on the first call, and every later
 * call divides the given total by the hours since then.
 */
public class RateCalculator
{
	private static final double MILLIS_TO_HOURS = 1.0 / (1000 * 60 * 60);

	private static long startTime = -1;

	/** As upstream: {@code currentValue} per hour since the first call. */
	public static synchronized int getRatePerHour(int currentValue)
	{
		long now = System.currentTimeMillis();
		if (startTime == -1)
		{
			startTime = now;
		}
		double hours = (now - startTime) * MILLIS_TO_HOURS;
		return hours <= 0 ? 0 : (int) (currentValue / hours);
	}
}
