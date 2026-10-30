package net.runelite.client.plugins.microbot.util.mouse;

import java.awt.Polygon;
import java.awt.Rectangle;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;

/**
 * Mouse clicks for scripts, with chsami/Microbot's {@code util/mouse/Mouse} method names (at
 * {@code a2114401bbbc35e9762db9e8aa666d70ffcd24d8}, BSD 2-Clause), written for a touchscreen.
 * <p>
 * A left click is one script tap at that canvas point through {@link MobileActions#tap}: only for
 * the bound script session, at most one per game tick, refused while the user is touching the
 * screen. A click waits up to a second for the tap to be accepted. {@code move} only records the
 * position {@link #click()} uses, as a touchscreen has no pointer to move. A drag and a wheel
 * scroll are one script action each.
 * <p>
 * Clicking a rectangle picks a randomised point in it, as upstream. Not provided, so that code
 * needing them does not compile rather than silently doing nothing: right clicks.
 */
public final class Mouse
{
	private static final int TAP_TIMEOUT_MILLIS = 1_000;

	private volatile Point position = new Point(-1, -1);
	private volatile Point lastClick = new Point(-1, -1);

	public Mouse click(int x, int y)
	{
		Point point = new Point(x, y);
		position = point;
		if (Global.sleepUntil(() -> MobileActions.tap(x, y), TAP_TIMEOUT_MILLIS))
		{
			lastClick = point;
		}
		return this;
	}

	public Mouse click(double x, double y)
	{
		return click((int) x, (int) y);
	}

	public Mouse click(Point point)
	{
		return point == null ? this : click(point.getX(), point.getY());
	}

	/**
	 * As upstream: a randomised point in {@code rectangle} ({@code Rs2UiHelper.getClickingPoint}), so
	 * repeated clicks do not land on the same pixel; nothing for a fallback rectangle.
	 */
	public Mouse click(Rectangle rectangle)
	{
		if (net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper.isFallbackRectangle(rectangle))
		{
			return this;
		}
		return click(net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper.getClickingPoint(rectangle, true, false));
	}

	/** As upstream: two wheel notches down (10 units) at {@code point}, as one script action. */
	public Mouse scrollDown(Point point)
	{
		return scroll(point, 2, 10);
	}

	/** As upstream: two wheel notches up (10 units) at {@code point}, as one script action. */
	public Mouse scrollUp(Point point)
	{
		return scroll(point, -2, -10);
	}

	private Mouse scroll(Point point, int wheelRotation, int unitsToScroll)
	{
		if (point != null)
		{
			position = point;
			Global.sleepUntil(() -> MobileActions.scroll(point.getX(), point.getY(), wheelRotation, unitsToScroll), TAP_TIMEOUT_MILLIS);
		}
		return this;
	}

	/** As upstream: sends the prepared menu entry (as {@code Microbot.doInvoke}); {@code point} is only remembered. */
	public Mouse click(Point point, net.runelite.client.plugins.microbot.util.menu.NewMenuEntry entry)
	{
		if (point != null)
		{
			position = point;
		}
		if (entry != null && Global.sleepUntil(() -> MobileActions.menu(entry), TAP_TIMEOUT_MILLIS) && point != null)
		{
			lastClick = point;
		}
		return this;
	}

	/** As upstream: {@link #click(Point, net.runelite.client.plugins.microbot.util.menu.NewMenuEntry)}. */
	public Mouse quickClick(Point point, net.runelite.client.plugins.microbot.util.menu.NewMenuEntry entry)
	{
		return click(point, entry);
	}

	/** As upstream: one script drag between two canvas points (see {@code Microbot.drag}). */
	public Mouse drag(Point startPoint, Point endPoint)
	{
		if (startPoint != null && endPoint != null)
		{
			net.runelite.client.plugins.microbot.Microbot.drag(new Rectangle(startPoint.getX(), startPoint.getY(), 1, 1),
				new Rectangle(endPoint.getX(), endPoint.getY(), 1, 1));
			position = endPoint;
		}
		return this;
	}

	/** At the last position given to {@code move} or {@code click}. */
	public Mouse click()
	{
		return position.getX() < 0 ? this : click(position);
	}

	public Mouse move(Point point)
	{
		if (point != null)
		{
			position = point;
		}
		return this;
	}

	public Mouse move(int x, int y)
	{
		return move(new Point(x, y));
	}

	public Mouse move(double x, double y)
	{
		return move((int) x, (int) y);
	}

	public Mouse move(Rectangle rectangle)
	{
		return rectangle == null ? this : move((int) rectangle.getCenterX(), (int) rectangle.getCenterY());
	}

	public Mouse move(Polygon polygon)
	{
		return polygon == null ? this : move(polygon.getBounds());
	}

	public void setLastClick(Point point)
	{
		if (point != null)
		{
			lastClick = point;
		}
	}

	public Point getLastClick()
	{
		return lastClick;
	}

	/** As upstream: the recorded position as an AWT point. */
	public java.awt.Point getMousePosition()
	{
		return new java.awt.Point(position.getX(), position.getY());
	}
}
