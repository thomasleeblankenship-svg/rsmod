package net.runelite.client.plugins.microbot.util.prayer;

import java.util.function.BooleanSupplier;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.mouse.Mouse;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util action batch AA: quick prayers, camera centring, menu-entry clicks. */
public class UtilActionBatchAATest
{
	@Test
	public void quickPrayersAreToggledToMatchThenDone()
	{
		try (MockedStatic<Rs2Prayer> prayer = mockStatic(Rs2Prayer.class, CALLS_REAL_METHODS);
			MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class);
			MockedStatic<MobileActions> actions = mockStatic(MobileActions.class);
			MockedStatic<Global> global = mockStatic(Global.class))
		{
			widget.when(() -> Rs2Widget.isWidgetVisible(anyInt())).thenReturn(true);
			actions.when(() -> MobileActions.widget(anyInt(), anyInt(), anyString())).thenReturn(true);
			global.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt()))
				.thenAnswer(a -> ((BooleanSupplier) a.getArgument(0)).getAsBoolean());
			prayer.when(() -> Rs2Prayer.isQuickPrayerSet(any())).thenAnswer(a -> a.getArgument(0) == Rs2PrayerEnum.PROTECT_MELEE);

			assertTrue(Rs2Prayer.setQuickPrayers(new Rs2PrayerEnum[]{Rs2PrayerEnum.PIETY}));
			actions.verify(() -> MobileActions.widget(InterfaceID.Orbs.PRAYERBUTTON, -1, "Setup"));
			actions.verify(() -> MobileActions.widget(InterfaceID.Quickprayer.BUTTONS, Rs2PrayerEnum.PROTECT_MELEE.getQuickPrayerIndex(), "Toggle"));
			actions.verify(() -> MobileActions.widget(InterfaceID.Quickprayer.BUTTONS, Rs2PrayerEnum.PIETY.getQuickPrayerIndex(), "Toggle"));
			actions.verify(() -> MobileActions.widget(InterfaceID.Quickprayer.CLOSE, -1, "Done"));
		}
	}

	@Test
	public void aMenuEntryClickSendsTheEntry()
	{
		NewMenuEntry entry = new NewMenuEntry().option("Bank");
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class);
			MockedStatic<Global> global = mockStatic(Global.class))
		{
			actions.when(() -> MobileActions.menu(entry)).thenReturn(true);
			global.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt()))
				.thenAnswer(a -> ((BooleanSupplier) a.getArgument(0)).getAsBoolean());
			new Mouse().quickClick(new Point(5, 5), entry);
			actions.verify(() -> MobileActions.menu(entry));
		}
		assertFalse(Rs2Camera.isTileCenteredOnScreen(null));
	}
}
