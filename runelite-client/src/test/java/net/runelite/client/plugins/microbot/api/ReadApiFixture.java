package net.runelite.client.plugins.microbot.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Real RuneLite/RuneMobile interface types mocked at their boundary, with a
 * dedicated single "client thread". {@link ClientThread#invoke(Runnable)} runs
 * on that thread and {@link Client#isClientThread()} is true only there, so
 * every read from the JUnit (script) thread performs a real cross-thread hop.
 * No game, cache or device is involved.
 */
final class ReadApiFixture implements AutoCloseable
{
	static final int TOP_LEVEL_ID = -1;
	static final int BASE_X = 3200;
	static final int BASE_Y = 3200;

	final ExecutorService clientExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-client-thread"));
	final AtomicReference<Thread> clientThreadRef = new AtomicReference<>();
	final Client client = mock(Client.class);
	final ClientThread clientThread = mock(ClientThread.class);
	final WorldView topLevel = mock(WorldView.class);
	final Scene scene = mock(Scene.class);
	final Tile[][][] tiles = new Tile[4][32][32];
	final List<NPC> npcs = new ArrayList<>();
	final Player localPlayer = mock(Player.class);
	final MicrobotReadServices services;
	volatile GameState gameState = GameState.LOGGED_IN;
	volatile int tick = 1;
	volatile boolean clientThreadStalled;

	ReadApiFixture() throws Exception
	{
		clientExecutor.submit(() -> clientThreadRef.set(Thread.currentThread())).get();
		when(client.isClientThread()).thenAnswer(i -> Thread.currentThread() == clientThreadRef.get());
		doAnswer(call ->
		{
			if (!clientThreadStalled)
			{
				clientExecutor.execute(call.getArgument(0));
			}
			return null;
		}).when(clientThread).invoke(any(Runnable.class));

		when(client.getGameState()).thenAnswer(i -> gameState);
		when(client.getTickCount()).thenAnswer(i -> tick);
		when(client.getTopLevelWorldView()).thenReturn(topLevel);
		when(client.getWorldView(anyInt())).thenAnswer(i -> (int) i.getArgument(0) == TOP_LEVEL_ID ? topLevel : null);
		when(client.getLocalPlayer()).thenReturn(localPlayer);

		when(topLevel.getId()).thenReturn(TOP_LEVEL_ID);
		when(topLevel.isTopLevel()).thenReturn(true);
		when(topLevel.getPlane()).thenReturn(0);
		when(topLevel.getBaseX()).thenReturn(BASE_X);
		when(topLevel.getBaseY()).thenReturn(BASE_Y);
		when(topLevel.getScene()).thenReturn(scene);
		when(scene.getTiles()).thenReturn(tiles);
		IndexedObjectSet<NPC> npcSet = objectSet(npcs);
		doAnswer(i -> npcSet).when(topLevel).npcs();

		when(localPlayer.getWorldView()).thenReturn(topLevel);
		placePlayer(new WorldPoint(BASE_X + 5, BASE_Y + 5, 0));

		services = new MicrobotReadServices(client, new ClientThreadCalls(client, clientThread, 2_000));
		MicrobotReadServices.install(services);
	}

	@SuppressWarnings("unchecked")
	private static <T> IndexedObjectSet<T> objectSet(List<T> backing)
	{
		IndexedObjectSet<T> set = mock(IndexedObjectSet.class);
		when(set.iterator()).thenAnswer(i -> new ArrayList<>(backing).iterator());
		return set;
	}

	void placePlayer(WorldPoint location)
	{
		when(localPlayer.getWorldLocation()).thenReturn(location);
	}

	/** Mocked NPC that asserts every read happens on the client thread. */
	NPC npc(int id, String name, WorldPoint location)
	{
		NPC npc = mock(NPC.class);
		when(npc.getId()).thenAnswer(i -> onClientThread(id));
		when(npc.getName()).thenAnswer(i -> onClientThread(name));
		when(npc.getWorldLocation()).thenAnswer(i -> onClientThread(location));
		when(npc.getWorldView()).thenReturn(topLevel);
		npcs.add(npc);
		return npc;
	}

	<T> T onClientThread(T value)
	{
		if (Thread.currentThread() != clientThreadRef.get())
		{
			throw new AssertionError("live game state read off the client thread: " + Thread.currentThread().getName());
		}
		return value;
	}

	void gameState(GameState state)
	{
		gameState = state;
		GameStateChanged event = new GameStateChanged();
		event.setGameState(state);
		services.onGameStateChanged(event);
	}

	@Override
	public void close() throws Exception
	{
		MicrobotReadServices.uninstall(services);
		clientExecutor.shutdownNow();
		clientExecutor.awaitTermination(5, TimeUnit.SECONDS);
	}
}
