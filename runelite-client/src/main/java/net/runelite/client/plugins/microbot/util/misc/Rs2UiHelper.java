package net.runelite.client.plugins.microbot.util.misc;

import java.awt.Rectangle;
import java.awt.Shape;
import net.runelite.api.Actor;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.text.Rs2TextSanitizer;

/** On-screen geometry and text helpers. Written for RuneMobile; reads happen on the client thread. */
public final class Rs2UiHelper
{
	private Rs2UiHelper()
	{
	}

	/**
	 * Bounds of the actor's on-screen outline, or an empty rectangle when it is not rendered or the
	 * client thread cannot be reached.
	 */
	public static Rectangle getActorClickbox(Actor actor)
	{
		if (actor == null)
		{
			return new Rectangle();
		}
		Shape hull = MobileActions.read(actor::getConvexHull, null);
		return hull == null ? new Rectangle() : hull.getBounds();
	}

	/** {@code text} without {@code <col=...>} colour tags; empty for {@code null}. */
	public static String stripColTags(String text)
	{
		return text == null ? "" : text.replaceAll("(?i)</?col(=[^>]*)?>", "");
	}

	/** As upstream: {@code text} without any markup tags, through {@link Rs2TextSanitizer#stripTags}. */
	public static String stripTags(String text)
	{
		return Rs2TextSanitizer.stripTags(text);
	}

	/** As upstream: the rectangle's corner lies inside the viewport. */
	public static boolean isRectangleWithinViewport(Rectangle rectangle)
	{
		if (rectangle == null)
		{
			return false;
		}
		int[] viewport = MobileActions.read(() -> new int[]{
			net.runelite.client.plugins.microbot.Microbot.getClient().getViewportWidth(),
			net.runelite.client.plugins.microbot.Microbot.getClient().getViewportHeight()}, new int[]{0, 0});
		return rectangle.getX() <= viewport[0] && rectangle.getY() <= viewport[1] && rectangle.getX() >= 0 && rectangle.getY() >= 0;
	}

	/** As upstream: {@code main} contains {@code sub}. */
	public static boolean isRectangleWithinRectangle(Rectangle main, Rectangle sub)
	{
		return main != null && sub != null && main.contains(sub);
	}

	/** As upstream: the bounds of {@code object}'s clickbox, or the default rectangle when it has none. */
	public static Rectangle getObjectClickbox(net.runelite.api.TileObject object)
	{
		Rectangle bounds = object == null ? null : MobileActions.read(() ->
		{
			Shape clickbox = object.getClickbox();
			return clickbox == null ? null : clickbox.getBounds();
		}, null);
		return bounds == null ? getDefaultRectangle() : new Rectangle(bounds);
	}

	/** As upstream: the whole canvas, from (1, 1). */
	public static Rectangle getDefaultRectangle()
	{
		int[] canvas = MobileActions.read(() -> new int[]{
			net.runelite.client.plugins.microbot.Microbot.getClient().getCanvasWidth(),
			net.runelite.client.plugins.microbot.Microbot.getClient().getCanvasHeight()}, new int[]{0, 0});
		return new Rectangle(1, 1, canvas[0], canvas[1]);
	}

	/** As upstream: {@link #getClickingPoint(Rectangle, boolean, boolean)} resolving the fallback rectangle. */
	public static net.runelite.api.Point getClickingPoint(Rectangle rectangle, boolean randomize)
	{
		return getClickingPoint(rectangle, randomize, true);
	}

	/**
	 * The point to click in {@code rectangle}: its centre. RuneMobile adaptation: humanised input
	 * stays inert by policy, so {@code randomize} does not jitter the point; a {@code null}
	 * rectangle (or, with {@code resolveFallback}, the default canvas rectangle) gives {@code null}
	 * instead of upstream's mouse resting point.
	 */
	public static net.runelite.api.Point getClickingPoint(Rectangle rectangle, boolean randomize, boolean resolveFallback)
	{
		if (rectangle == null || (resolveFallback && rectangle.x == 1 && rectangle.y == 1
			&& rectangle.width == getDefaultRectangle().width && rectangle.height == getDefaultRectangle().height))
		{
			return null;
		}
		return new net.runelite.api.Point((int) rectangle.getCenterX(), (int) rectangle.getCenterY());
	}

