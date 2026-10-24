/*
 * Adapted from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/shortestpath/PohPanel.java
 * (getTransportsToPoh, getAvailableTransports, createFairyRingMap, createSpiritTreeMap,
 * createTransportsToPoh). BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.shortestpath;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.poh.PohLayout;
import net.runelite.client.plugins.microbot.util.poh.PohLayoutStore;
import net.runelite.client.plugins.microbot.util.poh.data.HouseLocation;
import net.runelite.client.plugins.microbot.util.poh.data.JewelleryBox;
import net.runelite.client.plugins.microbot.util.poh.data.MountedDigsite;
import net.runelite.client.plugins.microbot.util.poh.data.MountedGlory;
import net.runelite.client.plugins.microbot.util.poh.data.MountedMythical;
import net.runelite.client.plugins.microbot.util.poh.data.MountedXerics;
import net.runelite.client.plugins.microbot.util.poh.data.NexusPortal;

/**
 * The player-owned-house transports {@code PathfinderConfig} merges into the catalogue.
 * <p>
 * Adaptation: upstream builds them from a Swing side panel (the exit-portal tile the user typed
 * and the ticked house features). Mobile builds them from the house as last detected inside it
 * ({@link PohLayoutStore}); without a detected house there are none. The house is one synthetic
 * tile, {@link #HOUSE_TILE}: ways in land there ({@code TransportStep.isIntoHouse()}), ways out
 * ({@link HouseTransport}, type {@link TransportType#POH}) leave from it, and the walker never
 * walks to it. A search that starts inside the house starts from it.
 */
public final class PohPanel
{
	/**
	 * The house as one planner node: inside the collision map's region extents (the search treats
	 * tiles beyond them as already visited) but in an unmapped region, so it has no walking
	 * neighbours and is reached and left only by transport.
	 */
	public static final WorldPoint HOUSE_TILE = new WorldPoint(1920, 5700, 0);

	private PohPanel()
	{
	}

	/** A key for the planner's transport cache: changes whenever the stored house changes. */
	public static int layoutHash()
	{
		return PohLayoutStore.get().serialize().hashCode();
	}

	/**
	 * As upstream: the ways into the house: the construction cape, the house teleport spell and
	 * tablet (usable anywhere, key {@code null}), and the house's outside portal.
	 */
	public static Map<WorldPoint, Set<Transport>> getTransportsToPoh()
	{
		return getTransportsToPoh(PohLayoutStore.get());
	}

	static Map<WorldPoint, Set<Transport>> getTransportsToPoh(PohLayout layout)
	{
		Map<WorldPoint, Set<Transport>> transports = new HashMap<>();
		HouseLocation location = layout.getLocation();
		if (location == null)
		{
			return transports;
		}
		transports.put(null, Set.of(
			new Transport(HOUSE_TILE, "Construction cape: Tele to POH", TransportType.TELEPORTATION_ITEM, true, 19, Set.of(Set.of(9789), Set.of(9790))),
			new Transport(HOUSE_TILE, "Teleport to House", TransportType.TELEPORTATION_SPELL, true, 19, Map.of(Skill.MAGIC, 40)),
			new Transport(HOUSE_TILE, "Teleport to House tablet: Inside", TransportType.TELEPORTATION_ITEM, true, 19, Set.of(Set.of(8013)))));
		WorldPoint outside = location.getPortalLocation();
		transports.put(outside, Set.of(new Transport(outside, HOUSE_TILE, location.name() + " -> PoH",
			TransportType.TELEPORTATION_PORTAL, true, "Home", "Portal", location.getPortalId())));
		return transports;
	}

	public static Map<WorldPoint, Set<Transport>> getAvailableTransports(Map<WorldPoint, Set<Transport>> allTransports)
	{
		return getAvailableTransports(allTransports, true);
	}

	/**
	 * As upstream: the ways out of the house through its detected devices (portals, the nexus when
	 * {@code usePortalNexus}, the jewellery box's tier, mounted glory, Xeric's talisman, digsite
	 * pendant and mythical cape), plus the house fairy ring and spirit tree joined both ways to
	 * every catalogue ring and tree. Empty without a detected house.
	 */
	public static Map<WorldPoint, Set<Transport>> getAvailableTransports(
		Map<WorldPoint, Set<Transport>> allTransports, boolean usePortalNexus)
	{
		return getAvailableTransports(PohLayoutStore.get(), allTransports, usePortalNexus);
	}

