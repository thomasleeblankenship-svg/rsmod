package net.runelite.client.plugins.microbot.util.camera;

/**
 * As upstream: keeps the camera turned towards an NPC every 200 ms until stopped. Mobile: one
 * tracker per client, shared with {@link Rs2Camera#trackNpc(int)}; this object is a handle on it.
 */
public class NpcTracker
{
	/** As upstream: starts tracking the nearest NPC with {@code npcId}; nothing while one is tracked. */
	public void startTracking(int npcId)
	{
		Rs2Camera.trackNpc(npcId);
	}

	/** As upstream: stops tracking. */
	public void stopTracking()
	{
		Rs2Camera.stopTrackingNpc();
	}

	/** As upstream: whether an NPC is being tracked. */
	public boolean isTracking()
	{
		return Rs2Camera.isTrackingNpc();
	}
}
