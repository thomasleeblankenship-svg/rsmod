package net.runelite.client.plugins.microbot.util.inventorysetup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsStackCompareID;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util-gap action batch J: pre-potting from an inventory setup, and player attack and walk-under. */
public class HubActionBatchJTest
{
	private static final int RANGING_POTION_4 = 2444;

	private static InventorySetup setupWithAdditional(Map<Integer, InventorySetupsItem> additional)
	{
		return new InventorySetup(new ArrayList<>(), new ArrayList<>(), null, null, null, new HashMap<>(additional),
			"test", "", null, false, null, false, false, 4, false, -1);
	}

	private static InventorySetupsItem row(int id, String name)
	{
		return new InventorySetupsItem(id, name, 1, false, InventorySetupsStackCompareID.Standard, false, -1);
	}

	@Test
	public void nothingIsPrePottedWithoutAdditionalItems()
	{
		assertFalse(new Rs2InventorySetup(setupWithAdditional(Map.of()), null).prePot());
	}

	@Test
	public void aCarriedListedPotionIsDrunkWithoutWithdrawing()
	{
		Rs2ItemModel potion = mock(Rs2ItemModel.class);
		when(potion.getId()).thenReturn(RANGING_POTION_4);
		when(potion.getName()).thenReturn("Ranging potion(4)");
		Rs2InventorySetup setup = new Rs2InventorySetup(
			setupWithAdditional(Map.of(0, row(RANGING_POTION_4, "Ranging potion"))), null);
		try (MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
			MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
			MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
			MockedStatic<MobileActions> actions = mockStatic(MobileActions.class))
		{
			actions.when(MobileActions::active).thenReturn(true);
			bank.when(Rs2Bank::bankItems).thenReturn(List.of(potion));
			inventory.when(() -> Rs2Inventory.hasItem(RANGING_POTION_4)).thenReturn(true);
			inventory.when(() -> Rs2Inventory.interact(anyInt(), anyString())).thenReturn(true);
			inventory.when(() -> Rs2Inventory.waitForInventoryChanges(any(Runnable.class), anyInt(), anyInt()))
				.thenAnswer(a ->
				{
					((Runnable) a.getArgument(0)).run();
					return true;
				});
			player.when(Rs2Player::getHealthPercentage).thenReturn(100.0);

			assertTrue(setup.prePot());
			inventory.verify(() -> Rs2Inventory.interact(RANGING_POTION_4, "Drink"));
			bank.verify(() -> Rs2Bank.withdrawOne(anyInt()), never());

			inventory.clearInvocations();
			player.when(() -> Rs2Player.hasRangingPotionActive(3)).thenReturn(true);
			assertTrue("already boosted", setup.prePot());
			inventory.verify(() -> Rs2Inventory.interact(RANGING_POTION_4, "Drink"), never());
		}
	}

	@Test
	public void playersAreAttackedThroughTheirOwnOption()
	{
		Rs2PlayerModel target = mock(Rs2PlayerModel.class);
		when(target.getId()).thenReturn(7);
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class))
		{
			actions.when(() -> MobileActions.player(7, "Attack")).thenReturn(true);
			assertTrue(Rs2Player.attack(target));
			actions.verify(() -> MobileActions.player(7, "Attack"));
		}
		assertFalse(Rs2Player.attack(null));
		assertFalse(Rs2Player.walkUnder(null));
	}

	@Test
	public void tradingIsNeverDispatched()
	{
		assertFalse(MobileActions.player(7, "Trade with"));
		assertFalse(MobileActions.player(7, null));
	}
}
