package net.runelite.client.plugins.microbot.util.dialogues;

import java.awt.event.KeyEvent;
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
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Dialogue options are picked by their number key, counted over the visible options only. */
public class Rs2DialogueClickTest
{
	static
	{
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
		Widget[] rows = {row("Select an option"), row("Where can I go?"), row(""), row("<col=ff0000>Musa Point</col>"), row("No, thank you.")};
		Widget menu = mock(Widget.class);
		when(menu.getDynamicChildren()).thenReturn(rows);
		when(client.getWidget(ComponentID.DIALOG_OPTION_OPTIONS)).thenReturn(menu);
	}

	private static Widget row(String text)
	{
		Widget widget = mock(Widget.class);
		when(widget.getText()).thenReturn(text);
		return widget;
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	@Test
	public void theMatchingOptionsNumberKeyIsPressed()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.key(anyInt())).thenReturn(true);
			actions.clearInvocations();
			assertTrue(Rs2Dialogue.clickOption("musa point"));
			actions.verify(() -> MobileActions.key(KeyEvent.VK_2));
			assertTrue(Rs2Dialogue.clickOption(true, "Nope", "No, thank you."));
			actions.verify(() -> MobileActions.key(KeyEvent.VK_3));
		}
	}

	@Test
	public void noKeyIsPressedWithoutAMatchOrAMenu()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.key(anyInt())).thenReturn(true);
			actions.clearInvocations();
			assertFalse(Rs2Dialogue.clickOption("Karamja"));
			assertFalse("exact needs the whole text", Rs2Dialogue.clickOption("Musa", true));
			when(client.getWidget(ComponentID.DIALOG_OPTION_OPTIONS)).thenReturn(null);
			assertFalse(Rs2Dialogue.clickOption("Musa Point"));
			actions.verify(() -> MobileActions.key(anyInt()), never());
		}
	}
}
