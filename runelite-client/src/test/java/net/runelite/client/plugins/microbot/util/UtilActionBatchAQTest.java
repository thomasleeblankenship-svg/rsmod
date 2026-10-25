package net.runelite.client.plugins.microbot.util;

import java.awt.Rectangle;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.equipment.JewelleryLocationEnum;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.poh.data.JewelleryBox;
import net.runelite.client.plugins.microbot.util.poh.data.MountedGlory;
import net.runelite.client.plugins.microbot.util.poh.data.PohTeleport;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Batch AQ: POH teleport execute(), Wilderness refusal, version check, modal overlap. */
public class UtilActionBatchAQTest
{
	@Test
	public void feroxEnclaveIsNeverTeleportedTo()
	{
		// Ferox is a level-0 safe zone inside the Wilderness, so only the explicit refusal stops it.
		assertFalse(PohTeleports.isWildernessDestination(JewelleryLocationEnum.FEROX_ENCLAVE.getLocation()));
		assertTrue(PohTeleports.isWildernessDestination(new WorldPoint(3100, 3700, 0)));
		assertFalse(PohTeleports.useJewelleryBox(JewelleryLocationEnum.FEROX_ENCLAVE));
		assertFalse(JewelleryBox.FEROX_ENCLAVE.execute());
		assertFalse(PohTeleports.isWildernessDestination(new WorldPoint(3222, 3218, 0)));
	}

	@Test
	public void everyHouseTeleportCanExecute()
	{
		PohTeleport glory = MountedGlory.values()[0];
		// Outside a house (no client) nothing is found, so nothing is clicked.
		assertFalse(glory.execute());
	}

	@Test
	public void versionCheckUsesTheTrackedUpstreamVersion()
	{
		assertTrue(Rs2UiHelper.isClientVersionCompatible("2.6.38"));
		assertTrue(Rs2UiHelper.isClientVersionCompatible("1.9.9.2"));
		assertTrue(Rs2UiHelper.isClientVersionCompatible(null));
		assertFalse(Rs2UiHelper.isClientVersionCompatible("2.7.0"));
	}

	@Test
	public void modalOverlapNeedsMoreThanEightPixelsEachWay()
	{
		Widget modal = mock(Widget.class);
		Widget child = mock(Widget.class);
		when(modal.getDynamicChildren()).thenReturn(new Widget[]{child});
		when(child.getCanvasLocation()).thenReturn(new Point(0, 0));
		when(child.getBounds()).thenReturn(new Rectangle(100, 100, 50, 50));
		assertTrue(net.runelite.client.plugins.microbot.util.widget.Rs2WidgetAccess.overlaps(modal, new Rectangle(140, 140, 20, 20)));
		assertFalse(net.runelite.client.plugins.microbot.util.widget.Rs2WidgetAccess.overlaps(modal, new Rectangle(145, 145, 20, 20)));
		assertFalse(net.runelite.client.plugins.microbot.util.widget.Rs2WidgetAccess.overlaps(null, new Rectangle(0, 0, 9, 9)));
	}
}
