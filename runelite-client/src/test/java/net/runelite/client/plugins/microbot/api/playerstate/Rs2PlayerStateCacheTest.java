/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/test/java/net/runelite/client/plugins/microbot/api/playerstate/Rs2PlayerStateCacheTest.java,
 * with the varp range cases from a2114401bbbc35e9762db9e8aa666d70ffcd24d8 (2.6.38).
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.playerstate;

import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Upstream's cache contract, exercised through the installed {@link MicrobotReadServices} so the
 * event routing is covered too. Client and client thread are mocked; reads run inline.
 */
public class Rs2PlayerStateCacheTest
{
	private static final int VARP_ID = 100;
	private static final int VARBIT_ID = 200;
	private static final int VARP_COUNT = 5996;

	private Client client;
	private ClientThread clientThread;
	private EventBus eventBus;
	private MicrobotReadServices services;
	private Rs2PlayerStateCache cache;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		clientThread = mock(ClientThread.class);
		eventBus = new EventBus();
		when(clientThread.runOnClientThreadOptional(any(), anyLong())).thenAnswer(invocation ->
		{
			Callable<?> callable = invocation.getArgument(0);
			return Optional.ofNullable(callable.call());
		});
		when(client.getVarps()).thenReturn(new int[VARP_COUNT]);
		services = new MicrobotReadServices(client, clientThread, eventBus, 2_000);
		services.startUp();
		cache = services.getRs2PlayerStateCache();
	}

	@After
	public void tearDown()
	{
		services.shutDown();
	}

	@Test
	public void outOfRangeVarpReturnsZeroWithoutReadingClient()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);

		assertEquals(0, cache.getVarpValue(VARP_COUNT));
		assertEquals(0, cache.getVarpValue(8063));
		assertEquals(0, cache.getVarpValue(-1));

		verify(client, never()).getVarpValue(anyInt());
	}

	@Test
	public void outOfRangeVarpIsNotCached()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarps()).thenReturn(new int[10]);
		assertEquals(0, cache.getVarpValue(VARP_ID));

		when(client.getVarps()).thenReturn(new int[VARP_COUNT]);
		when(client.getVarpValue(VARP_ID)).thenReturn(6);

		assertEquals(6, cache.getVarpValue(VARP_ID));
	}

	@Test
	public void isValidVarpIdChecksVarpArrayBounds()
	{
		assertEquals(Optional.of(true), cache.isValidVarpId(0));
		assertEquals(Optional.of(true), cache.isValidVarpId(VARP_COUNT - 1));
		assertEquals(Optional.of(false), cache.isValidVarpId(VARP_COUNT));
		assertEquals(Optional.of(false), cache.isValidVarpId(8063));
		assertEquals(Optional.of(false), cache.isValidVarpId(-1));
		assertEquals(Optional.of(false), cache.isValidVarpId(Integer.MIN_VALUE));
	}

	@Test
	public void isValidVarpIdIsEmptyWhenClientThreadUnavailable()
	{
		doReturn(Optional.empty()).when(clientThread).runOnClientThreadOptional(any(), anyLong());

		assertEquals(Optional.empty(), cache.isValidVarpId(281));
		assertEquals(Optional.of(false), cache.isValidVarpId(-1));
	}

	@Test
	public void isVarpIdInRangeRejectsMissingVarpArray()
	{
		assertFalse(Rs2PlayerStateCache.isVarpIdInRange(null, 0));
	}

	private void setGameState(GameState gameState)
	{
		when(client.getGameState()).thenReturn(gameState);
		GameStateChanged event = new GameStateChanged();
		event.setGameState(gameState);
		eventBus.post(event);
	}

	private void postVarp(int id, int value)
	{
		VarbitChanged event = new VarbitChanged();
		event.setVarpId(id);
		event.setValue(value);
		eventBus.post(event);
	}

	@Test
	public void servicesExposeOneCache()
	{
		assertSame(cache, MicrobotReadServices.require().getRs2PlayerStateCache());
	}

	@Test
	public void varpValueOfZeroIsCachedWhileLoggedIn()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarpValue(VARP_ID)).thenReturn(0);

		assertEquals(0, cache.getVarpValue(VARP_ID));
		assertEquals(0, cache.getVarpValue(VARP_ID));

		verify(client, times(1)).getVarpValue(VARP_ID);
	}

	@Test
	public void varbitValueOfZeroIsCachedWhileLoggedIn()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarbitValue(VARBIT_ID)).thenReturn(0);

		assertEquals(0, cache.getVarbitValue(VARBIT_ID));
		assertEquals(0, cache.getVarbitValue(VARBIT_ID));

		verify(client, times(1)).getVarbitValue(VARBIT_ID);
	}

	@Test
	public void nothingIsCachedBeforeLogin()
	{
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(client.getVarpValue(VARP_ID)).thenReturn(5);
		when(client.getVarbitValue(VARBIT_ID)).thenReturn(5);

		assertEquals(5, cache.getVarpValue(VARP_ID));
		assertEquals(5, cache.getVarbitValue(VARBIT_ID));

		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarpValue(VARP_ID)).thenReturn(0);
		when(client.getVarbitValue(VARBIT_ID)).thenReturn(0);

		assertEquals(0, cache.getVarpValue(VARP_ID));
		assertEquals(0, cache.getVarbitValue(VARBIT_ID));
	}

	@Test
	public void varbitChangedEventUpdatesCachedValues()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarpValue(VARP_ID)).thenReturn(0);
		assertEquals(0, cache.getVarpValue(VARP_ID));

		postVarp(VARP_ID, 3);

		assertEquals(3, cache.getVarpValue(VARP_ID));
		verify(client, times(1)).getVarpValue(VARP_ID);
	}

	@Test
	public void hoppingFlushesVarbitAndVarpCaches()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarpValue(VARP_ID)).thenReturn(4);
		assertEquals(4, cache.getVarpValue(VARP_ID));

		setGameState(GameState.HOPPING);
		setGameState(GameState.LOGGED_IN);
		when(client.getVarpValue(VARP_ID)).thenReturn(0);

		assertEquals(0, cache.getVarpValue(VARP_ID));
	}

	@Test
	public void connectionLossFlushesTheCache()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarbitValue(VARBIT_ID)).thenReturn(6);
		assertEquals(6, cache.getVarbitValue(VARBIT_ID));

		setGameState(GameState.CONNECTION_LOST);
		setGameState(GameState.LOGGED_IN);
		when(client.getVarbitValue(VARBIT_ID)).thenReturn(1);

		assertEquals(1, cache.getVarbitValue(VARBIT_ID));
	}

	/** An event between the hop flush and the next LOGGED_IN must not repopulate the flushed value. */
	@Test
	public void varbitChangedDuringHoppingIsNotCached()
	{
		setGameState(GameState.HOPPING);
		postVarp(VARP_ID, 42);

		setGameState(GameState.LOGGED_IN);
		when(client.getVarpValue(VARP_ID)).thenReturn(0);

		assertEquals(0, cache.getVarpValue(VARP_ID));
		verify(client, times(1)).getVarpValue(VARP_ID);
	}

	@Test
	public void loginScreenFlushesVarbitAndVarpCaches()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarbitValue(VARBIT_ID)).thenReturn(9);
		assertEquals(9, cache.getVarbitValue(VARBIT_ID));

		setGameState(GameState.LOGIN_SCREEN);
		setGameState(GameState.LOGGED_IN);
		when(client.getVarbitValue(VARBIT_ID)).thenReturn(0);

		assertEquals(0, cache.getVarbitValue(VARBIT_ID));
	}

	/** Region loads keep the cache: they are not a hop or relog, and the server does not re-send. */
	@Test
	public void regionLoadKeepsTheCache()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarpValue(VARP_ID)).thenReturn(8);
		assertEquals(8, cache.getVarpValue(VARP_ID));

		setGameState(GameState.LOADING);
		setGameState(GameState.LOGGED_IN);

		assertEquals(8, cache.getVarpValue(VARP_ID));
		verify(client, times(1)).getVarpValue(VARP_ID);
	}

	@Test
	public void shutDownDropsTheCacheAndStopsEvents()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarpValue(VARP_ID)).thenReturn(2);
		assertEquals(2, cache.getVarpValue(VARP_ID));

		services.shutDown();
		postVarp(VARP_ID, 99);
		when(client.getVarpValue(VARP_ID)).thenReturn(5);

		assertEquals(5, cache.getVarpValue(VARP_ID));
		services.startUp();
	}

	@Test
	public void unreachableClientThreadReadsZeroAndCachesNothing()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		doReturn(Optional.empty()).when(clientThread).runOnClientThreadOptional(any(), anyLong());

		assertEquals(0, cache.getVarpValue(VARP_ID));
		verify(client, times(0)).getVarpValue(VARP_ID);
	}
}
