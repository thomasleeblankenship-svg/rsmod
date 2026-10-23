/*
 * Adapted from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/shortestpath/PohPanel.java
 * (getTransportsToPoh, getAvailableTransports). BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.shortestpath;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * The player-owned-house transport hooks {@code PathfinderConfig} calls.
 * <p>
 * Adaptation: upstream's {@code PohPanel} is a Swing side panel where the user enters the
 * house exit-portal tile and ticks the house's portals, jewellery box, fairy ring and spirit
 * tree; every POH transport is built from that panel instance. Mobile has no such panel, so
 * these methods return what upstream returns while the panel instance is absent: no transports
 * into the house, and the transport map unchanged. POH routing is therefore unavailable.
 */
public final class PohPanel
{
	private PohPanel()
	{
	}

	/** Upstream with no panel instance: no transports into the house. */
	public static Map<WorldPoint, Set<Transport>> getTransportsToPoh()
	{
		return new HashMap<>();
	}

	public static Map<WorldPoint, Set<Transport>> getAvailableTransports(Map<WorldPoint, Set<Transport>> allTransports)
	{
		return getAvailableTransports(allTransports, true);
	}

	/** Upstream with no panel instance: {@code allTransports} unchanged. */
	public static Map<WorldPoint, Set<Transport>> getAvailableTransports(
		Map<WorldPoint, Set<Transport>> allTransports, boolean usePortalNexus)
	{
		return allTransports;
	}
}
