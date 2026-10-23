/*
 * Adapted from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/leaguetransport/
 * Rs2LeaguesTransport.java and LeaguesTransportInjection.java (LeaguesContext, leaguesContext,
 * isLeaguesActive, isTransportAllowed, injectLeaguesTransports). BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.leaguetransport;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.PrimitiveIntHashMap;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;

/**
 * The part of upstream's Leagues transport layer the pathfinder calls.
 * <p>
 * Adaptation: upstream also learns unlocked Leagues regions and blacklisted teleport
 * destinations from chat and teleport attempts, persists them, and injects Leagues area
 * teleports (about 4,000 lines). None of that is ported. The methods below are upstream's
 * logic with that learned data empty: outside Leagues every transport is allowed and nothing is
 * injected, exactly as upstream; on a Leagues world, region locks are not modelled, so routes
 * may cross locked regions and seasonal transports with no destination are excluded.
 */
public final class Rs2LeaguesTransport
{
	/** Upstream's batching snapshot of Leagues state for one transport refresh. */
	public static final class LeaguesContext
	{
		private final boolean active;

		private LeaguesContext(boolean active)
		{
			this.active = active;
		}

		public boolean isActive()
		{
			return active;
		}

		/** Unlocked Leagues regions; always empty on mobile (region learning is not ported). */
		public Set<Object> getUnlockedRegions()
		{
			return Collections.emptySet();
		}
	}

	private static final LeaguesContext INACTIVE_CONTEXT = new LeaguesContext(false);
	private static final LeaguesContext ACTIVE_CONTEXT = new LeaguesContext(true);

	private Rs2LeaguesTransport()
	{
	}

	/** As upstream: a Leagues world sets {@code VarbitID.LEAGUE_TYPE}. */
	public static boolean isLeaguesActive()
	{
		return Microbot.getVarbitValue(VarbitID.LEAGUE_TYPE) > 0;
	}

	public static LeaguesContext leaguesContext()
	{
		return isLeaguesActive() ? ACTIVE_CONTEXT : INACTIVE_CONTEXT;
	}

	/** Upstream {@code LeaguesTransportInjection.isTransportAllowed} with no learned blacklist. */
	public static boolean isTransportAllowed(LeaguesContext ctx, Transport transport)
	{
		if (transport == null)
		{
			return false;
		}
		if (ctx == null || !ctx.isActive())
		{
			return true;
		}
		return transport.getDestination() != null || transport.getType() != TransportType.SEASONAL_TRANSPORT;
	}

	/** Upstream returns without injecting unless regions are unlocked; none are known on mobile. */
	public static void injectLeaguesTransports(
		Predicate<Transport> transportUsable,
		LeaguesContext ctx,
		Set<Transport> usableTeleports,
		Map<WorldPoint, Set<Transport>> transports,
		PrimitiveIntHashMap<Set<Transport>> transportsPacked,
		Map<TransportType, int[]> typeStats)
	{
	}
}
