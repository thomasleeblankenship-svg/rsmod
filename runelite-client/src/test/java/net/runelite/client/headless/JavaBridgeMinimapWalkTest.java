package net.runelite.client.headless;

import java.awt.Rectangle;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Minimap walks tap only well inside the minimap disc, never its rim, corners or the orbs beside it. */
public class JavaBridgeMinimapWalkTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private final Rectangle minimap = new Rectangle(100, 50, 160, 160);

	@Test
	public void theCentreAndInnerPointsAreSafe()
	{
		assertTrue(JavaBridge.insideMinimapDisc(minimap, 180, 130));
		assertTrue(JavaBridge.insideMinimapDisc(minimap, 180 + 59, 130));
	}

	@Test
	public void theRimAndBoundingBoxCornersAreRefused()
	{
		assertFalse(JavaBridge.insideMinimapDisc(minimap, 180 + 61, 130));
		assertFalse(JavaBridge.insideMinimapDisc(minimap, 100, 50));
		assertFalse(JavaBridge.insideMinimapDisc(minimap, 255, 205));
	}

	@Test
	public void sceneWalkTapsVaryButStayOnTheTile()
	{
		java.awt.Polygon tile = new java.awt.Polygon(new int[]{100, 140, 100, 60}, new int[]{80, 100, 120, 100}, 4);
		net.runelite.api.Point centre = new net.runelite.api.Point(100, 100);
		Rectangle viewport = new Rectangle(0, 0, 500, 500);
		java.util.Set<net.runelite.api.Point> seen = new java.util.HashSet<>();
		for (int i = 0; i < 500; i++)
		{
			net.runelite.api.Point p = JavaBridge.jitterInTile(centre, tile, viewport);
			assertTrue(tile.contains(p.getX(), p.getY()));
			seen.add(p);
		}
		assertTrue(seen.size() > 10);
		assertTrue(centre == JavaBridge.jitterInTile(centre, null, viewport));
	}
}
