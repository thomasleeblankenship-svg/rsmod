package net.runelite.client.plugins.microbot.util.poh;

import java.util.Objects;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.poh.data.PohTeleport;

/**
 * As upstream: a house teleport as a {@link Transport} from the house's exit portal tile to the
 * teleport's destination. {@link #execute()} uses the device (Wilderness destinations refused, see
 * {@link PohTeleport#execute()}). The walker's own POH routing uses its own steps, not this class.
 */
public class PohTransport extends Transport
{
	private final PohTeleport teleport;

	/** As upstream: a transport for {@code teleport}, starting at the house's exit portal. */
	public PohTransport(WorldPoint exitPortalPoint, PohTeleport teleport)
	{
		super(Objects.requireNonNull(exitPortalPoint, "exitPortalPoint is null"),
			Objects.requireNonNull(teleport, "teleport is null").getDestination(),
			teleport.displayInfo(), TransportType.POH, true, teleport.getDuration());
		this.teleport = teleport;
	}

	/** As upstream: the house teleport this transport uses. */
	public PohTeleport getTeleport()
	{
		return teleport;
	}

	/** As upstream: uses the teleport; true once dispatched. */
	public boolean execute()
	{
		return teleport.execute();
	}
}
