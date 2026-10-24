package net.runelite.client.plugins.microbot.util.bank;

import java.util.List;
import java.util.function.Predicate;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Round-7 Hub actions (banksshopper): NPC interaction, banking at a banker, deposit by names, bank-and-return. */
public class HubActionRoundSevenTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final WorldPoint HERE = new WorldPoint(3200, 3200, 0);
	private Rs2NpcModel banker;
	private Client client;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenReturn(true);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		Player player = mock(Player.class);
		when(player.getWorldLocation()).thenReturn(HERE);
		when(client.getLocalPlayer()).thenReturn(player);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
		NPC npc = mock(NPC.class);
		when(npc.getId()).thenReturn(1613);
		when(npc.getIndex()).thenReturn(17);
		when(npc.getName()).thenReturn("Banker");
		banker = new Rs2NpcModel(npc);
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	@Test
	public void npcInteractionUsesTheNpcsIdAndIndex()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.npcAt(anyInt(), anyInt(), anyString())).thenReturn(true);
			actions.clearInvocations();
			assertTrue(Rs2Npc.interact(banker, "Bank"));
			actions.verify(() -> MobileActions.npcAt(1613, 17, "Bank"));
			assertFalse(Rs2Npc.interact((Rs2NpcModel) null, "Bank"));
			assertFalse(Rs2Npc.interact(banker, " "));
		}
	}

	@Test
	public void aBankerIsAskedToBankThenTheBankIsAwaited()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Npc> npcs = mockStatic(Rs2Npc.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class, CALLS_REAL_METHODS))
		{
			actions.when(MobileActions::active).thenReturn(true);
			when(client.isClientThread()).thenReturn(false); // waits run on the script thread only
			npcs.when(() -> Rs2Npc.interact(any(Rs2NpcModel.class), anyString())).thenReturn(true);
			bank.when(Rs2Bank::isOpen).thenReturn(false, true);
			npcs.clearInvocations();
			assertTrue(Rs2Bank.openBank(banker));
			npcs.verify(() -> Rs2Npc.interact(banker, "Bank"));

			bank.when(Rs2Bank::isOpen).thenReturn(true);
			npcs.clearInvocations();
			assertTrue("already open: no interaction", Rs2Bank.openBank(banker));
			npcs.verify(() -> Rs2Npc.interact(any(Rs2NpcModel.class), anyString()), never());
			bank.when(Rs2Bank::isOpen).thenReturn(false);
			assertFalse(Rs2Bank.openBank((Rs2NpcModel) null));
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void depositByNamesMatchesExactlyOrByContainment()
	{
		try (MockedStatic<Rs2DepositBox> box = mockStatic(Rs2DepositBox.class, CALLS_REAL_METHODS))
		{
			box.when(() -> Rs2DepositBox.depositAll(any(Predicate.class))).thenReturn(true);
			box.clearInvocations();
			Rs2ItemModel rune = mock(Rs2ItemModel.class);
			when(rune.getName()).thenReturn("Air rune");
			ArgumentCaptor<Predicate<Rs2ItemModel>> filter = ArgumentCaptor.forClass(Predicate.class);

			assertTrue(Rs2DepositBox.depositAll(List.of("air")));
			box.verify(() -> Rs2DepositBox.depositAll(filter.capture()));
			assertTrue("by containment, ignoring case", filter.getValue().test(rune));

			box.clearInvocations();
			assertTrue(Rs2DepositBox.depositAll(List.of("air"), true));
			box.verify(() -> Rs2DepositBox.depositAll(filter.capture()));
			assertFalse("exact needs the whole name", filter.getValue().test(rune));
			assertFalse(Rs2DepositBox.depositAll(List.of()));
		}
	}

	@Test
	public void withRoomTheScriptWalksBackInsteadOfBanking()
	{
		WorldPoint shop = new WorldPoint(3210, 3200, 0);
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Tile> tiles = mockStatic(Rs2Tile.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class, CALLS_REAL_METHODS))
		{
			inventory.when(Rs2Inventory::emptySlotCount).thenReturn(5);
			tiles.when(() -> Rs2Tile.isTileReachable(any())).thenReturn(true);
			walker.when(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt())).thenReturn(true);
			walker.clearInvocations();
			bank.clearInvocations();
			assertFalse("10 tiles away is not back yet", Rs2Bank.bankItemsAndWalkBackToOriginalPosition(List.of("Bucket"), shop));
			walker.verify(() -> Rs2Walker.walkTo(shop, 4));
			bank.verify(() -> Rs2Bank.walkToBankAndUseBank(any()), never());

			inventory.when(Rs2Inventory::emptySlotCount).thenReturn(0);
			bank.when(() -> Rs2Bank.walkToBankAndUseBank(any())).thenReturn(true);
			bank.when(() -> Rs2Bank.depositAll(anyString(), anyBoolean())).thenReturn(true);
			bank.when(() -> Rs2Bank.depositAll(anyInt())).thenReturn(true);
			assertFalse("full: banks this call", Rs2Bank.bankItemsAndWalkBackToOriginalPosition(List.of("Bucket", "1925"), shop));
			bank.verify(() -> Rs2Bank.depositAll("Bucket", false));
			bank.verify(() -> Rs2Bank.depositAll(1925));
		}
	}
}
