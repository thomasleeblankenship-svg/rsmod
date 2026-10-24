package net.runelite.client.plugins.microbot.util.equipment;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Round-4 Hub actions: worn-equipment options, item use, live-object interaction, walker overloads, the XP flag. */
public class HubActionRoundFourTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final int WEAPON = EquipmentInventorySlot.WEAPON.getSlotIdx();

	private Client client;
	private final AtomicBoolean clientThread = new AtomicBoolean(true);
	private final EventBus events = new EventBus();
	private volatile int panel = InterfaceTab.INVENTORY.getVarcIntIndex();

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> clientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			clientThread.set(true);
			try { ((Runnable) call.getArgument(0)).run(); }
			finally { clientThread.set(false); }
			return null;
		}).when(thread).invoke(any(Runnable.class));
		MicrobotReadServices services = new MicrobotReadServices(client, thread, events);
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class), List.of(services));
		when(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)).thenAnswer(call -> panel);
		Player player = mock(Player.class);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
		when(client.getLocalPlayer()).thenReturn(player);
		clientThread.set(false);
	}

	@After
	public void tearDown() { MicrobotRuntime.uninstall(); }

	private void define(int id, String name, String... inventoryActions)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn(name);
		when(definition.getInventoryActions()).thenReturn(inventoryActions);
		when(definition.getNote()).thenReturn(-1);
		when(client.getItemDefinition(id)).thenReturn(definition);
	}

	/** Wields {@code id} with the worn-slot widget offering {@code actions}. */
	private void wield(int id, String... actions)
	{
		Item[] worn = new Item[14];
		worn[WEAPON] = new Item(id, 1);
		ItemContainer equipment = mock(ItemContainer.class);
		when(equipment.getItems()).thenReturn(worn);
		when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);
		Widget slot = mock(Widget.class);
		when(slot.getActions()).thenReturn(actions);
		when(client.getWidget(InterfaceID.Wornitems.SLOT3)).thenReturn(slot);
	}

	/** Static mock of the dispatch boundary; the equipment tab opens when asked. */
	private MockedStatic<MobileActions> actions()
	{
		MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS);
		actions.when(MobileActions::active).thenReturn(true);
		actions.when(() -> MobileActions.openTab(InterfaceTab.EQUIPMENT.getVarcIntIndex())).thenAnswer(call ->
		{
			panel = InterfaceTab.EQUIPMENT.getVarcIntIndex();
			return true;
		});
		actions.when(() -> MobileActions.widget(anyInt(), anyInt(), anyString())).thenReturn(true);
		actions.clearInvocations();
		return actions;
	}

	@Test
	public void unEquipRemovesTheWornItemFromTheEquipmentTab()
	{
		define(1351, "Bronze axe");
		wield(1351, "Remove", null, null, "Operate");
		try (MockedStatic<MobileActions> actions = actions())
		{
			assertTrue(Rs2Equipment.unEquip(EquipmentInventorySlot.WEAPON));
			assertEquals(InterfaceTab.EQUIPMENT.getVarcIntIndex(), panel);
			actions.verify(() -> MobileActions.widget(InterfaceID.Wornitems.SLOT3, -1, "Remove"));
		}
	}

	@Test
	public void interactMatchesTheWornItemsOptionCaseInsensitively()
	{
		define(11920, "Dragon pickaxe");
		wield(11920, "Remove", null, null, "Operate");
		try (MockedStatic<MobileActions> actions = actions())
		{
			assertTrue(Rs2Equipment.interact(EquipmentInventorySlot.WEAPON, "operate"));
			actions.verify(() -> MobileActions.widget(InterfaceID.Wornitems.SLOT3, -1, "Operate"));
			actions.clearInvocations();
			assertFalse("an option the item lacks is not dispatched", Rs2Equipment.interact(11920, "Teleport"));
			assertFalse("nothing worn in that slot", Rs2Equipment.unEquip(EquipmentInventorySlot.HEAD));
			actions.verify(() -> MobileActions.widget(anyInt(), anyInt(), anyString()), never());
		}
		assertEquals(Integer.valueOf(InterfaceID.Wornitems.SLOT3), Rs2Equipment.wornSlotWidget(WEAPON));
		assertNull("slot 6 is unused", Rs2Equipment.wornSlotWidget(6));
	}

	@Test
	public void useSelectsTheItemWithItsUseOption()
	{
		define(21504, "Seaweed spore", "Use", null, null, null, "Drop");
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(new Item[]{new Item(21504, 1)});
		when(client.getItemContainer(net.runelite.api.gameval.InventoryID.INV)).thenReturn(container);
		clientThread.set(true);
		try { events.post(new ItemContainerChanged(net.runelite.api.gameval.InventoryID.INV, container)); }
		finally { clientThread.set(false); }
		try (MockedStatic<MobileActions> actions = actions())
		{
			actions.when(() -> MobileActions.inventory(anyInt(), anyInt(), anyString())).thenReturn(true);
			assertTrue(Rs2Inventory.use(Rs2Inventory.get(21504)));
			actions.verify(() -> MobileActions.inventory(0, 21504, "Use"));
			assertFalse(Rs2Inventory.use((net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel) null));
		}
	}

	@Test
	public void aLiveObjectIsInteractedWithAtItsSnapshotLocation()
	{
		GameObject tree = mock(GameObject.class);
		WorldPoint at = new WorldPoint(3205, 3204, 0);
		when(tree.getId()).thenReturn(1276);
		when(tree.getWorldLocation()).thenReturn(at);
		when(tree.getPlane()).thenReturn(0);
		try (MockedStatic<MobileActions> actions = actions())
		{
			actions.when(() -> MobileActions.objectAt(anyInt(), any(), anyString())).thenReturn(true);
			assertTrue(Rs2GameObject.interact(tree, "Chop down"));
			actions.verify(() -> MobileActions.objectAt(1276, at, "Chop down"));
			assertFalse(Rs2GameObject.interact((net.runelite.api.TileObject) null, "Chop down"));
		}
	}

	@Test
	public void walkToByCoordinatesIsWalkToTheWorldPoint()
	{
		try (MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class, CALLS_REAL_METHODS))
		{
			walker.when(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt())).thenReturn(true);
			assertTrue(Rs2Walker.walkTo(1395, 9322, 0, 2));
			walker.verify(() -> Rs2Walker.walkTo(eq(new WorldPoint(1395, 9322, 0)), eq(2)));
		}
	}

	@Test
	public void gainingExpLastsFourSecondsAfterTheLastStatChange() throws Exception
	{
		Microbot.setIsGainingExp(true);
		assertTrue(Microbot.isGainingExp);
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
		while (Microbot.isGainingExp && System.nanoTime() < deadline)
		{
			Thread.sleep(50);
		}
		assertFalse("cleared about 4 s later", Microbot.isGainingExp);
	}
}
