package net.runelite.client.plugins.microbot.shortestpath;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.TransportPlanningPolicy;

/**
 * Which catalogue transports may enter the planner graph on mobile.
 * <p>
 * Upstream admits whatever its {@code TransportExecutionRegistry} can execute. On mobile the
 * walker (Claude 1) executes object transports first: stairs, ladders, doors and gates
 * ({@link TransportType#TRANSPORT}) and agility shortcuts. Everything else (boats, spirit trees,
 * fairy rings, teleports, ...) stays out of planned routes until the walker executes it; the
 * walker widens this set with {@link #admitting(Set)}. Requirement checks (skills, quests,
 * items, varbits) still apply on top, in {@code PathfinderConfig}.
 */
public final class MobileTransportPolicy implements TransportPlanningPolicy
{
	/** Types the mobile walker can execute today. */
	public static final Set<TransportType> DEFAULT_TYPES =
		Collections.unmodifiableSet(EnumSet.of(TransportType.TRANSPORT, TransportType.AGILITY_SHORTCUT));

	public static final MobileTransportPolicy DEFAULT = new MobileTransportPolicy(DEFAULT_TYPES);

	private final Set<TransportType> admitted;

	private MobileTransportPolicy(Set<TransportType> admitted)
	{
		this.admitted = admitted.isEmpty()
			? Collections.emptySet()
			: Collections.unmodifiableSet(EnumSet.copyOf(admitted));
	}

	/** A policy admitting exactly {@code types}. */
	public static MobileTransportPolicy admitting(Set<TransportType> types)
	{
		return new MobileTransportPolicy(types);
	}

	public Set<TransportType> getAdmittedTypes()
	{
		return admitted;
	}

	@Override
	public boolean isAdmitted(Transport transport)
	{
		return transport != null && admitted.contains(transport.getType());
	}
}
