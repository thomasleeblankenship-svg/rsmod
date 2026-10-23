package net.runelite.client.plugins.microbot.util.misc;

import java.awt.Rectangle;
import java.awt.Shape;
import net.runelite.api.Actor;
import net.runelite.client.plugins.microbot.mobile.MobileActions;

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
}
