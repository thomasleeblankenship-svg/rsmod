/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/player/Rs2PlayerQueryable.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.player;

import java.util.stream.Stream;
import net.runelite.client.plugins.microbot.api.AbstractEntityQueryable;
import net.runelite.client.plugins.microbot.api.IEntityQueryable;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;

/** Query over one {@link Rs2PlayerCache} snapshot; created only by {@link Rs2PlayerCache#query()}. */
public final class Rs2PlayerQueryable extends AbstractEntityQueryable<Rs2PlayerQueryable, Rs2PlayerModel>
	implements IEntityQueryable<Rs2PlayerQueryable, Rs2PlayerModel>
{
	Rs2PlayerQueryable(MicrobotReadServices services, Stream<Rs2PlayerModel> source)
	{
		super(services, source);
	}
}
