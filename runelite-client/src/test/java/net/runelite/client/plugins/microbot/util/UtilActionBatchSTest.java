package net.runelite.client.plugins.microbot.util;

import java.util.function.Predicate;
import java.util.stream.Stream;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util action batch S: dialogue waits, player and equipment actions. */
public class UtilActionBatchSTest
{
	@Test
	public void onlyAQuestStartQuestionIsAccepted()
	{
		try (MockedStatic<Rs2Dialogue> dialogue = mockStatic(Rs2Dialogue.class, CALLS_REAL_METHODS))
		{
			dialogue.when(() -> Rs2Dialogue.clickOption("Yes", false)).thenReturn(true);
			dialogue.when(Rs2Dialogue::getQuestion).thenReturn("Would you like to start the Cook's Assistant quest?");
			assertTrue(Rs2Dialogue.acceptQuestStartDialogue());
			dialogue.when(Rs2Dialogue::getQuestion).thenReturn("Would you like to buy a kebab?");
			assertFalse(Rs2Dialogue.acceptQuestStartDialogue());
			dialogue.when(Rs2Dialogue::getQuestion).thenReturn(null);
			assertFalse(Rs2Dialogue.acceptQuestStartDialogue());
		}
	}

	@Test
	public void playerOptionsUseTheirOwnNames()
	{
		Rs2PlayerModel other = mock(Rs2PlayerModel.class);
		when(other.getId()).thenReturn(9);
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class))
		{
			actions.when(() -> MobileActions.player(anyInt(), anyString())).thenReturn(true);
			actions.when(() -> MobileActions.spellOnPlayer(anyInt())).thenReturn(true);
			assertTrue(Rs2Player.follow(other));
			actions.verify(() -> MobileActions.player(9, "Follow"));
			assertTrue(Rs2Player.challenge(other));
			actions.verify(() -> MobileActions.player(9, "Challenge"));
			assertTrue(Rs2Player.cast(other));
			assertTrue(Rs2Player.use(other));
			actions.verify(() -> MobileActions.spellOnPlayer(9), times(2));
		}
		assertFalse(Rs2Player.follow(null));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void unequipByNameMatchesWornItems()
	{
		Rs2ItemModel cape = mock(Rs2ItemModel.class);
		when(cape.getName()).thenReturn("Fire cape");
		try (MockedStatic<Rs2Equipment> equipment = mockStatic(Rs2Equipment.class, CALLS_REAL_METHODS))
		{
			equipment.when(() -> Rs2Equipment.unEquip(any(Predicate.class))).thenAnswer(a -> ((Predicate<Rs2ItemModel>) a.getArgument(0)).test(cape));
			assertTrue(Rs2Equipment.unEquip("fire", false));
			assertFalse(Rs2Equipment.unEquip("fire", true));
			assertTrue(Rs2Equipment.unEquip("Fire cape", true));
		}
	}

	@Test
	public void useUnNotedSkipsNotes()
	{
		Rs2ItemModel noted = mock(Rs2ItemModel.class);
		when(noted.getName()).thenReturn("Logs");
		when(noted.isNoted()).thenReturn(true);
		Rs2ItemModel logs = mock(Rs2ItemModel.class);
		when(logs.getName()).thenReturn("Logs");
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class, CALLS_REAL_METHODS))
		{
			inventory.when(() -> Rs2Inventory.items(any())).thenAnswer(a -> Stream.of(noted, logs).filter(a.getArgument(0)));
			inventory.when(() -> Rs2Inventory.interact(any(Rs2ItemModel.class), anyString())).thenReturn(true);
			assertTrue(Rs2Inventory.useUnNoted("logs"));
			inventory.verify(() -> Rs2Inventory.interact(logs, "Use"));
		}
	}
}
