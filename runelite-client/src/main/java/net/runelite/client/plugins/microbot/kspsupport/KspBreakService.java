package net.runelite.client.plugins.microbot.kspsupport;

/**
 * Mobile stand-in for KSP's logout-break controller: never schedules a break, never logs out,
 * never logs back in. Kept with KSP's method names so KSP plugins compile unchanged (see
 * {@link KspBreakConfig} for why breaks are not ported).
 */
public final class KspBreakService
{
	/** No-op on mobile. */
	public synchronized void start(KspBreakConfig settings)
	{
	}

	/** No-op on mobile. */
	public synchronized void shutdown()
	{
	}

	/** Always true: there is no break to wait out. */
	public static boolean awaitResume()
	{
		return true;
	}

	/** Always false on mobile. */
	public static boolean isBreakActive()
	{
		return false;
	}

	/** Always false on mobile: scripts are never paused for a break. */
	public static boolean shouldPause()
	{
		return false;
	}
}
