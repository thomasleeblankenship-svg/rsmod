package net.runelite.client.plugins.microbot.util.inventory;

import net.runelite.api.NPC;
import net.runelite.api.TileObject;
import net.runelite.client.Notifier;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util-gap action batch D: the notifier, inert hovers and hopping, NPC interaction by id, loot parameters. */
public class HubActionBatchDTest
{
	@Test
	public void theNotifierIsWhatTheMobilePluginBinds()
	{
		Notifier notifier = mock(Notifier.class);
		Microbot.bindNotifier(notifier);
		try
		{
			assertSame(notifier, Microbot.getNotifier());
		}
		finally
		{
			Microbot.bindNotifier(null);
		}
		assertNull(Microbot.getNotifier());
	}

	@Test
	public void hoveringAndHoppingAwayFromPlayersStayOff()
	{
		assertFalse(Rs2Bank.preHover());
		assertFalse(Rs2Npc.hoverOverActor(mock(NPC.class)));
		assertFalse(Rs2GameObject.hoverOverObject(mock(TileObject.class)));
		assertFalse(Rs2Inventory.hover(mock(Rs2ItemModel.class)));
		assertFalse("never hops away from other players", Rs2Player.hopIfPlayerDetected(1, 10, 10));
	}

	@Test
	public void anNpcIdIsInteractedWithThroughItsNearestModel()
	{
		Rs2NpcModel banker = new Rs2NpcModel(mock(NPC.class));
		try (MockedStatic<Rs2Npc> npcs = mockStatic(Rs2Npc.class, CALLS_REAL_METHODS))
		{
			npcs.when(() -> Rs2Npc.getNpc(1613)).thenReturn(banker);
			npcs.when(() -> Rs2Npc.interact(any(Rs2NpcModel.class), anyString())).thenReturn(true);
			npcs.clearInvocations();
			assertTrue(Rs2Npc.interact(1613, "Bank"));
			npcs.verify(() -> Rs2Npc.interact(banker, "Bank"));
		}
	}

	@Test
	public void lootParametersKeepUpstreamsFields()
	{
		LootingParameters value = new LootingParameters(1000, 50000, 12, 2, 3, true, true, new String[]{"Bones"});
		assertEquals(1000, value.getMinValue());
		assertEquals(50000, value.getMaxValue());
		assertEquals(12, value.getRange());
		assertEquals(1, value.getMinQuantity());
		assertArrayEquals(new String[]{"Bones"}, value.getIgnoredNames());
		assertFalse(value.isEatFoodForSpace());
		value.setEatFoodForSpace(true);
		assertTrue(value.isEatFoodForSpace());
		assertFalse("no bank object", Rs2Bank.openBank((TileObject) null));
	}
}
