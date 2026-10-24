package net.runelite.client.plugins.microbot.util.walker;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.coords.Rs2LocalPoint;

/**
 * Minimap geometry reads (upstream Microbot 2.6.38 {@code Rs2MiniMap}): where a tile is drawn on
 * the minimap and whether a canvas point is inside it. All reads run on the client thread.
 * RuneMobile adaptation: the clip area is the minimap widget's ellipse (upstream's own fallback);
 * upstream traces the minimap alpha-mask sprite when a sprite manager is available.
 */
public final class Rs2MiniMap
{
	private Rs2MiniMap()
	{
	}

	/** The minimap canvas point of {@code localPoint}, or {@code null}. */
	@Nullable
	public static Point localToMinimap(LocalPoint localPoint)
	{
		return localPoint == null ? null : MobileActions.read(() -> Perspective.localToMinimap(Microbot.getClient(), localPoint), null);
	}

	/** The minimap canvas point of {@code worldPoint} (instance coordinates converted), or {@code null}. */
	@Nullable
	public static Point worldToMinimap(WorldPoint worldPoint)
	{
		if (worldPoint == null)
		{
			return null;
		}
		return MobileActions.read(() ->
		{
			Client client = Microbot.getClient();
			WorldView view = client.getTopLevelWorldView();
			LocalPoint local = view == null ? null : LocalPoint.fromWorld(view, worldPoint);
			if (local == null && view != null && view.isInstance())
			{
				local = Rs2LocalPoint.fromWorldInstance(worldPoint);
			}
			return local == null ? null : Perspective.localToMinimap(client, local);
		}, null);
	}

	/** The widget the minimap is drawn in for the current layout, or {@code null}. */
	public static Widget getMinimapDrawWidget()
	{
		return MobileActions.read(() ->
		{
			Client client = Microbot.getClient();
			if (!client.isResized())
			{
				return client.getWidget(ComponentID.FIXED_VIEWPORT_MINIMAP_DRAW_AREA);
			}
			return client.getWidget(client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1
				? ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP_DRAW_AREA
				: ComponentID.RESIZABLE_VIEWPORT_MINIMAP_DRAW_AREA);
		}, null);
	}

	/** The minimap's clip area scaled about its centre by {@code scale}, or {@code null}. */
	public static Shape getMinimapClipArea(double scale)
	{
		Rectangle bounds = MobileActions.read(() ->
		{
			Widget widget = getMinimapDrawWidget();
			return widget == null ? null : widget.getBounds();
		}, null);
		if (bounds == null)
		{
			return null;
		}
		Shape ellipse = new Ellipse2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
		AffineTransform shrink = AffineTransform.getTranslateInstance(bounds.getCenterX(), bounds.getCenterY());
		shrink.scale(scale, scale);
		shrink.translate(-bounds.getCenterX(), -bounds.getCenterY());
		return shrink.createTransformedShape(ellipse);
	}

	/** As upstream: the clip area at 0.94 in resizable mode, 1.0 in fixed mode. */
	public static Shape getMinimapClipArea()
	{
		boolean resized = MobileActions.read(() -> Microbot.getClient().isResized(), false);
		return getMinimapClipArea(resized ? 0.94 : 1.0);
	}

	/** Whether canvas {@code point} is inside the minimap's clip area. */
	public static boolean isPointInsideMinimap(Point point)
	{
		Shape clip = point == null ? null : getMinimapClipArea();
		return clip != null && clip.contains(point.getX(), point.getY());
	}
}
