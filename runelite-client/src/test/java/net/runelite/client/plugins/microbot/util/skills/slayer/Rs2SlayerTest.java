package net.runelite.client.plugins.microbot.util.skills.slayer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Upstream's slayer task reads against a mocked client (varplayers and the slayer DB tables). */
public class Rs2SlayerTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final int SPECTRE_TASK = 41;
	private static final int SPECTRE_ROW = 7041;
	private final AtomicInteger target = new AtomicInteger();
	private final AtomicInteger count = new AtomicInteger();
	private Client client;

	@Before
	public void setUp() throws Exception
	{
		Rs2NpcManager.loadJson();
		client = mock(Client.class);
		when(client.isClientThread()).thenReturn(true);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarps()).thenReturn(new int[4000]);
		when(client.getVarpValue(anyInt())).thenAnswer(i -> {
			int varp = i.getArgument(0);
			return varp == VarPlayerID.SLAYER_TARGET ? target.get() : varp == VarPlayerID.SLAYER_COUNT ? count.get() : 0;
		});
		when(client.getDBRowsByValue(eq(DBTableID.SlayerTask.ID), eq(DBTableID.SlayerTask.COL_ID), eq(0), eq(SPECTRE_TASK)))
			.thenReturn(List.of(SPECTRE_ROW));
		when(client.getDBTableField(SPECTRE_ROW, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0))
			.thenReturn(new Object[]{"Aberrant spectres"});
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
		Rs2Slayer.blacklistedSlayerMonsters.clear();
	}

	@Test
	public void withoutATaskEverythingIsEmpty()
	{
		assertFalse(Rs2Slayer.hasSlayerTask());
		assertNull(Rs2Slayer.getSlayerTask());
		assertNull(Rs2Slayer.getSlayerMonsters());
		assertEquals(-1, Rs2Slayer.getSlayerTaskWeakness());
		assertNull(Rs2Slayer.getSlayerTaskProtectiveEquipment());
		assertNull(Rs2Slayer.getSlayerTaskLocation());
	}

	@Test
	public void theTaskComesFromTheVarplayersAndTheSlayerTables()
	{
		target.set(SPECTRE_TASK);
		count.set(87);
		assertTrue(Rs2Slayer.hasSlayerTask());
		assertEquals(87, Rs2Slayer.getSlayerTaskSize());
		assertEquals("Aberrant spectres", Rs2Slayer.getSlayerTask());
		List<String> monsters = Rs2Slayer.getSlayerMonsters();
		assertTrue(monsters.toString(), monsters.contains("Aberrant spectre"));
		Rs2Slayer.blacklistedSlayerMonsters.add("Aberrant spectre");
		assertFalse(Rs2Slayer.getSlayerMonsters().contains("Aberrant spectre"));
		assertEquals("Nose peg", Rs2Slayer.getSlayerTaskProtectiveEquipment());
		assertFalse("spectres have no finishing item", Rs2Slayer.hasSlayerTaskWeakness());
	}

	@Test
	public void aTaskWithAFinishingItemReportsIt()
	{
		target.set(SPECTRE_TASK);
		when(client.getDBTableField(SPECTRE_ROW, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)).thenReturn(new Object[]{"Gargoyles"});
		ItemComposition hammer = mock(ItemComposition.class);
		when(hammer.getName()).thenReturn("Rock hammer");
		when(client.getItemDefinition(ItemID.SLAYER_ROCK_HAMMER)).thenReturn(hammer);
		assertTrue(Rs2Slayer.hasSlayerTaskWeakness());
		assertEquals(ItemID.SLAYER_ROCK_HAMMER, Rs2Slayer.getSlayerTaskWeakness());
		assertEquals("Rock hammer", Rs2Slayer.getSlayerTaskWeaknessName());
		assertEquals(9, Rs2Slayer.getSlayerTaskWeaknessThreshold());
	}
}
