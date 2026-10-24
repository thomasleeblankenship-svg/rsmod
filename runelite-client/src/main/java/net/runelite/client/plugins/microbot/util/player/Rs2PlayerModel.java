package net.runelite.client.plugins.microbot.util.player;

import net.runelite.api.Client;
import net.runelite.api.Player;

/**
 * The player model Microbot's {@link Rs2Player#getPlayers} and {@link Rs2Player#getLocalPlayer}
 * return. On RuneMobile it is the same client-thread snapshot as
 * {@link net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel} (upstream's
 * version wraps the live {@link Player} instead), so either type can hold it.
 */
public class Rs2PlayerModel extends net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel
{
	/** Captures {@code player}; must be called on the client thread. */
	public Rs2PlayerModel(Client client, Player player)
	{
		super(client, player);
	}

	/** As upstream: the live player's appearance, read on the client thread ({@code null} when absent). */
	public net.runelite.api.PlayerComposition getPlayerComposition()
	{
		Player player = getPlayer();
		return player == null ? null : net.runelite.client.plugins.microbot.mobile.MobileActions.read(player::getPlayerComposition, null);
	}

	/** As upstream: the live player's team cape number (0 without one). */
	public int getTeam()
	{
		Player player = getPlayer();
		return player == null ? 0 : net.runelite.client.plugins.microbot.mobile.MobileActions.read(player::getTeam, 0);
	}
}
