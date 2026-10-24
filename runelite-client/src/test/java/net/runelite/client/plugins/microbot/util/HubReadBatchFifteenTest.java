package net.runelite.client.plugins.microbot.util;

import java.awt.Rectangle;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.death.Rs2Death;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
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

/** Hub read batch 15: death recovery item reads and the deposit box bounds. */
public class HubReadBatchFifteenTest
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
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn("Item");
		when(definition.getNote()).thenReturn(-1);
		when(definition.getInventoryActions()).thenReturn(new String[5]);
		when(client.getItemDefinition(anyInt())).thenReturn(definition);
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

	private static Widget slot(int itemId, int quantity)
	{
		Widget slot = mock(Widget.class);
		when(slot.getItemId()).thenReturn(itemId);
		when(slot.getItemQuantity()).thenReturn(quantity);
		return slot;
	}

	@Test
	public void deathsOfficeItemsComeFromTheOpenRetrievalInterface()
	{
		assertFalse(Rs2Death.isDeathsOfficeOpen());
		assertTrue(Rs2Death.getDeathsOfficeItems().isEmpty());
		Widget office = mock(Widget.class);
		when(client.getWidget(InterfaceID.DeathOffice.ITEMS_CONTAINER)).thenReturn(office);
		Widget items = mock(Widget.class);
		Widget[] slots = {slot(4151, 1), slot(-1, 0), slot(995, 0)};
		when(items.getDynamicChildren()).thenReturn(slots);
		when(client.getWidget(InterfaceID.DeathOffice.ITEMS)).thenReturn(items);
		assertTrue(Rs2Death.isDeathsOfficeOpen());
		List<Rs2ItemModel> held = Rs2Death.getDeathsOfficeItems();
		assertEquals(2, held.size());
		assertEquals(4151, held.get(0).getId());
		assertEquals("slot is the child index", 2, held.get(1).getSlot());
		assertEquals("a zero stack counts as one", 1, held.get(1).getQuantity());

		Widget free = mock(Widget.class);
		Widget[] bones = {slot(526, 3)};
		when(free.getDynamicChildren()).thenReturn(bones);
		when(client.getWidget(InterfaceID.GravestoneGeneric.FREEITEMS)).thenReturn(free);
		assertEquals(3, Rs2Death.getGraveFreeItems().get(0).getQuantity());
		assertTrue(Rs2Death.getGravePaidItems().isEmpty());
	}

	@Test
	public void depositBoxBoundsOnlyWhileOpen()
	{
		assertNull(Rs2DepositBox.getDepositBoxBounds());
		Widget root = mock(Widget.class);
		when(root.getBounds()).thenReturn(new Rectangle(5, 6, 300, 200));
		when(client.getWidget(192, 0)).thenReturn(root);
		assertEquals(new Rectangle(5, 6, 300, 200), Rs2DepositBox.getDepositBoxBounds());
		when(root.isHidden()).thenReturn(true);
		assertNull(Rs2DepositBox.getDepositBoxBounds());
	}
}
