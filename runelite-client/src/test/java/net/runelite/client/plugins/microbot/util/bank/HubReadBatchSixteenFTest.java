package net.runelite.client.plugins.microbot.util.bank;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.AnimationID;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Read batch 16f (remaining bank reads and the antiban activity reads) against upstream 2.6.38 semantics. */
public class HubReadBatchSixteenFTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final Map<Integer, ItemComposition> definitions = new HashMap<>();
	private final EventBus events = new EventBus();
	private Client client;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		// LOGIN_SCREEN keeps the varbit cache from holding values between assertions.
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(client.getVarbitValue(anyInt())).thenAnswer(i -> varbits.getOrDefault((int) i.getArgument(0), 0));
		ItemComposition fallback = definition(-1, "Item", -1);
		when(client.getItemDefinition(anyInt())).thenAnswer(i -> definitions.getOrDefault((int) i.getArgument(0), fallback));
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
		Rs2Bank.clearMemory();
		Rs2Bank.resetBankLiveEpoch();
	}

	@After
	public void tearDown()
	{
		Rs2Bank.clearMemory();
		MicrobotRuntime.uninstall();
	}

	private static ItemComposition definition(int id, String name, int linked)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getId()).thenReturn(id);
		when(definition.getName()).thenReturn(name);
		when(definition.getNote()).thenReturn(-1);
		when(definition.getLinkedNoteId()).thenReturn(linked);
		when(definition.getInventoryActions()).thenReturn(new String[5]);
		return definition;
	}

	private void define(int id, String name, int linked)
	{
		definitions.put(id, definition(id, name, linked));
	}

	private void bank(int... ids)
	{
		Item[] items = new Item[ids.length];
		for (int i = 0; i < ids.length; i++)
		{
			items[i] = new Item(ids[i], 5);
		}
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		post(new ItemContainerChanged(InventoryID.BANK.getId(), container));
	}

	private void post(Object event)
	{
		onClientThread.set(true);
		try
		{
			events.post(event);
		}
		finally
		{
			onClientThread.set(false);
		}
	}

	@Test
	public void openingTimeAndSnapshotFollowEvents()
	{
		assertEquals(0L, Rs2Bank.getBankLastOpenedAt());
		assertFalse(Rs2Bank.hasBankMirrorSnapshot());
		WidgetLoaded other = new WidgetLoaded();
		other.setGroupId(InterfaceID.BANKMAIN + 1);
		post(other);
		assertEquals(0L, Rs2Bank.getBankLastOpenedAt());
		long before = System.currentTimeMillis();
		WidgetLoaded bank = new WidgetLoaded();
		bank.setGroupId(InterfaceID.BANKMAIN);
		post(bank);
		assertTrue(Rs2Bank.getBankLastOpenedAt() >= before);
		bank(1511);
		assertTrue(Rs2Bank.hasBankMirrorSnapshot());
	}

	@Test
	public void itemLookupsBySubstringAndSavedId()
	{
		define(1511, "Logs", 1512);
		define(1521, "Oak logs", -1);
		define(4151, "Abyssal whip", 4152);
		define(4152, "Abyssal whip", 4151);
		define(9999, "Oak logs", -1);
		bank(1521, 1511, 4152);
		assertEquals("shortest contains match", 1511, Rs2Bank.findBankItem("LOGS").getId());
		assertNull(Rs2Bank.findBankItem("maple"));
		assertEquals("direct id", 1521, Rs2Bank.getBankItemForSavedId(1521).getId());
		assertEquals("linked note id", 4152, Rs2Bank.getBankItemForSavedId(4151).getId());
		assertEquals("drifted id by exact name", 1521, Rs2Bank.getBankItemForSavedId(9999).getId());
		assertNull(Rs2Bank.getBankItemForSavedId(0));
	}

	@Test
	public void modeTabsCountAndLockedSlots()
	{
		assertTrue(Rs2Bank.isWithdrawAs(false));
		varbits.put(VarbitID.BANK_WITHDRAWNOTES, 1);
		assertTrue(Rs2Bank.isWithdrawAs(true));
		assertTrue(Rs2Bank.isMainTabOpen());
		varbits.put(VarbitID.BANK_CURRENTTAB, 2);
		assertFalse(Rs2Bank.isMainTabOpen());

		assertEquals(0, Rs2Bank.getBankItemCount());
		Widget count = mock(Widget.class);
		when(count.getText()).thenReturn(" 312 ");
		when(client.getWidget(InterfaceID.Bankmain.OCCUPIEDSLOTS)).thenReturn(count);
		assertEquals(312, Rs2Bank.getBankItemCount());
		when(count.getText()).thenReturn("x");
		assertEquals(0, Rs2Bank.getBankItemCount());

		Widget bar = mock(Widget.class);
		Widget[] tabs = new Widget[12];
		for (int i = 0; i < tabs.length; i++)
		{
			tabs[i] = mock(Widget.class);
		}
		when(bar.getDynamicChildren()).thenReturn(tabs);
		when(client.getWidget(ComponentID.BANK_TAB_CONTAINER)).thenReturn(bar);
		assertSame(tabs[11], Rs2Bank.getTabWidget(1));
		assertNull(Rs2Bank.getTabWidget(2));
		assertNull(Rs2Bank.getTabWidget(-1));

		Widget side = mock(Widget.class);
		Widget open = mock(Widget.class);
		Widget locked = mock(Widget.class);
		when(open.getActions()).thenReturn(new String[]{"Deposit-1", null});
		when(locked.getActions()).thenReturn(new String[]{null, "Unlock-slot"});
		when(side.getChildren()).thenReturn(new Widget[]{open, locked, null});
		when(client.getWidget(ComponentID.BANK_INVENTORY_ITEM_CONTAINER)).thenReturn(side);
		assertTrue(Rs2Bank.isLockedSlot(1));
		assertFalse(Rs2Bank.isLockedSlot(0));
		assertFalse(Rs2Bank.isLockedSlot(28));
		assertEquals(List.of(1), Rs2Bank.findLockedSlots());
	}

	@Test
	public void antibanActivityFollowsTheAnimation()
	{
		Player player = mock(Player.class);
		when(client.getLocalPlayer()).thenReturn(player);
		assertFalse(Rs2Antiban.isWoodcutting());
		assertFalse(Rs2Antiban.isMining());
		when(player.getAnimation()).thenReturn(AnimationID.WOODCUTTING_RUNE);
		assertTrue(Rs2Antiban.isWoodcutting());
		assertFalse(Rs2Antiban.isMining());
		when(player.getAnimation()).thenReturn(AnimationID.MINING_MOTHERLODE_DRAGON);
		assertTrue(Rs2Antiban.isMining());
		assertTrue(Rs2Antiban.MINING_ANIMATION_IDS.contains(AnimationID.MINING_CRASHEDSTAR_RUNE));
	}
}
