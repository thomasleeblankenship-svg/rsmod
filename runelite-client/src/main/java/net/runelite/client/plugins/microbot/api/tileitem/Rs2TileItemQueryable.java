/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/tileitem/Rs2TileItemQueryable.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.tileitem;

import java.util.stream.Stream;
import net.runelite.client.plugins.microbot.api.AbstractEntityQueryable;
import net.runelite.client.plugins.microbot.api.IEntityQueryable;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;

/** Query over one {@link Rs2TileItemCache} snapshot; created only by {@link Rs2TileItemCache#query()}. */
public final class Rs2TileItemQueryable extends AbstractEntityQueryable<Rs2TileItemQueryable, Rs2TileItemModel>
	implements IEntityQueryable<Rs2TileItemQueryable, Rs2TileItemModel>
{
	Rs2TileItemQueryable(MicrobotReadServices services, Stream<Rs2TileItemModel> source)
	{
		super(services, source);
	}
}
