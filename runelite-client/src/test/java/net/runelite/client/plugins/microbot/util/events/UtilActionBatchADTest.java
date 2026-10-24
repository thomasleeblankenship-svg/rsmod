package net.runelite.client.plugins.microbot.util.events;

import java.util.List;
import java.util.stream.Collectors;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventManager;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Util action batch AD: blocking events. */
public class UtilActionBatchADTest
{
	@Test
	public void thePopupEventsAreRegisteredHighestFirst()
	{
		BlockingEventManager manager = new BlockingEventManager();
		try
		{
			assertTrue("nothing until the mobile plugin adds them", manager.getEvents().isEmpty());
			manager.addDefaultEvents();
			List<String> names = manager.getEvents().stream().map(BlockingEvent::getName).collect(Collectors.toList());
			assertEquals(List.of("WelcomeScreenEvent", "BankTutorialEvent", "DeathEvent", "BankJagexPopupEvent", "MembershipPromptEvent"), names);
			assertEquals(BlockingEventPriority.HIGHEST, manager.getEvents().get(0).priority());
		}
		finally
		{
			manager.shutdown();
		}
	}

	@Test
	public void nothingTriggersWithoutAScriptSession()
	{
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class, CALLS_REAL_METHODS))
		{
			microbot.when(Microbot::isLoggedIn).thenReturn(false);
			assertFalse(new WelcomeScreenEvent().validate());
			assertFalse(new BankTutorialEvent().validate());
			assertFalse(new BankJagexPopupEvent().validate());
			assertFalse(new MembershipPromptEvent().validate());
			assertFalse(new DeathEvent().validate());
			assertTrue("nothing to close is done", new BankTutorialEvent().execute());
		}
	}
}
