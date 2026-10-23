package net.runelite.client.plugins.microbot.util;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Round 2 items (4)-(6): in combat, full health, distance, equipment by slot. */
public class RoundTwoReadsTest
{
	static
	{
		// JavaBridge captures this flag on first load; set it before any test reaches the bridge.
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private Client client;
	private Player player;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		player = mock(Player.class);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
		when(player.getAnimation()).thenReturn(-1);
		when(client.getLocalPlayer()).thenReturn(player);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			onClientThread.set(true);
			try { ((Runnable) call.getArgument(0)).run(); }
			finally { onClientThread.set(false); }
			return null;
		}).when(thread).invoke(any(Runnable.class));
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	@Test
	public void inCombatNeedsAFightableTargetAndActivity()
	{
		assertFalse(Rs2Combat.inCombat());

		NPC goblin = mock(NPC.class);
		when(goblin.getCombatLevel()).thenReturn(2);
		when(player.getInteracting()).thenReturn(goblin);
		assertFalse("targeted but neither animating nor interacting", Rs2Combat.inCombat());
		when(player.isInteracting()).thenReturn(true);
		assertTrue(Rs2Combat.inCombat());

		NPC banker = mock(NPC.class);
		when(player.getInteracting()).thenReturn(banker);
		assertFalse("no combat level", Rs2Combat.inCombat());
	}

	@Test
	public void healthAndDistance()
	{
		when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(80);
		when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(80);
		assertTrue(Rs2Player.isFullHealth());
		when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(79);
		assertFalse(Rs2Player.isFullHealth());

		assertEquals(5, Rs2Player.distanceTo(new WorldPoint(3205, 3203, 0)));
		assertEquals(Integer.MAX_VALUE, Rs2Player.distanceTo(new WorldPoint(3200, 3200, 1)));
		assertEquals(Integer.MAX_VALUE, Rs2Player.distanceTo(null));
	}

	@Test
	public void equipmentBySlotAndPredicate()
	{
		ItemComposition whip = mock(ItemComposition.class);
		when(whip.getName()).thenReturn("Abyssal whip");
		when(whip.getInventoryActions()).thenReturn(new String[5]);
		when(client.getItemDefinition(4151)).thenReturn(whip);
		Item[] worn = new Item[14];
		for (int i = 0; i < worn.length; i++)
		{
			worn[i] = new Item(-1, 0);
		}
		worn[EquipmentInventorySlot.WEAPON.getSlotIdx()] = new Item(4151, 1);
		ItemContainer equipment = mock(ItemContainer.class);
		when(equipment.getItems()).thenReturn(worn);
		when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);

		assertEquals(4151, Rs2Equipment.get(EquipmentInventorySlot.WEAPON).getId());
		assertEquals(4151, Rs2Equipment.get(EquipmentInventorySlot.SHIELD, EquipmentInventorySlot.WEAPON).getId());
		assertNull(Rs2Equipment.get(EquipmentInventorySlot.HEAD));
		assertEquals("Abyssal whip", Rs2Equipment.get(item -> item.getName().contains("whip")).getName());
		assertNull(Rs2Equipment.get(item -> item.getId() == 1));
	}
}
