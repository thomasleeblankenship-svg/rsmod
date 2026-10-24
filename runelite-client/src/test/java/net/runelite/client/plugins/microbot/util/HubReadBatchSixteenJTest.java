package net.runelite.client.plugins.microbot.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.combat.models.Rs2DropSource;
import net.runelite.client.plugins.microbot.util.death.Rs2Death;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.grounditem.models.Rs2SpawnLocation;
import net.runelite.client.plugins.microbot.util.inventory.RunePouchType;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2WidgetInspector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Read batch 16j: small upstream 2.6.38 leftovers (death interface, pouch ids, spellbook stone, models, inspector). */
public class HubReadBatchSixteenJTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final EventBus events = new EventBus();
	private Client client;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			onClientThread.set(true);
			try
			{
				((Runnable) call.getArgument(0)).run();
			}
			finally
			{
				onClientThread.set(false);
			}
			return null;
		}).when(thread).invoke(any(Runnable.class));
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class), List.of(new MicrobotReadServices(client, thread, events)));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	private static Widget slot(int itemId, int quantity)
	{
		Widget widget = mock(Widget.class);
		when(widget.getItemId()).thenReturn(itemId);
		when(widget.getItemQuantity()).thenReturn(quantity);
		return widget;
	}

	@Test
	public void itemsKeptOnDeathInterface()
	{
		assertFalse(Rs2Death.isItemsKeptOnDeathOpen());
		assertEquals(0, Rs2Death.getRiskValue());
		Widget kept = mock(Widget.class);
		Widget[] keptSlots = {slot(4151, 1), slot(-1, 0)};
		when(kept.getDynamicChildren()).thenReturn(keptSlots);
		Widget grave = mock(Widget.class);
		Widget caption = mock(Widget.class);
		when(caption.getText()).thenReturn("Grave (fee: 45,000)");
		Widget[] graveSlots = {slot(995, 1000), caption};
		when(grave.getDynamicChildren()).thenReturn(graveSlots);
		Widget value = mock(Widget.class);
		when(value.getText()).thenReturn("Risked: 1,234,567 gp");
		when(client.getWidget(InterfaceID.Deathkeep.KEPT)).thenReturn(kept);
		when(client.getWidget(InterfaceID.Deathkeep.GRAVE)).thenReturn(grave);
		when(client.getWidget(InterfaceID.Deathkeep.VALUE)).thenReturn(value);

		assertTrue(Rs2Death.isItemsKeptOnDeathOpen());
		assertEquals(1, Rs2Death.getItemsKeptOnDeath().size());
		assertEquals(4151, Rs2Death.getItemsKeptOnDeath().get(0).getId());
		assertEquals(995, Rs2Death.getItemsSentToGrave().get(0).getId());
		assertEquals(45_000, Rs2Death.getPredictedGraveFee());
		assertEquals(1_234_567, Rs2Death.getRiskValue());
		when(caption.getText()).thenReturn("Grave");
		assertEquals("no fee caption", 0, Rs2Death.getPredictedGraveFee());
	}

	@Test
	public void pouchIdsSpellbookStoneAndLineOfSight()
	{
		int[] ids = RunePouchType.getPouchIds();
		assertEquals(RunePouchType.values().length, ids.length);
		assertEquals(RunePouchType.DIVINE.getItemId(), ids[3]);

		Widget fixed = mock(Widget.class);
		Widget classic = mock(Widget.class);
		Widget modern = mock(Widget.class);
		when(client.getWidget(InterfaceID.Toplevel.STONE6)).thenReturn(fixed);
		when(client.getWidget(InterfaceID.ToplevelPreEoc.STONE6)).thenReturn(classic);
		when(client.getWidget(InterfaceID.ToplevelOsrsStretch.STONE6)).thenReturn(modern);
		assertSame(fixed, Rs2Tab.getSpellBookTab());
		when(client.isResized()).thenReturn(true);
		assertSame(modern, Rs2Tab.getSpellBookTab());
		when(client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT)).thenReturn(1);
		assertSame(classic, Rs2Tab.getSpellBookTab());

		assertFalse(Rs2GroundItem.hasLineOfSight(null));
	}

	@Test
	public void dropAndSpawnModels()
	{
		Rs2DropSource drop = new Rs2DropSource(1249, "Black dragon", 227, 1, 3, 0.01, "");
		assertEquals("1 - 0.99^n >= 50%", 69, drop.getKillsForXDesiredPercentage(0.5));
		assertEquals(0.01 * 2 * 1000, drop.getDropValue(1000), 1e-9);

		List<WorldPoint> spots = new ArrayList<>(List.of(new WorldPoint(3200, 3200, 0), new WorldPoint(3300, 3300, 0)));
		Rs2SpawnLocation spawn = new Rs2SpawnLocation(1925, "Lumbridge", "Castle", spots, false, 0);
		assertNull("no player", spawn.nearestSpawnLocation());
		Player player = mock(Player.class);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3290, 3290, 0));
		when(client.getLocalPlayer()).thenReturn(player);
		assertEquals(new WorldPoint(3300, 3300, 0), spawn.nearestSpawnLocation());
		spawn.addSpawnLocation(new WorldPoint(3200, 3200, 0));
		assertEquals("duplicates ignored", 2, spawn.numberOfSpawns());
		assertEquals("no item manager", "Unknown Item", spawn.getItemName());
	}

	@Test
	public void widgetInspectorDescribesVisibleRoots()
	{
		assertTrue(Rs2WidgetInspector.getVisibleInterfaces().isEmpty());
		Widget shown = mock(Widget.class);
		when(shown.getId()).thenReturn((149 << 16) | 0);
		when(shown.getText()).thenReturn("Inventory");
		Widget hidden = mock(Widget.class);
		when(hidden.isHidden()).thenReturn(true);
		Widget[] roots = {shown, hidden, null};
		when(client.getWidgetRoots()).thenReturn(roots);
		List<Rs2WidgetInspector.WidgetDescription> visible = Rs2WidgetInspector.getVisibleInterfaces();
		assertEquals(1, visible.size());
		assertEquals(149, visible.get(0).groupId);
		assertArrayEquals(new Object[0], Rs2WidgetInspector.describeWidget(1, 1).toArray());
	}
}
