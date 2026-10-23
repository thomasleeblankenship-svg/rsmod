/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/IEntity.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api;

import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

/**
 * Common read surface of a cached game entity.
 * <p>
 * Adaptation: upstream's {@code click()}, {@code click(String)} and
 * {@code isReachable()} are omitted because the action, menu and reachability
 * stacks are not ported in this stage; calling them would be a compile error
 * rather than a silent no-op.
 */
public interface IEntity
{
	int getId();

	String getName();

	WorldPoint getWorldLocation();

	LocalPoint getLocalLocation();

	WorldView getWorldView();
}
