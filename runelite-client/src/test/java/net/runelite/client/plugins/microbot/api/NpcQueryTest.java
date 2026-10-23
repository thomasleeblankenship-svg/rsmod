package net.runelite.client.plugins.microbot.api;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.WorldViewLoaded;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** NPC cache/query semantics with mocked RuneLite interfaces; not live-game evidence. */
public class NpcQueryTest
{
	private ReadApiFixture f;

	@Before
	public void setUp() throws Exception
	{
		f = new ReadApiFixture();
	}

	@After
	public void tearDown() throws Exception
	{
		f.close();
	}

	private WorldPoint at(int dx, int dy, int plane)
	{
		return new WorldPoint(ReadApiFixture.BASE_X + 5 + dx, ReadApiFixture.BASE_Y + 5 + dy, plane);
	}

	private List<String> names(List<Rs2NpcModel> npcs)
	{
		return npcs.stream().map(Rs2NpcModel::getName).sorted().collect(Collectors.toList());
	}

	@Test
	public void withinAndNearestUseChebyshevDistanceOnThePlayersPlane()
	{
		f.npc(1, "Man", at(3, 2, 0));
		f.npc(2, "Guard", at(-10, 4, 0));
		f.npc(3, "Far", at(16, 0, 0));
		f.npc(4, "Upstairs", at(1, 0, 1));

		List<Rs2NpcModel> nearby = f.services.getRs2NpcCache().query().within(15).toList();
		assertEquals(List.of("Guard", "Man"), names(nearby));

		Rs2NpcModel nearest = f.services.getRs2NpcCache().query().within(15).nearest();
		assertEquals("Man", nearest.getName());
		assertEquals(1, nearest.getId());

		// Unbounded nearest still never selects another plane.
		assertEquals("Man", f.services.getRs2NpcCache().query().nearest().getName());
		assertEquals(1, f.services.getRs2NpcCache().query().within(at(16, 0, 0), 0).count());
	}

	@Test
	public void filtersMatchUpstreamNameAndIdRules()
	{
		f.npc(1, "Man", at(1, 0, 0));
		f.npc(2, "Woman", at(2, 0, 0));
		f.npc(3, null, at(3, 0, 0));

		assertEquals(1, f.services.getRs2NpcCache().query().withName("man").count());
		assertEquals(2, f.services.getRs2NpcCache().query().withNameContains("MAN").count());
		assertEquals(2, f.services.getRs2NpcCache().query().withNames("woman", "MAN").count());
		assertEquals(2, f.services.getRs2NpcCache().query().withIds(1, 3).count());
		assertEquals(0, f.services.getRs2NpcCache().query().withName(null).count());
		assertEquals(0, f.services.getRs2NpcCache().query().withIds().count());
		assertEquals("Woman", f.services.getRs2NpcCache().query().where(n -> n.getId() == 2).first().getName());
	}

	@Test
	public void noLocalPlayerYieldsEmptyWithinAndNullNearest()
	{
		f.npc(1, "Man", at(1, 0, 0));
		when(f.client.getLocalPlayer()).thenReturn(null);

		assertTrue(f.services.getRs2NpcCache().query().within(15).toList().isEmpty());
		assertNull(f.services.getRs2NpcCache().query().nearest());
	}

	@Test
	public void logoutEmptiesTheSnapshotEvenWithinTheSameTick()
	{
		f.npc(1, "Man", at(1, 0, 0));
		assertEquals(1, f.services.getRs2NpcCache().getStream().count());

		f.gameState(GameState.LOGIN_SCREEN);
		assertFalse(f.services.isLoggedIn());
		assertEquals(0, f.services.getRs2NpcCache().getStream().count());

		f.gameState(GameState.LOGGED_IN);
		assertEquals(1, f.services.getRs2NpcCache().getStream().count());
	}

