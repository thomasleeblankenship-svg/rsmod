package net.runelite.client.plugins.microbot.util.settings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/** {@link Rs2Settings}: varbit reads, the toggle dispatches, and the safety refusal. */
public class Rs2SettingsTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean clientThread = new AtomicBoolean(false);
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private Client client;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> clientThread.get());
		// LOGIN_SCREEN keeps the varbit cache from holding values between assertions.
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(client.getVarbitValue(anyInt())).thenAnswer(i -> varbits.getOrDefault((int) i.getArgument(0), 0));
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			clientThread.set(true);
			try
			{
				((Runnable) call.getArgument(0)).run();
			}
			finally
			{
				clientThread.set(false);
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

	private Widget widget(int id, int index, String... actions)
	{
		Widget w = mock(Widget.class);
		when(w.getId()).thenReturn(id);
		when(w.getIndex()).thenReturn(index);
		when(w.getActions()).thenReturn(actions);
		return w;
	}

	@Test
	public void readsFollowTheVarbits()
	{
		varbits.put(Rs2Settings.ALCHEMY_WARNING_VARBIT, 30_000);
		varbits.put(VarbitID.OPTION_HIDE_ROOFTOPS, 1);
		varbits.put(VarbitID.OPTION_LEVEL_UP_MESSAGE_DISABLED, 1);
		varbits.put(VarbitID.BANK_SIDE_SLOT_SHOWOP, 1);
		assertEquals(30_000, Rs2Settings.getMinimumItemValueAlchemyWarning());
		assertTrue(Rs2Settings.isHideRoofsEnabled());
		assertFalse(Rs2Settings.isLevelUpNotificationsEnabled());
		assertFalse(Rs2Settings.isDropShiftSettingEnabled());
		assertTrue(Rs2Settings.isWorldSwitcherConfirmationEnabled());
		assertTrue(Rs2Settings.isSpellFilteringEnabled());
		assertTrue(Rs2Settings.isBankSlotLockingEnabled());
		varbits.put(VarbitID.BANK_SIDE_SLOT_IGNOREINVLOCKS, 1);
		assertFalse(Rs2Settings.isBankSlotLockingEnabled());
	}

	@Test
	public void settingsAlreadyInPlaceDispatchNothing()
	{
		varbits.put(VarbitID.OPTION_HIDE_ROOFTOPS, 1);
		varbits.put(VarbitID.DESKTOP_SHIFTCLICKDROP_ENABLED, 1);
		varbits.put(VarbitID.OPTION_LEVEL_UP_MESSAGE_DISABLED, 1);
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			assertTrue(Rs2Settings.hideRoofs());
			assertTrue(Rs2Settings.enableDropShiftSetting());
			assertTrue(Rs2Settings.disableLevelUpNotifications());
			Rs2Settings.enableSpellFiltering();
			actions.verify(() -> MobileActions.widget(anyInt(), anyInt(), anyString()), never());
		}
	}

	@Test
	public void theWorldSwitcherConfirmationIsNeverTurnedOff()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			assertFalse(Rs2Settings.disableWorldSwitcherConfirmation());
			actions.verify(() -> MobileActions.widget(anyInt(), anyInt(), anyString()), never());
			actions.verify(() -> MobileActions.key(anyInt()), never());
		}
		varbits.put(VarbitID.WORLDSWITCHER_DISABLE_CONFIRMATION, 1);
		assertTrue(Rs2Settings.disableWorldSwitcherConfirmation(false));
	}

	@Test
	public void shiftDropOpensControlsThenTogglesItsRow()
	{
		Widget settings = widget(Rs2Settings.SETTINGS_INTERFACE, -1);
		when(client.getWidget(Rs2Settings.SETTINGS_INTERFACE)).thenReturn(settings);
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.widget(Rs2Settings.SETTINGS_CATEGORIES, 3, "")).thenReturn(true);
			actions.when(() -> MobileActions.widget(Rs2Settings.SETTINGS_CLICKABLE, Rs2Settings.SHIFT_DROP_CHILD, "Toggle")).thenAnswer(call ->
			{
				varbits.put(VarbitID.DESKTOP_SHIFTCLICKDROP_ENABLED, 1);
				return true;
			});
			actions.clearInvocations();
			assertTrue(Rs2Settings.enableDropShiftSetting(false));
			actions.verify(() -> MobileActions.widget(Rs2Settings.SETTINGS_CATEGORIES, 3, ""));
			actions.verify(() -> MobileActions.widget(Rs2Settings.SETTINGS_CLICKABLE, Rs2Settings.SHIFT_DROP_CHILD, "Toggle"));
		}
	}

	@Test
	public void searchResultIsTheFirstVisibleRowWithAWantedAction()
	{
		Widget hidden = widget(Rs2Settings.SETTINGS_CLICKABLE, 0, "Toggle");
		when(hidden.isHidden()).thenReturn(true);
		Widget other = widget(Rs2Settings.SETTINGS_CLICKABLE, 1, "Reset");
		Widget toggle = widget(Rs2Settings.SETTINGS_CLICKABLE, 2, null, "Toggle");
		Widget clickable = widget(Rs2Settings.SETTINGS_CLICKABLE, -1);
		when(clickable.getDynamicChildren()).thenReturn(new Widget[]{null, hidden, other, toggle});
		when(client.getWidget(Rs2Settings.SETTINGS_CLICKABLE)).thenReturn(clickable);
		assertArrayEquals(new int[]{Rs2Settings.SETTINGS_CLICKABLE, 2}, Rs2Settings.findSearchResult(client, "Toggle"));
		assertNull(Rs2Settings.findSearchResult(client, "Select"));
	}

	@Test
	public void soundButtonIsOnlyOfferedWhileItCanStillMute()
	{
		Widget button = widget(1234, -1, "Mute");
		Widget icon = widget(InterfaceID.SettingsSide.MUSIC_ICON, -1);
		when(icon.getStaticChildren()).thenReturn(new Widget[]{button});
		when(client.getWidget(InterfaceID.SettingsSide.MUSIC_ICON)).thenReturn(icon);
		assertEquals(1234, Rs2Settings.unmutedButton(client, InterfaceID.SettingsSide.MUSIC_ICON));
		when(button.getActions()).thenReturn(new String[]{"Unmute"});
		assertEquals(-1, Rs2Settings.unmutedButton(client, InterfaceID.SettingsSide.MUSIC_ICON));
		assertEquals(-1, Rs2Settings.unmutedButton(client, InterfaceID.SettingsSide.SOUND_ICON));
	}

	@Test
	public void spellbookStoneFollowsTheLayout()
	{
		Widget any = mock(Widget.class);
		when(client.getWidget(anyInt())).thenReturn(any);
		assertEquals(InterfaceID.Toplevel.STONE6, Rs2Settings.spellbookStone(client));
		when(client.isResized()).thenReturn(true);
		assertEquals(InterfaceID.ToplevelOsrsStretch.STONE6, Rs2Settings.spellbookStone(client));
		varbits.put(VarbitID.RESIZABLE_STONE_ARRANGEMENT, 1);
		assertEquals(InterfaceID.ToplevelPreEoc.STONE6, Rs2Settings.spellbookStone(client));
		when(client.getWidget(anyInt())).thenReturn(null);
		assertEquals(-1, Rs2Settings.spellbookStone(client));
	}
}
