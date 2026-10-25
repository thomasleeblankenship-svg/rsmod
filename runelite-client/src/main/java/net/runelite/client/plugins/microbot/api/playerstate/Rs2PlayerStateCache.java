/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc, with the varp range
 * guard and isValidVarpId from a2114401bbbc35e9762db9e8aa666d70ffcd24d8 (2.6.38),
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/playerstate/Rs2PlayerStateCache.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.playerstate;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.annotations.Varbit;
import net.runelite.api.annotations.Varp;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;

/**
 * Varbit and varp values, cached while logged in and kept current by {@link VarbitChanged}.
 * Obtain it through {@link MicrobotReadServices#getRs2PlayerStateCache()}; only
 * {@link MicrobotReadServices} constructs it and routes its events.
 * <p>
 * Same caching rules as upstream: a miss reads the client on the client thread and caches the
 * value (zero included) only while {@code LOGGED_IN}; change events are cached only while
 * {@code LOGGED_IN}; login screen, hop and connection loss flush both maps, because the server
 * re-sends only non-zero varps afterwards.
 * <p>
 * Not ported: quest states (upstream maps variable changes to quests through the Quest Helper
 * module, which mobile does not ship) and the local-player position cache. A read that cannot
 * reach the client thread returns 0, as upstream does.
 */
public final class Rs2PlayerStateCache
{
	private final MicrobotReadServices services;
	private final Map<Integer, Integer> varbits = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> varps = new ConcurrentHashMap<>();

	public Rs2PlayerStateCache(MicrobotReadServices services)
	{
		this.services = services;
	}

	/** The varbit's value, from the cache or, on a miss, from the client. */
	public int getVarbitValue(@Varbit int varbitId)
	{
		return read(varbits, varbitId, id -> services.getClient().getVarbitValue(id));
	}

	/**
	 * The varp's value, from the cache or, on a miss, from the client. An id outside the client's
	 * varp array reads 0 and is not cached (2.6.38).
	 */
	public int getVarpValue(@Varp int varpId)
	{
		return read(varps, varpId, id ->
		{
			Client client = services.getClient();
			return isVarpIdInRange(client.getVarps(), id) ? client.getVarpValue(id) : null;
		});
	}

	/**
	 * Whether {@code varpId} indexes the client's varp array: {@code false} for a negative id without
	 * a client read, empty when the client thread cannot be reached (2.6.38).
	 */
	public Optional<Boolean> isValidVarpId(int varpId)
	{
		if (varpId < 0)
		{
			return Optional.of(false);
		}
		return services.read(() -> isVarpIdInRange(services.getClient().getVarps(), varpId));
	}

	static boolean isVarpIdInRange(int[] varpValues, int varpId)
	{
		return varpValues != null && varpId >= 0 && varpId < varpValues.length;
	}

	/** Called by {@link MicrobotReadServices} on every game-state change. */
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			clear();
		}
	}

	/** Called by {@link MicrobotReadServices} on every variable change. */
	public void onVarbitChanged(VarbitChanged event)
	{
		// An event between a hop flush and the next LOGGED_IN would repopulate the value the
		// flush exists to discard.
		if (services.getClient().getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		if (event.getVarbitId() != -1)
		{
			varbits.put(event.getVarbitId(), event.getValue());
		}
		if (event.getVarpId() != -1)
		{
			varps.put(event.getVarpId(), event.getValue());
		}
	}

	/** Drops every cached value. */
	public void clear()
	{
		varbits.clear();
		varps.clear();
	}

	/** {@code clientRead} returns {@code null} for an id the client cannot hold; that reads 0 and is not cached. */
	private int read(Map<Integer, Integer> cache, int id, IntFunction<Integer> clientRead)
	{
		Integer cached = cache.get(id);
		if (cached != null)
		{
			return cached;
		}
		return services.read(() ->
		{
			Integer value = clientRead.apply(id);
			if (value == null)
			{
				return 0;
			}
			// Before the initial varp sync the client returns default or stale values, and a wrong
			// entry for a varp the server never re-sends would stick until the next flush.
			Client client = services.getClient();
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				cache.put(id, value);
			}
			return value;
		}).orElse(0);
	}

	// ---- api batch 1 (Claude 1, user-authorised; Claude 2's file): upstream reads ----

	/** As upstream: the local player's world location (read now; mobile keeps no per-tick copy). */
	public net.runelite.api.coords.WorldPoint getLocalPlayerPosition()
	{
		return net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
	}

	/** As upstream: the local player's world view, read on the client thread. */
	public net.runelite.api.WorldView getLocalPlayerWorldView()
	{
		return services.read(() ->
		{
			net.runelite.api.Player player = services.getClient().getLocalPlayer();
			return player == null ? null : player.getWorldView();
		}).orElse(null);
	}

	/** As upstream: the local player's state in {@code quest}. */
	public net.runelite.api.QuestState getQuestState(net.runelite.api.Quest quest)
	{
		return net.runelite.client.plugins.microbot.util.player.Rs2Player.getQuestState(quest);
	}

	/** As upstream's reset of its per-tick player copy; mobile reads live, so this does nothing. */
	public void invalidateLocalPlayer()
	{
		// The local player is read live; nothing is cached.
	}
}
