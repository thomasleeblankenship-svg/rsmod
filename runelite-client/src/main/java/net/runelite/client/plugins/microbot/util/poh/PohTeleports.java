/*
 * Adapted from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/poh/PohTeleports.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.poh;

import net.runelite.api.TileObject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.equipment.JewelleryLocationEnum;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.poh.data.JewelleryBoxType;
import net.runelite.client.plugins.microbot.util.poh.data.NexusPortal;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * Teleports from inside the player's house: the jewellery box and the portal nexus, as upstream.
 * Each opens the device's menu ("Teleport Menu"), then picks the destination with the walker's
 * house-device selection (the nexus by its shortcut key). Mobile adaptation: a nexus destination in
 * the Wilderness is refused, and the Wilderness warning is never confirmed.
 */
public final class PohTeleports
{
	private static final int MENU_TIMEOUT_MILLIS = 3_000;
	private static final int ARRIVAL_TIMEOUT_MILLIS = 6_000;

	private PohTeleports()
	{
	}

	/** As upstream: the player is inside their house. */
	public static boolean isInHouse()
	{
		return PohPresence.isInHouse();
	}

	/** As upstream: {@link #isInHouse()}, for callers that check before using a device. */
	public static boolean checkIsInHouse()
	{
		return isInHouse();
	}

	/** As upstream: the jewellery box menu's root, or null when it is not open. */
	public static Widget getJewelleryBoxInterface()
	{
		return Rs2Widget.isWidgetVisible(InterfaceID.PohJewelleryBox.UNIVERSE) ? Rs2Widget.getWidget(InterfaceID.PohJewelleryBox.UNIVERSE) : null;
	}

	/** As upstream: the portal nexus menu's root, or null when it is not open. */
	public static Widget getPortalNexusInterface()
	{
		return Rs2Widget.isWidgetVisible(InterfaceID.TelenexusTeleport.UNIVERSE) ? Rs2Widget.getWidget(InterfaceID.TelenexusTeleport.UNIVERSE) : null;
	}

	/** As upstream: in the house, opens the jewellery box and teleports to {@code destination}. */
	public static boolean useJewelleryBox(JewelleryLocationEnum destination)
	{
		if (destination == null || destination == JewelleryLocationEnum.FORTIS_COLOSSEUM || !checkIsInHouse())
		{
			return false;
		}
		if (getJewelleryBoxInterface() == null)
		{
			TileObject box = first(JewelleryBoxType.getJewelleryBoxIds());
			if (box == null || !Rs2GameObject.interact(box, "Teleport Menu")
				|| !Global.sleepUntil(() -> getJewelleryBoxInterface() != null, MENU_TIMEOUT_MILLIS))
			{
				return false;
			}
		}
		return interactWithJewelleryBoxWidget(destination);
	}

	/** As upstream: picks {@code destination} in the open jewellery box (refused when it is not unlocked). */
	public static boolean interactWithJewelleryBoxWidget(JewelleryLocationEnum destination)
	{
		if (destination == null || getJewelleryBoxInterface() == null || !Rs2Walker.selectJewelleryBoxDestination(destination.getDestination()))
		{
			return false;
		}
		Rs2Player.waitForAnimation();
		return true;
	}

	/** As upstream: in the house, opens the portal nexus and teleports to {@code destination}. */
	public static boolean usePortalNexus(NexusPortal destination)
	{
		if (destination == null || isWilderness(destination))
		{
			return false;
		}
		if (getPortalNexusInterface() == null)
		{
			TileObject portal = first(NexusPortal.PORTAL_IDS);
			if (portal == null || !Rs2GameObject.interact(portal, "Teleport Menu")
				|| !Global.sleepUntil(() -> getPortalNexusInterface() != null, MENU_TIMEOUT_MILLIS))
			{
				return false;
			}
		}
		return interactWithPortalNexusWidget(destination);
	}

	/**
	 * As upstream: picks {@code destination} in the open portal nexus and waits to land near it.
	 * A Wilderness destination is refused, and its warning is never confirmed.
	 */
	public static boolean interactWithPortalNexusWidget(NexusPortal destination)
	{
		if (destination == null || isWilderness(destination) || getPortalNexusInterface() == null
			|| !Rs2Walker.selectNexusDestination(destination.getText()))
		{
			return false;
		}
		if (Global.sleepUntilTrue(Rs2Widget::isWildernessInterfaceOpen, 100, 1_000))
		{
			return false;
		}
		return Global.sleepUntil(() ->
		{
			net.runelite.api.coords.WorldPoint here = Rs2Player.getWorldLocation();
			return here != null && destination.getLocation() != null && here.distanceTo(destination.getLocation()) < 10;
		}, ARRIVAL_TIMEOUT_MILLIS);
	}

	private static boolean isWilderness(NexusPortal destination)
	{
		return destination.getLocation() != null && Rs2Pvp.getWildernessLevelFrom(destination.getLocation()) > 0;
	}

	private static TileObject first(Integer[] ids)
	{
		for (Integer id : ids)
		{
			TileObject found = id == null ? null : Rs2GameObject.findObjectById(id);
			if (found != null)
			{
				return found;
			}
		}
		return null;
	}
}
