package net.runelite.client.plugins.microbot.util.inventory;

import java.util.function.Predicate;
import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Round-8 Hub actions (leaguestoolkit): slot interaction, worn items by name, pickpocketing. */
public class HubActionRoundEightTest
{
	@Test
	public void aSlotIsUsedThroughItsItem()
	{
		Rs2ItemModel glory = mock(Rs2ItemModel.class);
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class, CALLS_REAL_METHODS))
		{
			inventory.when(() -> Rs2Inventory.getItemInSlot(5)).thenReturn(glory);
			inventory.when(() -> Rs2Inventory.interact(any(Rs2ItemModel.class), anyString())).thenReturn(true);
			inventory.clearInvocations();
			assertTrue(Rs2Inventory.slotInteract(5, "Sell 1"));
			inventory.verify(() -> Rs2Inventory.interact(glory, "Sell 1"));
			assertTrue("blank: the first action", Rs2Inventory.slotInteract(5));
			inventory.verify(() -> Rs2Inventory.interact(glory, ""));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void wornItemsAreFoundByNameExactlyOrByContainment()
	{
		Rs2ItemModel briefcase = mock(Rs2ItemModel.class);
		when(briefcase.getName()).thenReturn("Banker's briefcase");
		try (MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class, CALLS_REAL_METHODS))
		{
			equipment.when(() -> Rs2Equipment.interact(any(Predicate.class), anyString())).thenReturn(true);
			equipment.clearInvocations();
			ArgumentCaptor<Predicate<Rs2ItemModel>> match = ArgumentCaptor.forClass(Predicate.class);

			assertTrue(Rs2Equipment.interact("briefcase", "Last-destination"));
			equipment.verify(() -> Rs2Equipment.interact(match.capture(), eq("Last-destination")));
			assertTrue("by containment, ignoring case", match.getValue().test(briefcase));

			equipment.clearInvocations();
			assertTrue(Rs2Equipment.interact("briefcase", "Last-destination", true));
			equipment.verify(() -> Rs2Equipment.interact(match.capture(), eq("Last-destination")));
			assertFalse("exact needs the whole name", match.getValue().test(briefcase));
			assertFalse(Rs2Equipment.interact((String) null, "Remove"));
		}
	}

	@Test
	public void pickpocketIsTheNpcsPickpocketOption()
	{
		try (MockedStatic<Rs2Npc> npcs = mockStatic(Rs2Npc.class, CALLS_REAL_METHODS))
		{
			npcs.when(() -> Rs2Npc.interact(any(Rs2NpcModel.class), anyString())).thenReturn(true);
			npcs.clearInvocations();
			Rs2NpcModel citizen = new Rs2NpcModel(mock(NPC.class));
			assertTrue(Rs2Npc.pickpocket(citizen));
			npcs.verify(() -> Rs2Npc.interact(citizen, "Pickpocket"));
			assertFalse(Rs2Npc.pickpocket((NPC) null));
		}
	}

	@Test
	public void targetingAGroundItemNeedsASession()
	{
		assertFalse("no script session: nothing is sent", MobileActions.targetGroundItem(1, null, "Cast"));
	}
}
