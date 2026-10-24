package net.runelite.client.plugins.microbot.util.combat;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Upstream's setAutoCastSpell: combat tab, spell box, the spell by its sprite, confirmed by the autocast varbits. */
public class AutoCastSpellTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean clientThread = new AtomicBoolean(true);

	@Before
	public void setUp() throws Exception
	{
		Client client = mock(Client.class);
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
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
		clientThread.set(false);
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	@Test
	public void theSpellIsPickedFromTheAutocastListByItsSprite()
	{
		Widget options = mock(Widget.class);
		Widget windBlast = mock(Widget.class);
		when(windBlast.getId()).thenReturn(13172737);
		when(windBlast.getIndex()).thenReturn(4);
		AtomicReference<Rs2CombatSpells> autocast = new AtomicReference<>();
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Magic> magic = mockStatic(Rs2Magic.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Tab> tabs = mockStatic(Rs2Tab.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Widget> widgets = mockStatic(Rs2Widget.class, CALLS_REAL_METHODS);
			MockedStatic<Microbot> microbot = mockStatic(Microbot.class, CALLS_REAL_METHODS))
		{
			actions.when(MobileActions::active).thenReturn(true);
			actions.when(() -> MobileActions.widget(anyInt(), anyInt(), anyString())).thenAnswer(call ->
			{
				if ((int) call.getArgument(0) == 13172737)
				{
					autocast.set(Rs2CombatSpells.WIND_BLAST);
				}
				return true;
			});
			magic.when(() -> Rs2Magic.canCast(any(MagicAction.class))).thenReturn(true);
			magic.when(Rs2Magic::getCurrentAutoCastSpell).thenAnswer(call -> autocast.get());
			tabs.when(() -> Rs2Tab.switchTo(InterfaceTab.COMBAT)).thenReturn(true);
			widgets.when(() -> Rs2Widget.isWidgetVisible(201, 1)).thenReturn(true);
			widgets.when(() -> Rs2Widget.getWidget(201, 1)).thenReturn(options);
			widgets.when(() -> Rs2Widget.findWidget(eq(MagicAction.WIND_BLAST.getSprite()), anyList())).thenReturn(windBlast);
			microbot.when(() -> Microbot.getVarbitValue(anyInt())).thenReturn(0);
			actions.clearInvocations();

			assertTrue(Rs2Combat.setAutoCastSpell(Rs2CombatSpells.WIND_BLAST, false));
			actions.verify(() -> MobileActions.widget(InterfaceID.CombatInterface.AUTOCAST_NORMAL, -1, ""));
			actions.verify(() -> MobileActions.widget(13172737, 4, ""));

			actions.clearInvocations();
			assertTrue("already set: nothing is pressed", Rs2Combat.setAutoCastSpell(Rs2CombatSpells.WIND_BLAST, false));
			actions.verify(() -> MobileActions.widget(anyInt(), anyInt(), anyString()), never());

			magic.when(() -> Rs2Magic.canCast(any(MagicAction.class))).thenReturn(false);
			assertFalse("cannot cast it: false", Rs2Combat.setAutoCastSpell(Rs2CombatSpells.FIRE_SURGE, false));
			assertFalse(Rs2Combat.setAutoCastSpell(null, false));
		}
	}
}
