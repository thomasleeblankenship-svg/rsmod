package net.runelite.client.plugins.microbot.util.inventory;

import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Upstream potion reads used by AmmoniteCrabs, with mocked RuneLite interfaces. */
public class Rs2InventoryPotionReadTest
{
	private final EventBus events = new EventBus();
	private Client client;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenReturn(true);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	private void define(int id, String name, String... actions)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn(name);
		when(definition.getNote()).thenReturn(-1);
		String[] ops = new String[5];
		System.arraycopy(actions, 0, ops, 0, actions.length);
		when(definition.getInventoryActions()).thenReturn(ops);
		when(client.getItemDefinition(id)).thenReturn(definition);
	}

	private static List<String> names(List<Rs2ItemModel> items)
	{
		return items.stream().map(Rs2ItemModel::getName).collect(Collectors.toList());
	}

	@Test
	public void potionsAreDrinkableItemsMatchedWithoutTheirDoseSuffix()
	{
		define(2436, "Super attack(4)", "Drink", null, null, null, "Drop");
		define(149, "Super attack(1)", "Drink", null, null, null, "Drop");
		define(2440, "Super strength(4)", "Drink", null, null, null, "Drop");
		define(385, "Shark", "Eat", null, null, null, "Drop");
		define(1, "Super attack mix", null, null, null, null, "Drop");
		ItemContainer inventory = mock(ItemContainer.class);
		when(inventory.getItems()).thenReturn(new Item[]{
			new Item(2436, 1), new Item(-1, 0), new Item(149, 1), new Item(2440, 1), new Item(385, 1), new Item(1, 1)});
		events.post(new ItemContainerChanged(InventoryID.INV, inventory));

		assertEquals(List.of("Super attack(4)", "Super attack(1)", "Super strength(4)"), names(Rs2Inventory.getPotions()));
		assertEquals(List.of("Super attack(4)", "Super attack(1)"),
			names(Rs2Inventory.getFilteredPotionItemsInInventory("super ATTACK")));
		assertEquals(List.of("Super attack(4)", "Super attack(1)", "Super strength(4)"),
			names(Rs2Inventory.getFilteredPotionItemsInInventory(List.of("Super attack", "Super strength"))));
		assertTrue("prefix is not a match", Rs2Inventory.getFilteredPotionItemsInInventory("Super").isEmpty());
		assertTrue("not drinkable", Rs2Inventory.getFilteredPotionItemsInInventory("Shark").isEmpty());
	}
}
