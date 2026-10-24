package net.runelite.client.headless;

import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Script key holds (released by themselves when the script stops) and script drags; real AWT queue, mocked engine. */
public class JavaBridgeScriptHoldDragTest
{
	static { System.setProperty("runelite.bridge", "true"); }

	private final List<String> keys = new CopyOnWriteArrayList<>();
	private final List<MouseEvent> mouse = new CopyOnWriteArrayList<>();
	private Client client;
	private ScriptInputSession session;

	@Before
	public void setUp() throws Exception
	{
		reset();
		Canvas canvas = new Canvas();
		canvas.addKeyListener(new KeyAdapter()
		{
			@Override public void keyPressed(KeyEvent e) { keys.add("down " + e.getKeyCode()); }
			@Override public void keyReleased(KeyEvent e) { keys.add("up " + e.getKeyCode()); }
		});
		MouseAdapter record = new MouseAdapter()
		{
			@Override public void mousePressed(MouseEvent e) { mouse.add(e); }
			@Override public void mouseReleased(MouseEvent e) { mouse.add(e); }
			@Override public void mouseDragged(MouseEvent e) { mouse.add(e); }
		};
		canvas.addMouseListener(record);
		canvas.addMouseMotionListener(record);
		client = mock(Client.class);
		when(client.getCanvas()).thenReturn(canvas);
		when(client.isClientThread()).thenReturn(true);
		when(client.getCanvasWidth()).thenReturn(800);
		when(client.getCanvasHeight()).thenReturn(600);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		JavaBridge.register(client);
		JavaBridge.setScriptInputSuspended(false);
		session = new ScriptInputSession();
	}

	@After
	public void tearDown() throws Exception
	{
		session.close();
		JavaBridge.releaseStaleScriptKeys();
		pump();
		long deadline = System.currentTimeMillis() + 2_000;
		while (JavaBridge.isScriptDragging() && System.currentTimeMillis() < deadline) { Thread.sleep(20); }
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
		Field held = JavaBridge.class.getDeclaredField("scriptHeldKeys");
		held.setAccessible(true);
		((Map<?, ?>) held.get(null)).clear();
	}

	private static void pump() throws Exception
	{
		EventQueue.invokeAndWait(() -> { });
	}

	@Test
	public void aHeldKeyStaysDownUntilReleased() throws Exception
	{
		assertTrue(JavaBridge.onScriptKeyHold(KeyEvent.VK_LEFT, session));
		assertTrue("holding it again is a no-op", JavaBridge.onScriptKeyHold(KeyEvent.VK_LEFT, session));
		pump();
		assertEquals(List.of("down 37"), keys);
		assertTrue(JavaBridge.isScriptKeyHeld(KeyEvent.VK_LEFT));

		assertTrue(JavaBridge.onScriptKeyRelease(KeyEvent.VK_LEFT, session));
		assertFalse("released once only", JavaBridge.onScriptKeyRelease(KeyEvent.VK_LEFT, session));
		pump();
		assertEquals(List.of("down 37", "up 37"), keys);
		assertFalse(JavaBridge.onScriptKeyRelease(KeyEvent.VK_SPACE, session));
	}

	@Test
	public void aStoppedScriptNeverLeavesAKeyDown() throws Exception
	{
		assertTrue(JavaBridge.onScriptKeyHold(KeyEvent.VK_RIGHT, session));
		session.close();
		JavaBridge.tapTick(client);
		pump();
		assertEquals(List.of("down 39", "up 39"), keys);
		assertFalse(JavaBridge.isScriptKeyHeld(KeyEvent.VK_RIGHT));
		assertFalse("a closed session holds nothing", JavaBridge.onScriptKeyHold(KeyEvent.VK_RIGHT, session));
	}

	@Test
	public void theUsersTouchReleasesAScriptsHeldKey() throws Exception
	{
		assertTrue(JavaBridge.onScriptKeyHold(KeyEvent.VK_SPACE, session));
		ScriptInputSession.manualInput();
		JavaBridge.releaseStaleScriptKeys();
		pump();
		assertEquals(List.of("down 32", "up 32"), keys);
	}

	@Test
	public void aDragHoldsPastTheDeadTimeThenMovesAndReleasesAtTheEnd() throws Exception
	{
		assertTrue(JavaBridge.onScriptDrag(100, 100, 180, 140, session));
		assertTrue(JavaBridge.isScriptDragging());
		assertFalse("one drag at a time", JavaBridge.onScriptDrag(10, 10, 20, 20, session));
		assertFalse("no key while dragging", JavaBridge.onScriptKeyHold(KeyEvent.VK_LEFT, session));
		long deadline = System.currentTimeMillis() + 2_000;
		while (JavaBridge.isScriptDragging() && System.currentTimeMillis() < deadline) { Thread.sleep(20); }
		pump();
		assertFalse(JavaBridge.isScriptDragging());
		assertEquals(MouseEvent.MOUSE_PRESSED, mouse.get(0).getID());
		assertEquals(100, mouse.get(0).getX());
		MouseEvent release = mouse.get(mouse.size() - 1);
		assertEquals(MouseEvent.MOUSE_RELEASED, release.getID());
		assertEquals(180, release.getX());
		assertEquals(140, release.getY());
		long drags = mouse.stream().filter(e -> e.getID() == MouseEvent.MOUSE_DRAGGED).count();
		assertEquals(4, drags);
		MouseEvent firstDrag = mouse.stream().filter(e -> e.getID() == MouseEvent.MOUSE_DRAGGED).findFirst().orElseThrow();
		assertTrue("held past the drag dead time before moving", firstDrag.getWhen() - mouse.get(0).getWhen() >= 140);
	}

	@Test
	public void aDragOffTheCanvasOrWithoutASessionIsRefused()
	{
		assertFalse(JavaBridge.onScriptDrag(100, 100, 900, 100, session));
		session.close();
		assertFalse(JavaBridge.onScriptDrag(100, 100, 120, 100, session));
		assertFalse(JavaBridge.isScriptDragging());
	}
}
