package net.runelite.client.plugins.microbot.util.mouse;

import java.awt.Rectangle;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Mobile mouse: left clicks become script taps at the point; move only records a position. */
public class MouseTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	@Before
	public void setUp() throws Exception
	{
		Client client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(thread).invoke(any(Runnable.class));
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	@Test
	public void clicksTapThePointOrARandomisedPointInTheRectangle()
	{
		Mouse mouse = new Mouse();
		Rectangle target = new Rectangle(100, 200, 20, 10);
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.tap(anyInt(), anyInt())).thenReturn(true);
			actions.clearInvocations();
			mouse.click(new Point(40, 50));
			actions.verify(() -> MobileActions.tap(40, 50));
			java.util.Set<Point> taps = new java.util.HashSet<>();
			for (int i = 0; i < 50; i++)
			{
				mouse.click(target);
				Point tapped = mouse.getLastClick();
				assertTrue(tapped + " is inside the rectangle", target.contains(tapped.getX(), tapped.getY()));
				taps.add(tapped);
			}
			assertTrue("repeated clicks do not tap one pixel", taps.size() > 3);
		}
	}

	@Test
	public void moveOnlyRecordsThePositionThatClickUses()
	{
		Mouse mouse = new Mouse();
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.tap(anyInt(), anyInt())).thenReturn(true);
			actions.clearInvocations();
			mouse.click();
			actions.verify(() -> MobileActions.tap(anyInt(), anyInt()), never());
			mouse.move(7, 9);
			actions.verify(() -> MobileActions.tap(anyInt(), anyInt()), never());
			assertEquals(new java.awt.Point(7, 9), mouse.getMousePosition());
			mouse.click();
			actions.verify(() -> MobileActions.tap(7, 9));
		}
	}
}
