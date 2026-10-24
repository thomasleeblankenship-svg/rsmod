package net.runelite.client.plugins.microbot;

import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util-gap action batch P: the remaining small actions of the Hub table. */
public class HubActionBatchPTest
{
	@Test
	public void heldKeysAreAllReleased()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class);
			MockedStatic<net.runelite.client.plugins.microbot.util.Global> global = mockStatic(net.runelite.client.plugins.microbot.util.Global.class))
		{
			global.when(() -> net.runelite.client.plugins.microbot.util.Global.sleepUntil(any(java.util.function.BooleanSupplier.class), anyInt()))
				.thenAnswer(a -> ((java.util.function.BooleanSupplier) a.getArgument(0)).getAsBoolean());
			actions.when(() -> MobileActions.keyHold(anyInt())).thenReturn(true);
			Rs2Keyboard.keyHold(37);
			Rs2Keyboard.keyHold(39);
			Rs2Keyboard.releaseHeldKeys();
			actions.verify(() -> MobileActions.keyRelease(37));
			actions.verify(() -> MobileActions.keyRelease(39));
			Rs2Keyboard.releaseHeldKeys();
			actions.verify(() -> MobileActions.keyRelease(anyInt()), times(2));
		}
	}

	@Test
	public void tabsLootAndDefaultInteractionsDelegate()
	{
		try (MockedStatic<Rs2Tab> tabs = mockStatic(Rs2Tab.class, CALLS_REAL_METHODS))
		{
			tabs.when(() -> Rs2Tab.switchTo(any(InterfaceTab.class))).thenReturn(true);
			assertTrue(Rs2Tab.switchToEmotesTab());
			tabs.verify(() -> Rs2Tab.switchTo(InterfaceTab.EMOTES));
			assertTrue(Rs2Tab.switchToMusicTab());
			tabs.verify(() -> Rs2Tab.switchTo(InterfaceTab.MUSIC));
		}
		try (MockedStatic<Rs2GroundItem> items = mockStatic(Rs2GroundItem.class, CALLS_REAL_METHODS))
		{
			items.when(() -> Rs2GroundItem.interact(anyInt(), anyString(), anyInt())).thenReturn(true);
			assertTrue(Rs2GroundItem.loot(995));
			items.verify(() -> Rs2GroundItem.interact(995, "Take", 50));
		}
		Rs2NpcModel npc = mock(Rs2NpcModel.class);
		try (MockedStatic<Rs2Npc> npcs = mockStatic(Rs2Npc.class, CALLS_REAL_METHODS))
		{
			npcs.when(() -> Rs2Npc.interact(any(Rs2NpcModel.class), anyString())).thenReturn(true);
			assertTrue(Rs2Npc.interact(npc));
			npcs.verify(() -> Rs2Npc.interact(npc, ""));
		}
	}

	@Test
	public void theOneArgumentLogoutCheckWaitsNoTime()
	{
		try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class, CALLS_REAL_METHODS))
		{
			player.when(() -> Rs2Player.logoutIfPlayerDetected(anyInt(), anyInt())).thenReturn(false);
			assertFalse(Rs2Player.logoutIfPlayerDetected(2));
			player.verify(() -> Rs2Player.logoutIfPlayerDetected(2, 0));
		}
	}

	@Test
	public void npcTrackingStartsOnceAndStops()
	{
		assertFalse(Rs2Camera.isTrackingNpc());
		Rs2Camera.trackNpc(1);
		assertTrue(Rs2Camera.isTrackingNpc());
		Rs2Camera.trackNpc(2);
		Rs2Camera.stopTrackingNpc();
		assertFalse(Rs2Camera.isTrackingNpc());
	}
}
