package net.runelite.client.plugins.microbot.mobile;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real dispatch selection and revocable session against mocked engine interfaces, not a live game. */
public class MobileInventoryDispatchTest
{
	private Client client;
	private Widget inventory;
	private Widget itemWidget;
	private ItemComposition definition;
	private ScriptInputSession session;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenReturn(true);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getLocalPlayer()).thenReturn(mock(Player.class));
		when(client.getTickCount()).thenReturn(100);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
		session = new ScriptInputSession();
		MobileActions.bind(session);
		ItemContainer items = mock(ItemContainer.class);
		when(items.getItems()).thenReturn(new Item[]{new Item(1511, 1)});
		when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(items);
		inventory = mock(Widget.class);
		when(inventory.getId()).thenReturn(WidgetInfo.INVENTORY.getId());
		when(client.getWidget(WidgetInfo.INVENTORY)).thenReturn(inventory);
		itemWidget = mock(Widget.class);
		when(inventory.getChild(0)).thenReturn(itemWidget);
		when(itemWidget.getItemId()).thenReturn(1511);
		when(itemWidget.getActions()).thenReturn(new String[]{null, null, "Use", "Drop"});
		definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn("Logs");
		when(definition.getInventoryActions()).thenReturn(new String[]{null, null, "Use", "Drop"});
		when(client.getItemDefinition(1511)).thenReturn(definition);
	}

	@After
	public void tearDown()
	{
		MobileActions.unbind(session);
		MicrobotRuntime.uninstall();
	}

	@Test
	public void inventoryDropUsesModernWidgetOpcodeAndLiveActionOrdinal()
	{
		assertTrue(MobileActions.inventory(0, "Drop"));
		verify(client).menuAction(0, WidgetInfo.INVENTORY.getId(), MenuAction.CC_OP, 4, 1511, "Drop", "Logs");
	}

	@Test
	public void bankInventoryContextTakesPriorityForBasketEmpty()
	{
		Widget bankSide = mock(Widget.class);
		when(bankSide.getId()).thenReturn(WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId());
		when(bankSide.getChild(0)).thenReturn(itemWidget);
		when(client.getWidget(WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER)).thenReturn(bankSide);
		when(itemWidget.getActions()).thenReturn(new String[]{"Deposit-1", "Deposit-5", "Deposit-All", "Empty"});
		assertTrue(MobileActions.inventory(0, "Empty"));
		verify(client).menuAction(0, WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId(), MenuAction.CC_OP,
			4, 1511, "Empty", "Logs");
	}

	@Test
	public void widgetActionOverridesDefinitionOrdinal()
	{
		when(itemWidget.getActions()).thenReturn(new String[]{"Drop", "Use"});
		assertTrue(MobileActions.inventory(0, "Drop"));
		verify(client).menuAction(0, WidgetInfo.INVENTORY.getId(), MenuAction.CC_OP, 1, 1511, "Drop", "Logs");
	}

	@Test
	public void bankWithdrawalUsesCurrentApiContainerNotScrollbar()
	{
		Widget bank = mock(Widget.class);
		when(client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER.getId())).thenReturn(bank);
		when(bank.getChild(0)).thenReturn(itemWidget);
		when(itemWidget.getActions()).thenReturn(new String[]{"Withdraw-1", "Withdraw-5", "Withdraw-All"});
		ItemContainer bankItems = mock(ItemContainer.class);
		when(bankItems.getItems()).thenReturn(new Item[]{new Item(1511, 100)});
		when(client.getItemContainer(InventoryID.BANK)).thenReturn(bankItems);
		assertTrue(MobileActions.bank(0, "Withdraw-All"));
		verify(client).menuAction(0, WidgetInfo.BANK_ITEM_CONTAINER.getId(), MenuAction.CC_OP,
			3, 1511, "Withdraw-All", null);
	}

	@Test
	public void staleInventoryWidgetCannotTargetReplacementItem()
	{
		when(itemWidget.getItemId()).thenReturn(1351);
		assertFalse(MobileActions.inventory(0, "Drop"));
		verify(client, never()).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), any(), any());
	}

	@Test
	public void pendingSelectionCannotTurnDropIntoUse()
	{
		when(client.isWidgetSelected()).thenReturn(true);
		assertFalse(MobileActions.inventory(0, "Drop"));
		verify(client, never()).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), any(), any());
	}

	@Test
	public void useSelectedItemTargetsWidgetRatherThanReselecting()
	{
		when(client.isWidgetSelected()).thenReturn(true);
		assertTrue(MobileActions.inventory(0, "Use"));
		verify(client).menuAction(0, WidgetInfo.INVENTORY.getId(), MenuAction.WIDGET_TARGET_ON_WIDGET,
			0, 1511, "Use", "Logs");
	}

	@Test
	public void stoppedSessionAndHiddenWidgetCannotDispatch()
	{
		when(itemWidget.isHidden()).thenReturn(true);
		assertFalse(MobileActions.inventory(0, "Drop"));
		when(itemWidget.isHidden()).thenReturn(false);
		session.close();
		assertFalse(MobileActions.inventory(0, "Drop"));
		verify(client, never()).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), any(), any());
	}

	@Test
	public void bankPlaceholderCannotBeWithdrawn()
	{
		Widget bank = mock(Widget.class);
		when(client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER.getId())).thenReturn(bank);
		when(bank.getChild(0)).thenReturn(itemWidget);
		when(itemWidget.getActions()).thenReturn(new String[]{"Withdraw-All"});
		ItemContainer bankItems = mock(ItemContainer.class);
		when(bankItems.getItems()).thenReturn(new Item[]{new Item(1511, 0)});
		when(client.getItemContainer(InventoryID.BANK)).thenReturn(bankItems);
		assertFalse(MobileActions.bank(0, "Withdraw-All"));
		verify(client, never()).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), any(), any());
	}

	@Test
	public void oneActionPerTickBudgetAppliesToInventory()
	{
		assertTrue(MobileActions.inventory(0, "Drop"));
		assertFalse(MobileActions.inventory(0, "Drop"));
		verify(client, times(1)).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), any(), any());
	}
	@Test
	public void capturedExpectedIdRejectsEvenConsistentReplacementWidget()
	{
		ItemContainer replacement = mock(ItemContainer.class);
		when(replacement.getItems()).thenReturn(new Item[]{new Item(1351, 1)});
		when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(replacement);
		when(itemWidget.getItemId()).thenReturn(1351);
		assertFalse(MobileActions.inventory(0, 1511, "Drop"));
		verify(client, never()).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), any(), any());
	}

	@Test
	public void capturedBankExpectedIdRejectsReplacementStack()
	{
		Widget bank = mock(Widget.class);
		when(client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER.getId())).thenReturn(bank);
		when(bank.getChild(0)).thenReturn(itemWidget);
		when(itemWidget.getItemId()).thenReturn(1351);
		when(itemWidget.getActions()).thenReturn(new String[]{"Withdraw-All"});
		ItemContainer bankItems = mock(ItemContainer.class);
		when(bankItems.getItems()).thenReturn(new Item[]{new Item(1351, 1)});
		when(client.getItemContainer(InventoryID.BANK)).thenReturn(bankItems);
		assertFalse(MobileActions.bank(0, 1511, "Withdraw-All"));
		verify(client, never()).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), any(), any());
	}

	@Test
	public void hiddenInventoryIsOpenedWithoutSendingAnItemAction()
	{
		when(inventory.isHidden()).thenReturn(true);
		assertFalse(MobileActions.ensureInventoryVisible());
		verify(client).runScript(915, 3);
		verify(client, never()).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), any(), any());
		when(inventory.isHidden()).thenReturn(false);
		assertTrue(MobileActions.ensureInventoryVisible());
		assertFalse(MobileActions.inventory(0, "Drop")); // Opening the panel consumed this tick's input.
		when(client.getTickCount()).thenReturn(101);
		assertTrue(MobileActions.inventory(0, "Drop"));
	}

	@Test
	public void cancelledSessionCannotOpenInventory()
	{
		when(inventory.isHidden()).thenReturn(true);
		session.close();
		assertFalse(MobileActions.ensureInventoryVisible());
		verify(client, never()).runScript(any(Object[].class));
	}

	@Test
	public void missingWidgetActionsMapDefinitionDropToWidgetSevenRatherThanUseFive()
	{
		when(itemWidget.getActions()).thenReturn(null);
		when(definition.getInventoryActions()).thenReturn(new String[]{null, null, null, null, "Drop"});
		assertTrue(MobileActions.inventory(0, 1511, "Drop"));
		verify(client).menuAction(0, WidgetInfo.INVENTORY.getId(), MenuAction.CC_OP_LOW_PRIORITY, 7, 1511, "Drop", "Logs");
	}

	@Test
	public void emptyWidgetActionsAlsoUseTheDefinitionToWidgetMapping()
	{
		when(itemWidget.getActions()).thenReturn(new String[0]);
		when(definition.getInventoryActions()).thenReturn(new String[]{null, null, null, null, "Drop"});
		assertTrue(MobileActions.inventory(0, 1511, "Drop"));
		verify(client).menuAction(0, WidgetInfo.INVENTORY.getId(), MenuAction.CC_OP_LOW_PRIORITY, 7, 1511, "Drop", "Logs");
	}

	@Test
	public void axeDropIsRefusedEvenIfCallerOmitsTheExpectedId()
	{
		when(definition.getName()).thenReturn("Dragon felling axe");
		assertFalse(MobileActions.inventory(0, "Drop"));
		assertEquals("Axes are protected from dropping", MobileActions.inventoryFailure());
		verify(client, never()).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), any(), any());
	}

}
