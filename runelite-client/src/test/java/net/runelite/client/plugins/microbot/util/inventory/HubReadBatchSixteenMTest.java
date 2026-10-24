package net.runelite.client.plugins.microbot.util.inventory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Read batch 16m: upstream 2.6.38's variation- and charge-aware item counts. */
public class HubReadBatchSixteenMTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final int NECKLACE_8 = 3853;
	private static final int NECKLACE_3 = 3863;
	private static final int NECKLACE_1 = 3867;

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final EventBus events = new EventBus();
	private Client client;
	private Object previousItemManager;
	private java.lang.reflect.Field itemManagerField;

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

		ItemComposition fallback = definition(-1, "Item");
		ItemComposition eight = definition(NECKLACE_8, "Games necklace(8)");
		ItemComposition three = definition(NECKLACE_3, "Games necklace(3)");
		ItemComposition one = definition(NECKLACE_1, "Games necklace(1)");
		when(client.getItemDefinition(anyInt())).thenReturn(fallback);
		when(client.getItemDefinition(NECKLACE_8)).thenReturn(eight);
		when(client.getItemDefinition(NECKLACE_3)).thenReturn(three);
		when(client.getItemDefinition(NECKLACE_1)).thenReturn(one);
		ItemManager items = mock(ItemManager.class);
		when(items.getItemComposition(NECKLACE_8)).thenReturn(eight);
		itemManagerField = Microbot.class.getDeclaredField("itemManager");
		itemManagerField.setAccessible(true);
		previousItemManager = itemManagerField.get(null);
		itemManagerField.set(null, items);

		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		Item[] carried = {new Item(NECKLACE_3, 1), new Item(NECKLACE_1, 1), new Item(995, 100)};
		ItemContainer inventory = mock(ItemContainer.class);
		when(inventory.getItems()).thenReturn(carried);
		when(client.getItemContainer(net.runelite.api.gameval.InventoryID.INV)).thenReturn(inventory);
		onClientThread.set(true);
		events.post(new ItemContainerChanged(net.runelite.api.gameval.InventoryID.INV, inventory));
		onClientThread.set(false);
	}

	@After
	public void tearDown() throws Exception
	{
		itemManagerField.set(null, previousItemManager);
		MicrobotRuntime.uninstall();
	}

	private static ItemComposition definition(int id, String name)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getId()).thenReturn(id);
		when(definition.getName()).thenReturn(name);
		when(definition.getNote()).thenReturn(-1);
		when(definition.getLinkedNoteId()).thenReturn(-1);
		when(definition.getInventoryActions()).thenReturn(new String[5]);
		return definition;
	}

	@Test
	public void chargedVariantsCountByName()
	{
		assertEquals(2, Rs2FuzzyItem.getChargedInventoryCount(NECKLACE_8, false, 0, 8));
		assertEquals("only the (3)", 1, Rs2FuzzyItem.getChargedInventoryCount(NECKLACE_8, false, 2, 8));
		assertEquals(4, Rs2FuzzyItem.getTotalCharges(NECKLACE_8));
		assertTrue(Rs2FuzzyItem.hasFuzzyItemAvailable(NECKLACE_8, false));
		assertEquals(NECKLACE_3, Rs2FuzzyItem.getBestFuzzyItemInInventory(NECKLACE_8, false).getId());
	}

	@Test
	public void nameHelpers()
	{
		assertEquals("Games necklace", Rs2FuzzyItem.getBaseItemNameFromString("Games necklace(8)"));
		assertEquals("", Rs2FuzzyItem.getBaseItemNameFromString(null));
		assertTrue(Rs2FuzzyItem.isChargedItem("Amulet of glory(4)"));
		assertFalse(Rs2FuzzyItem.isChargedItem("Amulet of glory"));
		assertTrue(Rs2FuzzyItem.isChargedItem(NECKLACE_8));
	}
}
