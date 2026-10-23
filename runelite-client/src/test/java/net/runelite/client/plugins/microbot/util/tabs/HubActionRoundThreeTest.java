package net.runelite.client.plugins.microbot.util.tabs;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Round-3 Hub actions: tab switching, area checks and food choice. */
public class HubActionRoundThreeTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private Client client;
	private final AtomicBoolean clientThread = new AtomicBoolean(true);
	private final EventBus events = new EventBus();
	private volatile int panel = InterfaceTab.INVENTORY.getVarcIntIndex();

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
		when(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)).thenAnswer(call -> panel);
		Player player = mock(Player.class);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
		when(client.getLocalPlayer()).thenReturn(player);
		clientThread.set(false);
	}

	@After
	public void tearDown() { MicrobotRuntime.uninstall(); }

	private void define(int id, String name, String... actions)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn(name);
		when(definition.getInventoryActions()).thenReturn(actions);
		when(definition.getNote()).thenReturn(-1);
		when(client.getItemDefinition(id)).thenReturn(definition);
	}

	private void inventory(Item... items)
	{
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items);
		when(client.getItemContainer(InventoryID.INV)).thenReturn(container);
		clientThread.set(true);
		try { events.post(new ItemContainerChanged(InventoryID.INV, container)); }
		finally { clientThread.set(false); }
	}

	@Test
	public void currentTabIsReadFromThePanelVarcAndSwitchingRunsTheTabScript()
	{
		assertEquals(InterfaceTab.INVENTORY, Rs2Tab.getCurrentTab());
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(MobileActions::active).thenReturn(true);
			actions.when(() -> MobileActions.openTab(InterfaceTab.PRAYER.getVarcIntIndex())).thenAnswer(call ->
			{
				panel = InterfaceTab.PRAYER.getVarcIntIndex();
				return true;
			});
			actions.clearInvocations();
			assertTrue(Rs2Tab.switchTo(InterfaceTab.PRAYER));
			assertTrue(Rs2Tab.switchTo(InterfaceTab.PRAYER));
			actions.verify(() -> MobileActions.openTab(anyInt()), times(1));
		}
	}

	@Test
	public void aTabWithoutAPanelIndexIsNotSwitched()
	{
		assertFalse(Rs2Tab.switchTo(InterfaceTab.NOTHING_SELECTED));
	}

	@Test
	public void isInAreaUsesTheBoxSpannedByTwoCornersInEitherOrder()
	{
		assertTrue(Rs2Walker.isInArea(new WorldPoint(3205, 3195, 0), new WorldPoint(3195, 3205, 0)));
		assertTrue(Rs2Walker.isInArea(new WorldPoint(3200, 3200, 0), 0));
		assertFalse(Rs2Walker.isInArea(new WorldPoint(3201, 3201, 0), new WorldPoint(3210, 3210, 0)));
	}

	@Test
	public void useFoodEatsTheFirstRegularFoodOutsideTheWilderness()
	{
		define(379, "Lobster", "Eat", null, null, null, "Drop");
		define(1993, "Jug of wine", "Drink", null, null, null, "Drop");
		inventory(new Item(1993, 1), new Item(379, 1));
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.inventory(anyInt(), anyInt(), any())).thenReturn(true);
			actions.clearInvocations();
			assertTrue(Rs2Player.useFood());
			actions.verify(() -> MobileActions.inventory(0, 1993, "Drink"));
		}
	}

	@Test
	public void useFoodIsFalseWithoutFood()
	{
		define(995, "Coins", null, null, null, null, "Drop");
		inventory(new Item(995, 10));
		assertFalse(Rs2Player.useFood());
		assertTrue(Rs2ItemModel.class.getName().endsWith("Rs2ItemModel"));
	}
}
