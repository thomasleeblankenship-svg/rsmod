package net.runelite.client.plugins.microbot.firemakingplus;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Progressive log choice for the Firemaking Plus mobile port: members logs only on members worlds. */
public class LogsTest
{
	@Test
	public void freeWorldSkipsMembersLogs()
	{
		assertEquals(Logs.WILLOW, Logs.bestFor(35, false));
		assertEquals(Logs.MAPLE, Logs.bestFor(50, false));
		assertEquals(Logs.YEW, Logs.bestFor(99, false));
	}

	@Test
	public void membersWorldKeepsUpstreamChoice()
	{
		assertEquals(Logs.TEAK, Logs.bestFor(35, true));
		assertEquals(Logs.MAHOGANY, Logs.bestFor(50, true));
		assertEquals(Logs.REDWOOD, Logs.bestFor(99, true));
	}

	@Test
	public void lowLevelsBurnPlainLogs()
	{
		assertEquals(Logs.LOGS, Logs.bestFor(1, false));
		assertEquals(Logs.OAK, Logs.bestFor(15, true));
	}
}
