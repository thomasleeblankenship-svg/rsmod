package net.runelite.client.plugins.microbot.util.walker;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.MobilePathfinder;
import net.runelite.client.plugins.microbot.shortestpath.MobileRouteTestAccess;
import net.runelite.client.plugins.microbot.shortestpath.MobileTransportPolicy;
import net.runelite.client.plugins.microbot.shortestpath.PathResult;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Webwalker contract (C2-3): for each transport type the walker executes, the catalogue and a real
 * route over the collision map carry what {@link Rs2Walker}'s executor reads from a
 * {@link PathResult.TransportStep}, checked with the executor's own parsers. A data, loader or
 * merge change that would break an executor silently fails here. No game client.
 */
public class TransportContractTest
{
	private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);

	/** Types the executor handles beyond walking, objects and NPC travel. */
	private static final Set<TransportType> CONTRACT_TYPES = EnumSet.of(TransportType.FAIRY_RING,
		TransportType.SPIRIT_TREE, TransportType.GNOME_GLIDER, TransportType.HOT_AIR_BALLOON, TransportType.MINECART,
		TransportType.MAGIC_CARPET, TransportType.TELEPORTATION_PORTAL, TransportType.TELEPORTATION_LEVER,
		TransportType.TELEPORTATION_ITEM, TransportType.TELEPORTATION_SPELL, TransportType.QUETZAL,
		TransportType.MAGIC_MUSHTREE, TransportType.CANOE);

	private static List<Transport> catalogue(TransportType type)
	{
		return MobileRouteTestAccess.catalogue().values().stream()
			.flatMap(Set::stream)
			.filter(t -> t.getType() == type)
			.collect(Collectors.toList());
	}

	/** What {@code Rs2Walker.pathfinderLeg} needs from a step of this type before it builds a leg. */
	private static String violation(PathResult.TransportStep step)
	{
		TransportType type = step.getType();
		if (!Rs2Walker.EXECUTABLE_TRANSPORTS.contains(type))
		{
			return "not executable";
		}
		switch (type)
		{
			case TELEPORTATION_SPELL:
				return step.getDisplayInfo() == null || step.getDisplayInfo().isBlank() ? "spell without a name" : null;
			case TELEPORTATION_ITEM:
				if (step.getDisplayInfo() == null || step.getDisplayInfo().isBlank())
				{
					return "item without a label";
				}
				return step.getItemIds().isEmpty() ? "item without item ids" : null;
			case FAIRY_RING:
				return Rs2Walker.fairyRingCode(step.getDisplayInfo()) == null ? "fairy ring without a code" : null;
			default:
				break;
		}
		if (step.getAction() == null || step.getAction().isBlank())
		{
			return "no menu action";
		}
		if (step.getOrigin() == null || step.getDestination() == null)
		{
			return "no origin or landing";
		}
		if (step.getObjectId() <= 0 && (step.getName() == null || step.getName().isBlank()))
		{
			return "no object id or name to find";
		}
		if (type == TransportType.GNOME_GLIDER
			&& Rs2Walker.destinationButton(Rs2Walker.GLIDER_BUTTONS, step.getDisplayInfo()) == null)
		{
			return "glider destination without a map button";
		}
		if (type == TransportType.HOT_AIR_BALLOON
			&& Rs2Walker.destinationButton(Rs2Walker.BALLOON_BUTTONS, step.getDisplayInfo()) == null)
		{
			return "balloon destination without a map button";
		}
		if (type == TransportType.SPIRIT_TREE
			&& (step.getDisplayInfo() == null || Rs2Walker.menuDestinationName(step.getDisplayInfo()).isEmpty()))
		{
			return "spirit tree without a menu entry";
		}
		return null;
	}

	@Test
	public void everyCatalogueEntryOfAnExecutedTypeCarriesWhatItsExecutorReads()
	{
		for (TransportType type : CONTRACT_TYPES)
		{
			List<Transport> entries = catalogue(type);
			assertFalse(type + " has catalogue entries", entries.isEmpty());
			List<String> broken = entries.stream()
				.map(t -> new Object[]{t, violation(MobileRouteTestAccess.step(t))})
				.filter(pair -> pair[1] != null)
				.map(pair -> pair[1] + ": " + pair[0])
				.limit(5)
				.collect(Collectors.toList());
			assertTrue(type + ": " + broken, broken.isEmpty());
		}
	}

	/** The catalogue entry of {@code type} that saves the most walking, so a route surely takes it. */
	private static Transport longest(TransportType type)
	{
		return catalogue(type).stream()
			.filter(t -> t.getDestination() != null)
			.filter(t -> !Rs2Walker.entersWilderness(t.getDestination(), LUMBRIDGE))
			.max(Comparator.comparingInt(t -> t.getOrigin() == null
				? LUMBRIDGE.distanceTo2D(t.getDestination()) : t.getOrigin().distanceTo2D(t.getDestination())))
			.orElseThrow();
	}

	@Test
	public void aRouteThroughEachExecutedTypeYieldsAnExecutableStep()
	{
		for (TransportType type : CONTRACT_TYPES)
		{
			Transport transport = longest(type);
			WorldPoint start = transport.getOrigin() == null ? LUMBRIDGE : transport.getOrigin();
			MobileTransportPolicy policy = MobileTransportPolicy.admitting(
				EnumSet.of(TransportType.TRANSPORT, TransportType.AGILITY_SHORTCUT, type));
			PathResult result = MobilePathfinder.search(
				MobileRouteTestAccess.offlineConfig(policy, t -> t.getType() == type
					|| t.getType() == TransportType.TRANSPORT || t.getType() == TransportType.AGILITY_SHORTCUT),
				start, transport.getDestination());

			assertEquals(type + " route " + start + " -> " + transport.getDestination(), PathResult.Status.FOUND, result.getStatus());
			List<PathResult.TransportStep> steps = result.getTransports().values().stream()
				.filter(step -> step.getType() == type)
				.collect(Collectors.toList());
			assertFalse(type + " is used on the route", steps.isEmpty());
			for (PathResult.TransportStep step : steps)
			{
				assertNull(type + " " + step, violation(step));
			}
		}
	}

	/**
	 * Tablets and scrolls carry only their item name ("Varrock tablet"): the walker uses the item's
	 * own option with no destination, so the item name is the whole label.
	 */
	@Test
	public void singleNameTeleportItemsAreExecutable()
	{
		List<Transport> singleName = MobileRouteTestAccess.catalogue().values().stream()
			.flatMap(Set::stream)
			.filter(t -> t.getType() == TransportType.TELEPORTATION_ITEM && t.getDisplayInfo() != null
				&& !t.getDisplayInfo().contains(":"))
			.collect(Collectors.toList());
		assertFalse(singleName.isEmpty());
		assertTrue(singleName.stream().anyMatch(t -> "Varrock tablet".equals(t.getDisplayInfo())));
		for (Transport transport : singleName)
		{
			assertNull(transport.getDisplayInfo(), violation(MobileRouteTestAccess.step(transport)));
		}
	}

	@Test
	public void wildernessLeversAreRefusedUnlessTheGoalIsThere()
	{
		Transport lever = catalogue(TransportType.TELEPORTATION_LEVER).stream()
			.filter(t -> t.getDestination() != null && t.getDestination().getY() > 3520 && t.getDestination().getY() < 4000)
			.findFirst().orElseThrow();
		assertTrue(Rs2Walker.entersWilderness(lever.getDestination(), LUMBRIDGE));
		assertFalse(Rs2Walker.entersWilderness(lever.getDestination(), lever.getDestination()));
		assertTrue(Objects.nonNull(lever.getOrigin()));
	}
}
