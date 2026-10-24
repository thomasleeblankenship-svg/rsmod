package net.runelite.client.plugins.microbot.util;

import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsVariationMapping;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.models.Rs2ShopSource;
import net.runelite.client.plugins.microbot.util.shop.models.Rs2ShopType;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util action batch AI: Claude 2's handoffs (injector, banked reachability, shop access, variation mapping). */
public class UtilActionBatchAITest
{
	@Test
	public void bankedReachabilityFallsBackToThePlainCheck()
	{
		WorldPoint point = new WorldPoint(3200, 3200, 0);
		try (MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class, CALLS_REAL_METHODS))
		{
			walker.when(() -> Rs2Walker.canReach(point)).thenReturn(true);
			assertTrue(Rs2Walker.canReach(point, true));
			walker.when(() -> Rs2Walker.canReach(point)).thenReturn(false);
			assertFalse(Rs2Walker.canReach(point, false));
			// No client: no banked route is planned.
			assertFalse(Rs2Walker.canReach(point, true));
		}
	}

	@Test
	public void shopAccessNeedsMembershipQuestsAndReach()
	{
		WorldArea area = new WorldArea(3200, 3200, 3, 3, 0);
		Rs2ShopSource shop = new Rs2ShopSource("Shopkeeper", area, Rs2ShopType.GENERAL_STORE, 1.0, 1.0, 0.03,
			Map.of(Quest.COOKS_ASSISTANT, QuestState.FINISHED), Map.of(), Map.of(), true, "");
		try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
			MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class))
		{
			walker.when(() -> Rs2Walker.canReach(any(WorldPoint.class), eq(true))).thenReturn(true);
			player.when(Rs2Player::isMember).thenReturn(false);
			assertFalse(shop.canAccess());
			player.when(Rs2Player::isMember).thenReturn(true);
			player.when(() -> Rs2Player.getQuestState(Quest.COOKS_ASSISTANT)).thenReturn(QuestState.IN_PROGRESS);
			assertFalse(shop.canAccess());
			player.when(() -> Rs2Player.getQuestState(Quest.COOKS_ASSISTANT)).thenReturn(QuestState.FINISHED);
			assertTrue(shop.canAccess());
		}
	}

	@Test
	public void injectorAndVariationMappingAreAvailable()
	{
		assertSame(net.runelite.client.RuneLite.getInjector(), Microbot.getInjector());
		assertEquals(InventorySetupsVariationMapping.map(617), InventorySetupsVariationMapping.map(995));
	}
}
