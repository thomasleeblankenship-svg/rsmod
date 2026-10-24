package net.runelite.client.plugins.microbot.util.inventory;

import java.util.List;
import java.util.function.Predicate;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.misc.Operation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util-gap action batch C: value-returning invoke, Operation, closest combine, potions, dialogue keys. */
public class HubActionBatchCTest
{
	@Test
	public void invokeReturnsTheClientThreadsValue() throws Exception
	{
		Client client = mock(Client.class);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		when(client.isClientThread()).thenReturn(true);
		assertEquals("inline on the client thread", Integer.valueOf(7), thread.invoke(() -> 7));

		when(client.isClientThread()).thenReturn(false);
		doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(thread).invoke(any(Runnable.class));
		assertEquals("handed to the client thread", "tick", thread.invoke(() -> "tick"));
	}

	@Test
	public void operationsCompareAsUpstream()
	{
		assertTrue(Operation.GREATER_EQUAL.check(50, 50));
		assertFalse(Operation.GREATER.check(50, 50));
		assertTrue(Operation.NOT_EQUAL.check(1, 2));
		assertEquals("<=", Operation.LESS_EQUAL.getDisplayText());
	}

	private static Rs2ItemModel item(int id, int slot)
	{
		Rs2ItemModel item = mock(Rs2ItemModel.class);
		when(item.getId()).thenReturn(id);
		when(item.getSlot()).thenReturn(slot);
		return item;
	}

	@Test
	@SuppressWarnings("unchecked")
	public void combineClosestUsesTheNearestPairOfSlots()
	{
		List<Rs2ItemModel> items = List.of(item(946, 0), item(1511, 27), item(1511, 5), item(946, 20));
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class, CALLS_REAL_METHODS);
			MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			inventory.when(() -> Rs2Inventory.items(any(Predicate.class))).thenAnswer(call ->
				items.stream().filter((Predicate<Rs2ItemModel>) call.getArgument(0)));
			actions.when(() -> MobileActions.itemOnItem(anyInt(), anyInt())).thenReturn(true);
			actions.clearInvocations();
			assertTrue(Rs2Inventory.combineClosest(946, 1511));
			actions.verify(() -> MobileActions.itemOnItem(0, 5));
			assertFalse("nothing to combine with", Rs2Inventory.combineClosest(946, 314));
		}
	}

	@Test
	public void activeEffectsAreRespected()
	{
		try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class, CALLS_REAL_METHODS))
		{
			player.when(Rs2Player::hasAntiFireActive).thenReturn(true);
			player.when(Rs2Player::hasGoadingActive).thenReturn(true);
			assertTrue("antifire already active: nothing to drink", Rs2Player.drinkAntiFirePotion());
			assertFalse("goading already active", Rs2Player.drinkGoadingPotion());
		}
	}

	@Test
	public void dialogueOptionsByNumberAndByText()
	{
		try (MockedStatic<Rs2Dialogue> dialogue = mockStatic(Rs2Dialogue.class, CALLS_REAL_METHODS))
		{
			dialogue.when(Rs2Dialogue::hasSelectAnOption).thenReturn(true);
			dialogue.when(() -> Rs2Dialogue.clickOption(anyString(), anyBoolean())).thenReturn(true);
			dialogue.clearInvocations();
			assertTrue(Rs2Dialogue.keyPressForDialogueOption("Yes"));
			dialogue.verify(() -> Rs2Dialogue.clickOption("Yes", false));
			assertFalse("options are numbered 1-9", Rs2Dialogue.keyPressForDialogueOption(0));
			assertFalse(Rs2Dialogue.keyPressForDialogueOption(10));

			dialogue.when(Rs2Dialogue::hasSelectAnOption).thenReturn(false);
			assertFalse("no option menu", Rs2Dialogue.keyPressForDialogueOption("Yes", true));
			dialogue.when(Rs2Dialogue::hasCombinationDialogue).thenReturn(false);
			assertFalse("no make-X interface", Rs2Dialogue.clickCombinationOption("Maple longbow"));
		}
	}
}
