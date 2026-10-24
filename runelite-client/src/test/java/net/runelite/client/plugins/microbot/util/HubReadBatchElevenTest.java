package net.runelite.client.plugins.microbot.util;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Hub read batch 11 model reads against upstream 2.6.38 semantics. */
public class HubReadBatchElevenTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
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
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class), List.of(new MicrobotReadServices(client, thread, events)));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	private static Rs2ItemModel item(int id, String name, int quantity, int slot)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn(name);
		when(definition.getNote()).thenReturn(-1);
		when(definition.getInventoryActions()).thenReturn(new String[5]);
		return new Rs2ItemModel(new Item(id, quantity), definition, null, slot);
	}

	@Test
	public void matchesPredicatesFollowUpstream()
	{
		Rs2ItemModel law = item(563, "Law rune", 10, 3);
		Predicate<Rs2ItemModel> contains = Rs2ItemModel.matches(false, "law");
		assertTrue(contains.test(law));
		assertFalse(Rs2ItemModel.matches(true, "law").test(law));
		assertTrue(Rs2ItemModel.matches(true, "Nature rune", "law RUNE").test(law));
		assertTrue(Rs2ItemModel.matches(561, 563).test(law));
		assertFalse(Rs2ItemModel.matches(561).test(law));
		assertTrue("slot 3 is the weapon slot index", Rs2ItemModel.matches(EquipmentInventorySlot.WEAPON).test(law));
		assertFalse(Rs2ItemModel.matches(EquipmentInventorySlot.CAPE).test(law));
	}

	@Test
	public void priceIsGePriceTimesQuantityOnTheClientThread() throws Exception
	{
		Rs2ItemModel law = item(563, "Law rune", 10, 3);
		assertEquals("no item manager", 0L, law.getPrice());
		ItemManager items = mock(ItemManager.class);
		when(items.getItemPrice(563)).thenAnswer(i ->
		{
			assertTrue("read on the client thread", onClientThread.get());
			return 150L;
		});
		java.lang.reflect.Field field = net.runelite.client.plugins.microbot.Microbot.class.getDeclaredField("itemManager");
		field.setAccessible(true);
		Object previous = field.get(null);
		field.set(null, items);
		try
		{
			assertEquals(1500L, law.getPrice());
		}
		finally
		{
			field.set(null, previous);
		}
	}

	@Test
	public void sleepUntilNotInDialogueReturnsAtOnceWithoutADialogue()
	{
		long start = System.nanoTime();
		assertTrue(net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.sleepUntilNotInDialogue());
		assertTrue((System.nanoTime() - start) / 1_000_000 < 1_000);
	}

	@Test
	public void runeliteNpcIsTheWrappedNpc()
	{
		NPC npc = mock(NPC.class);
		assertSame(npc, new Rs2NpcModel(npc).getRuneliteNpc());
	}
}
