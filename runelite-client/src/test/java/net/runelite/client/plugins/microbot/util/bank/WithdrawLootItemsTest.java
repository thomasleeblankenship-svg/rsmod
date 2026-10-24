package net.runelite.client.plugins.microbot.util.bank;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.loottracker.LootTrackerItem;
import net.runelite.client.plugins.loottracker.LootTrackerRecord;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Upstream's withdrawLootItems: tradeable loot, noted, except food and the kept items. */
public class WithdrawLootItemsTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final int RUNE_PLATELEGS = 1079;
	private static final int SHARK = 385;
	private static final int NOTED_BONES = 537;
	private static final int BONES = 536;
	private static final int CLUE = 2722;
	private Client client;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenReturn(true);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
		define(RUNE_PLATELEGS, true, -1, "Wear");
		define(SHARK, true, -1, "Eat");
		define(NOTED_BONES, false, 799, null);
		define(BONES, true, -1, "Bury");
		define(CLUE, false, -1, "Read");
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	private void define(int id, boolean tradeable, int note, String action)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.isTradeable()).thenReturn(tradeable);
		when(definition.getNote()).thenReturn(note);
		when(definition.getInventoryActions()).thenReturn(new String[]{action, null, null, null, "Drop"});
		when(client.getItemDefinition(id)).thenReturn(definition);
	}

	private static LootTrackerItem item(int id, String name)
	{
		return new LootTrackerItem(id, name, 1, 0, 0, false);
	}

	@Test
	public void tradeableLootIsWithdrawnExceptFoodAndKeptItems()
	{
		LootTrackerRecord vorkath = new LootTrackerRecord("Vorkath", "", LootRecordType.NPC, new LootTrackerItem[]{
			item(RUNE_PLATELEGS, "Rune platelegs"), item(SHARK, "Shark"), item(NOTED_BONES, "Superior dragon bones"),
			item(CLUE, "Clue scroll"), item(RUNE_PLATELEGS, "Rune platelegs")}, 1);
		LootTrackerRecord other = new LootTrackerRecord("Zulrah", "", LootRecordType.NPC,
			new LootTrackerItem[]{item(BONES, "Bones")}, 1);
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2GrandExchange> ge = mockStatic(Rs2GrandExchange.class);
			MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class, CALLS_REAL_METHODS))
		{
			microbot.when(Microbot::getAggregateLootRecords).thenReturn(List.of(other, vorkath));
			ge.when(Rs2GrandExchange::walkToGrandExchange).thenReturn(true);
			bank.when(Rs2Bank::openBank).thenReturn(true);
			bank.when(Rs2Bank::depositAll).thenReturn(true);
			bank.when(Rs2Bank::setWithdrawAsNote).thenReturn(true);
			bank.when(() -> Rs2Bank.withdrawAll(anyInt())).thenReturn(true);
			bank.when(Rs2Bank::closeBank).thenReturn(true);
			bank.clearInvocations();

			assertTrue(Rs2Bank.withdrawLootItems("vorkath", List.of(" rune platelegs ")));
			bank.verify(Rs2Bank::depositAll);
			bank.verify(() -> Rs2Bank.withdrawAll(BONES), times(1));
			bank.verify(() -> Rs2Bank.withdrawAll(anyInt()), times(1));
			bank.verify(Rs2Bank::closeBank);
		}
	}

	@Test
	public void withUpstreamsEmptyRecordsNothingIsWithdrawn()
	{
		assertTrue(Microbot.getAggregateLootRecords().isEmpty());
		try (MockedStatic<Rs2GrandExchange> ge = mockStatic(Rs2GrandExchange.class);
			MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class, CALLS_REAL_METHODS))
		{
			ge.when(Rs2GrandExchange::walkToGrandExchange).thenReturn(true);
			bank.when(Rs2Bank::openBank).thenReturn(false);
			bank.clearInvocations();
			assertFalse("the bank did not open", Rs2Bank.withdrawLootItems("Vorkath", List.of()));
			bank.verify(Rs2Bank::depositAll, never());
		}
	}
}
