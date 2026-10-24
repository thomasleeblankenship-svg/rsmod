package net.runelite.client.plugins.microbot.util;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2LogBasket;
import net.runelite.client.plugins.microbot.util.logging.Rs2LogRateLimit;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.skills.fletching.data.FletchingArrow;
import net.runelite.client.plugins.microbot.util.skills.fletching.data.FletchingBolt;
import net.runelite.client.plugins.microbot.util.skills.fletching.data.FletchingDart;
import net.runelite.client.plugins.microbot.util.skills.fletching.data.FletchingItem;
import net.runelite.client.plugins.microbot.util.skills.fletching.data.FletchingMaterial;
import net.runelite.client.plugins.microbot.util.skills.fletching.data.FletchingMode;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util action batch AE: log basket, object click helpers, UI helpers, log rate limit, fletching data. */
public class UtilActionBatchAETest
{
	@Test
	public void rateLimitFiresOnceAndEveryNth()
	{
		AtomicBoolean token = new AtomicBoolean();
		assertTrue(Rs2LogRateLimit.once(token));
		assertFalse(Rs2LogRateLimit.once(token));
		assertFalse(Rs2LogRateLimit.once(null));
		AtomicInteger counter = new AtomicInteger();
		boolean[] fired = new boolean[6];
		for (int i = 0; i < fired.length; i++)
		{
			fired[i] = Rs2LogRateLimit.everyN(counter, 3);
		}
		assertArrayEquals(new boolean[]{true, false, true, false, false, true}, fired);
		assertFalse(Rs2LogRateLimit.everyN(counter, 0));
	}

	@Test
	public void uiHelpersExtractNumbersAndClipToTheCanvas()
	{
		assertEquals(1, Rs2UiHelper.extractNumber("+1%"));
		assertEquals(99, Rs2UiHelper.extractNumber("Price: 99 dollars"));
		assertEquals(-1, Rs2UiHelper.extractNumber("none"));
		assertEquals(-1, Rs2UiHelper.extractNumber(null));
		assertEquals("a b", Rs2UiHelper.stripTagsToSpace("a<br>b"));
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class))
		{
			actions.when(() -> MobileActions.read(any(), any())).thenReturn(new int[]{100, 50});
			assertEquals(new Rectangle(90, 40, 10, 10), Rs2UiHelper.clipToCanvas(new Rectangle(90, 40, 30, 30)));
			assertNull(Rs2UiHelper.clipToCanvas(new Rectangle(200, 200, 5, 5)));
			assertNull(Rs2UiHelper.clipToCanvas(null));
		}
	}

	@Test
	public void closestCandidateIgnoresNulls()
	{
		WorldPoint anchor = new WorldPoint(3200, 3200, 0);
		List<WorldPoint> points = Arrays.asList(new WorldPoint(3210, 3200, 0), null, new WorldPoint(3202, 3200, 0));
		assertEquals(new WorldPoint(3202, 3200, 0), Rs2GameObject.pickClosest(points, p -> p, anchor).orElseThrow());
		assertTrue(Rs2GameObject.pickClosest(List.<WorldPoint>of(), p -> p, anchor).isEmpty());
	}

	@Test
	public void farObjectsAreWalkedTowardsAndNearOnesClicked()
	{
		TileObject object = mock(TileObject.class);
		WorldPoint far = new WorldPoint(3300, 3200, 0);
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class);
			MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class);
			MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class);
			MockedStatic<Rs2GameObject> objects = mockStatic(Rs2GameObject.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.read(any(), any())).thenReturn(far);
			objects.when(() -> Rs2GameObject.interact(any(TileObject.class), anyString())).thenReturn(true);
			player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3200, 3200, 0));
			assertFalse(Rs2GameObject.clickObject(object, "Open"));
			walker.verify(() -> Rs2Walker.walkTo(far));
			player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3290, 3200, 0));
			assertTrue(Rs2GameObject.clickObject(object, "Open"));
			objects.verify(() -> Rs2GameObject.interact(object, "Open"));
		}
		assertFalse(Rs2GameObject.clickObject(null, "Open"));
	}

	@Test
	public void unreadableBasketContentsReadAsEmpty()
	{
		try (MockedStatic<Rs2LogBasket> basket = mockStatic(Rs2LogBasket.class, CALLS_REAL_METHODS))
		{
			basket.when(Rs2LogBasket::getCurrentBasketContents).thenReturn(null);
			assertTrue(Rs2LogBasket.checkBasketContents().isEmpty);
			basket.when(Rs2LogBasket::getCurrentBasketContents).thenReturn(new Rs2LogBasket.BasketContents(12, "Yew logs"));
			assertEquals(12, Rs2LogBasket.checkBasketContents().quantity);
		}
	}

	@Test
	public void basketBankingEmptiesWithdrawsAndFills()
	{
		try (MockedStatic<Rs2LogBasket> basket = mockStatic(Rs2LogBasket.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class);
			MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class))
		{
			basket.when(Rs2LogBasket::hasLogBasket).thenReturn(true);
			basket.when(Rs2LogBasket::emptyLogBasketAtBank).thenReturn(true);
			basket.when(Rs2LogBasket::fillLogBasket).thenReturn(true);
			bank.when(Rs2Bank::isOpen).thenReturn(true);
			bank.when(() -> Rs2Bank.count(1515)).thenReturn(100);
			bank.when(() -> Rs2Bank.withdrawX(eq(1515), anyInt())).thenReturn(true);
			inventory.when(Rs2Inventory::emptySlotCount).thenReturn(27, 0);
			inventory.when(() -> Rs2Inventory.count(1515)).thenReturn(0);
			inventory.when(() -> Rs2Inventory.waitFor(any(), anyInt())).thenReturn(true);
			assertTrue(Rs2LogBasket.handleLogBasketBanking(1515, 40));
			bank.verify(() -> Rs2Bank.withdrawX(1515, 27), times(2));
			basket.verify(Rs2LogBasket::fillLogBasket);
		}
		assertFalse(Rs2LogBasket.handleLogBasketBanking(1515, 27));
	}

	@Test
	public void fletchingDataMatchesUpstream()
	{
		assertEquals(FletchingArrow.RUNE, FletchingArrow.getArrowByTipId(FletchingArrow.RUNE.getArrowTipId()));
		assertFalse(FletchingBolt.BROAD.supportsTips());
		assertTrue(FletchingBolt.DRAGON.supportsTips());
		assertEquals(FletchingBolt.IRON, FletchingBolt.getBoltByUnfinishedId(9377));
		assertEquals(FletchingDart.DRAGON, FletchingDart.getDartByTipId(11230));
		assertEquals('1', FletchingItem.LONG.getOption(FletchingMaterial.YEW, FletchingMode.STRUNG));
		assertEquals('4', FletchingItem.LONG.getOption(FletchingMaterial.LOG, FletchingMode.UNSTRUNG));
		assertEquals('2', FletchingItem.LONG.getOption(FletchingMaterial.REDWOOD, FletchingMode.UNSTRUNG));
		try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class))
		{
			player.when(() -> Rs2Player.getRealSkillLevel(Skill.FLETCHING)).thenReturn(50);
			assertTrue(FletchingDart.STEEL.meetsLevelRequirement());
			assertFalse(FletchingArrow.RUNE.meetsLevelRequirement());
		}
	}
}
