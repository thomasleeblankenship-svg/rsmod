package net.runelite.client.plugins.microbot.util.keyboard;

import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** keyPress(int) retries a refused key and sends it once. */
public class KeyPressRetryTest
{
	@Test
	public void aRefusedKeyIsRetriedUntilItGoesOut()
	{
		AtomicInteger attempts = new AtomicInteger();
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class);
			MockedStatic<Global> global = mockStatic(Global.class))
		{
			actions.when(() -> MobileActions.key(KeyEvent.VK_SPACE)).thenAnswer(invocation -> attempts.incrementAndGet() >= 3);
			global.when(() -> Global.sleepUntil(any(BooleanSupplier.class), anyInt())).thenAnswer(invocation ->
			{
				BooleanSupplier condition = invocation.getArgument(0);
				for (int i = 0; i < 10; i++)
				{
					if (condition.getAsBoolean())
					{
						return true;
					}
				}
				return false;
			});
			assertTrue(Rs2Keyboard.keyPress(KeyEvent.VK_SPACE));
			assertEquals("sent once, after two refusals", 3, attempts.get());
		}
	}
}
