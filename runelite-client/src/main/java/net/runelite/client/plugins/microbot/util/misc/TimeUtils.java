package net.runelite.client.plugins.microbot.util.misc;

import java.time.Duration;
import java.time.Instant;

/** Time formatting for overlays. Written for RuneMobile. */
public final class TimeUtils
{
	private TimeUtils()
	{
	}

	/** {@code HH:MM:SS} between two instants; hours keep counting past 24, negative spans read 00:00:00. */
	public static String getFormattedDurationBetween(Instant start, Instant end)
	{
		long seconds = Math.max(0, Duration.between(start, end).getSeconds());
		return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
	}
}
