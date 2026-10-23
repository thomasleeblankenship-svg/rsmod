package net.runelite.client.plugins.microbot.util.magic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.magic.thralls.Rs2Thrall;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Magic reads: spellbook and autocast varbits, rune counting from inventory, staff and pouch. */
public class Rs2MagicReadTest
{
	static
	{
		// JavaBridge captures this flag on first load; set it before any test reaches the bridge.
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final EventBus events = new EventBus();
	private Client client;
	private Item[] worn = new Item[14];

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		// LOGIN_SCREEN keeps the varbit cache from holding values between assertions.
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(client.getVarbitValue(anyInt())).thenAnswer(i -> varbits.getOrDefault((int) i.getArgument(0), 0));
		when(client.getRealSkillLevel(Skill.MAGIC)).thenReturn(99);
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn("Item");
		when(definition.getInventoryActions()).thenReturn(new String[5]);
		when(client.getItemDefinition(anyInt())).thenReturn(definition);
		for (int i = 0; i < worn.length; i++)
		{
			worn[i] = new Item(-1, 0);
		}
		ItemContainer equipment = mock(ItemContainer.class);
		when(equipment.getItems()).thenAnswer(i -> worn);
		when(client.getItemContainer(net.runelite.api.InventoryID.EQUIPMENT)).thenReturn(equipment);
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

	private void inventory(Item... items)
	{
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		when(client.getItemContainer(InventoryID.INV)).thenReturn(container);
		when(client.getItemContainer(net.runelite.api.InventoryID.INVENTORY)).thenReturn(container);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		onClientThread.set(true);
		try { events.post(new ItemContainerChanged(InventoryID.INV, container)); }
		finally { onClientThread.set(false); }
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
	}

	@Test
	public void spellbookAndAutocastFollowTheVarbits()
	{
		assertEquals(Rs2Spellbook.MODERN, Rs2Magic.getSpellbook());
		varbits.put(VarbitID.SPELLBOOK, 2);
		assertTrue(Rs2Magic.isSpellbook(Rs2Spellbook.LUNAR));

		varbits.put(VarbitID.SPELLBOOK, 0);
		varbits.put(VarbitID.AUTOCAST_SPELL, Rs2CombatSpells.WIND_STRIKE.getVarbitValue());
		assertEquals(Rs2CombatSpells.WIND_STRIKE, Rs2Magic.getCurrentAutoCastSpell());
		varbits.put(VarbitID.AUTOCAST_SPELL, 0);
		assertNull(Rs2Magic.getCurrentAutoCastSpell());

		varbits.put(VarbitID.ARCEUUS_SHADOW_VEIL_ACTIVE, 1);
		assertTrue(Rs2Magic.isShadowVeilActive());
		assertFalse(Rs2Thrall.isActive());
		varbits.put(VarbitID.ARCEUUS_RESURRECTION_ACTIVE, 1);
		assertTrue(Rs2Magic.isThrallActive());
	}

	@Test
	public void runesComeFromInventoryStaffAndComboRunes()
	{
		inventory(new Item(ItemID.LAWRUNE, 1), new Item(ItemID.EARTHRUNE, 5));
		assertFalse("Camelot needs air and law", Rs2Magic.hasRequiredRunes(Rs2Spells.CAMELOT_TELEPORT));

		inventory(new Item(ItemID.LAWRUNE, 1), new Item(ItemID.DUSTRUNE, 5));
		assertTrue("dust runes count as air", Rs2Magic.hasRequiredRunes(Rs2Spells.CAMELOT_TELEPORT));
		Map<Runes, Integer> runes = Rs2Magic.getRunes();
		assertEquals(5, (int) runes.get(Runes.AIR));
		assertEquals(5, (int) runes.get(Runes.EARTH));

		inventory(new Item(ItemID.LAWRUNE, 1), new Item(ItemID.AIRRUNE, 5));
		assertTrue(Rs2Magic.hasRequiredRunes(Rs2Spells.CAMELOT_TELEPORT));
		assertTrue(Rs2Magic.canCast(Rs2Spells.CAMELOT_TELEPORT));

		inventory(new Item(ItemID.LAWRUNE, 1));
		assertEquals(Map.of(Runes.AIR, 5), Rs2Magic.getMissingRunes(Rs2Spells.CAMELOT_TELEPORT));
		worn[EquipmentInventorySlot.WEAPON.getSlotIdx()] = new Item(ItemID.STAFF_OF_AIR, 1);
		assertTrue("the staff supplies unlimited air", Rs2Magic.hasRequiredRunes(Rs2Spells.CAMELOT_TELEPORT));
		assertEquals(Rs2Staff.STAFF_OF_AIR, Rs2Magic.getRs2Staff(ItemID.STAFF_OF_AIR));
	}

	@Test
	public void wrongSpellbookFailsCanCast()
	{
		inventory(new Item(ItemID.LAWRUNE, 1), new Item(ItemID.AIRRUNE, 5));
		varbits.put(VarbitID.SPELLBOOK, 1);
		assertFalse(Rs2Magic.canCast(Rs2Spells.CAMELOT_TELEPORT));
	}

	@Test
	public void lookupsAndRuneData()
	{
		assertEquals(Rs2Spells.HIGH_LEVEL_ALCHEMY, Rs2Magic.getRs2Spell("high level"));
		assertEquals(Runes.NATURE, Runes.byItemId(ItemID.NATURERUNE));
		assertTrue(Runes.MIST.providesRune(Runes.WATER));
		assertTrue(Rs2Staff.LAVA_BATTLESTAFF.provides(Runes.LAVA));
		assertEquals(Rs2Spellbook.MODERN, Rs2Spells.HIGH_LEVEL_ALCHEMY.getSpellbook());
	}
}
