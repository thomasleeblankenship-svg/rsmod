/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/tileitem/Rs2TileItemCache.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.tileitem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.TickSnapshotCache;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;

/**
 * Ground items on each loaded world view's scanned floor (the local player's floor
 * when the player is in that view; see MicrobotReadServices#scanPlane), polled once per tick
 * on the client thread (upstream does not rely on ItemSpawned/ItemDespawned).
 * Obtain it through {@code Microbot.getRs2TileItemCache()}; only
 * {@link MicrobotReadServices} constructs it.
 */
public final class Rs2TileItemCache
{
	private final MicrobotReadServices services;
	private final TickSnapshotCache<Rs2TileItemModel> snapshot;

	public Rs2TileItemCache(MicrobotReadServices services)
	{
		this.services = services;
		this.snapshot = new TickSnapshotCache<>(services, this::capture);
	}

	public Rs2TileItemQueryable query()
	{
		return new Rs2TileItemQueryable(services, getStream());
	}

	/** All ground items in the current scene across all world views. */
	public Stream<Rs2TileItemModel> getStream()
	{
		return snapshot.getStream();
	}

	private List<Rs2TileItemModel> capture()
	{
		Client client = services.getClient();
		if (client.getLocalPlayer() == null)
		{
			return Collections.emptyList();
		}

		List<Rs2TileItemModel> result = new ArrayList<>();
		for (int id : services.getWorldViewIds())
		{
			WorldView worldView = client.getWorldView(id);
			Scene scene = worldView == null ? null : worldView.getScene();
			if (scene == null)
			{
				continue;
			}

			Tile[][] tiles = MicrobotReadServices.planeTiles(scene, services.scanPlane(worldView));
			if (tiles == null)
			{
				continue;
			}
			for (Tile[] tileRow : tiles)
			{
				for (Tile tile : tileRow)
				{
					if (tile == null)
					{
						continue;
					}

					List<TileItem> items = tile.getGroundItems();
					if (items == null || items.isEmpty())
					{
						continue;
					}

					for (TileItem item : items)
					{
						if (item != null)
						{
							result.add(new Rs2TileItemModel(client, worldView, tile, item));
						}
					}
				}
			}
		}
		return result;
	}
}
