package net.runelite.client.plugins.microbot.util.inventory;

import java.awt.Rectangle;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Util-gap action batch B, script side: key hold/release, drags and moving an item to a slot. */
public class HubActionBatchBTest
{
	@Test
	public void keyReleaseIsNotHeldBackByTheTickLimit()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.keyRelease(anyInt())).thenReturn(true);
			actions.clearInvocations();
			Rs2Keyboard.keyRelease(37);
			actions.verify(() -> MobileActions.keyRelease(37));
		}
		assertFalse("no session: nothing is held, nothing to release", MobileActions.keyRelease(37));
		assertFalse("no session: no hold", MobileActions.keyHold(37));
	}

	@Test
	public void movingAnItemNeedsAnotherValidSlot()
	{
		Rs2ItemModel item = mock(Rs2ItemModel.class);
		when(item.getSlot()).thenReturn(3);
		assertFalse(Rs2Inventory.moveItemToSlot(null, 4));
		assertFalse("already there", Rs2Inventory.moveItemToSlot(item, 3));
		assertFalse(Rs2Inventory.moveItemToSlot(item, 28));
		assertFalse(Rs2Inventory.moveItemToSlot(item, -1));
	}

	@Test
	public void aDragWithoutBothRectanglesSendsNothing()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.clearInvocations();
			Microbot.drag(null, new Rectangle(1, 1, 2, 2));
			Microbot.drag(new Rectangle(1, 1, 2, 2), null);
			actions.verify(() -> MobileActions.drag(anyInt(), anyInt(), anyInt(), anyInt()), never());
		}
	}
}
