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

	/**
	 * Whether the local player is fighting: interacting with an actor that has a combat level, and
	 * either animating or still interacting. False when logged out or without a player.
	 */
	public static boolean inCombat()
	{
		return MobileActions.read(() ->
		{
			net.runelite.api.Player player = Microbot.getClient().getLocalPlayer();
			net.runelite.api.Actor target = player == null ? null : player.getInteracting();
			return target != null && target.getCombatLevel() >= 1
				&& (player.getAnimation() != -1 || player.isInteracting());
		}, false);
	}

	// ---- Actions (Claude 1) ----

	/** As upstream: true once auto retaliate matches {@code enable}; opens the combat tab to reach the button. */
	public static boolean setAutoRetaliate(boolean enable)
	{
		int expected = enable ? 0 : 1;
		if (Microbot.getVarbitPlayerValue(net.runelite.api.gameval.VarPlayerID.OPTION_NODEF) == expected) { return true; }
		return net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.switchTo(net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.COMBAT)
			&& net.runelite.client.plugins.microbot.util.Global.sleepUntil(() ->
				MobileActions.widget(net.runelite.api.gameval.InterfaceID.CombatInterface.RETALIATE, -1, ""), 2_000)
			&& net.runelite.client.plugins.microbot.util.Global.sleepUntil(() ->
				Microbot.getVarbitPlayerValue(net.runelite.api.gameval.VarPlayerID.OPTION_NODEF) == expected, 2_000);
	}

	/** Upstream spelling. */
	public static boolean enableAutoRetialiate()
	{
		return setAutoRetaliate(true);
	}

	/** As upstream: special attack energy, 0-1000 (100% = 1000). */
	public static int getSpecEnergy()
	{
		return Microbot.getVarbitPlayerValue(net.runelite.api.gameval.VarPlayerID.SA_ENERGY);
	}
}
