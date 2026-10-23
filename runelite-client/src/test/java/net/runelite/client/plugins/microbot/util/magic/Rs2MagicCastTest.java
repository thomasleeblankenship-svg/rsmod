package net.runelite.client.plugins.microbot.util.magic;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Casting: direct spells use their option, target spells are selected and then applied. */
public class Rs2MagicCastTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private Client client;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getRealSkillLevel(Skill.MAGIC)).thenReturn(70);
		when(client.isWidgetSelected()).thenReturn(true);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(thread).invoke(any(Runnable.class));
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	@Test
	public void aTeleportIsCastWithItsOwnOption()
	{
		try (MockedStatic<Rs2Magic> magic = mockStatic(Rs2Magic.class, CALLS_REAL_METHODS);
			MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			magic.when(() -> Rs2Magic.canCast(any(MagicAction.class))).thenReturn(true);
			actions.when(() -> MobileActions.widget(anyInt(), anyInt(), anyString())).thenReturn(true);
			// getWidgetId() reads the live spellbook; resolve it outside verify().
			int widgetId = MagicAction.VARROCK_TELEPORT.getWidgetId();
			actions.clearInvocations();
			assertTrue(Rs2Magic.cast(MagicAction.VARROCK_TELEPORT));
			actions.verify(() -> MobileActions.widget(widgetId, -1, "Cast"));
			actions.verify(() -> MobileActions.selectSpell(anyInt(), anyString(), anyInt()), never());
		}
	}

	@Test
	public void alchemySelectsTheSpellThenTargetsTheItem()
	{
		Rs2ItemModel item = mock(Rs2ItemModel.class);
		when(item.getSlot()).thenReturn(4);
		when(item.getId()).thenReturn(1127);
		try (MockedStatic<Rs2Magic> magic = mockStatic(Rs2Magic.class, CALLS_REAL_METHODS);
			MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Tab> tabs = mockStatic(Rs2Tab.class))
		{
			magic.when(() -> Rs2Magic.canCast(any(MagicAction.class))).thenReturn(true);
			tabs.when(() -> Rs2Tab.switchTo(any(InterfaceTab.class))).thenReturn(true);
			actions.when(() -> MobileActions.selectSpell(anyInt(), anyString(), anyInt())).thenReturn(true);
			actions.when(() -> MobileActions.inventory(anyInt(), anyInt(), anyString())).thenReturn(true);
			int widgetId = MagicAction.HIGH_LEVEL_ALCHEMY.getWidgetId();
			actions.clearInvocations();
			Rs2Magic.alch(item);
			actions.verify(() -> MobileActions.selectSpell(eq(widgetId), eq("Cast"), eq(1)));
			actions.verify(() -> MobileActions.inventory(4, 1127, "Use"));
		}
	}

	@Test
	public void castOnAnNpcUsesItsIndexAndNothingIsAppliedWhenSelectionFails()
	{
		NPC npc = mock(NPC.class);
		when(npc.getIndex()).thenReturn(77);
		try (MockedStatic<Rs2Magic> magic = mockStatic(Rs2Magic.class, CALLS_REAL_METHODS);
			MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			magic.when(() -> Rs2Magic.canCast(any(MagicAction.class))).thenReturn(true);
			actions.when(() -> MobileActions.selectSpell(anyInt(), anyString(), anyInt())).thenReturn(true);
			actions.when(() -> MobileActions.spellOnNpc(anyInt())).thenReturn(true);
			actions.clearInvocations();
			assertTrue(Rs2Magic.castOn(MagicAction.WIND_STRIKE, npc));
			actions.verify(() -> MobileActions.spellOnNpc(77));

			magic.when(() -> Rs2Magic.canCast(any(MagicAction.class))).thenReturn(false);
			actions.clearInvocations();
			assertFalse(Rs2Magic.castOn(MagicAction.WIND_STRIKE, npc));
			actions.verify(() -> MobileActions.spellOnNpc(anyInt()), never());
		}
	}

	private static net.runelite.api.widgets.Widget spellWidget(String name, net.runelite.api.widgets.Widget... children)
	{
		net.runelite.api.widgets.Widget widget = mock(net.runelite.api.widgets.Widget.class);
		when(widget.getName()).thenReturn(name);
		when(widget.getStaticChildren()).thenReturn(children);
		return widget;
	}

	@Test
	public void aSpellIsFoundByItsDisplayedNameIgnoringColourTags()
	{
		net.runelite.api.widgets.Widget home = spellWidget("<col=ff9040>Lunar Home Teleport</col>");
		net.runelite.api.widgets.Widget book = spellWidget("", spellWidget("<col=ff9040>Bake Pie</col>"), spellWidget("", home));
		org.junit.Assert.assertSame(home, Rs2Magic.findSpell(book, "Lunar Home Teleport"));
		org.junit.Assert.assertNull(Rs2Magic.findSpell(book, "Lunar Home"));
	}
}
