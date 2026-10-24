package net.runelite.client.plugins.microbot.util.poh;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.TileObject;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.shortestpath.PohDevice;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.poh.data.HouseLocation;
import net.runelite.client.plugins.microbot.util.poh.data.JewelleryBoxType;
import net.runelite.client.plugins.microbot.util.poh.data.MountedDigsite;
import net.runelite.client.plugins.microbot.util.poh.data.MountedMythical;
import net.runelite.client.plugins.microbot.util.poh.data.MountedXerics;
import net.runelite.client.plugins.microbot.util.poh.data.NexusPortal;
import net.runelite.client.plugins.microbot.util.poh.data.PohPortal;

/**
 * Detects what the player's house holds while the player is inside it: one scan of the loaded
 * house for upstream's device object ids (portal-room portals, nexus, jewellery box, mounted
 * glory, Xeric's talisman, digsite pendant and mythical cape, fairy ring, spirit tree), plus the
 * house location and nexus destination varbits. Written for RuneMobile (upstream: the side
 * panel's "Detect" button).
 */
public final class PohDetector
{
	private static final Map<Integer, PohPortal> PORTALS = new HashMap<>();
	private static final Map<Integer, PohDevice> DEVICES = new HashMap<>();
	private static final Map<Integer, JewelleryBoxType> BOXES = new HashMap<>();
	private static final Set<Integer> SPIRIT_RINGS = new HashSet<>();

	static
	{
		for (PohPortal portal : PohPortal.values())
		{
			for (int id : portal.getObjectIds())
			{
				PORTALS.put(id, portal);
			}
		}
		for (int id : NexusPortal.PORTAL_IDS)
		{
			DEVICES.put(id, PohDevice.NEXUS);
		}
		for (JewelleryBoxType box : JewelleryBoxType.values())
		{
			if (box != JewelleryBoxType.NONE)
			{
				DEVICES.put(box.getObjectId(), PohDevice.JEWELLERY_BOX);
				BOXES.put(box.getObjectId(), box);
			}
		}
		DEVICES.put(ObjectID.POH_TROPHY_AMULETOFGLORY_4, PohDevice.MOUNTED_GLORY);
		for (int id : MountedXerics.IDS)
		{
			DEVICES.put(id, PohDevice.MOUNTED_XERICS);
		}
		for (int id : MountedDigsite.IDS)
		{
			DEVICES.put(id, PohDevice.MOUNTED_DIGSITE);
		}
		DEVICES.put(MountedMythical.MYTHS_GUILD.getObjectId(), PohDevice.MOUNTED_MYTHICAL);
		// As upstream's PohTeleports: a spirit ring is both a fairy ring and a spirit tree.
		SPIRIT_RINGS.addAll(Rs2GameObject.getObjectIdsByName("poh_spirit_ring"));
		for (int id : Rs2GameObject.getObjectIdsByName("poh_fairy_ring"))
		{
			DEVICES.put(id, PohDevice.FAIRY_RING);
		}
		for (int id : Rs2GameObject.getObjectIdsByName("poh_spirit_tree"))
		{
			DEVICES.put(id, PohDevice.SPIRIT_TREE);
		}
	}

	private PohDetector()
	{
	}

	/** The house as it is now, or {@code null} when the player is not inside it. */
	public static PohLayout detect()
	{
		if (!PohPresence.isInHouse())
		{
			return null;
		}
		List<TileObject> found = Rs2GameObject.getAll(o -> PORTALS.containsKey(o.getId())
			|| DEVICES.containsKey(o.getId()) || SPIRIT_RINGS.contains(o.getId()));
		Map<PohPortal, PohLayout.Device> portals = new EnumMap<>(PohPortal.class);
		Map<PohDevice, PohLayout.Device> devices = new EnumMap<>(PohDevice.class);
		JewelleryBoxType box = JewelleryBoxType.NONE;
		for (TileObject object : found)
		{
			int id = MobileActions.read(object::getId, -1);
			PohLayout.Device device = new PohLayout.Device(id, Rs2GameObject.getCompositionName(object).orElse(""));
			if (PORTALS.containsKey(id))
			{
				portals.putIfAbsent(PORTALS.get(id), device);
			}
			if (SPIRIT_RINGS.contains(id))
			{
				devices.putIfAbsent(PohDevice.FAIRY_RING, device);
				devices.putIfAbsent(PohDevice.SPIRIT_TREE, device);
			}
			PohDevice kind = DEVICES.get(id);
			if (kind != null)
			{
				devices.putIfAbsent(kind, device);
				box = BOXES.getOrDefault(id, box);
			}
		}
		return new PohLayout(HouseLocation.getHouseLocation(), portals,
			devices.containsKey(PohDevice.NEXUS) ? new HashSet<>(NexusPortal.getAvailableTeleports()) : Set.of(), box, devices);
	}
}
