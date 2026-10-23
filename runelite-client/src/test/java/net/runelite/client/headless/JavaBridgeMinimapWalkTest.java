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
}
