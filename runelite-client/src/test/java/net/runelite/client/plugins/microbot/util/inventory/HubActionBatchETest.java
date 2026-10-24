package net.runelite.client.plugins.microbot.util.inventory;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Predicate;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util-gap action batch E: active plugins, drags by point, NPCs by name, fast widget clicks, deposit-all-except. */
public class HubActionBatchETest
{
	/** A plugin in a {@code microbot} package. */
	static final class ScriptPlugin extends Plugin
	{
	}

	/** Upstream leaves this name out of the active list. */
	static final class MicrobotPlugin extends Plugin
	{
	}

	@Test
	public void activeMicrobotPluginsLeaveOutUpstreamsHelpers() throws Exception
	{
		Plugin script = new ScriptPlugin();
		Plugin helper = new MicrobotPlugin();
		Plugin stopped = new ScriptPlugin();
		PluginManager manager = mock(PluginManager.class);
		when(manager.getPlugins()).thenReturn(List.of(script, helper, stopped));
		when(manager.isPluginActive(script)).thenReturn(true);
		when(manager.isPluginActive(helper)).thenReturn(true);
		Field field = Microbot.class.getDeclaredField("pluginManager");
		field.setAccessible(true);
		Object before = field.get(null);
		field.set(null, manager);
		try
		{
			assertEquals(List.of(script), Microbot.getActiveMicrobotPlugins());
		}
		finally
		{
			field.set(null, before);
		}
		assertTrue("stamina use defaults on, as upstream", Microbot.useStaminaPotsIfNeeded);
	}

	@Test
	public void aDragBetweenPointsGoesThroughMicrobotDrag()
	{
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class, CALLS_REAL_METHODS))
		{
			microbot.when(() -> Microbot.drag(any(), any())).thenAnswer(call -> null);
			new Mouse().drag(new Point(10, 20), new Point(40, 50));
			microbot.verify(() -> Microbot.drag(new Rectangle(10, 20, 1, 1), new Rectangle(40, 50, 1, 1)));
		}
	}

	@Test
	public void npcsByNameAndFastWidgetClicks()
	{
		Rs2NpcModel jane = new Rs2NpcModel(mock(net.runelite.api.NPC.class));
		try (MockedStatic<Rs2Npc> npcs = mockStatic(Rs2Npc.class, CALLS_REAL_METHODS))
		{
			npcs.when(() -> Rs2Npc.getNpc("Guildmaster Jane")).thenReturn(jane);
			npcs.when(() -> Rs2Npc.interact(any(Rs2NpcModel.class), anyString())).thenReturn(true);
			assertTrue(Rs2Npc.interact("Guildmaster Jane", "Contract"));
			npcs.verify(() -> Rs2Npc.interact(jane, "Contract"));
			assertFalse(Rs2Npc.interact((String) null, "Contract"));
		}

		Widget button = mock(Widget.class);
		when(button.getId()).thenReturn(17694734);
		when(button.getType()).thenReturn(5);
		when(button.getItemId()).thenReturn(-1);
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS);
			MockedStatic<Microbot> microbot = mockStatic(Microbot.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.read(any(), any())).thenAnswer(call -> ((java.util.concurrent.Callable<?>) call.getArgument(0)).call());
			microbot.when(() -> Microbot.doInvoke(any(NewMenuEntry.class), any())).thenAnswer(call -> null);
			Rs2Widget.clickWidgetFast(button, 3, 2);
			ArgumentCaptor<NewMenuEntry> entry = ArgumentCaptor.forClass(NewMenuEntry.class);
			microbot.verify(() -> Microbot.doInvoke(entry.capture(), any()));
			assertEquals(MenuAction.CC_OP, entry.getValue().getType());
			assertEquals(3, entry.getValue().getParam0());
			assertEquals(17694734, entry.getValue().getParam1());
			assertEquals(2, entry.getValue().getIdentifier());
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void depositAllExceptKeepsTheNamedStacks()
	{
		Rs2ItemModel bag = mock(Rs2ItemModel.class);
		when(bag.getName()).thenReturn("Open gem bag");
		Rs2ItemModel ore = mock(Rs2ItemModel.class);
		when(ore.getName()).thenReturn("Iron ore");
		try (MockedStatic<Rs2DepositBox> box = mockStatic(Rs2DepositBox.class, CALLS_REAL_METHODS))
		{
			box.when(() -> Rs2DepositBox.depositAll(any(Predicate.class))).thenReturn(true);
			box.clearInvocations();
			ArgumentCaptor<Predicate<Rs2ItemModel>> keep = ArgumentCaptor.forClass(Predicate.class);
			assertTrue(Rs2DepositBox.depositAllExcept("open gem bag"));
			box.verify(() -> Rs2DepositBox.depositAll(keep.capture()));
			assertFalse("the gem bag stays", keep.getValue().test(bag));
			assertTrue(keep.getValue().test(ore));
		}
		Rs2AntibanSettings.loadFromProfile();
		assertFalse(Rs2Inventory.drop((Predicate<Rs2ItemModel>) null));
	}
}
