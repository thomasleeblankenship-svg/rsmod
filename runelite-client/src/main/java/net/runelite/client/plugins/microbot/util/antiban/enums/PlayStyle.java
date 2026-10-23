package net.runelite.client.plugins.microbot.util.antiban.enums;

/**
 * Play styles (label only on mobile).
 * <p>
 * Mobile: the constants and display names of chsami/Microbot {@code util/antiban/enums/PlayStyle.java} at
 * {@code a2114401bbbc35e9762db9e8aa666d70ffcd24d8} (BSD 2-Clause), with no behaviour. Upstream attaches
 * anti-ban timing and busy-detection logic to each constant; that is an anti-detection feature and is
 * not ported. Kept so scripts that name these constants compile; nothing on mobile reads them.
 */
public enum PlayStyle
{
	EXTREME_AGGRESSIVE("Extreme Aggressive"),
	AGGRESSIVE("Aggressive"),
	MODERATE("Moderate"),
	BALANCED("Balanced"),
	CAREFUL("Careful"),
	CAUTIOUS("Cautious"),
	PASSIVE("Passive"),
	RANDOM("Random");

	private final String name;

	PlayStyle(String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}
}
