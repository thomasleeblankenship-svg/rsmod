package net.runelite.client.plugins.microbot.util.antiban;

/**
 * Upstream {@code AntibanPlugin}'s activity reads, for scripts that call them. Only the reads are
 * here: the anti-ban plugin itself (breaks, fatigue, mouse simulation, its panel) is not ported, and
 * this is not a plugin. The state comes from {@link Rs2Antiban}'s tracker.
 */
public final class AntibanPlugin
{
	private AntibanPlugin()
	{
	}

	/** As upstream: a cooking animation, or a cooking message within the last 3 s. */
	public static boolean isCooking()
	{
		return Rs2Antiban.isCookingActivity();
	}

	/** As upstream: a mining animation now or within the last 3 s. */
	public static boolean isMining()
	{
		return Rs2Antiban.isMiningActivity();
	}

	/** As upstream: more than one game tick without an animation. */
	public static boolean isIdle()
	{
		return Rs2Antiban.isIdle();
	}
}