	/** As upstream: whether {@code rectangle} lies wholly on the canvas. */
	public static boolean isRectangleWithinCanvas(Rectangle rectangle)
	{
		if (rectangle == null)
		{
			return false;
		}
		int[] canvas = MobileActions.read(() -> new int[]{
			net.runelite.client.plugins.microbot.Microbot.getClient().getCanvasWidth(),
			net.runelite.client.plugins.microbot.Microbot.getClient().getCanvasHeight()}, new int[]{0, 0});
		return rectangle.getX() >= 0 && rectangle.getY() >= 0
			&& rectangle.getX() + rectangle.getWidth() <= canvas[0] && rectangle.getY() + rectangle.getHeight() <= canvas[1];
	}

	// ---- Read batch 16e (upstream 2.6.38) ----

	/** As upstream: {@code null}, or a rectangle at (0, 0) or (1, 1), marks "no real clickbox". */
	public static boolean isFallbackRectangle(Rectangle rectangle)
	{
		return rectangle == null || (rectangle.getX() == 1 && rectangle.getY() == 1) || (rectangle.getX() == 0 && rectangle.getY() == 0);
	}

	/** As upstream: the script mouse position is in {@code rectangle} (always true for a fallback rectangle). */
	public static boolean isMouseWithinRectangle(Rectangle rectangle)
	{
		if ((rectangle.getX() == 1 && rectangle.getY() == 1) || (rectangle.getX() == 0 && rectangle.getY() == 0))
		{
			return true;
		}
		return rectangle.contains(net.runelite.client.plugins.microbot.Microbot.getMouse().getMousePosition());
	}

	/** As upstream: a half-tile square centred on {@code tile}'s canvas point, or the default rectangle. */
	public static Rectangle getTileClickbox(net.runelite.api.Tile tile)
	{
		Rectangle box = tile == null ? null : MobileActions.read(() ->
		{
			net.runelite.api.coords.LocalPoint local = tile.getLocalLocation();
			net.runelite.api.Client client = net.runelite.client.plugins.microbot.Microbot.getClient();
			net.runelite.api.Point point = local == null ? null : net.runelite.api.Perspective.localToCanvas(client, local, client.getPlane());
			int size = net.runelite.api.Perspective.LOCAL_TILE_SIZE;
			return point == null ? null : new Rectangle(point.getX() - size / 4, point.getY() - size / 4, size / 2, size / 2);
		}, null);
		return box == null ? getDefaultRectangle() : box;
	}

	/** As upstream: the entry targets an actor. */
	public static boolean hasActor(net.runelite.client.plugins.microbot.util.menu.NewMenuEntry entry)
	{
		return entry.getActor() != null;
	}

	/** As upstream: the entry targets a scene object. */
	public static boolean isGameObject(net.runelite.client.plugins.microbot.util.menu.NewMenuEntry entry)
	{
		return entry.getGameObject() != null;
	}

	/** As upstream: compares dotted versions part by part (numeric prefix of each part; missing parts are 0). */
	public static int compareVersions(String version1, String version2)
	{
		if (version1 == null || version2 == null)
		{
			return version1 == null ? (version2 == null ? 0 : -1) : 1;
		}
		String[] v1 = version1.split("\\.");
		String[] v2 = version2.split("\\.");
		for (int i = 0; i < Math.max(v1.length, v2.length); i++)
		{
			int a = i < v1.length ? parseVersionPart(v1[i]) : 0;
			int b = i < v2.length ? parseVersionPart(v2[i]) : 0;
			if (a != b)
			{
				return a < b ? -1 : 1;
			}
		}
		return 0;
	}

	/** As upstream: the leading digits of {@code part}, or 0. */
	public static int parseVersionPart(String part)
	{
		if (part == null)
		{
			return 0;
		}
		int end = 0;
		while (end < part.length() && Character.isDigit(part.charAt(end)))
		{
			end++;
		}
		try
		{
			return end == 0 ? 0 : Integer.parseInt(part.substring(0, end));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
}
