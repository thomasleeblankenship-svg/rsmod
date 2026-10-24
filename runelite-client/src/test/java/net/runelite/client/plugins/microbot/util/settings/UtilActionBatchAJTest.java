package net.runelite.client.plugins.microbot.util.settings;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util action batch AJ: spellbook filter toggles. */
public class UtilActionBatchAJTest
{
	@Test
	public void aFilterAlreadyInPlaceClicksNothing()
	{
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
			MockedStatic<Rs2Widget> widgets = mockStatic(Rs2Widget.class))
		{
			microbot.when(() -> Microbot.getVarbitValue(6605)).thenReturn(0);
			assertTrue(Rs2SpellBookSettings.toggleCombatSpells(true));
			widgets.verifyNoInteractions();
		}
	}

	@Test
	public void aFilterIsClickedUntilItsVarbitChanges()
	{
		AtomicInteger varbit = new AtomicInteger(1);
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
			MockedStatic<Rs2Widget> widgets = mockStatic(Rs2Widget.class);
			MockedStatic<Rs2Tab> tabs = mockStatic(Rs2Tab.class);
			MockedStatic<Rs2Settings> settings = mockStatic(Rs2Settings.class);
			MockedStatic<Global> global = mockStatic(Global.class))
		{
			microbot.when(() -> Microbot.getVarbitValue(6609)).thenAnswer(invocation -> varbit.get());
			settings.when(Rs2Settings::isSpellFilteringEnabled).thenReturn(true);
			tabs.when(Rs2Tab::getCurrentTab).thenReturn(InterfaceTab.MAGIC);
			global.when(() -> Global.sleepUntilTrue(any(BooleanSupplier.class), anyInt(), anyInt()))
				.thenAnswer(invocation -> ((BooleanSupplier) invocation.getArgument(0)).getAsBoolean());
			widgets.when(() -> Rs2Widget.hasWidgetText("Show Teleport spells", 218, 0, true)).thenReturn(true);
			widgets.when(() -> Rs2Widget.clickWidget(eq("Show Teleport spells"), any(), eq(0), eq(true)))
				.thenAnswer(invocation -> { varbit.set(0); return true; });
			assertTrue(Rs2SpellBookSettings.toggleTeleportSpells(true, false));
			widgets.verify(() -> Rs2Widget.clickWidget("Show Teleport spells", Optional.of(218), 0, true));
			widgets.verify(() -> Rs2Widget.clickWidget(eq("Filters"), any(), anyInt(), anyBoolean()), never());
		}
	}
}
