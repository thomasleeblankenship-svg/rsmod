package net.runelite.client.plugins.microbot.util.item;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.*;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.headless.JavaBridge;
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.Spell;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real inventory reads and targeting dispatcher; casting/game/widget changes simulated at engine boundaries. */
public class Rs2EnsouledHeadTest
{
	static { System.setProperty("runelite.bridge", "true"); }
	private Client client;
	private EventBus events;
	private ScriptInputSession owner;
	private Widget spell;
	private Widget inventory;
	private final AtomicBoolean onClient = new AtomicBoolean();
	private final AtomicBoolean selected = new AtomicBoolean();
	private final AtomicBoolean inventoryVisible = new AtomicBoolean(true);
	private final AtomicInteger tick = new AtomicInteger(1);
	private final AtomicInteger targets = new AtomicInteger();

	@Before
	public void setUp() throws Exception
	{
		JavaBridge.setScriptInputSuspended(false);
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClient.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getLocalPlayer()).thenReturn(mock(Player.class));
		when(client.getTickCount()).thenAnswer(call -> tick.getAndIncrement());
		when(client.isWidgetSelected()).thenAnswer(call -> selected.get());
		when(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)).thenAnswer(call -> inventoryVisible.get() ? 3 : 6);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			boolean previous = onClient.get();
			onClient.set(true);
			try { ((Runnable) call.getArgument(0)).run(); }
			finally { onClient.set(previous); }
			return null;
		}).when(thread).invoke(any(Runnable.class));
		events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
		owner = new ScriptInputSession();
		MobileActions.bind(owner);
		spell = mock(Widget.class);
		when(spell.getId()).thenReturn((218 << 16) | 10);
		when(spell.getName()).thenReturn("Basic Reanimation");
		when(client.getSelectedWidget()).thenReturn(spell);
		Widget book = mock(Widget.class);
		when(book.getStaticChildren()).thenReturn(new Widget[]{spell});
		when(client.getWidget(218, 0)).thenReturn(book);
		inventory = mock(Widget.class);
		when(inventory.getId()).thenReturn(WidgetInfo.INVENTORY.getId());
		when(inventory.isHidden()).thenAnswer(call -> !inventoryVisible.get());
		when(client.getWidget(WidgetInfo.INVENTORY)).thenReturn(inventory);
		doAnswer(call ->
		{
			assertTrue("targeting must run on client thread", onClient.get());
			assertEquals(MenuAction.WIDGET_TARGET_ON_WIDGET, call.getArgument(2));
			assertEquals(13448, (int) call.getArgument(4));
			assertEquals("Use", call.getArgument(5));
			targets.incrementAndGet();
			selected.set(false);
			return null;
		}).when(client).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), anyString(), anyString());
		contents(13448);
	}

	private void contents(int... ids)
	{
		Item[] items = new Item[ids.length];
		for (int slot = 0; slot < ids.length; slot++)
		{
			int id = ids[slot];
			items[slot] = new Item(id, 1);
			ItemComposition definition = mock(ItemComposition.class);
			when(definition.getName()).thenReturn("Head " + id);
			when(client.getItemDefinition(id)).thenReturn(definition);
			Widget item = mock(Widget.class);
			when(item.getItemId()).thenReturn(id);
			when(inventory.getChild(slot)).thenReturn(item);
		}
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(container);
		when(client.getItemContainer(net.runelite.api.gameval.InventoryID.INV)).thenReturn(container);
		boolean previous = onClient.get();
		onClient.set(true);
		try { events.post(new ItemContainerChanged(net.runelite.api.gameval.InventoryID.INV, container)); }
		finally { onClient.set(previous); }
	}

	private MockedStatic<Rs2Magic> cast(Runnable change)
	{
		MockedStatic<Rs2Magic> magic = mockStatic(Rs2Magic.class);
		magic.when(() -> Rs2Magic.cast(Rs2Spells.BASIC_REANIMATION)).thenAnswer(call ->
		{
			change.run();
			return true;
		});
		return magic;
	}

	@After
	public void tearDown()
	{
		onClient.set(false);
		MobileActions.unbind(owner);
		MicrobotRuntime.uninstall();
		JavaBridge.setScriptInputSuspended(false);
	}

	@Test
	public void catalogueMapsEveryRealHeadAndSentinelIsNeverATarget()
	{
		assertEquals(24, Rs2EnsouledHead.values().length);
		for (Rs2EnsouledHead head : Rs2EnsouledHead.values())
		{
			if (head == Rs2EnsouledHead.ALL_IN_BANK) continue;
			assertSame(head, Rs2EnsouledHead.forItem(new Rs2ItemModel(new Item(head.getItemId(), 1), null, null, 0)));
			assertEquals(head.getMagicLevelRequirement(), head.getMagicSpell().getRequiredLevel());
		}
		assertNull(Rs2EnsouledHead.forItem(null));
		assertNull(Rs2EnsouledHead.forItem(new Rs2ItemModel(new Item(1337, 1), null, null, 0)));
		assertNull(Rs2EnsouledHead.ALL_IN_BANK.getInventoryHead());
		assertFalse(Rs2EnsouledHead.ALL_IN_BANK.hasRequirements());
		assertFalse(Rs2EnsouledHead.ALL_IN_BANK.reanimate());
	}

	@Test
	public void requirementsRunOnClientThreadAndSelectionKeepsInventoryOrder()
	{
		contents(13508, 13448);
		try (MockedStatic<Rs2Magic> magic = mockStatic(Rs2Magic.class))
		{
			magic.when(() -> Rs2Magic.canCast(any(Spell.class))).thenAnswer(call ->
			{
				assertTrue(onClient.get());
				return call.getArgument(0) == Rs2Spells.BASIC_REANIMATION;
			});
			assertSame(Rs2EnsouledHead.GOBLIN, Rs2EnsouledHead.getReanimatableHead());
			magic.when(() -> Rs2Magic.canCast(Rs2Spells.MASTER_REANIMATION)).thenReturn(true);
			assertSame(Rs2EnsouledHead.ABYSSAL, Rs2EnsouledHead.getReanimatableHead());
		}
	}

	@Test
	public void reanimationSelectsSpellAndDispatchesOneBoundInventoryTarget()
	{
		try (MockedStatic<Rs2Magic> magic = cast(() -> selected.set(true)))
		{
			assertTrue(Rs2EnsouledHead.GOBLIN.reanimate());
			magic.verify(() -> Rs2Magic.cast(Rs2Spells.BASIC_REANIMATION), times(1));
			assertEquals(1, targets.get());
			verify(client).menuAction(0, WidgetInfo.INVENTORY.getId(), MenuAction.WIDGET_TARGET_ON_WIDGET,
				0, 13448, "Use", "Head 13448");
		}
	}

	@Test
	public void selectedSpellRemainsUsableAfterInventoryHidesSpellbook()
	{
		when(spell.isHidden()).thenReturn(true);
		when(spell.getName()).thenReturn("<col=00ff00>Basic Reanimation</col>");
		when(client.getWidget(218, 0)).thenReturn(null);
		try (MockedStatic<Rs2Magic> magic = cast(() -> selected.set(true)))
		{
			assertTrue(Rs2EnsouledHead.GOBLIN.reanimate());
			assertEquals(1, targets.get());
		}
	}

	@Test
	public void noHeadOrFailedSpellCannotTargetInventory()
	{
		try (MockedStatic<Rs2Magic> magic = mockStatic(Rs2Magic.class))
		{
			assertFalse(Rs2EnsouledHead.GOBLIN.reanimate());
			contents(1511);
			magic.clearInvocations();
			assertFalse(Rs2EnsouledHead.GOBLIN.reanimate());
			magic.verifyNoInteractions();
			assertEquals(0, targets.get());
		}
	}

	@Test
	public void stopAndManualInputAfterSelectionPreventTargeting()
	{
		try (MockedStatic<Rs2Magic> magic = cast(owner::close))
		{
			assertFalse(Rs2EnsouledHead.GOBLIN.reanimate());
			assertEquals(0, targets.get());
		}
		owner = new ScriptInputSession();
		MobileActions.bind(owner);
		try (MockedStatic<Rs2Magic> magic = cast(() -> JavaBridge.onTapMove(100, 120)))
		{
			assertFalse(Rs2EnsouledHead.GOBLIN.reanimate());
			assertTrue("manual input ends the queued action, not the session", owner.isActive());
			assertEquals(0, targets.get());
		}
	}

	@Test
	public void aDifferentSelectedSpellIsNeverUsed()
	{
		Widget different = mock(Widget.class);
		when(different.getId()).thenReturn((218 << 16) | 99);
		try (MockedStatic<Rs2Magic> magic = cast(() ->
		{
			selected.set(true);
			when(client.getSelectedWidget()).thenReturn(different);
		}))
		{
			assertFalse(Rs2EnsouledHead.GOBLIN.reanimate());
			assertEquals(0, targets.get());
		}
	}

	@Test
	public void waitsForInventoryBeforeTargeting()
	{
		inventoryVisible.set(false);
		AtomicInteger reads = new AtomicInteger();
		when(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)).thenAnswer(call ->
		{
			assertTrue(onClient.get());
			if (reads.incrementAndGet() >= 2) inventoryVisible.set(true);
			return inventoryVisible.get() ? 3 : 6;
		});
		try (MockedStatic<Rs2Magic> magic = cast(() -> selected.set(true)))
		{
			assertTrue(Rs2EnsouledHead.GOBLIN.reanimate());
			assertTrue(reads.get() >= 2);
			assertEquals(1, targets.get());
		}
	}

	@Test
	public void stopWhileWaitingForInventoryDispatchesNothing()
	{
		inventoryVisible.set(false);
		when(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)).thenAnswer(call ->
		{
			owner.close();
			return 6;
		});
		try (MockedStatic<Rs2Magic> magic = cast(() -> selected.set(true)))
		{
			assertFalse(Rs2EnsouledHead.GOBLIN.reanimate());
			assertEquals(0, targets.get());
		}
	}

	@Test
	public void aReplacementItemInTheSlotIsNotTargeted()
	{
		try (MockedStatic<Rs2Magic> magic = cast(() ->
		{
			selected.set(true);
			contents(1511);
		}))
		{
			assertFalse(Rs2EnsouledHead.GOBLIN.reanimate());
			assertTrue(owner.isActive());
			assertEquals(0, targets.get());
		}
	}

	@Test
	public void aReplacedScriptSessionDoesNotInheritTheTarget()
	{
		try (MockedStatic<Rs2Magic> magic = cast(() ->
		{
			owner = new ScriptInputSession();
			MobileActions.bind(owner);
			selected.set(true);
		}))
		{
			assertFalse(Rs2EnsouledHead.GOBLIN.reanimate());
			assertTrue(owner.isActive());
			assertEquals(0, targets.get());
		}
	}

	@Test
	public void clientThreadNeverWaitsOrCasts()
	{
		onClient.set(true);
		try (MockedStatic<Rs2Magic> magic = mockStatic(Rs2Magic.class))
		{
			assertFalse(Rs2EnsouledHead.GOBLIN.reanimate());
			magic.verifyNoInteractions();
		}
	}

	@Test
	public void legacyNpcNameReadIsMarshalledAndNullSafe()
	{
		assertFalse(Rs2EnsouledHead.isNpcReanimated(null));
		NPC npc = mock(NPC.class);
		when(npc.getName()).thenAnswer(call -> { assertTrue(onClient.get()); return "Reanimated goblin"; });
		Rs2NpcModel model = new Rs2NpcModel(npc);
		assertTrue(Rs2EnsouledHead.isNpcReanimated(model));
		doReturn(null).when(npc).getName();
		assertFalse(Rs2EnsouledHead.isNpcReanimated(model));
	}
}
