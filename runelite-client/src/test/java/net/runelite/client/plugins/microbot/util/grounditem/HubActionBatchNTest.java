package net.runelite.client.plugins.microbot.util.grounditem;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Util-gap action batch N: upstream's loot engine over the read-only ground items. */
public class HubActionBatchNTest
{
	private static final WorldPoint HERE = new WorldPoint(3200, 3200, 0);

	private static GroundItem item(int id, String name, int x, int ownership, long gePrice)
	{
		return GroundItem.builder().id(id).itemId(id).name(name).quantity(1).location(new WorldPoint(x, 3200, 0))
			.ownership(ownership).gePrice(gePrice).tradeable(true).build();
	}

	@Test
	public void namedItemsInRangeAreLootedNearestFirstAndOthersLeft()
	{
		GroundItem far = item(526, "Bones", 3204, TileItem.OWNERSHIP_SELF, 50);
		GroundItem near = item(532, "Big bones", 3201, TileItem.OWNERSHIP_SELF, 300);
		GroundItem foreign = item(536, "Dragon bones", 3202, TileItem.OWNERSHIP_OTHER, 2_000);
		GroundItem outOfRange = item(526, "Bones", 3230, TileItem.OWNERSHIP_SELF, 50);
		Table<WorldPoint, Integer, GroundItem> ground = HashBasedTable.create();
		for (GroundItem gi : List.of(far, near, foreign, outOfRange))
		{
			ground.put(gi.getLocation(), gi.getId(), gi);
		}
		LootingParameters params = new LootingParameters(10, 1, 1, 0, false, true, "bones");
		List<GroundItem> looted = new ArrayList<>();
		try (MockedStatic<Rs2GroundItem> items = mockStatic(Rs2GroundItem.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
			MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class))
		{
			items.when(Rs2GroundItem::getGroundItems).thenReturn(ground);
			player.when(Rs2Player::getWorldLocation).thenReturn(HERE);
			inventory.when(Rs2Inventory::emptySlotCount).thenReturn(20);
			boolean done = Rs2LootEngine.with(params).withLootAction(gi ->
			{
				looted.add(gi);
				ground.remove(gi.getLocation(), gi.getId());
			}).addByNames().loot();
			assertTrue(done);
			assertEquals("own drops in range, nearest first", List.of(near, far), looted);
			assertFalse("scripts are unpaused again", Microbot.pauseAllScripts.get());
		}
	}

	@Test
	public void roomForLootFollowsStacks()
	{
		GroundItem coins = GroundItem.builder().id(995).name("Coins").quantity(50).stackable(true).build();
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class))
		{
			inventory.when(Rs2Inventory::emptySlotCount).thenReturn(0);
			inventory.when(() -> Rs2Inventory.hasItem(995)).thenReturn(true);
			assertTrue("an existing stack takes more", Rs2GroundItem.canTakeGroundItem(coins));
			inventory.when(() -> Rs2Inventory.hasItem(995)).thenReturn(false);
			assertFalse(Rs2GroundItem.canTakeGroundItem(coins));
			assertFalse("no room, nothing picked up", Rs2GroundItem.coreLoot(coins));
		}
		assertEquals(0, Rs2GroundItem.calculateDespawnTime(coins));
		assertFalse(Rs2GroundItem.interact((GroundItem) null));
	}
}
