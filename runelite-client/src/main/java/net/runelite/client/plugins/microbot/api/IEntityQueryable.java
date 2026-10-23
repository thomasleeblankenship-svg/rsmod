/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/IEntityQueryable.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api;

import java.util.List;
import java.util.function.Predicate;
import net.runelite.api.coords.WorldPoint;

/**
 * Fluent, single-use query over one cache snapshot.
 * <p>
 * Adaptation: the read-only subset of the upstream interface. Upstream's
 * {@code firstReachable}, {@code nearestReachable*}, {@code *OnClientThread}
 * and {@code interact*} are not ported (reachability and action stacks are
 * out of scope); their absence is reported in {@code docs/microbot/claude2-api-report.md}.
 */
public interface IEntityQueryable<Q extends IEntityQueryable<Q, E>, E extends IEntity>
{
	Q fromWorldView();

	Q where(Predicate<E> predicate);

	Q within(int distance);

	Q within(WorldPoint anchor, int distance);

	Q withName(String name);

	Q withNameContains(String substring);

	Q withNames(String... names);

	Q withId(int id);

	Q withIds(int... ids);

	E first();

	E nearest();

	E nearest(int maxDistance);

	E nearest(WorldPoint anchor, int maxDistance);

	List<E> toList();

	int count();

	/** First entity the local player can walk to (or stand next to). */
	E firstReachable();

	/** Nearest entity the local player can walk to (or stand next to). */
	E nearestReachable();

	/** {@link #nearestReachable()} within {@code maxDistance} tiles. */
	E nearestReachable(int maxDistance);

	/** As upstream: clicks the nearest reachable match with its first action. */
	boolean interact();

	/** As upstream: {@code action} on the nearest reachable match. */
	boolean interact(String action);

	boolean interact(String action, int maxDistance);

	boolean interact(int id);

	boolean interact(int id, String action);

	boolean interact(int id, String action, int maxDistance);

	E firstOnClientThread();

	E nearestOnClientThread();

	E nearestOnClientThread(int maxDistance);

	E nearestOnClientThread(WorldPoint anchor, int maxDistance);

	List<E> toListOnClientThread();
}
