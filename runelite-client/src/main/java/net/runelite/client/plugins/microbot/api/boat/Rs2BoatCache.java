/*
 * Adapted from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/boat/Rs2BoatCache.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.boat;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.boat.models.Rs2BoatModel;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;

/**
 * As upstream: the boat the local player (or another player) is standing on, rechecked at most
 * every two game ticks. Adaptation: built over the mobile read services instead of Guice, and the
 * lookup runs as one client-thread read.
 */
public final class Rs2BoatCache
{
	private final MicrobotReadServices services;
	private int lastCheckedOnBoat = Integer.MIN_VALUE / 2;
	private Rs2BoatModel boat;

	/** Built by {@code Microbot.getRs2BoatCache()} over the installed read services. */
	public Rs2BoatCache(MicrobotReadServices services)
	{
		this.services = services;
	}

	/** As upstream: the local player's boat, or {@code null} when not on one. */
	public synchronized Rs2BoatModel getLocalBoat()
	{
		return getBoat(null);
	}

	/** As upstream: {@code player}'s boat ({@code null} means the local player), or {@code null}. */
	public synchronized Rs2BoatModel getBoat(Rs2PlayerModel player)
	{
		Client client = services.getClient();
		int tick = services.read(client::getTickCount).orElse(0);
		if (lastCheckedOnBoat + 2 >= tick)
		{
			return boat;
		}
		lastCheckedOnBoat = tick;
		WorldEntity entity = services.read(() ->
		{
			Player live = player == null ? client.getLocalPlayer() : player.getPlayer();
			WorldView view = live == null ? null : live.getWorldView();
			if (view == null || view.isTopLevel())
			{
				return null;
			}
			LocalPoint local = live.getLocalLocation();
			return local == null ? null : client.getTopLevelWorldView().worldEntities().byIndex(local.getWorldView());
		}).orElse(null);
		boat = entity != null ? new Rs2BoatModel(entity) : null;
		return boat;
	}
}
