package net.runelite.client.plugins.microbot.util.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A full reset with real dispatch/read services and a mocked engine that compacts inventory after each drop. */
public class MobileDropWorkflowTest
{
	static
	{
		// JavaBridge captures this flag on first load, including indirect action dispatch.
		System.setProperty("runelite.bridge", "true");
	}

	private Client client;
	private ScriptInputSession owner;
	private final AtomicBoolean clientThread = new AtomicBoolean(true);
	private final AtomicBoolean visible = new AtomicBoolean(false);
	private final AtomicInteger tick = new AtomicInteger(1);
	private final List<Integer> contents = new ArrayList<>();
	private boolean cancelWhenOpened;
	private int drops;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> clientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getLocalPlayer()).thenReturn(mock(Player.class));
		when(client.getTickCount()).thenAnswer(call -> tick.get());
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			clientThread.set(true);
			try { ((Runnable) call.getArgument(0)).run(); }
			finally { clientThread.set(false); }
			return null;
		}).when(thread).invoke(any(Runnable.class));
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
		owner = new ScriptInputSession();
		MobileActions.bind(owner);
		contents.add(1351);
		for (int i = 0; i < 27; i++) { contents.add(1511); }
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenAnswer(call -> contents.stream().map(id -> new Item(id, 1)).toArray(Item[]::new));
		when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(container);
		when(client.getItemContainer(net.runelite.api.gameval.InventoryID.INV)).thenReturn(container);
		define(1351, "Bronze axe");
		define(1511, "Logs");
		Widget inventory = mock(Widget.class);
		when(inventory.getId()).thenReturn(WidgetInfo.INVENTORY.getId());
		when(inventory.isHidden()).thenAnswer(call -> !visible.get());
		when(inventory.getChild(anyInt())).thenAnswer(call ->
		{
			int slot = call.getArgument(0);
			if (slot >= contents.size()) { return null; }
			Widget item = mock(Widget.class);
			when(item.getItemId()).thenReturn(contents.get(slot));
			// No per-widget actions, as on rebuilt slots; dispatch must map the item's ops correctly.
			return item;
		});
		when(client.getWidget(WidgetInfo.INVENTORY)).thenReturn(inventory);
		doAnswer(call ->
		{
			visible.set(true);
			tick.incrementAndGet();
			if (cancelWhenOpened) { owner.close(); }
			return null;
		}).when(client).runScript(915, 3);
		doAnswer(call ->
		{
			assertTrue(clientThread.get());
			assertEquals(MenuAction.CC_OP_LOW_PRIORITY, call.getArgument(2));
			assertEquals(7, (int) call.getArgument(3));
			int slot = call.getArgument(0);
			assertEquals(1511, (int) contents.get(slot));
			contents.remove(slot); // Compact the slots; stale pre-drop slot snapshots must not be reused.
			drops++;
			tick.incrementAndGet(); // The mocked engine confirms and advances its tick after each action.
			return null;
		}).when(client).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), any(), any());
		clientThread.set(false);
	}

	private void define(int id, String name)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn(name);
		when(definition.getInventoryActions()).thenReturn(new String[]{null, null, null, null, "Drop"});
		when(client.getItemDefinition(id)).thenReturn(definition);
	}

	@After
	public void tearDown()
	{
		MobileActions.unbind(owner);
		MicrobotRuntime.uninstall();
	}

	@Test
	public void fullInventoryDropsAllTwentySevenLogsAndRetainsAxeWithBlankKeepList()
	{
		assertTrue(Rs2Inventory.dropAllExcept(false, InteractOrder.STANDARD, ""));
		assertEquals(27, drops);
		assertEquals(List.of(1351), contents);
		verify(client, times(1)).runScript(915, 3);
	}

	@Test
	public void cancellationAfterOpeningPreventsEveryDrop()
	{
		cancelWhenOpened = true;
		assertFalse(Rs2Inventory.dropAllExcept(false, InteractOrder.STANDARD, ""));
		assertEquals(0, drops);
		assertEquals(28, contents.size());
	}

	@Test
	public void axesAndLeaguesToolsAreProtectedIndependentlyOfKeepNames()
	{
		assertTrue(Rs2Inventory.isProtectedAxe(1351, "Bronze axe"));
		assertTrue(Rs2Inventory.isProtectedAxe(1, "Crystal felling axe (inactive)"));
		assertTrue(Rs2Inventory.isProtectedAxe(net.runelite.api.gameval.ItemID.TRAILBLAZER_AXE, "Infernal tool"));
		assertFalse(Rs2Inventory.isProtectedAxe(1511, "Logs"));
		assertFalse(Rs2Inventory.isProtectedAxe(1265, "Bronze pickaxe"));
	}

	@Test
	public void dropAllByNameDropsOnlyExactMatchesOnePerRead()
	{
		assertTrue(Rs2Inventory.dropAll("logs"));
		assertEquals(27, drops);
		assertEquals(List.of(1351), contents);
	}

	@Test
	public void dropAllByIdIgnoresOtherItemsAndReportsDoneWhenNoneMatch()
	{
		assertTrue(Rs2Inventory.dropAll(1511));
		assertEquals(27, drops);
		assertTrue(Rs2Inventory.dropAll(9999));
		assertEquals(27, drops);
	}

	@Test
	public void dropAllInexactNameRequiresTheFlag()
	{
		assertTrue(Rs2Inventory.dropAll(true, "Log"));
		assertEquals(0, drops);
		assertTrue(Rs2Inventory.dropAll(false, "Log"));
		assertEquals(27, drops);
	}
}
