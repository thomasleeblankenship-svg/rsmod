package net.runelite.client.plugins.microbot.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.equipment.JewelleryLocationEnum;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.misc.SpecialAttackWeaponEnum;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Hub read batch 5 against upstream 2.6.38 semantics, with mocked RuneLite interfaces. */
public class HubReadBatchFiveTest
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
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
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

	private static List<Rs2ItemModel> slots(int... slots)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn("Logs");
		when(definition.getNote()).thenReturn(-1);
		List<Rs2ItemModel> items = new ArrayList<>();
		for (int slot : slots)
		{
			items.add(new Rs2ItemModel(new Item(1511, 1), definition, null, slot));
		}
		return items;
	}

	private static List<Integer> order(InteractOrder order, int... slots)
	{
		return Rs2Inventory.calculateInteractOrder(slots(slots), order).stream()
			.map(Rs2ItemModel::getSlot).collect(Collectors.toList());
	}

	@Test
	public void interactOrdersMatchUpstream()
	{
		int[] firstRows = {7, 6, 5, 4, 3, 2, 1, 0, 8, 11};
		assertEquals(List.of(0, 1, 2, 3, 7, 6, 5, 4, 8, 11), order(InteractOrder.EFFICIENT_ROW, firstRows));
		assertEquals(List.of(0, 4, 8, 1, 5, 2, 6, 3, 7, 11), order(InteractOrder.COLUMN, firstRows));
		assertEquals(List.of(0, 4, 8, 5, 1, 2, 6, 11, 7, 3), order(InteractOrder.EFFICIENT_COLUMN, firstRows));
		assertEquals(List.of(0, 4, 1, 5, 2, 6, 3, 7, 11, 8), order(InteractOrder.ZIGZAG, firstRows));
		assertEquals(List.of(27, 26, 25, 24), order(InteractOrder.ZIGZAG, 24, 25, 26, 27));
		assertEquals(List.of(3, 1, 2), order(InteractOrder.STANDARD, 3, 1, 2));
		assertEquals("RANDOM leaves the list as upstream does", List.of(3, 1, 2), order(InteractOrder.RANDOM, 3, 1, 2));

		List<Rs2ItemModel> list = slots(1, 0);
		assertSame("sorted in place", list, Rs2Inventory.calculateInteractOrder(list, InteractOrder.COLUMN));
		assertEquals(0, list.get(0).getSlot());
	}

	@Test
	public void selectedItemIdIsReadOnTheClientThread()
	{
		assertEquals(-1, Rs2Inventory.getSelectedItemId());
		Widget selected = mock(Widget.class);
		when(selected.getItemId()).thenReturn(1511);
		when(client.getSelectedWidget()).thenAnswer(i ->
		{
			assertTrue("read on the client thread", onClientThread.get());
			return selected;
		});
		assertEquals(1511, Rs2Inventory.getSelectedItemId());
	}

	private void varbit(int id, int value)
	{
		VarbitChanged event = new VarbitChanged();
		event.setVarbitId(id);
		event.setValue(value);
		onClientThread.set(true);
		try { events.post(event); }
		finally { onClientThread.set(false); }
	}

	@Test
	public void potionTimersFollowTheirVarbitsAndClearOnLogout()
	{
		assertFalse(Rs2Player.hasAntiFireActive());
		varbit(Varbits.SUPER_ANTIFIRE, 30);
		assertTrue("super antifire counts as antifire", Rs2Player.hasAntiFireActive());
		assertTrue(Rs2Player.hasSuperAntiFireActive());
		varbit(Varbits.DIVINE_BASTION, 50);
		assertTrue("bastion counts as divine ranged", Rs2Player.hasDivineRangedActive());
		assertTrue(Rs2Player.hasDivineBastionActive());
		varbit(Varbits.DIVINE_SUPER_COMBAT, 12);
		varbit(Varbits.DIVINE_MAGIC, 3);
		varbit(Varbits.BUFF_GOADING_POTION, 4);
		varbit(Varbits.MOONLIGHT_POTION, 5);
		assertTrue(Rs2Player.hasDivineCombatActive());
		assertTrue(Rs2Player.hasDivineMagicActive());
		assertTrue(Rs2Player.hasGoadingActive());
		assertTrue(Rs2Player.hasMoonlightActive());
		varbit(Varbits.DIVINE_SUPER_COMBAT, 0);
		assertFalse(Rs2Player.hasDivineCombatActive());

		Rs2Player.clear();
		assertFalse(Rs2Player.hasAntiFireActive());
		assertFalse(Rs2Player.hasDivineRangedActive());
		assertFalse(Rs2Player.hasMoonlightActive());
	}

	private void poisonVarp(int value)
	{
		VarbitChanged event = new VarbitChanged();
		event.setVarpId(net.runelite.api.VarPlayer.POISON);
		event.setValue(value);
		onClientThread.set(true);
		try { events.post(event); }
		finally { onClientThread.set(false); }
	}

	@Test
	public void poisonVarpDrivesUpstreamsAntiPoisonAndAntiVenomReads()
	{
		assertFalse(Rs2Player.hasAntiPoisonActive());
		assertFalse(Rs2Player.hasAntiVenomActive());
		poisonVarp(5);
		assertTrue("upstream: a positive varp (poisoned) reads as active", Rs2Player.hasAntiPoisonActive());
		assertFalse(Rs2Player.hasAntiVenomActive());
		poisonVarp(-40);
		assertTrue("below the venom cutoff", Rs2Player.hasAntiVenomActive());
		assertFalse(Rs2Player.hasAntiPoisonActive());
		poisonVarp(-38);
		assertFalse("at the cutoff", Rs2Player.hasAntiVenomActive());
		poisonVarp(0);
		assertFalse(Rs2Player.hasAntiPoisonActive());
		poisonVarp(-60);
		Rs2Player.clear();
		assertFalse(Rs2Player.hasAntiVenomActive());

		when(client.getVarbitValue(net.runelite.api.gameval.VarbitID.PRAYER_REGENERATION_POTION_TIMER)).thenReturn(12);
		assertTrue(Rs2Player.hasPrayerRegenerationActive());
	}

	@Test
	public void boostChecksCompareAgainstTheRealLevelPlusThreshold()
	{
		when(client.getRealSkillLevel(Skill.ATTACK)).thenReturn(90);
		when(client.getBoostedSkillLevel(Skill.ATTACK)).thenReturn(95);
		assertTrue(Rs2Player.hasAttackActive(4));
		assertFalse(Rs2Player.hasAttackActive(5));
		when(client.getRealSkillLevel(Skill.RANGED)).thenReturn(80);
		when(client.getBoostedSkillLevel(Skill.RANGED)).thenReturn(80);
		assertFalse(Rs2Player.hasRangingPotionActive(0));
	}

	@Test
	public void upstreamEnumsAreCopiedUnchanged()
	{
		assertEquals(500, SpecialAttackWeaponEnum.ABYSSAL_WHIP.getEnergyRequired());
		assertEquals("dragon 2h sword", SpecialAttackWeaponEnum.DRAGON_2H_SWORD.getName());
		assertTrue(JewelleryLocationEnum.values().length > 0);
	}
}
