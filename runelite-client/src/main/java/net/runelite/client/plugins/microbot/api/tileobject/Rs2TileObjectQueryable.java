/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/tileobject/Rs2TileObjectQueryable.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.tileobject;

import java.util.stream.Stream;
import net.runelite.client.plugins.microbot.api.AbstractEntityQueryable;
import net.runelite.client.plugins.microbot.api.IEntityQueryable;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

/** Query over one {@link Rs2TileObjectCache} snapshot; created only by {@link Rs2TileObjectCache#query()}. */
public final class Rs2TileObjectQueryable extends AbstractEntityQueryable<Rs2TileObjectQueryable, Rs2TileObjectModel>
	implements IEntityQueryable<Rs2TileObjectQueryable, Rs2TileObjectModel>
{
	Rs2TileObjectQueryable(MicrobotReadServices services, Stream<Rs2TileObjectModel> source)
	{
		super(services, source);
	}
	/** Model reads and live action resolution already marshal to the client thread. */
	public Rs2TileObjectModel nearestOnClientThread()
	{
		return nearest();
	}

	public java.util.List<Rs2TileObjectModel> toListOnClientThread()
	{
		return toList();
	}

}
