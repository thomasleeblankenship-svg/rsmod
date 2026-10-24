/*
 * Copied from chsami/Microbot (2.6.38),
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/death/DeathsOfficeLocation.java.
 * BSD 2-Clause License; see LICENSE. Mobile adaptation: the Ferox Enclave entrance is in the
 * Wilderness, so {@link #getNearest(WorldPoint)} never picks it.
 */
package net.runelite.client.plugins.microbot.util.death;

import java.util.Arrays;
import java.util.Comparator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Death's Office entrances, one beside each major respawn point; each leads to the same office.
 * Entry is the {@code Death's Domain} object (id 38426), found by id once nearby, so a few tiles of
 * drift in these points costs nothing.
 */
@Getter
@RequiredArgsConstructor
public enum DeathsOfficeLocation
{
	/** Verified in game upstream: the {@code Death's Domain} object sits here. */
	LUMBRIDGE(new WorldPoint(3238, 3192, 0), "Graveyard by the church"),
	FALADOR(new WorldPoint(2964, 3331, 0), "White Knights' Castle Crypt"),
	EDGEVILLE(new WorldPoint(3096, 3475, 0), "Edgeville Mausoleum"),
	SEERS_VILLAGE(new WorldPoint(2715, 3466, 0), "Graveyard by the church"),
	FEROX_ENCLAVE(new WorldPoint(3127, 3630, 0), "Ferox Enclave"),
	KOUREND_CASTLE(new WorldPoint(1622, 3663, 0), "Kourend Castle"),
	PRIFDDINAS(new WorldPoint(3256, 6118, 0), "Hefin district, north of the bank"),
	CIVITAS_ILLA_FORTIS(new WorldPoint(1654, 3135, 0), "West of the Sunrise Palace");

	private final WorldPoint entrance;

	/** What the entrance sits next to. */
	private final String landmark;

	/** Whether this entrance is reached only through the Wilderness. */
	public boolean isInWilderness()
	{
		return this == FEROX_ENCLAVE;
	}

	/** As upstream: the entrance nearest the player, or null when the position is unknown. */
	public static DeathsOfficeLocation getNearest()
	{
		return getNearest(Rs2Player.getWorldLocation());
	}

	/** As upstream (by 2D distance, every entrance being on plane 0), never one in the Wilderness. */
	public static DeathsOfficeLocation getNearest(WorldPoint from)
	{
		if (from == null)
		{
			return null;
		}
		return Arrays.stream(values()).filter(location -> !location.isInWilderness())
			.min(Comparator.comparingInt(location -> location.entrance.distanceTo2D(from)))
			.orElse(null);
	}
}
