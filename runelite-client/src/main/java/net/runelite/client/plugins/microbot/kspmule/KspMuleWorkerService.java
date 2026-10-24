package net.runelite.client.plugins.microbot.kspmule;

/**
 * Mobile stand-in for KSP's mule worker: it never contacts or trades with a mule account (see
 * {@link KspMuleConfig}). Kept with KSP's method names so KSP plugins compile unchanged.
 */
public final class KspMuleWorkerService
{
	/** Lifecycle states, as KSP names them; mobile stays {@link #IDLE}. */
	public enum State { IDLE }

	private final String ownerName;

	public KspMuleWorkerService(String ownerName)
	{
		this.ownerName = ownerName;
	}

	/** Always false on mobile: no transfer ever takes priority over the script. */
	public static boolean isTransferPriorityActive()
	{
		return false;
	}

	/** No-op on mobile. */
	public synchronized void start(KspMuleConfig config)
	{
	}

	/** No-op on mobile. */
	public synchronized void shutdown()
	{
	}

	public State getState()
	{
		return State.IDLE;
	}

	public String getStatus()
	{
		return "Mule transfers are not available on mobile (" + ownerName + ")";
	}

	public String getMuleName()
	{
		return "";
	}

	public long getTotalCoins()
	{
		return 0;
	}

	public long getTransferCoins()
	{
		return 0;
	}

	public int getQueuePosition()
	{
		return -1;
	}

	public boolean isReceiverOnline()
	{
		return false;
	}
}
