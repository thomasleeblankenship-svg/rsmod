package net.runelite.client.plugins.mobilemicrobot;

import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

/** Minimal client-thread player state, no implicit input side effects. */
public final class Rs2Player
{
	private final MobileScriptContext context;

	Rs2Player(MobileScriptContext context)
	{
		this.context = context;
	}

	public WorldPoint location()
	{
		Player p = context.client().getLocalPlayer();
		return p == null ? null : p.getWorldLocation();
	}

	public boolean isMoving()
	{
		Player p = context.client().getLocalPlayer();
		// RuneMobile exposes movement poses, not Microbot's Player.isMoving().
		// Conservatively treat turning/non-idle poses as busy too.
		return p != null && p.getPoseAnimation() != p.getIdlePoseAnimation();
	}

	public boolean isAnimating()
	{
		Player p = context.client().getLocalPlayer();
		return p != null && p.getAnimation() != -1;
	}

	public boolean isIdle()
	{
		return context.loggedIn() && !isMoving() && !isAnimating();
	}
}
