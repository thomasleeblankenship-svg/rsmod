package net.runelite.client.plugins.microbot.util;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Hub read batch 4 against upstream semantics, with mocked RuneLite interfaces. */
public class HubReadBatchFourTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final int BONES = 526;
	private static final int NOTED_BONES = 527;
	private static final int KNIFE = 946;

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final EventBus events = new EventBus();
	private Client client;
	private Player player;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
		player = mock(Player.class);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
		when(client.getLocalPlayer()).thenReturn(player);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			onClientThread.set(true);
			try { ((Runnable) call.getArgument(0)).run(); }
			finally { onClientThread.set(false); }
			return null;
		}).when(thread).invoke(any(Runnable.class));
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	private void define(int id, String name, boolean noted)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn(name);
		when(definition.getNote()).thenReturn(noted ? 799 : -1);
		when(definition.getInventoryActions()).thenReturn(new String[5]);
		when(client.getItemDefinition(id)).thenReturn(definition);
	}

	private void inventory(Item... items)
	{
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		onClientThread.set(true);
		try { events.post(new ItemContainerChanged(net.runelite.api.gameval.InventoryID.INV, container)); }
		finally { onClientThread.set(false); }
	}

	@Test
	public void prayerReads()
	{
		when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(30);
		when(client.getRealSkillLevel(Skill.PRAYER)).thenReturn(43);
		assertTrue(Rs2Player.hasPrayerPoints());
		assertEquals(69, Rs2Player.getPrayerPercentage());
		when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(0);
		assertFalse(Rs2Player.hasPrayerPoints());
	}

	@Test
	public void areaLocationAndStunReads()
	{
		assertTrue(Rs2Player.isInArea(new WorldPoint(3203, 3198, 0), 3));
		assertFalse(Rs2Player.isInArea(new WorldPoint(3204, 3200, 0), 3));
		assertTrue(Rs2Player.isInArea(new WorldPoint(3204, 3201, 0), 4, 1));
		assertFalse("other floor", Rs2Player.isInArea(new WorldPoint(3200, 3200, 1), 3));
		assertFalse(Rs2Player.isInArea(null, 3));
		assertFalse(Rs2Player.isInArea(new WorldPoint(3200, 3200, 0), -1));

		LocalPoint local = new LocalPoint(6400, 6400, -1);
		when(player.getLocalLocation()).thenReturn(local);
		assertSame(local, Rs2Player.getLocalLocation());

		assertFalse(Rs2Player.isStunned());
		when(player.hasSpotAnim(245)).thenReturn(true);
		assertTrue(Rs2Player.isStunned());
		when(client.getLocalPlayer()).thenReturn(null);
		assertFalse(Rs2Player.isStunned());
		assertNull(Rs2Player.getLocalLocation());
	}

	@Test
	public void wildernessLevelsFollowClientScript384()
	{
		assertEquals(0, Rs2Pvp.getWildernessLevelFrom(new WorldPoint(3200, 3200, 0)));
		assertEquals("wilderness starts at y=3525", 0, Rs2Pvp.getWildernessLevelFrom(new WorldPoint(3100, 3523, 0)));
		assertEquals(1, Rs2Pvp.getWildernessLevelFrom(new WorldPoint(3100, 3527, 0)));
		assertEquals(20, Rs2Pvp.getWildernessLevelFrom(new WorldPoint(3100, 3677, 0)));
		assertEquals("Ferox Enclave is safe", 0, Rs2Pvp.getWildernessLevelFrom(new WorldPoint(3135, 3630, 0)));
		assertEquals("Callisto's den", 40, Rs2Pvp.getWildernessLevelFrom(new WorldPoint(3350, 10330, 0)));
	}

	@Test
	public void specEnergyIsTheVarp()
	{
		// The varp cache only reads ids inside the client's varp array (2.6.38 range guard).
		when(client.getVarps()).thenReturn(new int[4000]);
		when(client.getVarpValue(anyInt())).thenAnswer(i -> (int) i.getArgument(0) == VarPlayerID.SA_ENERGY ? 750 : 0);
		assertEquals(750, Rs2Combat.getSpecEnergy());
	}

	@Test
	public void equipmentItemsListsWornSlots()
	{
		define(KNIFE, "Knife", false);
		ItemContainer worn = mock(ItemContainer.class);
		when(worn.getItems()).thenReturn(new Item[]{new Item(-1, 0), new Item(KNIFE, 1)});
		when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(worn);
		assertEquals(1, Rs2Equipment.items().size());
		assertEquals(KNIFE, Rs2Equipment.items().get(0).getId());
	}

	@Test
	public void inventoryReads()
	{
		define(BONES, "Bones", false);
		define(NOTED_BONES, "Bones", true);
		define(KNIFE, "Knife", false);
		inventory(new Item(BONES, 1), new Item(NOTED_BONES, 40), new Item(-1, 0), new Item(KNIFE, 1));

		assertEquals(28, Rs2Inventory.capacity());
		assertEquals("three of 28 slots hold items", 25, Rs2Inventory.getEmptySlots());
		assertTrue(Rs2Inventory.contains(item -> item.getName().toLowerCase().contains("knif")));
		assertFalse(Rs2Inventory.contains(item -> item.getId() == 1));
		assertTrue(Rs2Inventory.slotContains(3, KNIFE));
		assertTrue(Rs2Inventory.slotContains(3, "knife"));
		assertFalse(Rs2Inventory.slotContains(2, KNIFE));
		assertTrue(Rs2Inventory.hasNotedItem("bones"));
		assertTrue(Rs2Inventory.hasUnNotedItem("bones"));
		assertEquals(BONES, Rs2Inventory.getUnNotedItem("Bones", true).getId());
		assertEquals(NOTED_BONES, Rs2Inventory.getNotedItem("bon", false).getId());
		assertFalse(Rs2Inventory.hasNotedItem("knife"));
	}
}
