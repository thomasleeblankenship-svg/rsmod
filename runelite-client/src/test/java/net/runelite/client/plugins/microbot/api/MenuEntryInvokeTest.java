package net.runelite.client.plugins.microbot.api;

import java.awt.Rectangle;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.client.headless.JavaBridge;
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** {@code Microbot.doInvoke}: a script-built menu entry goes out exactly, as one action through the session. */
public class MenuEntryInvokeTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private ReadApiFixture f;
	private ScriptInputSession session;
	private final List<String> dispatched = new CopyOnWriteArrayList<>();

	@Before
	public void setUp() throws Exception
	{
		JavaBridge.setScriptInputSuspended(false);
		f = new ReadApiFixture();
		session = new ScriptInputSession();
		MobileActions.bind(session);
		doAnswer(call ->
		{
			assertTrue("engine actions run on the client thread", f.client.isClientThread());
			dispatched.add(call.getArgument(0) + "," + call.getArgument(1) + "," + call.getArgument(2) + ","
				+ call.getArgument(3) + "," + call.getArgument(4) + "," + call.getArgument(5) + "," + call.getArgument(6));
			f.tick++;
			return null;
		}).when(f.client).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), anyString(), anyString());
	}

	@After
	public void tearDown() throws Exception
	{
		MobileActions.unbind(session);
		f.close();
	}

	@Test
	public void theEntryIsSentExactlyWhateverTheRectangle()
	{
		// DriftNetScript's withdraw-X; as upstream, this constructor's last string is the option.
		Microbot.doInvoke(new NewMenuEntry(0, 20250629, 57, 3, 21652, "<col=ff9040>Drift net</col>"), new Rectangle(1, 1));
		assertEquals(List.of("0,20250629,CC_OP,3,21652,<col=ff9040>Drift net</col>,"), dispatched);

		Microbot.doInvokeQuick(new NewMenuEntry("Fletch", "Bruma root", 0, MenuAction.WIDGET_TARGET_ON_WIDGET, 4, 9764864, false),
			null);
		assertEquals("a missing option or target goes out empty, never null", 2, dispatched.size());
		assertEquals("4,9764864,WIDGET_TARGET_ON_WIDGET,0,0,Fletch,Bruma root", dispatched.get(1));
	}

	@Test
	public void oneEntryPerTick()
	{
		NewMenuEntry entry = new NewMenuEntry(0, 20250629, 57, 1, -1, "Bank");
		doAnswer(call -> { dispatched.add("sent"); return null; })
			.when(f.client).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), anyString(), anyString());
		assertTrue(MobileActions.menu(entry));
		assertFalse("a second action in the same tick is refused", MobileActions.menu(entry));
		assertEquals(1, dispatched.size());
	}

	@Test
	public void nothingIsSentWhileTheWildernessWarningIsShowing()
	{
		Widget warning = mock(Widget.class);
		when(f.client.getWidget(475, 11)).thenReturn(warning);
		assertFalse(MobileActions.menu(new NewMenuEntry(0, 30867467, 57, 1, -1, "Enter Wilderness")));
		assertTrue(dispatched.isEmpty());
	}

	@Test
	public void aWalkEntryTapsTheCanvasPointItCarries()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.tap(anyInt(), anyInt())).thenReturn(true);
			actions.clearInvocations();
			Microbot.doInvoke(new NewMenuEntry().param0(412).param1(230).type(MenuAction.WALK).option("Walk here"),
				new Rectangle(412, 230, 1, 1));
			actions.verify(() -> MobileActions.tap(412, 230));
		}
		assertTrue("a walk is a tap, not a menu action", dispatched.isEmpty());
	}

	@Test
	public void anEntryWithoutATypeOrANullEntryIsIgnored()
	{
		assertFalse(MobileActions.menu(new NewMenuEntry().option("Use")));
		Microbot.doInvoke(null, new Rectangle(1, 1));
		assertTrue(dispatched.isEmpty());
	}
}
