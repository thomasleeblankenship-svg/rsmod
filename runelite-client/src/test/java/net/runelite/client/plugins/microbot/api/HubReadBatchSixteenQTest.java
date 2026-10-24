package net.runelite.client.plugins.microbot.api;

import net.runelite.api.GameObject;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Read batch 16q: the five Hub-blocking reads (Claude 1's sweep after action batch AM). */
public class HubReadBatchSixteenQTest
{
	private ReadApiFixture f;

	@Before
	public void setUp() throws Exception
	{
		f = new ReadApiFixture();
	}

	@After
	public void tearDown() throws Exception
	{
		f.close();
	}

	private ItemComposition definition(int id, String name, int note, int linked, boolean tradeable)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getId()).thenReturn(id);
		when(definition.getName()).thenReturn(name);
		when(definition.getNote()).thenReturn(note);
		when(definition.getLinkedNoteId()).thenReturn(linked);
		when(definition.isTradeable()).thenReturn(tradeable);
		when(definition.getInventoryActions()).thenReturn(new String[5]);
		return definition;
	}

	@Test
	public void tileObjectModelIsATileObject()
	{
		GameObject rock = mock(GameObject.class);
		when(rock.getId()).thenReturn(11389);
		when(rock.getX()).thenAnswer(i -> f.onClientThread(640));
		when(rock.getWorldLocation()).thenAnswer(i -> f.onClientThread(new WorldPoint(3205, 3205, 0)));
		when(rock.getLocalLocation()).thenAnswer(i -> f.onClientThread(new LocalPoint(640, 640, -1)));
		when(rock.getWorldView()).thenReturn(f.topLevel);
		when(rock.isOpShown(0)).thenAnswer(i -> f.onClientThread(true));
		ObjectComposition composition = mock(ObjectComposition.class);
		when(composition.getName()).thenReturn("Rocks");
		when(composition.getActions()).thenReturn(new String[]{"Mine", "Prospect", null, null, null});
		when(f.client.getObjectDefinition(11389)).thenReturn(composition);

		Rs2TileObjectModel model = f.services.read(() -> new Rs2TileObjectModel(rock)).orElse(null);
		TileObject asTileObject = model;
		assertEquals(11389, asTileObject.getId());
		assertEquals("live delegate", 640, asTileObject.getX());
		assertTrue(asTileObject.isOpShown(0));
		assertTrue("the crafting call: hasAction on the model", Rs2GameObject.hasAction(model, "Mine"));
		assertFalse(Rs2GameObject.hasAction(model, "Chop down"));
	}

	@Test
	public void npcModelFromTheNpcAlone()
	{
		NPC fire = f.npc(10571, "Fire", new WorldPoint(3206, 3206, 0));
		Rs2NpcModel model = f.services.read(() -> new Rs2NpcModel(fire)).orElse(null);
		assertEquals(10571, model.getId());
		assertEquals("Fire", model.getName());
	}

	@Test
	public void itemModelConstructorQuantityAndInventoryIds()
	{
		ItemComposition fallback = definition(-1, "Item", -1, -1, false);
		ItemComposition notedLogs = definition(1512, "Logs", 799, 1511, false);
		ItemComposition logs = definition(1511, "Logs", -1, 1512, true);
		when(f.client.getItemDefinition(anyInt())).thenReturn(fallback);
		when(f.client.getItemDefinition(1512)).thenReturn(notedLogs);
		when(f.client.getItemDefinition(1511)).thenReturn(logs);

		Rs2ItemModel noted = f.services.read(() -> new Rs2ItemModel(new Item(1512, 40), notedLogs, 3)).orElse(null);
		assertEquals(3, noted.getSlot());
		assertEquals(40, noted.getQuantity());
		assertTrue("tradeable through the linked definition", noted.isTradeable());
		noted.setQuantity(12);
		assertEquals(12, noted.getQuantity());

		Item[] carried = {new Item(995, 100), new Item(1511, 1)};
		ItemContainer inventory = mock(ItemContainer.class);
		when(inventory.getItems()).thenReturn(carried);
		when(f.client.getItemContainer(net.runelite.api.gameval.InventoryID.INV)).thenReturn(inventory);
		f.services.read(() ->
		{
			f.eventBus.post(new ItemContainerChanged(net.runelite.api.gameval.InventoryID.INV, inventory));
			return true;
		});
		assertEquals(1511, Rs2Inventory.get(4, 1511, 1521).getId());
		assertNull(Rs2Inventory.get(4, 5, 6));
	}
}
