/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/npc/Rs2NpcCache.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.TickSnapshotCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;

/**
 * NPCs across all loaded world views, as a per-tick client-thread snapshot.
 * Obtain it through {@code Microbot.getRs2NpcCache()}; only
 * {@link MicrobotReadServices} constructs it.
 */
public final class Rs2NpcCache
{
	private final MicrobotReadServices services;
	private final TickSnapshotCache<Rs2NpcModel> snapshot;

	public Rs2NpcCache(MicrobotReadServices services)
	{
		this.services = services;
		this.snapshot = new TickSnapshotCache<>(services, this::capture);
	}

	public Rs2NpcQueryable query()
	{
		return new Rs2NpcQueryable(services, getStream());
	}

	/** All NPCs in the current scene across all world views. */
	public Stream<Rs2NpcModel> getStream()
	{
		return snapshot.getStream();
	}

	private List<Rs2NpcModel> capture()
	{
		Client client = services.getClient();
		List<Rs2NpcModel> result = new ArrayList<>();
		for (int id : services.getWorldViewIds())
		{
			WorldView worldView = client.getWorldView(id);
			if (worldView == null)
			{
				continue;
			}

			for (NPC npc : worldView.npcs())
			{
				if (npc != null)
				{
					result.add(new Rs2NpcModel(client, npc));
				}
			}
		}
		return result;
	}
}
