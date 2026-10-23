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
 * {@code click()} uses the entity's first action; {@code click(String)} dispatches one action
 * through {@code MobileActions} (one per game tick, only for the bound script session), and
 * returns whether it was dispatched. Adaptation: upstream's {@code isReachable()} is omitted;
 * use the queryables' {@code nearestReachable}.
 */
public interface IEntity
{
	int getId();

	String getName();

	WorldPoint getWorldLocation();

	LocalPoint getLocalLocation();

	WorldView getWorldView();

	boolean click();

	boolean click(String action);
}
