package net.runelite.client.plugins.microbot.util.widget;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Upstream's read-only {@code Rs2Widget} methods against real read services. Every widget getter
 * asserts it runs on the (flagged) client thread, so a read that skips the hop fails.
 */
public class Rs2WidgetReadTest
{
	static
	{
		// JavaBridge captures this flag on first load; set it before any test reaches the bridge.
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private Client client;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			onClientThread.set(true);
			try { ((Runnable) call.getArgument(0)).run(); }
			finally { onClientThread.set(false); }
			return null;
		}).when(thread).invoke(any(Runnable.class));
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	private <T> T live(T value)
	{
		if (!onClientThread.get())
		{
			throw new AssertionError("widget read off the client thread");
		}
		return value;
	}

	private Widget widget(String text, boolean hidden, Widget... dynamicChildren)
	{
		Widget widget = mock(Widget.class);
		when(widget.getText()).thenAnswer(i -> live(text));
		when(widget.isHidden()).thenAnswer(i -> live(hidden));
		when(widget.getSpriteId()).thenAnswer(i -> live(-1));
		when(widget.getDynamicChildren()).thenAnswer(i -> live(dynamicChildren));
		return widget;
	}

	@Test
	public void visibilityAndHiddenFollowUpstreamForMissingWidgets()
	{
		Widget shown = widget("Bank", false);
		Widget hidden = widget("Smithing", true);
		when(client.getWidget(12, 0)).thenReturn(shown);
		when(client.getWidget(312, 0)).thenReturn(hidden);
		when(client.getWidget((12 << 16))).thenReturn(shown);

		assertTrue(Rs2Widget.isWidgetVisible(12 << 16));
		assertFalse(Rs2Widget.isHidden(12, 0));
		assertTrue(Rs2Widget.isHidden(312, 0));
		assertTrue("a missing widget counts as hidden", Rs2Widget.isHidden(99, 1));
		assertFalse(Rs2Widget.isSmithingWidgetOpen());
		assertSame(shown, Rs2Widget.getWidget(12, 0));
		assertNull(Rs2Widget.getWidget(99, 1));
	}

	@Test
	public void childTextAndSpriteReadsUseFallbacksForMissingWidgets()
	{
		Widget label = widget("<col=ff0000>Hello</col>", false);
		doAnswer(i -> live(535)).when(label).getSpriteId();
		when(client.getWidget(162, 5)).thenReturn(label);

		assertEquals("<col=ff0000>Hello</col>", Rs2Widget.getChildWidgetText(162, 5));
		assertEquals(535, Rs2Widget.getChildWidgetSpriteID(162, 5));
		assertEquals("", Rs2Widget.getChildWidgetText(162, 6));
		assertEquals(-1, Rs2Widget.getChildWidgetSpriteID(162, 6));
	}

	@Test
	public void hasWidgetTextSearchesOnlyVisibleDescendantsOfTheRoot()
	{
		Widget hiddenOption = widget("Make-All", true);
		Widget option = widget("<col=ffffff>Arrow shafts</col>", false);
		Widget root = widget("", false, hiddenOption, option);
		when(client.getWidget(270, 13)).thenReturn(root);

		assertTrue(Rs2Widget.hasWidgetText("arrow shafts", 270, 13, true));
		assertTrue(Rs2Widget.hasWidgetText("shafts", 270, 13, false));
		assertFalse("hidden descendants are skipped", Rs2Widget.hasWidgetText("Make-All", 270, 13, false));
		assertFalse(Rs2Widget.hasWidgetText("arrow", 270, 99, false));
	}

	@Test
	public void spriteSearchFindsADescendantUnderGivenChildren()
	{
		Widget icon = widget("", false);
		doAnswer(i -> live(1071)).when(icon).getSpriteId();
		Widget root = widget("", false, icon);

		assertSame(icon, Rs2Widget.findWidget(1071, List.of(root)));
		assertSame(icon, Rs2Widget.searchChildren(1071, root));
		assertNull(Rs2Widget.findWidget(9999, List.of(root)));
	}

	@Test
	public void keyMapReadsDigitAndSpaceLabels()
	{
		Widget keys = widget("", false, widget("<col=ff9040>1</col>", false), widget("Space", false), widget("?", false), widget("7", false));
		when(client.getWidget(270, 13)).thenReturn(keys);

		Map<Integer, Integer> map = Rs2Widget.getWidgetsKeyMap(270, 13);

		assertEquals(Map.of(0, KeyEvent.VK_1, 1, KeyEvent.VK_SPACE, 3, KeyEvent.VK_7), map);
		assertTrue(Rs2Widget.getWidgetsKeyMap(1, 1).isEmpty());
	}

	@Test
	public void bestMatchPrefersExactThenContainsThenWordOverlap()
	{
		Widget oak = widget("Oak logs", false);
		Widget willow = widget("Willow logs", false);
		Widget longbow = widget("Maple longbow (u)", false);
		Widget parent = widget("", false, oak, willow, longbow);
		when(client.getWidget(5000)).thenReturn(parent);

		assertSame(willow, Rs2Widget.findBestMatchingWidget(5000, "WILLOW LOGS"));
		assertSame(oak, Rs2Widget.findBestMatchingWidget(5000, "logs"));
		assertSame(longbow, Rs2Widget.findBestMatchingWidget(5000, "unstrung maple longbow"));
		assertNull(Rs2Widget.findBestMatchingWidget(5001, "logs"));
	}

	@Test
	public void similarityHelpersMatchUpstreamScoring()
	{
		assertEquals(1.0, Rs2Widget.calculateTextSimilarity("Oak logs", "oak LOGS"), 0.0);
		assertEquals(0.5, Rs2Widget.calculateTextSimilarity("Oak logs", "Oak plank"), 0.0);
		assertEquals(0.0, Rs2Widget.calculateTextSimilarity(null, "x"), 0.0);

		Widget a = widget("Oak logs", false);
		Widget b = widget("Oak plank", false);
		Widget c = widget("Rune bar", false);
		assertEquals(List.of(a, b), Rs2Widget.findSimilarWidgets(List.of(c, b, a), "oak logs", 0.5));
	}

	@Test
	public void unreachableRuntimeReturnsUpstreamFallbacks()
	{
		MicrobotRuntime.uninstall();

		assertFalse(Rs2Widget.isWidgetVisible(1));
		assertFalse(Rs2Widget.isHidden(1, 1));
		assertEquals("", Rs2Widget.getChildWidgetText(1, 1));
		assertEquals(-1, Rs2Widget.getChildWidgetSpriteID(1, 1));
		assertTrue(Rs2Widget.getWidgetsKeyMap(1, 1).isEmpty());
		assertNull(Rs2Widget.getWidget(1, 1));
	}
}
