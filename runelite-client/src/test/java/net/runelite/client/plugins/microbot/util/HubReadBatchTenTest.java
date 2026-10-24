package net.runelite.client.plugins.microbot.util;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Hub read batch 10 against upstream 2.6.38 semantics, with mocked RuneLite interfaces. */
public class HubReadBatchTenTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final EventBus events = new EventBus();
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
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class), List.of(new MicrobotReadServices(client, thread, events)));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	private <T> T onThread(T value)
	{
		assertTrue("read on the client thread", onClientThread.get());
		return value;
	}

	private static ItemComposition definition(int note, int linked, boolean stackable)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn("Logs");
		when(definition.getNote()).thenReturn(note);
		when(definition.getLinkedNoteId()).thenReturn(linked);
		when(definition.isStackable()).thenReturn(stackable);
		when(definition.getInventoryActions()).thenReturn(new String[5]);
		return definition;
	}

	@Test
	public void unNotedIdFollowsUpstream()
	{
		assertEquals("noted logs give the unnoted id", 1511, new Rs2ItemModel(new Item(1512, 5), definition(799, 1511, true), null, 0).getUnNotedId());
		assertEquals("a noted item without a link keeps its id", 1512, new Rs2ItemModel(new Item(1512, 5), definition(799, -1, true), null, 0).getUnNotedId());
		assertEquals("unnoted, unstackable", 1511, new Rs2ItemModel(new Item(1511, 1), definition(-1, 1512, false), null, 0).getUnNotedId());
		assertEquals("no definition", 995, new Rs2ItemModel(new Item(995, 1), null, null, 0).getUnNotedId());
	}

	@Test
	public void selectedItemNameIsStrippedAndReadOnTheClientThread()
	{
		assertNull(Rs2Inventory.getSelectedItemName());
		Widget selected = mock(Widget.class);
		when(selected.getName()).thenReturn("<col=ff9040>Oak logs</col>");
		when(client.getSelectedWidget()).thenAnswer(i -> onThread(selected));
		assertEquals("Oak logs", Rs2Inventory.getSelectedItemName());
	}

	@Test
	public void teleblockCountsOnlyAboveOneHundred()
	{
		assertFalse(Rs2Player.isTeleBlocked());
		VarbitChanged event = new VarbitChanged();
		event.setVarbitId(VarbitID.TELEBLOCK_CYCLES);
		event.setValue(100);
		onClientThread.set(true);
		when(client.getVarbitValue(VarbitID.TELEBLOCK_CYCLES)).thenReturn(100);
		events.post(event);
		onClientThread.set(false);
		assertFalse(Rs2Player.isTeleBlocked());
		event.setValue(300);
		when(client.getVarbitValue(VarbitID.TELEBLOCK_CYCLES)).thenReturn(300);
		onClientThread.set(true);
		events.post(event);
		onClientThread.set(false);
		assertTrue(Rs2Player.isTeleBlocked());
	}

	@Test
	public void idleLogoutUsesTheLowerIdleCountAgainstTheTimeout()
	{
		when(client.getIdleTimeout()).thenAnswer(i -> onThread(15000));
		when(client.getMouseIdleTicks()).thenReturn(14000);
		when(client.getKeyboardIdleTicks()).thenReturn(14600);
		assertFalse(Rs2Player.checkIdleLogout(500));
		assertTrue(Rs2Player.checkIdleLogout(1000));
		when(client.getMouseIdleTicks()).thenReturn(15000);
		assertFalse("the keyboard is still below the timeout", Rs2Player.checkIdleLogout(0));
		when(client.getKeyboardIdleTicks()).thenReturn(15000);
		assertTrue(Rs2Player.checkIdleLogout(0));
	}

	@Test
	public void equipmentNamesSkipEmptySlots()
	{
		Player player = mock(Player.class);
		PlayerComposition composition = mock(PlayerComposition.class);
		when(composition.getEquipmentId(any(KitType.class))).thenReturn(-1);
		when(composition.getEquipmentId(KitType.WEAPON)).thenReturn(4151);
		when(player.getPlayerComposition()).thenAnswer(i -> onThread(composition));
		ItemComposition whip = mock(ItemComposition.class);
		when(whip.getName()).thenReturn("Abyssal whip");
		when(client.getItemDefinition(4151)).thenReturn(whip);
		Rs2PlayerModel model = mock(Rs2PlayerModel.class);
		when(model.getPlayer()).thenReturn(player);

		Map<KitType, String> names = Rs2Player.getPlayerEquipmentNames(model);
		assertEquals("Abyssal whip", names.get(KitType.WEAPON));
		assertNull(names.get(KitType.TORSO));
		assertTrue(Rs2Player.getPlayerEquipmentNames(null).isEmpty());
	}

	@Test
	public void dialogueOptionAndTextFollowTheWidgets()
	{
		assertNull(Rs2Dialogue.getDialogueOption("Yes"));
		assertEquals("", Rs2Dialogue.getDialogueText());

		Widget title = mock(Widget.class);
		when(title.getText()).thenReturn("Select an option");
		Widget yes = mock(Widget.class);
		when(yes.getText()).thenAnswer(i -> onThread("Yes, don't ask again"));
		Widget no = mock(Widget.class);
		when(no.getText()).thenReturn("No");
		Widget menu = mock(Widget.class);
		when(menu.getDynamicChildren()).thenReturn(new Widget[]{title, yes, no});
		when(client.getWidget(ComponentID.DIALOG_OPTION_OPTIONS)).thenReturn(menu);
		assertSame(yes, Rs2Dialogue.getDialogueOption("yes, don't"));
		assertSame(no, Rs2Dialogue.getDialogueOption("no", true));
		assertNull(Rs2Dialogue.getDialogueOption("Maybe"));

		Widget npcText = mock(Widget.class);
		when(npcText.getText()).thenAnswer(i -> onThread("<col=0000ff>Hello</col> there"));
		when(client.getWidget(231, 6)).thenReturn(npcText);
		when(client.getWidget(net.runelite.api.widgets.WidgetInfo.DIALOG_NPC_TEXT)).thenReturn(npcText);
		ScriptInputSession session = new ScriptInputSession();
		MobileActions.bind(session);
		try
		{
			assertEquals("Hello there", Rs2Dialogue.getDialogueText());
		}
		finally
		{
			MobileActions.unbind(session);
		}
	}
}
