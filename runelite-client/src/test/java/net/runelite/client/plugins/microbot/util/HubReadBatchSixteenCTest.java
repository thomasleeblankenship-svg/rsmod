package net.runelite.client.plugins.microbot.util;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ParamID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Read batch 16c (inventory, item model and equipment reads) against upstream 2.6.38 semantics, with mocked RuneLite interfaces. */
public class HubReadBatchSixteenCTest
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
		// LOGIN_SCREEN keeps the varbit cache from holding values between assertions.
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

	private static ItemComposition definition(int id, String name, String... actions)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getId()).thenReturn(id);
		when(definition.getName()).thenReturn(name);
		when(definition.getNote()).thenReturn(-1);
		when(definition.getLinkedNoteId()).thenReturn(-1);
		when(definition.getInventoryActions()).thenReturn(actions.length == 0 ? new String[5] : actions);
		return definition;
	}

	private void inventory(Item... items)
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		ItemContainer inventory = mock(ItemContainer.class);
		when(inventory.getItems()).thenReturn(items);
		when(client.getItemContainer(net.runelite.api.gameval.InventoryID.INV)).thenReturn(inventory);
		onClientThread.set(true);
		events.post(new ItemContainerChanged(net.runelite.api.gameval.InventoryID.INV, inventory));
		onClientThread.set(false);
	}

	private void wear(Item... worn)
	{
		ItemContainer equipment = mock(ItemContainer.class);
		when(equipment.getItems()).thenReturn(worn);
		when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);
	}

	@Test
	public void inventorySlotAndNameReads()
	{
		ItemComposition logs = definition(1511, "Logs", "Light", null, null, null, "Drop");
		ItemComposition oak = definition(1521, "Oak logs");
		ItemComposition runes = definition(556, "Air rune");
		when(runes.isStackable()).thenReturn(true);
		when(client.getItemDefinition(anyInt())).thenAnswer(i -> definition(i.getArgument(0), "Item"));
		when(client.getItemDefinition(1511)).thenReturn(logs);
		when(client.getItemDefinition(1521)).thenReturn(oak);
		when(client.getItemDefinition(556)).thenReturn(runes);
		inventory(new Item(-1, 0), new Item(1521, 1), new Item(1511, 1), new Item(556, 50));

		assertTrue(Rs2Inventory.containsAll("LOGS", "oak logs"));
		assertFalse(Rs2Inventory.containsAll("Logs", "Maple logs"));
		assertEquals("exact", 1511, Rs2Inventory.get(new String[]{"logs"}, true).getId());
		assertEquals("shortest contains match", 1511, Rs2Inventory.get(new String[]{"logs"}, false).getId());
		assertEquals("stackable only", 556, Rs2Inventory.get(new String[]{"rune", "logs"}, true, false).getId());
		assertEquals("Light", Rs2Inventory.getActionsForSlot(2)[0]);
		assertEquals(0, Rs2Inventory.getActionsForSlot(0).length);
		assertEquals(0, Rs2Inventory.getFirstEmptySlot());
		assertEquals(1, Rs2Inventory.getFirstFullSlot());
		assertEquals(1521, Rs2Inventory.getIdForSlot(1));
		assertEquals(-1, Rs2Inventory.getIdForSlot(5));
		assertEquals("Oak logs", Rs2Inventory.getNameForSlot(1));
		assertTrue(Rs2Inventory.isSlotsEmpty(0, 4));
		assertFalse(Rs2Inventory.isSlotsEmpty(0, 1));
		assertTrue(Rs2Inventory.isSlotFull(0, 1));
		assertFalse(Rs2Inventory.isFull(1511));
		assertEquals(-1, Rs2Inventory.getSelectedItemIndex());
		Widget selected = mock(Widget.class);
		when(selected.getIndex()).thenReturn(2);
		when(client.getSelectedWidget()).thenReturn(selected);
		assertEquals(2, Rs2Inventory.getSelectedItemIndex());
	}

	@Test
	public void itemModelDefinitionReads()
	{
		ItemComposition whip = definition(4151, "Abyssal whip", "Wield", null, "Rub", null, "Drop");
		when(whip.getHaPrice()).thenReturn(72000);
		when(whip.getLinkedNoteId()).thenReturn(4152);
		when(whip.getStringValue(anyInt())).thenReturn(null);
		when(whip.getStringValue(ParamID.OC_ITEM_OP1)).thenReturn("Remove");
		when(whip.getSubops()).thenReturn(new String[][]{null, null, {"Check charges", "Uncharge"}, null, null});
		when(client.getItemDefinition(4151)).thenReturn(whip);
		inventory(new Item(4151, 1));
		Rs2ItemModel model = Rs2Inventory.get(4151);

		assertSame(whip, model.getItemComposition());
		assertEquals(72000, model.getHaPrice());
		assertEquals(4152, model.getLinkedId());
		assertEquals(4152, model.getNotedId());
		assertEquals(8, model.getEquipmentActions().size());
		assertEquals("Remove", model.getEquipmentActions().get(0));
		assertEquals("", model.getEquipmentActions().get(7));
		assertEquals("Wield", model.getAction("wie"));
		assertNull(model.getAction("eat"));
		assertEquals("Drop", model.getActionFromList(List.of("drop", "wield")));
		Map.Entry<String, Integer> sub = model.getIndexOfSubAction("UNCHARGE");
		assertEquals("Rub", sub.getKey());
		assertEquals(1, (int) sub.getValue());
		assertNull(model.getIndexOfSubAction("Teleport"));
		assertEquals("equal by id", model, Rs2Inventory.get(4151));
		assertEquals(model.hashCode(), Rs2Inventory.get(4151).hashCode());

		ItemComposition noted = definition(4152, "Abyssal whip");
		when(noted.getNote()).thenReturn(799);
		when(noted.isStackable()).thenReturn(true);
		when(noted.getLinkedNoteId()).thenReturn(4151);
		when(client.getItemDefinition(4152)).thenReturn(noted);
		assertEquals("noted stackable keeps its id", 4152, Rs2ItemModel.getNotedId(4152));
		assertEquals(4152, Rs2ItemModel.getNotedId(4151));
	}

	@Test
	public void equipmentNameAndSlotReads()
	{
		when(client.getItemDefinition(anyInt())).thenAnswer(i -> definition(i.getArgument(0), "Item"));
		ItemComposition whip = definition(4151, "Abyssal whip");
		ItemComposition helm = definition(1163, "Rune full helm");
		when(client.getItemDefinition(4151)).thenReturn(whip);
		when(client.getItemDefinition(1163)).thenReturn(helm);
		Item[] worn = new Item[14];
		for (int i = 0; i < worn.length; i++)
		{
			worn[i] = new Item(-1, 0);
		}
		assertFalse(Rs2Equipment.isWearing());
		worn[EquipmentInventorySlot.WEAPON.getSlotIdx()] = new Item(4151, 1);
		worn[EquipmentInventorySlot.HEAD.getSlotIdx()] = new Item(1163, 1);
		wear(worn);

		assertTrue(Rs2Equipment.isWearing());
		assertEquals(1163, Rs2Equipment.get().getId());
		assertEquals(4151, Rs2Equipment.get("whip", false).getId());
		assertNull(Rs2Equipment.get("whip", true));
		assertEquals(1, Rs2Equipment.all("rune full helm", true).count());
		String[] both = {"Abyssal whip", "Rune full helm"};
		EquipmentInventorySlot[] weaponAndHead = {EquipmentInventorySlot.WEAPON, EquipmentInventorySlot.HEAD};
		assertTrue(Rs2Equipment.isWearing(both, true, weaponAndHead));
		assertFalse(Rs2Equipment.isWearing(both, true, new EquipmentInventorySlot[]{EquipmentInventorySlot.WEAPON}));
		assertTrue("search all but body", Rs2Equipment.isWearing(both, true,
			new EquipmentInventorySlot[]{EquipmentInventorySlot.BODY}, false));
		assertFalse("exclude weapon", Rs2Equipment.isWearing(both, true,
			new EquipmentInventorySlot[]{EquipmentInventorySlot.WEAPON}, false));
		assertEquals(1, Rs2Equipment.all(EquipmentInventorySlot.WEAPON).count());
		assertEquals(2, Rs2Equipment.all(4151, 1163).count());
		assertEquals(2, Rs2Equipment.all("whip", "helm").count());
		assertEquals(1, Rs2Equipment.all(both, true).filter(i -> i.getId() == 4151).count());
		assertEquals(4151, Rs2Equipment.get(new String[]{"abyssal whip"}, true).getId());
		assertEquals(1163, Rs2Equipment.get("helm", "whip").getId());
		assertTrue(Rs2Equipment.isWearing(new String[]{"Dragon boots", "Abyssal whip"}, true));
		assertTrue(Rs2Equipment.isWearing("boots", "whip"));
		assertFalse(Rs2Equipment.isWearing("boots", "cape"));
		assertTrue(Rs2Equipment.isWearing(new String[]{"whip", "helm"}, weaponAndHead));
	}
}
