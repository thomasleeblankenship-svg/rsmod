package net.runelite.client.headless;

import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.event.MouseWheelEvent;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Script mouse-wheel scrolls; real AWT queue, mocked engine. */
public class JavaBridgeScriptScrollTest
{
	static { System.setProperty("runelite.bridge", "true"); }

	private final List<MouseWheelEvent> wheel = new CopyOnWriteArrayList<>();
	private Client client;
	private ScriptInputSession session;

	@Before
	public void setUp() throws Exception
	{
		reset();
		Canvas canvas = new Canvas();
		canvas.addMouseWheelListener(wheel::add);
		client = mock(Client.class);
		when(client.getCanvas()).thenReturn(canvas);
		when(client.isClientThread()).thenReturn(true);
		when(client.getCanvasWidth()).thenReturn(800);
		when(client.getCanvasHeight()).thenReturn(600);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		JavaBridge.register(client);
		session = new ScriptInputSession();
	}

	@After
	public void tearDown() throws Exception
	{
		session.close();
		pump();
		reset();
		JavaBridge.register(null);
	}

	private static void set(String name, Object value) throws Exception
	{
		Field field = JavaBridge.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(null, value);
	}

	private static void reset() throws Exception
	{
		JavaBridge.setScriptInputSuspended(false);
		Class<?> state = Class.forName(JavaBridge.class.getName() + "$TapState");
		set("tapState", state.getEnumConstants()[0]);
		for (String field : new String[]{"tapUncommitted", "queuedTap", "pressed", "entered", "focused",
			"shiftHeld", "ctrlHeld", "altHeld", "scriptDragging"}) { set(field, false); }
	}

	private static void pump() throws Exception
	{
		EventQueue.invokeAndWait(() -> { });
	}

	@Test
	public void aScrollIsOneWheelEventAtThePoint() throws Exception
	{
		assertTrue(JavaBridge.onScriptScroll(300, 200, 2, 10, session));
		pump();
		assertEquals(1, wheel.size());
		MouseWheelEvent event = wheel.get(0);
		assertEquals(2, event.getWheelRotation());
		assertEquals(10, event.getScrollAmount());
		assertEquals(300, event.getX());
		assertEquals(200, event.getY());
	}

	@Test
	public void scrollsAreRefusedOffCanvasDuringADragOrWithoutTheSession() throws Exception
	{
		assertFalse("off the canvas", JavaBridge.onScriptScroll(900, 200, 2, 10, session));
		assertFalse("no rotation", JavaBridge.onScriptScroll(300, 200, 0, 10, session));
		set("scriptDragging", true);
		assertFalse("while a drag runs", JavaBridge.onScriptScroll(300, 200, -2, -10, session));
		set("scriptDragging", false);
		session.close();
		assertFalse("session closed", JavaBridge.onScriptScroll(300, 200, -2, -10, session));
		when(client.isClientThread()).thenReturn(false);
		assertFalse("not on the client thread", JavaBridge.onScriptScroll(300, 200, -2, -10, new ScriptInputSession()));
		pump();
		assertTrue(wheel.isEmpty());
	}
}
