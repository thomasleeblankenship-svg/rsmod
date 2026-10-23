package net.runelite.client.plugins.microbot.api;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import net.runelite.client.eventbus.Subscribe;
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
 * Lifecycle (performed by the hosting plugin, not by scripts):
 * <ol>
 * <li>inject this class, {@code eventBus.register(services)} and {@link #install(MicrobotReadServices)};</li>
 * <li>on shutdown, {@code eventBus.unregister(services)} and {@link #uninstall(MicrobotReadServices)}.</li>
 * </ol>
 * Event handlers mirror upstream {@code MicrobotPlugin}: world-view ids,
 * {@code Rs2Inventory.storeInventoryItemsInMemory} and
 * {@code Rs2Player.handleAnimationChanged}. Additionally, any game-state or
 * world-view change invalidates every snapshot, and leaving
 * {@code LOGGED_IN} clears inventory and animation state.
 */
@Singleton
public final class MicrobotReadServices implements MicrobotReadApi
{
	private static volatile MicrobotReadServices installed;

	private final Client client;
	private final ClientThreadCalls clientThreadCalls;
	private final Set<Integer> worldViewIds = ConcurrentHashMap.newKeySet();
	private final AtomicInteger generation = new AtomicInteger();
	private final Rs2NpcCache npcCache;
	private final Rs2TileObjectCache tileObjectCache;
	private final Rs2TileItemCache tileItemCache;

	@Inject
	public MicrobotReadServices(Client client, ClientThread clientThread)
	{
		this(client, new ClientThreadCalls(client, clientThread));
	}

	public MicrobotReadServices(Client client, ClientThreadCalls clientThreadCalls)
	{
		this.client = client;
		this.clientThreadCalls = clientThreadCalls;
		this.npcCache = new Rs2NpcCache(this);
		this.tileObjectCache = new Rs2TileObjectCache(this);
		this.tileItemCache = new Rs2TileItemCache(this);
	}

	/** Makes {@code services} the process-wide provider. */
	public static void install(MicrobotReadServices services)
	{
		installed = services;
	}

	/** Removes {@code services} if it is still the installed provider and clears static read state. */
	public static void uninstall(MicrobotReadServices services)
	{
		if (installed == services)
		{
			installed = null;
			Rs2Inventory.clear();
			Rs2Player.clear();
		}
	}

	/** The installed provider, or {@code null} before plugin start-up / after shutdown. */
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
	public Client getClient()
	{
		return client;
	}

	@Override
	public ClientThreadCalls getClientThreadCalls()
	{
		return clientThreadCalls;
	}

	@Override
	public boolean isLoggedIn()
	{
		return client.getGameState() == GameState.LOGGED_IN;
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

	/**
	 * World views to scan: the top-level view plus every loaded sub-view (boats).
	 * Upstream only iterates ids from {@link WorldViewLoaded}; the top-level id is
	 * added explicitly so a plugin started after login still sees the main scene.
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