	@Test
	public void sceneChangeInvalidatesWithoutATickAdvance()
	{
		f.npc(1, "Man", at(1, 0, 0));
		assertEquals(List.of("Man"), names(f.services.getRs2NpcCache().getStream().collect(Collectors.toList())));

		// Same tick: snapshot is reused, the live list is not re-read.
		f.npcs.clear();
		f.npc(2, "Goblin", at(2, 0, 0));
		assertEquals(List.of("Man"), names(f.services.getRs2NpcCache().getStream().collect(Collectors.toList())));

		// Region load (LOADING) bumps the generation even if the tick counter did not move.
		f.gameState(GameState.LOADING);
		f.gameState = GameState.LOGGED_IN;
		assertEquals(List.of("Goblin"), names(f.services.getRs2NpcCache().getStream().collect(Collectors.toList())));
	}

	@Test
	public void staleEntitiesKeepCaptureTickValuesUntilRequeried()
	{
		NPC npc = f.npc(1, "Man", at(1, 0, 0));
		Rs2NpcModel captured = f.services.getRs2NpcCache().query().first();

		doAnswer(i -> f.onClientThread(at(9, 0, 0))).when(npc).getWorldLocation();
		f.tick++;

		assertEquals(at(1, 0, 0), captured.getWorldLocation());
		assertEquals(at(9, 0, 0), f.services.getRs2NpcCache().query().first().getWorldLocation());
		assertSame(npc, captured.getNpc());
	}

	@Test
	public void backgroundThreadsReadSnapshotsCapturedOnTheClientThread() throws Exception
	{
		for (int i = 0; i < 20; i++)
		{
			f.npc(i, "Npc" + i, at(i % 10, 0, 0));
		}

		ExecutorService scripts = Executors.newFixedThreadPool(4);
		try
		{
			Callable<Integer> read = () -> f.services.getRs2NpcCache().query().within(15).toList().size();
			List<Future<Integer>> results = scripts.invokeAll(List.of(read, read, read, read, read, read, read, read));
			for (Future<Integer> result : results)
			{
				assertEquals(20, (int) result.get());
			}
		}
		finally
		{
			scripts.shutdownNow();
		}

		// The mocks throw if read off the client thread; this read also runs on it directly.
		assertEquals(20, (long) f.clientExecutor.submit(() -> f.services.getRs2NpcCache().getStream().count()).get());
	}

	@Test
	public void stalledClientThreadReturnsEmptyInsteadOfBlockingForever()
	{
		f.npc(1, "Man", at(1, 0, 0));
		f.clientThreadStalled = true;
		long started = System.nanoTime();
		assertEquals(0, f.services.getRs2NpcCache().getStream().count());
		assertTrue(System.nanoTime() - started < 5_000_000_000L);
	}

	@Test
	public void subWorldViewNpcsAreIncludedAfterWorldViewLoaded()
	{
		WorldView boat = mock(WorldView.class);
		when(boat.getId()).thenReturn(7);
		NPC deckhand = mock(NPC.class);
		when(deckhand.getName()).thenReturn("Deckhand");
		when(deckhand.getWorldView()).thenReturn(boat);
		when(deckhand.getWorldLocation()).thenReturn(at(2, 0, 0));
		when(boat.isTopLevel()).thenReturn(true);
		@SuppressWarnings("unchecked")
		net.runelite.api.IndexedObjectSet<NPC> boatNpcs = mock(net.runelite.api.IndexedObjectSet.class);
		when(boatNpcs.iterator()).thenAnswer(i -> List.of(deckhand).iterator());
		doAnswer(i -> boatNpcs).when(boat).npcs();
		when(f.client.getWorldView(7)).thenReturn(boat);

		assertEquals(0, f.services.getRs2NpcCache().getStream().count());
		f.services.onWorldViewLoaded(new WorldViewLoaded(boat));
		assertEquals("Deckhand", f.services.getRs2NpcCache().query().first().getName());
	}
}
