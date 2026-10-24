package net.runelite.client.plugins.microbot.util.player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

/** Util action batch AB: the terminal logout prompt. */
public class UtilActionBatchABTest
{
	@Test
	public void theReasonIsShownOnceAfterTheLogout()
	{
		TerminalLogoutPrompt prompt = new TerminalLogoutPrompt();
		List<String> shown = new ArrayList<>();
		assertFalse("nothing begun", prompt.tick(() -> Rs2Player.TerminalLogoutState.LOGGED_OUT, shown::add));

		prompt.begin("Ran out of supplies: Shark");
		prompt.begin("ignored while active");
		assertTrue(prompt.active());
		assertFalse(prompt.tick(() -> Rs2Player.TerminalLogoutState.LOGOUT_REQUESTED, shown::add));
		assertTrue(prompt.tick(() -> Rs2Player.TerminalLogoutState.LOGGED_OUT, shown::add));
		assertEquals(List.of("Ran out of supplies: Shark"), shown);
		assertFalse(prompt.active());
	}

	@Test
	public void attemptsAreThrottled()
	{
		AtomicLong now = new AtomicLong(1_000);
		TerminalLogoutPrompt prompt = new TerminalLogoutPrompt(now::get);
		int[] calls = {0};
		prompt.begin("out of food");
		prompt.throttledTick(() -> { calls[0]++; return Rs2Player.TerminalLogoutState.LOGOUT_REQUESTED; }, s -> { });
		prompt.throttledTick(() -> { calls[0]++; return Rs2Player.TerminalLogoutState.LOGOUT_REQUESTED; }, s -> { });
		assertEquals("within 600 ms", 1, calls[0]);
		now.addAndGet(600);
		prompt.throttledTick(() -> { calls[0]++; return Rs2Player.TerminalLogoutState.LOGOUT_REQUESTED; }, s -> { });
		assertEquals(2, calls[0]);
		prompt.reset();
		assertFalse(prompt.active());
	}
}
