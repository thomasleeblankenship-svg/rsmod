package net.runelite.client.plugins.microbot.api;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.GameState;

/**
 * Immutable per-tick scene snapshot shared by the entity caches.
 * <p>
 * Upstream caches rebuild once per {@code client.getTickCount()} on the client
 * thread. This class keeps that rule and adds an invalidation generation that
 * {@link MicrobotReadServices} bumps on every game-state and world-view change,
 * so a logout, hop or scene load can never serve the previous scene's list even
 * when the tick counter does not advance or restarts. While the client is not
 * {@link GameState#LOGGED_IN} the snapshot is empty.
 * <p>
 * The published list is unmodifiable and safely published through a volatile
 * field, so any thread may stream it. Entities in it are snapshots of the
 * capture tick; re-query instead of holding them across ticks.
 */
public final class TickSnapshotCache<T>
{
	private static final class Snapshot<T>
	{
		final int tick;
		final int generation;
		final List<T> entities;

		Snapshot(int tick, int generation, List<T> entities)
		{
			this.tick = tick;
			this.generation = generation;
			this.entities = entities;
		}
	}

	private final MicrobotReadServices services;
	private final Supplier<List<T>> capture;
	private volatile Snapshot<T> snapshot;

	/**
	 * @param capture builds the scene list; always invoked on the client thread
	 */
	public TickSnapshotCache(MicrobotReadServices services, Supplier<List<T>> capture)
	{
		this.services = services;
		this.capture = capture;
	}

	/** Returns the current tick's entities, rebuilding on the client thread if stale. */
	public Stream<T> getStream()
	{
		return services.getClientThreadCalls().call(this::currentOnClientThread)
			.orElse(Collections.emptyList())
			.stream();
	}

	private List<T> currentOnClientThread()
	{
		Client client = services.getClient();
		int generation = services.getGeneration();
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			snapshot = null;
			return Collections.emptyList();
		}

		int tick = client.getTickCount();
		Snapshot<T> current = snapshot;
		if (current != null && current.tick == tick && current.generation == generation)
		{
			return current.entities;
		}

		List<T> entities = Collections.unmodifiableList(capture.get());
		snapshot = new Snapshot<>(tick, generation, entities);
		return entities;
	}
}
