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
 * position {@link #click()} uses, as a touchscreen has no pointer to move.
 * <p>
 * Not provided, so that code needing them does not compile rather than silently doing nothing:
 * right clicks, scrolling, dragging, clicks with a prepared menu entry, and upstream's humanised
 * pointer movement (anti-detection behaviour, not ported).
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

	/** As upstream: the centre of {@code rectangle}. */
	public Mouse click(Rectangle rectangle)
	{
		return rectangle == null ? this : click((int) rectangle.getCenterX(), (int) rectangle.getCenterY());
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
