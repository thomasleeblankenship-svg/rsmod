/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/player/Rs2PlayerCache.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.TickSnapshotCache;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;

/**
 * Players across all loaded world views (the local player included, as upstream), as a per-tick
 * client-thread snapshot. Obtain it through {@link MicrobotReadServices#getRs2PlayerCache()};
 * only {@link MicrobotReadServices} constructs it. Upstream's deprecated static
 * {@code getPlayersStream()} is not ported.
 */
public final class Rs2PlayerCache
{
	private final MicrobotReadServices services;
	private final TickSnapshotCache<Rs2PlayerModel> snapshot;

	public Rs2PlayerCache(MicrobotReadServices services)
	{
		this.services = services;
		this.snapshot = new TickSnapshotCache<>(services, this::capture);
	}

	public Rs2PlayerQueryable query()
	{
		return new Rs2PlayerQueryable(services, getStream());
	}

	/** All players in the current scene across all world views. */
	public Stream<Rs2PlayerModel> getStream()
	{
		return snapshot.getStream();
	}

	private List<Rs2PlayerModel> capture()
	{
		Client client = services.getClient();
		List<Rs2PlayerModel> result = new ArrayList<>();
		for (int id : services.getWorldViewIds())
		{
			WorldView worldView = client.getWorldView(id);
			if (worldView == null)
			{
				continue;
			}

			for (Player player : worldView.players())
			{
				if (player != null)
				{
					result.add(new Rs2PlayerModel(client, player));
				}
			}
		}
		return result;
	}
}
