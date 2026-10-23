/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/npc/Rs2NpcQueryable.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.npc;

import java.util.stream.Stream;
import net.runelite.client.plugins.microbot.api.AbstractEntityQueryable;
import net.runelite.client.plugins.microbot.api.IEntityQueryable;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;

/** Query over one {@link Rs2NpcCache} snapshot; created only by {@link Rs2NpcCache#query()}. */
public final class Rs2NpcQueryable extends AbstractEntityQueryable<Rs2NpcQueryable, Rs2NpcModel>
	implements IEntityQueryable<Rs2NpcQueryable, Rs2NpcModel>
{
	Rs2NpcQueryable(MicrobotReadServices services, Stream<Rs2NpcModel> source)
	{
		super(services, source);
	}
}
