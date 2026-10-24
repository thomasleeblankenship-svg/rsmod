package net.runelite.client.plugins.microbot.util.inventorysetup;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.inventorysetups.ConfigInventorySetupDataManager;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util-gap action batch O: saving the current gear as an inventory setup, in upstream's V3 format. */
public class HubActionBatchOTest
{
	private static final String GROUP = "inventorysetups";

	private final AtomicBoolean clientThread = new AtomicBoolean(false);
	private final Map<String, String> stored = new ConcurrentHashMap<>();
	private Client client;
	private ConfigManager config;
	private MInventorySetupsPlugin plugin;

	private static void set(Object target, String name, Object value) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

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
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));

		config = mock(ConfigManager.class);
		doAnswer(call -> stored.put(call.getArgument(1), call.getArgument(2)))
			.when(config).setConfiguration(eq(GROUP), anyString(), anyString());
		doAnswer(call -> stored.remove(call.<String>getArgument(1)))
			.when(config).unsetConfiguration(eq(GROUP), anyString());
		when(config.getConfiguration(eq(GROUP), anyString())).thenAnswer(call -> stored.get(call.<String>getArgument(1)));
		when(config.getConfigurationKeys(anyString())).thenAnswer(call -> stored.keySet().stream()
			.filter(k -> k.startsWith("setupsV3_")).map(k -> GROUP + "." + k).collect(Collectors.toList()));

		ConfigInventorySetupDataManager manager = new ConfigInventorySetupDataManager();
		set(manager, "gson", net.runelite.http.api.RuneLiteAPI.GSON);
		plugin = new MInventorySetupsPlugin();
		set(plugin, "configManager", config);
		set(plugin, "dataManager", manager);
		java.lang.reflect.Method startUp = MInventorySetupsPlugin.class.getDeclaredMethod("startUp");
		startUp.setAccessible(true);
		startUp.invoke(plugin);
	}

	@After
	public void tearDown() throws Exception
	{
		java.lang.reflect.Method shutDown = MInventorySetupsPlugin.class.getDeclaredMethod("shutDown");
		shutDown.setAccessible(true);
		shutDown.invoke(plugin);
		MicrobotRuntime.uninstall();
	}

	private void carry(int containerId, Item... items)
	{
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		when(client.getItemContainer(containerId)).thenReturn(container);
	}

	@Test
	public void theCurrentGearIsSavedAndReplacesASetupOfTheSameName()
	{
		ItemComposition shark = mock(ItemComposition.class);
		when(shark.getName()).thenReturn("Shark");
		when(client.getItemDefinition(385)).thenReturn(shark);
		ItemComposition whip = mock(ItemComposition.class);
		when(whip.getName()).thenReturn("Abyssal whip");
		when(client.getItemDefinition(4151)).thenReturn(whip);
		when(client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(1);
		carry(InventoryID.INV, new Item(385, 5));
		carry(InventoryID.WORN, new Item(-1, 0), new Item(-1, 0), new Item(-1, 0), new Item(4151, 1));

		plugin.addInventorySetup("cache");
		List<InventorySetup> setups = MInventorySetupsPlugin.getInventorySetups();
		assertEquals(1, setups.size());
		InventorySetup saved = setups.get(0);
		assertEquals("cache", saved.getName());
		assertEquals(28, saved.getInventory().size());
		assertEquals(385, saved.getInventory().get(0).getId());
		assertEquals(5, saved.getInventory().get(0).getQuantity());
		assertEquals(-1, saved.getInventory().get(1).getId());
		assertEquals(4151, saved.getEquipment().get(3).getId());
		assertEquals(1, saved.getSpellBook());

		carry(InventoryID.INV, new Item(4151, 1));
		plugin.addInventorySetup("CACHE");
		setups = MInventorySetupsPlugin.getInventorySetups();
		assertEquals("replaced, not added", 1, setups.size());
		assertEquals("cache", setups.get(0).getName());
		assertEquals(4151, setups.get(0).getInventory().get(0).getId());

		plugin.addInventorySetup("");
		assertEquals(1, MInventorySetupsPlugin.getInventorySetups().size());
	}
}
