/*
 * Copied from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/ui/overlay/components/HorizontalRowComponent.java.
 * BSD 2-Clause License; see LICENSE. Adapted for PMD: the unread preferred size
 * is not stored, and the image spacing is a constant.
 */
package net.runelite.client.ui.overlay.components;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

public class HorizontalRowComponent implements LayoutableRenderableEntity
{
	private final List<BufferedImage> images;
	private static final int IMAGE_SPACING = 4;

	private Point preferredLocation = new Point();
	private Rectangle bounds;

	public HorizontalRowComponent(List<BufferedImage> images)
	{
		this.images = images;
	}

	@Override
	public Rectangle getBounds()
	{
		return bounds;
	}

	@Override
	public void setPreferredLocation(Point position)
	{
		this.preferredLocation = position;
	}

	@Override
	public void setPreferredSize(Dimension dimension)
	{
		// The row sizes itself to its images; upstream stored this value but never read it.
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		int x = preferredLocation.x;
		int y = preferredLocation.y;
		int maxHeight = 0;

		for (BufferedImage image : images)
		{
			graphics.drawImage(image, x, y, null);
			x += image.getWidth() + IMAGE_SPACING;
			maxHeight = Math.max(maxHeight, image.getHeight());
		}

		int totalWidth = x - preferredLocation.x;
		Dimension size = new Dimension(totalWidth, maxHeight);
		this.bounds = new Rectangle(preferredLocation, size);
		return size;
	}
}
