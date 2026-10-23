package net.runelite.client.plugins.microbot.util.combat;

import net.runelite.api.VarPlayer;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/** Special-attack toggle with energy guard and state confirmation. */
public final class Rs2Combat
{
	private Rs2Combat() { }
	public static boolean getSpecState()
	{
		return MobileActions.read(() -> Microbot.getClient().getVarpValue(VarPlayer.SPECIAL_ATTACK_ENABLED) == 1, false);
	}
	public static boolean setSpecState(boolean enabled, int requiredEnergy)
	{
		if (getSpecState() == enabled) { return true; }
		int energy = MobileActions.read(() -> Microbot.getClient().getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT), 0);
		if (enabled && energy < Math.max(0, requiredEnergy)) { return false; }
		if (!MobileActions.widget(10485795, -1, "")) { return false; }
		return Rs2Player.waitFor(() -> getSpecState() == enabled, 1800);
	}
	public static boolean setSpecState(boolean enabled) { return setSpecState(enabled, 0); }
}
