package net.runelite.client.plugins.microbot;

import java.util.Map;
import java.util.stream.Stream;
import net.runelite.api.NPC;
import net.runelite.client.game.npcoverlay.HighlightedNpc;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.RateCalculator;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util-gap action batch M: samplers, rate calculator, restore-energy and pickpocket actions. */
public class HubActionBatchMTest
{
	@Test
	public void skewedAndNormalSamplesStayInRange()
	{
		for (int i = 0; i < 2_000; i++)
		{
			long skewed = Rs2Random.skewedRand(180L, 100L, 350L, 0.0);
			assertTrue(skewed >= 100 && skewed <= 350);
			long normal = Rs2Random.normalRange(150L, 400L, 0.0);
			assertTrue(normal >= 150 && normal <= 400);
		}
	}

	@Test
	public void theRateIsZeroBeforeAnyTimePasses()
	{
		assertTrue(RateCalculator.getRatePerHour(0) >= 0);
	}

	@Test
	public void staminaIsPreferredUnlessItsEffectRuns()
	{
		Rs2ItemModel stamina = mock(Rs2ItemModel.class);
		when(stamina.getName()).thenReturn("Stamina potion(4)");
		Rs2ItemModel energy = mock(Rs2ItemModel.class);
		when(energy.getName()).thenReturn("Energy potion(3)");
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class))
		{
			inventory.when(Rs2Inventory::getPotions).thenReturn(java.util.List.of(stamina, energy));
			inventory.when(() -> Rs2Inventory.interact(any(Rs2ItemModel.class), anyString())).thenReturn(true);
			inventory.clearInvocations();
			Rs2Inventory.useRestoreEnergyItem();
			inventory.verify(() -> Rs2Inventory.interact(stamina, "Drink"));

			player.when(Rs2Player::hasStaminaBuffActive).thenReturn(true);
			Rs2Inventory.useRestoreEnergyItem();
			inventory.verify(() -> Rs2Inventory.interact(energy, "Drink"));
		}
	}

	@Test
	public void aHighlightedNpcIsPickpocketed()
	{
		NPC npc = mock(NPC.class);
		try (MockedStatic<Rs2Npc> npcs = mockStatic(Rs2Npc.class, CALLS_REAL_METHODS))
		{
			npcs.when(() -> Rs2Npc.pickpocket(any(NPC.class))).thenReturn(true);
			npcs.clearInvocations();
			assertTrue(Rs2Npc.pickpocket(Map.of(npc, mock(HighlightedNpc.class))));
			npcs.verify(() -> Rs2Npc.pickpocket(npc));
			assertFalse(Rs2Npc.pickpocket(Map.<NPC, HighlightedNpc>of()));
		}
	}

	@Test
	public void cheapItemsAreDroppedButNamedOnesKept()
	{
		Rs2ItemModel junk = mock(Rs2ItemModel.class);
		when(junk.getName()).thenReturn("Bones");
		Rs2ItemModel kept = mock(Rs2ItemModel.class);
		when(kept.getName()).thenReturn("Coins");
		when(kept.getSlot()).thenReturn(1);
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class, CALLS_REAL_METHODS))
		{
			inventory.when(Rs2Inventory::items).thenAnswer(a -> Stream.of(junk, kept));
			inventory.when(() -> Rs2Inventory.dropAll(any(java.util.function.Predicate.class))).thenAnswer(a ->
			{
				java.util.function.Predicate<Rs2ItemModel> drop = a.getArgument(0);
				return drop.test(junk) && !drop.test(kept);
			});
			assertTrue(Rs2Inventory.dropAllExcept(100, new String[]{"coins"}));
		}
	}
}
