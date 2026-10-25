package net.runelite.client.plugins.microbot.util.overlay;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Point;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Gembag;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * As upstream: draws the gem bag's total gem count on it in the inventory ("?" while unknown) and,
 * under the pointer, a tooltip of its contents. Renders on the client thread.
 */
public class GembagOverlay extends WidgetItemOverlay
{
	/** Shows on the inventory, as upstream. */
	@Inject
	public GembagOverlay()
	{
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem itemWidget)
	{
		if (!Rs2Gembag.getGemBagItemIds().contains(itemId))
		{
			return;
		}
		Rectangle bounds = itemWidget.getCanvasBounds();
		TextComponent text = new TextComponent();
		text.setPosition(new java.awt.Point(bounds.x - 1, bounds.y + 8));
		text.setColor(Color.CYAN);
		text.setText(Rs2Gembag.isUnknown() ? "?" : String.valueOf(Rs2Gembag.getTotalGemCount()));
		text.render(graphics);

		Point mouse = Microbot.getClient().getMouseCanvasPosition();
		TooltipManager tooltips = Microbot.getTooltipManager();
		if (mouse == null || tooltips == null || Rs2Gembag.isUnknown() || !bounds.contains(mouse.getX(), mouse.getY()))
		{
			return;
		}
		StringBuilder tooltip = new StringBuilder("Gem Bag Contents:<br>");
		Rs2Gembag.getGemBagContents().forEach(item -> tooltip.append(item.getQuantity()).append(' ').append(item.getName()).append("<br>"));
		tooltips.add(new Tooltip(tooltip.toString()));
	}
}
