package net.runelite.client.plugins.microbot.util.mouse.naturalmouse;

/**
 * Upstream Microbot's humanised mouse ({@code util/mouse/naturalmouse/NaturalMouse}), kept only as
 * a type so Hub scripts compile. Humanised input is not ported (project policy: no anti-detection
 * features), so {@code Microbot.naturalMouse} is always {@code null} and scripts skip their
 * {@code naturalMouse} branches, as upstream does when it is unset. Every method here does nothing.
 */
public final class NaturalMouse
{
	private NaturalMouse()
	{
	}

	/** Upstream: a humanised move to a canvas point. Does nothing. */
	public void moveTo(int dx, int dy)
	{
	}

	/** Upstream: a humanised move off the canvas. Does nothing. */
	public void moveOffScreen()
	{
	}

	/** Upstream: {@link #moveOffScreen()} with a chance. Does nothing. */
	public void moveOffScreen(double chancePercentage)
	{
	}
}
