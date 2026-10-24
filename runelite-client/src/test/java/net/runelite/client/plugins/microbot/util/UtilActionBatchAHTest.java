package net.runelite.client.plugins.microbot.util;

import java.util.function.BooleanSupplier;
import net.runelite.api.GameObject;
import net.runelite.client.plugins.microbot.util.camera.NpcTracker;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.tileobject.Rs2TileObjectModel;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util action batch AH: shop change wait, NpcTracker handle, legacy object click. */
public class UtilActionBatchAHTest
{
	@Test
	public void shopChangeWaitTimesOutWhenNothingChanges()
	{
		try (MockedStatic<Global> global = mockStatic(Global.class))
		{
			global.when(() -> Global.sleepUntil(any(BooleanSupplier.class)))
				.thenAnswer(invocation -> ((BooleanSupplier) invocation.getArgument(0)).getAsBoolean());
			// Without a client the stock reads empty both times: unchanged.
			assertFalse(Rs2Shop.waitForShopChanges());
		}
	}

	@Test
	public void npcTrackerIsAHandleOnTheCameraTracker()
	{
		try (MockedStatic<Rs2Camera> camera = mockStatic(Rs2Camera.class))
		{
			camera.when(Rs2Camera::isTrackingNpc).thenReturn(true);
			NpcTracker tracker = new NpcTracker();
			tracker.startTracking(2042);
			assertTrue(tracker.isTracking());
			tracker.stopTracking();
			camera.verify(() -> Rs2Camera.trackNpc(2042));
			camera.verify(Rs2Camera::stopTrackingNpc);
		}
	}

	@Test
	public void legacyObjectClickUsesClickObject()
	{
		Rs2TileObjectModel model = new Rs2TileObjectModel(mock(GameObject.class));
		try (MockedStatic<Rs2GameObject> objects = mockStatic(Rs2GameObject.class))
		{
			objects.when(() -> Rs2GameObject.clickObject(model, "Open")).thenReturn(true);
			assertTrue(model.click("Open"));
		}
	}
}
