package net.runelite.client.plugins.microbot.pouch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.headless.JavaBridge;
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real inventory dispatch and unchanged tracker; the mocked engine emits its menu/inventory/chat events. */
public class PouchActionsTest
{
	static { System.setProperty("runelite.bridge", "true"); }
	private final AtomicBoolean onClient = new AtomicBoolean();
	private final AtomicInteger actions = new AtomicInteger();
	private Client client;
	private EventBus events;
	private ScriptInputSession owner;
	private Widget inventory;
	private int pouchId = ItemID.RCU_POUCH_SMALL;
	private int essence;
	private int essenceId = ItemID.BLANKRUNE_HIGH;
	private int level = 99;
	private int tick = 100;
	private int checkAmount = 1;
	private boolean observe = true;
	private Runnable afterDispatch = () -> {};

	@Before
	public void setUp() throws Exception
	{
		JavaBridge.setScriptInputSuspended(false);
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClient.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getTickCount()).thenAnswer(call -> tick);
		when(client.getLocalPlayer()).thenReturn(mock(Player.class));
		when(client.getRealSkillLevel(Skill.RUNECRAFT)).thenAnswer(call -> { assertTrue(onClient.get()); return level; });
		when(client.getBoostedSkillLevel(Skill.RUNECRAFT)).thenAnswer(call -> { assertTrue(onClient.get()); return level; });
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			boolean previous = onClient.get();
			onClient.set(true);
			try { ((Runnable) call.getArgument(0)).run(); }
			finally { onClient.set(previous); }
			return null;
		}).when(thread).invoke(any(Runnable.class));
		events = spy(new EventBus());
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
		owner = new ScriptInputSession();
		MobileActions.bind(owner);
		inventory = mock(Widget.class);
		when(inventory.getId()).thenReturn(WidgetInfo.INVENTORY.getId());
		when(client.getWidget(WidgetInfo.INVENTORY)).thenReturn(inventory);
		Widget item = mock(Widget.class);
		when(item.getItemId()).thenAnswer(call -> pouchId);
		when(item.getActions()).thenReturn(new String[]{"Fill", "Empty", "Check"});
		when(inventory.getChild(0)).thenReturn(item);
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn("Essence pouch");
		when(client.getItemDefinition(anyInt())).thenReturn(definition);
		doAnswer(call ->
		{
			assertTrue(onClient.get());
			assertEquals(pouchId, (int) call.getArgument(4));
			assertEquals(MenuAction.CC_OP, call.getArgument(2));
			String option = call.getArgument(5);
			actions.incrementAndGet();
			clicked(option);
			afterDispatch.run();
			if (observe)
			{
				if (option.equals("Fill")) inventory(Math.max(0, essence - Pouch.forItem(pouchId).getRemaining()));
				else if (option.equals("Empty")) inventory(essence + Pouch.forItem(pouchId).getHolding());
				else if (option.equals("Check")) checkedMessage(checkAmount);
				else fail("Unexpected option " + option);
			}
			tick++;
			return null;
		}).when(client).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), anyString(), anyString());
		inventory(5);
		GameStateChanged login = new GameStateChanged();
		login.setGameState(GameState.LOGGED_IN);
		post(login);
	}

	private void post(Object event)
	{
		boolean previous = onClient.get();
		onClient.set(true);
		try { events.post(event); }
		finally { onClient.set(previous); }
	}

	private void inventory(int amount)
	{
		essence = amount;
		List<Item> items = new ArrayList<>();
		if (pouchId >= 0) items.add(new Item(pouchId, 1));
		for (int i = 0; i < amount; i++) items.add(new Item(essenceId, 1));
		while (items.size() < 28) items.add(new Item(-1, 0));
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(items.toArray(new Item[0]));
		when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(container);
		when(client.getItemContainer(net.runelite.api.gameval.InventoryID.INV)).thenReturn(container);
		post(new ItemContainerChanged(net.runelite.api.gameval.InventoryID.INV, container));
	}

	private void clicked(String option)
	{
		MenuEntry entry = mock(MenuEntry.class);
		when(entry.getItemId()).thenReturn(pouchId);
		when(entry.getOption()).thenReturn(option);
		post(new MenuOptionClicked(entry));
	}

	private void checkedMessage(int count)
	{
		String[] words = {"no", "one", "two", "three", "four", "five", "six"};
		post(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "",
			"There " + (count <= 1 ? "is " : "are ") + words[count]
				+ " pure essence" + (count > 1 ? "s" : "") + " in this pouch.", "", 0));
	}

	private void known(int count)
	{
		clicked("Check");
		checkedMessage(count);
	}

	@After
	public void tearDown()
	{
		onClient.set(false);
		MobileActions.unbind(owner);
		MicrobotRuntime.uninstall();
		JavaBridge.setScriptInputSuspended(false);
	}

	@Test
	public void fillWaitsForTheRealTrackerAndDoesNotInventCapacity()
	{
		assertTrue(Pouch.SMALL.fill());
		assertEquals(3, Pouch.SMALL.getHolding());
		assertFalse(Pouch.SMALL.isUnknown());
		assertEquals(2, essence);
		assertEquals(1, actions.get());
	}

	@Test
	public void partialFillKeepsTheTrackersUncertainty()
	{
		inventory(1);
		assertTrue(Pouch.SMALL.fill());
		assertEquals(1, Pouch.SMALL.getHolding());
		assertTrue(Pouch.SMALL.isUnknown());
		assertEquals(0, essence);
	}

	@Test
	public void daeyaltEssenceIsFilledAndTracked()
	{
		essenceId = ItemID.BLANKRUNE_DAEYALT;
		inventory(5);
		assertTrue(Pouch.SMALL.fill());
		assertEquals(3, Pouch.SMALL.getHolding());
		assertEquals(2, essence);
	}

	@Test
	public void guardianEssenceIsFilledAndTracked()
	{
		essenceId = ItemID.GOTR_GUARDIAN_ESSENCE;
		inventory(5);
		assertTrue(Pouch.SMALL.fill());
		assertEquals(3, Pouch.SMALL.getHolding());
		assertEquals(2, essence);
	}

	@Test
	public void degradedPouchUsesItsActualIdAndCapacity()
	{
		pouchId = ItemID.RCU_POUCH_MEDIUM_DEGRADE;
		inventory(5);
		assertTrue(Pouch.MEDIUM.fill());
		assertTrue(Pouch.MEDIUM.isDegraded());
		assertEquals(3, Pouch.MEDIUM.getHolding());
		assertEquals(2, essence);
	}

	@Test
	public void emptyRequiresObservedEssenceTransfer()
	{
		inventory(0);
		known(3);
		assertTrue(Pouch.SMALL.empty());
		assertEquals(0, Pouch.SMALL.getHolding());
		assertEquals(3, essence);
		assertEquals(1, actions.get());
	}

	@Test
	public void checkConfirmsUnknownAndUnchangedKnownCountsWithFreshReply()
	{
		assertTrue(Pouch.SMALL.check());
		assertEquals(1, Pouch.SMALL.getHolding());
		assertFalse(Pouch.SMALL.isUnknown());
		assertTrue(Pouch.SMALL.check());
		assertEquals(2, actions.get());
		verify(events, times(4)).unregister(any(EventBus.Subscriber.class));
	}

	@Test
	public void knownCountWithoutANewCheckReplyDoesNotPass()
	{
		known(1);
		observe = false;
		assertFalse(Pouch.SMALL.check());
		assertEquals(1, actions.get());
		assertEquals(1, Pouch.SMALL.getHolding());
		verify(events, times(2)).unregister(any(EventBus.Subscriber.class));
	}

	@Test
	public void dispatchedFillWithoutObservedChangeRemainsUnknownAndFalse()
	{
		observe = false;
		assertFalse(Pouch.SMALL.fill());
		assertTrue(Pouch.SMALL.isUnknown());
		assertEquals(0, Pouch.SMALL.getHolding());
		assertEquals(1, actions.get());
	}

	@Test
	public void requirementsAndMissingPouchRefuseWithoutActions()
	{
		level = 24;
		pouchId = ItemID.RCU_POUCH_MEDIUM;
		inventory(5);
		assertFalse(Pouch.MEDIUM.fill());
		assertFalse(Pouch.MEDIUM.empty());
		assertFalse(Pouch.MEDIUM.check());
		assertFalse(Pouch.SMALL.check());
		assertEquals(0, actions.get());
	}

	@Test
	public void noEssenceFullPouchAndNoSpaceRefuseWithoutActions()
	{
		inventory(0);
		assertFalse(Pouch.SMALL.fill());
		known(3);
		inventory(27);
		assertFalse(Pouch.SMALL.fill());
		assertFalse(Pouch.SMALL.empty());
		assertEquals(0, actions.get());
	}

	@Test
	public void stopAndManualInputAfterDispatchEndTheWaitWithoutRetries()
	{
		observe = false;
		afterDispatch = owner::close;
		assertFalse(Pouch.SMALL.fill());
		assertEquals(1, actions.get());
		owner = new ScriptInputSession();
		MobileActions.bind(owner);
		afterDispatch = () -> JavaBridge.onTapMove(100, 120);
		assertFalse(Pouch.SMALL.fill());
		assertTrue(owner.isActive());
		assertEquals(2, actions.get());
	}

	@Test
	public void logoutResetIsNotMistakenForASuccessfulTransfer()
	{
		known(1);
		observe = false;
		afterDispatch = () ->
		{
			when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
			GameStateChanged logout = new GameStateChanged();
			logout.setGameState(GameState.LOGIN_SCREEN);
			post(logout);
		};
		assertFalse(Pouch.SMALL.empty());
		assertTrue(Pouch.SMALL.isUnknown());
		assertEquals(0, Pouch.SMALL.getHolding());
		assertEquals(1, actions.get());
	}

	@Test
	public void clientThreadNeverDispatchesOrWaits()
	{
		onClient.set(true);
		assertFalse(Pouch.SMALL.fill());
		assertFalse(Pouch.SMALL.empty());
		assertFalse(Pouch.SMALL.check());
		assertEquals(0, actions.get());
	}
}
