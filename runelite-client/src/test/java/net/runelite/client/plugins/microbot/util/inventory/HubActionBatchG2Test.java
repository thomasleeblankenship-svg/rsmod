package net.runelite.client.plugins.microbot.util.inventory;

import java.util.List;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util-gap action batch G2: walker settings, fast food, pouch repair and ground-item guards. */
public class HubActionBatchG2Test
{
	@Test
	public void theWalkerSettingsAreUpstreamsDefaults()
	{
		assertEquals(5, Rs2Walker.config.reachedDistance());
	}

	@Test
	public void fastFoodIsPreferredWhenAskedAndCarried()
	{
		Rs2ItemModel karambwan = mock(Rs2ItemModel.class);
		try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class, CALLS_REAL_METHODS))
		{
			player.when(Rs2Player::getHealthPercentage).thenReturn(40.0);
			player.when(Rs2Player::useFood).thenReturn(true);
			inventory.when(Rs2Inventory::getInventoryFastFood).thenReturn(List.of(karambwan));
			inventory.when(() -> Rs2Inventory.interact(any(Rs2ItemModel.class), anyString())).thenReturn(true);
			inventory.clearInvocations();
			player.clearInvocations();
			assertTrue(Rs2Player.eatAt(50, true));
			inventory.verify(() -> Rs2Inventory.interact(karambwan, "Eat"));
			player.verify(Rs2Player::useFood, never());

			assertTrue("without the flag: ordinary food", Rs2Player.eatAt(50, false));
			player.verify(Rs2Player::useFood);
			assertFalse("above the threshold", Rs2Player.eatAt(30, true));
		}
	}

	@Test
	public void nothingIsRepairedOrLootedWithoutTheirPreconditions()
	{
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Magic> magic = mockStatic(Rs2Magic.class, CALLS_REAL_METHODS))
		{
			inventory.when(Rs2Inventory::hasDegradedPouch).thenReturn(false);
			magic.clearInvocations();
			assertFalse("nothing degraded", Rs2Magic.repairPouchesWithLunar());
			magic.verify(() -> Rs2Magic.npcContact(anyString()), never());
		}
		assertFalse(Rs2Magic.npcContact(null));
		assertFalse(Rs2GroundItem.lootItemsBasedOnLocation(null, 1));
		assertFalse(Rs2GroundItem.interact((net.runelite.client.plugins.microbot.util.models.RS2Item) null));
	}
}
