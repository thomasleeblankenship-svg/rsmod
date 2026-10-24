package net.runelite.client.plugins.microbot.shortestpath;

import java.util.Objects;
import net.runelite.api.coords.WorldPoint;

/**
 * A player-owned-house transport for the mobile planner: either into the house (a house teleport
 * spell, tablet or cape, or the house's outside portal, landing on the synthetic house tile) or
 * out of it through a {@link PohDevice}. {@link PathResult.TransportStep} copies what the walker's
 * POH executor reads. Written for RuneMobile (upstream: {@code PohTransport} with {@code execute()}).
 */
public final class HouseTransport extends Transport
{
	private final boolean intoHouse;
	private final PohDevice device;
	private final String option;

	/**
	 * A way out of the house: from {@code house} (the synthetic house tile) to {@code destination}
	 * through {@code device}. {@code objectId}/{@code objectName} are the device as detected;
	 * {@code option} is what its menu or interface shows; {@code action} the menu option for
	 * one-click devices (portals, mounted items), else the option that opens the interface.
	 */
	public HouseTransport(WorldPoint house, WorldPoint destination, String displayInfo, PohDevice device, String option,
		String action, String objectName, int objectId, int duration)
	{
		super(Objects.requireNonNull(house, "house"), Objects.requireNonNull(destination, "destination"), displayInfo,
			TransportType.POH, true, action, objectName, objectId, duration);
		this.intoHouse = false;
		this.device = Objects.requireNonNull(device, "device");
		this.option = option;
	}

	/**
	 * A way into the house, landing on {@code house}: a {@link TransportType#TELEPORTATION_SPELL} or
	 * {@link TransportType#TELEPORTATION_ITEM} ({@code origin} {@code null}, used anywhere; the
	 * executor reads {@code displayInfo} as for any teleport) or the outside portal
	 * ({@link TransportType#TELEPORTATION_PORTAL} with its action, name and id).
	 */
	public HouseTransport(WorldPoint origin, WorldPoint house, String displayInfo, TransportType type, String action,
		String objectName, int objectId, int duration)
	{
		super(origin, Objects.requireNonNull(house, "house"), displayInfo, Objects.requireNonNull(type, "type"), true,
			action, objectName, objectId, duration);
		this.intoHouse = true;
		this.device = null;
		this.option = null;
	}

	/** Whether this lands in the house: arrival is being in the house, not a tile. */
	public boolean isIntoHouse()
	{
		return intoHouse;
	}

	/** The device a way out uses; {@code null} for a way in. */
	public PohDevice getPohDevice()
	{
		return device;
	}

	/** What the device's menu or interface shows for this destination; {@code null} for a way in. */
	public String getPohOption()
	{
		return option;
	}
}
