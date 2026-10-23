package net.runelite.client.headless;

import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.client.callback.ClientThread;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real AWT queue and bridge, mocked engine; no game or device. */
public class JavaBridgeScriptInputTest
{
	static { System.setProperty("runelite.bridge", "true"); }
	private static final int X = 100;
	private static final int Y = 120;
	private final List<MouseEvent> events = new CopyOnWriteArrayList<>();
	private Client client;
	private Canvas canvas;
	private CountDownLatch unblock;

	@Before
	public void setUp() throws Exception
	{
		reset();
		canvas = new Canvas();
		canvas.addMouseListener(new MouseAdapter()
		{
			@Override public void mousePressed(MouseEvent e) { events.add(e); }
			@Override public void mouseReleased(MouseEvent e) { events.add(e); }
			@Override public void mouseClicked(MouseEvent e) { events.add(e); }
		});
		client = mock(Client.class);
		when(client.getCanvas()).thenReturn(canvas);
		when(client.isClientThread()).thenReturn(true);
		when(client.getCanvasWidth()).thenReturn(800);
		when(client.getCanvasHeight()).thenReturn(600);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getMouseCanvasPosition()).thenReturn(new Point(X, Y));
		MenuEntry walk = mock(MenuEntry.class);
		when(walk.getOption()).thenReturn("Walk here");
		when(walk.getType()).thenReturn(MenuAction.WALK);
		Menu menu = mock(Menu.class);
		when(menu.getMenuEntries()).thenReturn(new MenuEntry[]{walk});
		when(client.getMenu()).thenReturn(menu);
		ClientThread thread = mock(ClientThread.class);
		doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; })
			.when(thread).invoke(any(Runnable.class));
		doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; })
			.when(thread).invokeLater(any(Runnable.class));
		JavaBridge.register(client);
		JavaBridge.setClientThread(thread);
	}

	@After
	public void tearDown() throws Exception
	{
		if (unblock != null) { unblock.countDown(); }
		pump();
		reset();
		JavaBridge.register(null);
		JavaBridge.setClientThread(null);
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
		set("scriptTap", null);
		for (String field : new String[]{"tapUncommitted", "queuedTap", "pressed", "entered", "focused",
			"shiftHeld", "ctrlHeld", "altHeld"}) { set(field, false); }
		set("camHoldDxPerSec", 0);
		set("camHoldDyPerSec", 0);
	}

	private static void pump() throws Exception
	{
		EventQueue.invokeAndWait(() -> {});
	}

	private void settle()
	{
		for (int i = 0; i < 4; i++) { JavaBridge.tapTick(client); }
	}

	private long count(int kind)
	{
		return events.stream().filter(e -> e.getID() == kind).count();
	}

	private void blockEdt() throws Exception
	{
		CountDownLatch started = new CountDownLatch(1);
		unblock = new CountDownLatch(1);
		EventQueue.invokeLater(() ->
		{
			started.countDown();
			try
			{
				if (!unblock.await(5, TimeUnit.SECONDS)) { throw new AssertionError("EDT blocker timed out"); }
			}
			catch (InterruptedException exception)
			{
				Thread.currentThread().interrupt();
				throw new AssertionError(exception);
			}
		});
		assertTrue(started.await(2, TimeUnit.SECONDS));
	}

	@Test
	public void activeScriptUsesSettleBeforeOneBalancedClick() throws Exception
	{
		ScriptInputSession session = new ScriptInputSession();
		assertTrue(JavaBridge.onScriptClick(X, Y, session));
		pump();
		assertEquals(0, count(MouseEvent.MOUSE_PRESSED));
		assertFalse(JavaBridge.onScriptClick(X, Y, session));
		settle();
		pump();
		assertEquals(1, count(MouseEvent.MOUSE_PRESSED));
		assertEquals(1, count(MouseEvent.MOUSE_RELEASED));
		assertEquals(1, count(MouseEvent.MOUSE_CLICKED));
		assertTrue(session.isActive());
	}

	@Test
	public void stopBeforeSettleDispatchesNothing() throws Exception
	{
		ScriptInputSession session = new ScriptInputSession();
		assertTrue(JavaBridge.onScriptClick(X, Y, session));
		session.close();
		settle();
		pump();
		assertTrue(events.isEmpty());
	}

	@Test
	public void stopAfterSettleButBeforeEdtDispatchCancelsBatch() throws Exception
	{
		blockEdt();
		ScriptInputSession session = new ScriptInputSession();
		assertTrue(JavaBridge.onScriptClick(X, Y, session));
		settle();
		session.close();
		unblock.countDown();
		pump();
		assertTrue(events.isEmpty());
	}

	@Test
	public void cancellationDuringPressStillReleasesAndDoesNotClick() throws Exception
	{
		ScriptInputSession session = new ScriptInputSession();
		canvas.addMouseListener(new MouseAdapter()
		{
			@Override public void mousePressed(MouseEvent event) { session.close(); }
		});
		assertTrue(JavaBridge.onScriptClick(X, Y, session));
		settle();
		pump();
		assertEquals(1, count(MouseEvent.MOUSE_PRESSED));
		assertEquals(1, count(MouseEvent.MOUSE_RELEASED));
		assertEquals(0, count(MouseEvent.MOUSE_CLICKED));
	}

	@Test
	public void nativeTouchTakesOverWithoutScriptClick() throws Exception
	{
		ScriptInputSession session = new ScriptInputSession();
		assertTrue(JavaBridge.onScriptClick(X, Y, session));
		JavaBridge.armTap(X, Y);
		assertFalse(session.isActive());
		settle();
		pump();
		assertTrue(events.isEmpty());
	}

	@Test
	public void nativeRightClickIsNotSuppressedByCancelledScript() throws Exception
	{
		ScriptInputSession session = new ScriptInputSession();
		assertTrue(JavaBridge.onScriptClick(X, Y, session));
		JavaBridge.onClick(50, 60, MouseEvent.BUTTON3);
		pump();
		assertFalse(session.isActive());
		assertEquals(1, count(MouseEvent.MOUSE_PRESSED));
		assertEquals(MouseEvent.BUTTON3, events.get(0).getButton());
	}

	@Test
	public void scriptCannotReplaceAnArmedNativeContact()
	{
		JavaBridge.armTap(X, Y);
		assertFalse(JavaBridge.onScriptClick(X, Y, new ScriptInputSession()));
	}

	@Test
	public void clientThreadAndBoundsAndMenuAreRequired()
	{
		ScriptInputSession session = new ScriptInputSession();
		when(client.isClientThread()).thenReturn(false);
		assertFalse(JavaBridge.onScriptClick(X, Y, session));
		when(client.isClientThread()).thenReturn(true);
		assertFalse(JavaBridge.onScriptClick(-1, Y, session));
		assertFalse(JavaBridge.onScriptClick(800, Y, session));
		when(client.isMenuOpen()).thenReturn(true);
		assertFalse(JavaBridge.onScriptClick(X, Y, session));
	}

	@Test
	public void heldModifiersRejectScriptInput() throws Exception
	{
		set("shiftHeld", true);
		assertFalse(JavaBridge.onScriptClick(X, Y, new ScriptInputSession()));
	}

	@Test
	public void logoutCancelsPendingScriptTap() throws Exception
	{
		ScriptInputSession session = new ScriptInputSession();
		assertTrue(JavaBridge.onScriptClick(X, Y, session));
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		settle();
		pump();
		assertFalse(session.isActive());
		assertTrue(events.isEmpty());
	}

	@Test
	public void confirmationTimeoutRevokesDelayedEdtBatch() throws Exception
	{
		blockEdt();
		ScriptInputSession session = new ScriptInputSession();
		assertTrue(JavaBridge.onScriptClick(X, Y, session));
		for (int i = 0; i < 100; i++) { JavaBridge.tapTick(client); }
		assertFalse(session.isActive());
		unblock.countDown();
		pump();
		assertTrue(events.isEmpty());
	}

	@Test
	public void replacementCannotRenewRevokedPermission()
	{
		ScriptInputSession old = new ScriptInputSession();
		ScriptInputSession next = old.successor();
		old.close();
		assertTrue(next.isActive());
		ScriptInputSession.manualInput();
		assertFalse(next.isActive());
		assertFalse(next.successor().isActive());
		assertTrue(new ScriptInputSession().isActive());
	}

	@Test
	public void backgroundResumeDoesNotRenewOldOrBackgroundCreatedSessions()
	{
		ScriptInputSession old = new ScriptInputSession();
		JavaBridge.setScriptInputSuspended(true);
		ScriptInputSession duringPause = new ScriptInputSession();
		assertFalse(old.isActive());
		assertFalse(duringPause.isActive());
		JavaBridge.setScriptInputSuspended(false);
		assertFalse(old.isActive());
		assertFalse(duringPause.isActive());
		assertTrue(new ScriptInputSession().isActive());
	}

	@Test
	public void backgroundingBeforeEdtDispatchCancelsAlreadySettledBatch() throws Exception
	{
		blockEdt();
		ScriptInputSession session = new ScriptInputSession();
		assertTrue(JavaBridge.onScriptClick(X, Y, session));
		settle();
		JavaBridge.setScriptInputSuspended(true);
		JavaBridge.setScriptInputSuspended(false);
		unblock.countDown();
		pump();
		assertFalse(session.isActive());
		assertTrue(events.isEmpty());
	}
	@Test
	public void scriptKeyDoesNotClaimHumanInputAndAlwaysReleases() throws Exception
	{
		List<Integer> keys = new CopyOnWriteArrayList<>();
		ScriptInputSession owner = new ScriptInputSession();
		canvas.addKeyListener(new java.awt.event.KeyAdapter()
		{
			@Override public void keyPressed(java.awt.event.KeyEvent event)
			{
				keys.add(event.getID());
				owner.close();
			}
			@Override public void keyReleased(java.awt.event.KeyEvent event) { keys.add(event.getID()); }
			@Override public void keyTyped(java.awt.event.KeyEvent event) { keys.add(event.getID()); }
		});
		assertTrue(JavaBridge.onScriptKey(java.awt.event.KeyEvent.VK_SPACE, owner));
		pump();
		assertEquals(List.of(java.awt.event.KeyEvent.KEY_PRESSED, java.awt.event.KeyEvent.KEY_RELEASED), keys);
	}

	@Test
	public void keyQueuedBeforeStopCannotReachListeners() throws Exception
	{
		List<Integer> keys = new CopyOnWriteArrayList<>();
		canvas.addKeyListener(new java.awt.event.KeyAdapter()
		{
			@Override public void keyPressed(java.awt.event.KeyEvent event) { keys.add(event.getID()); }
		});
		blockEdt();
		ScriptInputSession owner = new ScriptInputSession();
		assertTrue(JavaBridge.onScriptKey(java.awt.event.KeyEvent.VK_ENTER, owner));
		owner.close();
		unblock.countDown();
		pump();
		assertTrue(keys.isEmpty());
	}

	@Test
	public void keyAcceptedByEngineKeepsItsScriptPermission() throws Exception
	{
		ScriptInputSession owner = new ScriptInputSession();
		assertTrue(JavaBridge.onScriptKey(java.awt.event.KeyEvent.VK_SPACE, owner));
		pump();
		assertTrue(owner.isActive());
	}

}
