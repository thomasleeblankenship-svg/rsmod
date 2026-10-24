package net.runelite.client.plugins.microbot.util.inventory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Round-6 Hub actions: combining models, selling to a shop, exact text clicks, walking with run set. */
public class HubActionRoundSixTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final int PIPE = 1785;
	private static final int GLASS = 1775;

	private Client client;
	private final AtomicBoolean clientThread = new AtomicBoolean(true);
	private final EventBus events = new EventBus();

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> clientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			clientThread.set(true);
			try { ((Runnable) call.getArgument(0)).run(); }
			finally { clientThread.set(false); }
			return null;
		}).when(thread).invoke(any(Runnable.class));
		MicrobotReadServices services = new MicrobotReadServices(client, thread, events);
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class), List.of(services));
		define(PIPE, "Glassblowing pipe");
		define(GLASS, "Molten glass");
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(new Item[]{new Item(GLASS, 1), new Item(PIPE, 1), new Item(GLASS, 1)});
		when(client.getItemContainer(InventoryID.INV)).thenReturn(container);
		events.post(new ItemContainerChanged(InventoryID.INV, container));
		clientThread.set(false);
	}

	@After
	public void tearDown() { MicrobotRuntime.uninstall(); }

	private void define(int id, String name)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn(name);
		when(definition.getNote()).thenReturn(-1);
		when(client.getItemDefinition(id)).thenReturn(definition);
	}

	@Test
	public void combiningModelsUsesTheFirstOnAnotherSlotOfTheSecond()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.itemOnItem(anyInt(), anyInt())).thenReturn(true);
			actions.clearInvocations();
			assertTrue(Rs2Inventory.combine(Rs2Inventory.get(PIPE), Rs2Inventory.get(GLASS)));
			actions.verify(() -> MobileActions.itemOnItem(1, 0));
			actions.clearInvocations();
			assertTrue("the same id twice uses two different slots", Rs2Inventory.combine(Rs2Inventory.get(GLASS), Rs2Inventory.get(GLASS)));
			actions.verify(() -> MobileActions.itemOnItem(0, 2));
			assertFalse(Rs2Inventory.combine(null, Rs2Inventory.get(GLASS)));
		}
	}

	@Test
	public void sellingUsesTheShopSideInventorysSellOption()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(MobileActions::active).thenReturn(true);
			actions.when(() -> MobileActions.widget(anyInt(), anyInt(), anyString())).thenReturn(true);
			actions.clearInvocations();
			assertTrue(Rs2Inventory.sellItem("Glassblowing pipe", "1"));
			actions.verify(() -> MobileActions.widget(InterfaceID.Shopside.ITEMS, 1, "Sell 1"));
			assertTrue(Rs2Inventory.sellItem(GLASS, "5"));
			actions.verify(() -> MobileActions.widget(InterfaceID.Shopside.ITEMS, 0, "Sell 5"));
			assertFalse("names match exactly", Rs2Inventory.sellItem("pipe", "1"));
		}
	}

	@Test
	public void exactTextClicksDoNotMatchSubstrings()
	{
		Widget make = mock(Widget.class);
		when(make.getText()).thenReturn("<col=ff9040>Make Beer glass</col>");
		when(make.getId()).thenReturn(17694734);
		when(make.getIndex()).thenReturn(-1);
		Widget[] roots = {make};
		when(client.getWidgetRoots()).thenReturn(roots);
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.widget(anyInt(), anyInt(), anyString())).thenReturn(true);
			actions.clearInvocations();
			assertFalse(Rs2Widget.clickWidget("Beer glass", true));
			assertTrue(Rs2Widget.clickWidget("Beer glass", false));
			assertTrue(Rs2Widget.clickWidget("make beer glass", true));
			actions.verify(() -> MobileActions.widget(17694734, -1, ""), times(2));
		}
	}

	@Test
	public void walkFastCanvasSetsRunThenWalks()
	{
		WorldPoint there = new WorldPoint(3200, 3200, 0);
		try (MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class, CALLS_REAL_METHODS))
		{
			player.when(() -> Rs2Player.toggleRunEnergy(anyBoolean())).thenReturn(true);
			walker.when(() -> Rs2Walker.walkFastCanvas(any(WorldPoint.class))).thenReturn(true);
			player.clearInvocations();
			walker.clearInvocations();
			assertTrue(Rs2Walker.walkFastCanvas(there, false));
			player.verify(() -> Rs2Player.toggleRunEnergy(false));
			walker.verify(() -> Rs2Walker.walkFastCanvas(eq(there)));
		}
	}
}
