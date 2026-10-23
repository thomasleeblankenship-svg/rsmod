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
}
