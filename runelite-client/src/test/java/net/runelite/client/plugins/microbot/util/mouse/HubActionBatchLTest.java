package net.runelite.client.plugins.microbot.util.mouse;

import java.util.function.BooleanSupplier;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util-gap action batch L: upstream's {@code Mouse.scrollDown}/{@code scrollUp} as script wheel scrolls. */
public class HubActionBatchLTest
{
	@Test
	public void scrollsAreTwoNotchesAtThePoint()
	{
		Mouse mouse = new Mouse();
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class);
			MockedStatic<Global> global = mockStatic(Global.class))
		{
			actions.when(() -> MobileActions.scroll(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(true);
			global.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt()))
				.thenAnswer(a -> ((BooleanSupplier) a.getArgument(0)).getAsBoolean());
			mouse.scrollDown(new Point(120, 340));
			actions.verify(() -> MobileActions.scroll(120, 340, 2, 10));
			mouse.scrollUp(new Point(5, 6));
			actions.verify(() -> MobileActions.scroll(5, 6, -2, -10));
			mouse.scrollDown(null);
			actions.verify(() -> MobileActions.scroll(anyInt(), anyInt(), anyInt(), anyInt()), times(2));
		}
	}
}
