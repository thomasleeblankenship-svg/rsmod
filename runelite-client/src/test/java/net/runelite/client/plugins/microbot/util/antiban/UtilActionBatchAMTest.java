package net.runelite.client.plugins.microbot.util.antiban;

import net.runelite.api.AnimationID;
import net.runelite.api.ChatMessageType;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.enums.Category;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Util action batch AM: activity state and Category busy rules. */
public class UtilActionBatchAMTest
{
	private static ChatMessage chat(ChatMessageType type, String text)
	{
		ChatMessage message = new ChatMessage();
		message.setType(type);
		message.setMessage(text);
		return message;
	}

	@Test
	public void idleTicksCountUpWithoutAnAnimation()
	{
		try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
			MockedStatic<Microbot> microbot = mockStatic(Microbot.class))
		{
			microbot.when(Microbot::isLoggedIn).thenReturn(true);
			player.when(Rs2Player::getAnimation).thenReturn(-1);
			player.when(Rs2Player::isAnimating).thenReturn(true);
			AntibanPlugin.onGameTick();
			assertFalse(Rs2Antiban.isIdle());
			assertTrue(Category.SKILLING_CRAFTING.isBusy());
			player.when(Rs2Player::isAnimating).thenReturn(false);
			AntibanPlugin.onGameTick();
			AntibanPlugin.onGameTick();
			assertTrue(AntibanPlugin.isIdle());
			assertFalse(Category.SKILLING_CRAFTING.isBusy());
			microbot.when(Microbot::isLoggedIn).thenReturn(false);
			player.when(Rs2Player::isAnimating).thenReturn(true);
			assertFalse(Category.SKILLING_THIEVING.isBusy());
		}
	}

	@Test
	public void miningAndCookingAreDetected()
	{
		try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class))
		{
			player.when(Rs2Player::getAnimation).thenReturn(AnimationID.MINING_RUNE_PICKAXE);
			assertTrue(Rs2Antiban.isMining());
			AntibanPlugin.onGameTick();
			player.when(Rs2Player::getAnimation).thenReturn(-1);
			assertFalse(Rs2Antiban.isMining());
			assertTrue("mining lingers 3 s", AntibanPlugin.isMining());
			assertTrue(Rs2Antiban.checkForCookingEvent(chat(ChatMessageType.SPAM, "You successfully cook a lobster.")));
			assertFalse(Rs2Antiban.checkForCookingEvent(chat(ChatMessageType.GAMEMESSAGE, "You successfully cook a lobster.")));
			AntibanPlugin.onChatMessage(chat(ChatMessageType.SPAM, "You accidentally burn the lobster."));
			assertTrue(AntibanPlugin.isCooking());
		}
	}

	@Test
	public void areasIntersectIn2dIgnoringThePlane()
	{
		WorldArea ground = new WorldArea(3200, 3200, 3, 3, 0);
		assertTrue(ground.intersectsWith2D(new WorldArea(3202, 3202, 3, 3, 1)));
		assertFalse(ground.intersectsWith2D(new WorldArea(3203, 3200, 3, 3, 0)));
	}
}
