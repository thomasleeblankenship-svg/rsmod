/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/tileobject/Rs2TileObjectCache.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.tileobject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.TickSnapshotCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

/**
 * Game, ground, wall and decorative objects on each loaded world view's scanned
 * floor (the local player's floor when the player is in that view; see
 * {@link MicrobotReadServices#scanPlane}), as a per-tick client-thread snapshot. A multi-tile game object is
 * listed once, from its scene-min tile (upstream rule). Obtain it through
 * {@code Microbot.getRs2TileObjectCache()}; only {@link MicrobotReadServices}
 * constructs it.
 */
public final class Rs2TileObjectCache
{
	private final MicrobotReadServices services;
	private final TickSnapshotCache<Rs2TileObjectModel> snapshot;

	public Rs2TileObjectCache(MicrobotReadServices services)
	{
		this.services = services;
		this.snapshot = new TickSnapshotCache<>(services, this::capture);
	}

	public Rs2TileObjectQueryable query()
	{
		return new Rs2TileObjectQueryable(services, getStream());
	}

	/** All tile objects in the current scene. */
	public Stream<Rs2TileObjectModel> getStream()
	{
		return snapshot.getStream();
	}

	private List<Rs2TileObjectModel> capture()
	{
		Client client = services.getClient();
		if (client.getLocalPlayer() == null)
		{
			return Collections.emptyList();
		}

		List<Rs2TileObjectModel> result = new ArrayList<>();
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

					if (tile.getGameObjects() != null)
					{
						for (GameObject gameObject : tile.getGameObjects())
						{
							if (gameObject != null && gameObject.getSceneMinLocation().equals(tile.getSceneLocation()))
							{
								result.add(new Rs2TileObjectModel(services, gameObject));
							}
						}
					}
					if (tile.getGroundObject() != null)
					{
						result.add(new Rs2TileObjectModel(services, tile.getGroundObject()));
					}
					if (tile.getWallObject() != null)
					{
						result.add(new Rs2TileObjectModel(services, tile.getWallObject()));
					}
					if (tile.getDecorativeObject() != null)
					{
						result.add(new Rs2TileObjectModel(services, tile.getDecorativeObject()));
					}
				}
			}
		}
		return result;
	}
}
