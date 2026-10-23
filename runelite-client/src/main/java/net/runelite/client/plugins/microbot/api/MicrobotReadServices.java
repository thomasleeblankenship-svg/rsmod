package net.runelite.client.plugins.microbot.api;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WorldView;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WorldViewLoaded;
import net.runelite.api.events.WorldViewUnloaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.MicrobotRuntimeService;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Singleton implementation of {@link MicrobotReadApi}: owns the NPC, tile-object
 * and tile-item caches and routes the client events that keep the static
 * {@link Rs2Inventory} and {@link Rs2Player} read state current.
 * <p>
 * Lifecycle: pass this service to {@link MicrobotRuntime#install}. Its
 * {@link #startUp()} subscribes to the event bus and makes it the installed
 * provider; {@link #shutDown()} reverses that and drops every cached entity.
 * Scripts never construct it.
 * <p>
 * Event handlers mirror upstream {@code MicrobotPlugin}: world-view ids,
 * {@code Rs2Inventory.storeInventoryItemsInMemory} and
 * {@code Rs2Player.handleAnimationChanged}. Additionally, any game-state or
 * world-view change invalidates every snapshot, and leaving
 * {@code LOGGED_IN} clears inventory and animation state.
 */
@Singleton
public final class MicrobotReadServices implements MicrobotReadApi, MicrobotRuntimeService
{
	private static volatile MicrobotReadServices installed;

	private final Client client;
	private final ClientThread clientThread;
	private final EventBus eventBus;
	private final long readTimeoutMillis;
	private final Set<Integer> worldViewIds = ConcurrentHashMap.newKeySet();
	private final AtomicInteger generation = new AtomicInteger();
	private final List<TickSnapshotCache<?>> snapshots = new CopyOnWriteArrayList<>();
	private final Rs2NpcCache npcCache;
	private final Rs2TileObjectCache tileObjectCache;
	private final Rs2TileItemCache tileItemCache;

	@Inject
	public MicrobotReadServices(Client client, ClientThread clientThread, EventBus eventBus)
	{
		this(client, clientThread, eventBus, ClientThread.DEFAULT_CLIENT_THREAD_TIMEOUT_MILLIS);
	}

	/**
	 * @param readTimeoutMillis how long an off-client-thread read waits before it
	 *                          returns empty; upstream uses 10 s
	 */
	public MicrobotReadServices(Client client, ClientThread clientThread, EventBus eventBus, long readTimeoutMillis)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.eventBus = eventBus;
		this.readTimeoutMillis = readTimeoutMillis;
		this.npcCache = new Rs2NpcCache(this);
		this.tileObjectCache = new Rs2TileObjectCache(this);
		this.tileItemCache = new Rs2TileItemCache(this);
	}

	/** The installed provider, or {@code null} while the runtime is not installed. */
	public static MicrobotReadServices current()
	{
		return installed;
	}

	/** The installed provider; throws when none is installed rather than returning fake data. */
	public static MicrobotReadServices require()
	{
		MicrobotReadServices services = installed;
		if (services == null)
		{
			throw new IllegalStateException("Microbot read services are not installed");
		}
		return services;
	}

	@Override
	public void startUp()
	{
		if (installed != null && installed != this)
		{
			throw new IllegalStateException("Another MicrobotReadServices instance is installed");
		}
		eventBus.register(this);
		installed = this;
	}

	@Override
	public void shutDown()
	{
		if (installed == this)
		{
			installed = null;
		}
		eventBus.unregister(this);
		worldViewIds.clear();
		generation.incrementAndGet();
		snapshots.forEach(TickSnapshotCache::clear);
		Rs2Inventory.clear();
		Rs2Player.clear();
	}

	@Override
	public Client getClient()
	{
		return client;
	}

	@Override
	public ClientThread getClientThread()
	{
		return clientThread;
	}

	/**
	 * Runs {@code method} on the client thread via
	 * {@link ClientThread#runOnClientThreadOptional(Callable, long)}: inline on the
	 * client thread, otherwise waiting at most the read timeout.
	 */
	public <T> Optional<T> read(Callable<T> method)
	{
		return clientThread.runOnClientThreadOptional(method, readTimeoutMillis);
	}

	@Override
	public Rs2NpcCache getRs2NpcCache()
	{
		return npcCache;
	}

	@Override
	public Rs2TileObjectCache getRs2TileObjectCache()
	{
		return tileObjectCache;
	}

	@Override
	public Rs2TileItemCache getRs2TileItemCache()
	{
		return tileItemCache;
	}

	/** Snapshot invalidation counter; increases on every game-state or world-view change. */
	public int getGeneration()
	{
		return generation.get();
	}

	void track(TickSnapshotCache<?> snapshot)
	{
		snapshots.add(snapshot);
	}

	/**
	 * World views to scan: the top-level view plus every loaded sub-view (boats).
	 * Upstream only iterates ids from {@link WorldViewLoaded}; the top-level id is
	 * added explicitly so a runtime installed after login still sees the main scene.
	 * Call on the client thread.
	 */
	public Set<Integer> getWorldViewIds()
	{
		Set<Integer> ids = new LinkedHashSet<>();
		WorldView topLevel = client.getTopLevelWorldView();
		if (topLevel != null)
		{
			ids.add(topLevel.getId());
		}
		ids.addAll(worldViewIds);
		return ids;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		generation.incrementAndGet();
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			worldViewIds.clear();
			Rs2Inventory.clear();
			Rs2Player.clear();
		}
	}

	@Subscribe
	public void onWorldViewLoaded(WorldViewLoaded event)
	{
		worldViewIds.add(event.getWorldView().getId());
		generation.incrementAndGet();
	}

	@Subscribe
	public void onWorldViewUnloaded(WorldViewUnloaded event)
	{
		worldViewIds.remove(event.getWorldView().getId());
		generation.incrementAndGet();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		Rs2Inventory.storeInventoryItemsInMemory(event);
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Rs2Player.handleAnimationChanged(event);
	}
}