	static Map<WorldPoint, Set<Transport>> getAvailableTransports(PohLayout layout,
		Map<WorldPoint, Set<Transport>> allTransports, boolean usePortalNexus)
	{
		Map<WorldPoint, Set<Transport>> transports = new HashMap<>();
		if (!layout.isKnown())
		{
			return transports;
		}
		Set<Transport> out = transports.computeIfAbsent(HOUSE_TILE, k -> new HashSet<>());
		layout.getPortals().forEach((portal, device) -> out.add(new HouseTransport(HOUSE_TILE, portal.getDestination(),
			portal.displayInfo(), PohDevice.PORTAL, "Enter", "Enter", device.getName(), device.getObjectId(), portal.getDuration())));
		PohLayout.Device nexus = layout.getDevices().get(PohDevice.NEXUS);
		if (nexus != null && usePortalNexus)
		{
			for (NexusPortal destination : layout.getNexus())
			{
				out.add(new HouseTransport(HOUSE_TILE, destination.getDestination(), destination.displayInfo(), PohDevice.NEXUS,
					destination.getText(), "Teleport Menu", nexus.getName(), nexus.getObjectId(), destination.getDuration()));
			}
		}
		PohLayout.Device box = layout.getDevices().get(PohDevice.JEWELLERY_BOX);
		if (box != null)
		{
			for (JewelleryBox destination : layout.getJewelleryBox().getAvailableTeleports())
			{
				out.add(new HouseTransport(HOUSE_TILE, destination.getDestination(), destination.displayInfo(), PohDevice.JEWELLERY_BOX,
					destination.getLocation().getDestination(), "Teleport Menu", box.getName(), box.getObjectId(), destination.getDuration()));
			}
		}
		PohLayout.Device glory = layout.getDevices().get(PohDevice.MOUNTED_GLORY);
		if (glory != null)
		{
			for (MountedGlory destination : MountedGlory.values())
			{
				out.add(mounted(PohDevice.MOUNTED_GLORY, glory, destination.getDestination(), destination.displayInfo(),
					destination.getDestinationName(), destination.getDestinationName(), destination.getDuration()));
			}
		}
		PohLayout.Device xerics = layout.getDevices().get(PohDevice.MOUNTED_XERICS);
		if (xerics != null)
		{
			for (MountedXerics destination : MountedXerics.values())
			{
				// As upstream: a talisman set to this destination offers it directly, else its teleport menu.
				out.add(mounted(PohDevice.MOUNTED_XERICS, xerics, destination.getDestination(), destination.displayInfo(),
					destination.getDestinationName(), xerics.getObjectId() == destination.getObjectId()
						? destination.getDestinationName() : "Teleport menu", destination.getDuration()));
			}
		}
		PohLayout.Device digsite = layout.getDevices().get(PohDevice.MOUNTED_DIGSITE);
		if (digsite != null)
		{
			for (MountedDigsite destination : MountedDigsite.values())
			{
				out.add(mounted(PohDevice.MOUNTED_DIGSITE, digsite, destination.getDestination(), destination.displayInfo(),
					destination.getDestinationName(), digsite.getObjectId() == destination.getObjectId()
						? destination.getDestinationName() : "Teleport menu", destination.getDuration()));
			}
		}
		PohLayout.Device mythical = layout.getDevices().get(PohDevice.MOUNTED_MYTHICAL);
		if (mythical != null)
		{
			MountedMythical destination = MountedMythical.MYTHS_GUILD;
			out.add(mounted(PohDevice.MOUNTED_MYTHICAL, mythical, destination.getDestination(), destination.displayInfo(),
				"Teleport", "Teleport", destination.getDuration()));
		}
		PohLayout.Device ring = layout.getDevices().get(PohDevice.FAIRY_RING);
		if (ring != null)
		{
			connect(transports, allTransports, TransportType.FAIRY_RING, PohDevice.FAIRY_RING, ring, "DIQ");
		}
		PohLayout.Device tree = layout.getDevices().get(PohDevice.SPIRIT_TREE);
		if (tree != null)
		{
			connect(transports, allTransports, TransportType.SPIRIT_TREE, PohDevice.SPIRIT_TREE, tree, "C: Your house");
		}
		return transports;
	}

	private static HouseTransport mounted(PohDevice kind, PohLayout.Device device, WorldPoint destination, String displayInfo,
		String option, String action, int duration)
	{
		return new HouseTransport(HOUSE_TILE, destination, displayInfo, kind, option, action, device.getName(),
			device.getObjectId(), duration);
	}

	/**
	 * As upstream's {@code createTransportsToPoh}: from the house's ring or tree to every
	 * destination the catalogue's rings or trees reach (with that destination's code or entry), and
	 * from every catalogue ring or tree to the house ({@code houseEntry}: the house's code or entry).
	 */
	private static void connect(Map<WorldPoint, Set<Transport>> transports, Map<WorldPoint, Set<Transport>> allTransports,
		TransportType type, PohDevice kind, PohLayout.Device device, String houseEntry)
	{
		Map<WorldPoint, String> destinations = new HashMap<>();
		Set<WorldPoint> origins = new HashSet<>();
		for (Map.Entry<WorldPoint, Set<Transport>> entry : allTransports.entrySet())
		{
			for (Transport transport : entry.getValue())
			{
				if (transport.getType() == type && transport.getOrigin() != null && transport.getDestination() != null)
				{
					destinations.putIfAbsent(transport.getDestination(), transport.getDisplayInfo());
					origins.add(transport.getOrigin());
				}
			}
		}
		Set<Transport> out = transports.computeIfAbsent(HOUSE_TILE, k -> new HashSet<>());
		destinations.forEach((destination, entry) -> out.add(new HouseTransport(HOUSE_TILE, destination, entry, kind, entry,
			null, device.getName(), device.getObjectId(), 5)));
		for (WorldPoint origin : origins)
		{
			Transport sample = allTransports.get(origin).stream().filter(t -> t.getType() == type).findFirst().orElse(null);
			if (sample != null)
			{
				transports.computeIfAbsent(origin, k -> new HashSet<>()).add(new Transport(origin, HOUSE_TILE, houseEntry, type,
					true, sample.getAction(), sample.getName(), sample.getObjectId(), 5));
			}
		}
	}
}
