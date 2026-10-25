package net.runelite.client.plugins.microbot.util.widget;

import java.awt.Rectangle;
import net.runelite.api.widgets.Widget;

/** Test access to Rs2Widget's package-private overlap rule. */
public final class Rs2WidgetAccess
{
	private Rs2WidgetAccess()
	{
	}

	/** {@code Rs2Widget.overlapsChild} with no viewport offset. */
	public static boolean overlaps(Widget modal, Rectangle overlay)
	{
		return Rs2Widget.overlapsChild(modal, overlay, 0, 0);
	}
}
