package net.runelite.client.plugins.microbot.util.dialogues;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.ComponentID;
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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Hub item (4) dialogue reads; every widget read must happen on the client thread. */
public class Rs2DialogueReadTest
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
		when(client.getWidgetRoots()).thenReturn(new Widget[0]);
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
		when(widget.getDynamicChildren()).thenAnswer(i -> live(dynamicChildren));
		return widget;
	}

	@Test
	public void continuePromptIsFoundAnywhereVisible()
	{
		assertFalse(Rs2Dialogue.hasContinue());

		Widget prompt = widget("Click here to continue", false);
		Widget root = widget("", false);
		when(root.getChildren()).thenAnswer(i -> live(new Widget[]{prompt}));
		when(client.getWidgetRoots()).thenReturn(new Widget[]{root});

		assertTrue(Rs2Dialogue.hasContinue());
	}

	@Test
	public void npcDialogueTextIgnoresTagsLineBreaksAndCase()
	{
		Widget text = widget("<col=0000ff>Would you like</col><br>to trade?", false);
		when(client.getWidget(ComponentID.DIALOG_NPC_TEXT)).thenReturn(text);

		assertTrue(Rs2Dialogue.hasDialogueText("like to TRADE"));
		assertTrue(Rs2Dialogue.hasDialogueText("would you like to trade?", true));
		assertFalse(Rs2Dialogue.hasDialogueText("trade", true));
		assertFalse(Rs2Dialogue.hasDialogueText("bank"));
		assertFalse(Rs2Dialogue.hasDialogueText(null));
	}

	@Test
	public void hiddenDialogueTextDoesNotCount()
	{
		Widget hidden = widget("Hello", true);
		when(client.getWidget(ComponentID.DIALOG_PLAYER_TEXT)).thenReturn(hidden);
		assertFalse(Rs2Dialogue.hasDialogueText("hello"));
	}

	@Test
	public void optionsSkipTheTitleRow()
	{
		assertFalse(Rs2Dialogue.hasSelectAnOption());

		Widget menu = widget("", false,
			widget("Select an option", false), widget("Yes, please.", false), widget("No thanks.", false));
		when(client.getWidget(ComponentID.DIALOG_OPTION_OPTIONS)).thenReturn(menu);

		assertTrue(Rs2Dialogue.hasSelectAnOption());
		assertTrue(Rs2Dialogue.hasDialogueOption("yes"));
		assertTrue(Rs2Dialogue.hasDialogueOption("No thanks.", true));
		assertFalse(Rs2Dialogue.hasDialogueOption("No", true));
		assertFalse("the title is not an option", Rs2Dialogue.hasDialogueOption("Select an option"));
	}

	@Test
	public void optionTitleKeepsItsTagsAndTheWaitSeesAShownOption()
	{
		assertFalse(Rs2Dialogue.hasDialogueOptionTitle("travel"));
		Widget menu = widget("", false,
			widget("<col=800000>Travel to Karamja?</col>", false), widget("Yes.", false));
		when(client.getWidget(ComponentID.DIALOG_OPTION_OPTIONS)).thenReturn(menu);

		assertTrue(Rs2Dialogue.hasDialogueOptionTitle("TRAVEL to"));
		assertTrue(Rs2Dialogue.hasDialogueOptionTitle("<col=800000>travel to karamja?</col>", true));
		assertFalse("compared with its tags, as upstream", Rs2Dialogue.hasDialogueOptionTitle("Travel to Karamja?", true));
		assertTrue(Rs2Dialogue.sleepUntilHasDialogueOption("yes"));
	}

	@Test
	public void questionIsTheUntaggedTitleRow()
	{
		assertNull(Rs2Dialogue.getQuestion());
		assertFalse(Rs2Dialogue.hasQuestion("travel"));

		Widget menu = widget("", false,
			widget("<col=800000>Travel to Karamja?</col>", false), widget("Yes.", false));
		when(client.getWidget(ComponentID.DIALOG_OPTION_OPTIONS)).thenReturn(menu);

		assertEquals("Travel to Karamja?", Rs2Dialogue.getQuestion());
		assertTrue(Rs2Dialogue.hasQuestion("TRAVEL to"));
		assertTrue(Rs2Dialogue.hasQuestion("travel to karamja?", true));
		assertFalse(Rs2Dialogue.hasQuestion("Travel", true));
		assertFalse(Rs2Dialogue.hasQuestion(null));
	}
}
