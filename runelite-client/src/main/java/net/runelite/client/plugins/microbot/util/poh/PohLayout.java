package net.runelite.client.plugins.microbot.util.poh;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.runelite.client.plugins.microbot.shortestpath.PohDevice;
import net.runelite.client.plugins.microbot.util.poh.data.HouseLocation;
import net.runelite.client.plugins.microbot.util.poh.data.JewelleryBoxType;
import net.runelite.client.plugins.microbot.util.poh.data.NexusPortal;
import net.runelite.client.plugins.microbot.util.poh.data.PohPortal;

/**
 * What the player's house holds, as last detected inside it ({@link PohDetector}): its location,
 * the portal-room portals, the nexus destinations, the jewellery box tier, and every device's
 * object id and name (for the walker's POH executor). Immutable; stored per RS profile by
 * {@link PohLayoutStore}. Written for RuneMobile (upstream keeps this in its side panel).
 */
public final class PohLayout
{
	/** One detected house object: its id and name. */
	public static final class Device
	{
		private final int objectId;
		private final String name;

		public Device(int objectId, String name)
		{
			this.objectId = objectId;
			this.name = name == null ? "" : name;
		}

		public int getObjectId()
		{
			return objectId;
		}

		public String getName()
		{
			return name;
		}

		@Override
		public boolean equals(Object o)
		{
			return o instanceof Device && ((Device) o).objectId == objectId && ((Device) o).name.equals(name);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(objectId, name);
		}
	}

	/** A house with nothing detected. */
	public static final PohLayout EMPTY = new PohLayout(null, Map.of(), Set.of(), JewelleryBoxType.NONE, Map.of());

	private final HouseLocation location;
	private final Map<PohPortal, Device> portals;
	private final Set<NexusPortal> nexus;
	private final JewelleryBoxType jewelleryBox;
	private final Map<PohDevice, Device> devices;

	public PohLayout(HouseLocation location, Map<PohPortal, Device> portals, Set<NexusPortal> nexus,
		JewelleryBoxType jewelleryBox, Map<PohDevice, Device> devices)
	{
		this.location = location;
		this.portals = portals.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(portals));
		this.nexus = nexus.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(nexus));
		this.jewelleryBox = jewelleryBox == null ? JewelleryBoxType.NONE : jewelleryBox;
		this.devices = devices.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(devices));
	}

	/** The house's location, from the house location varbit; {@code null} when unknown or none. */
	public HouseLocation getLocation()
	{
		return location;
	}

	/** The portal-room portals, each with its object. */
	public Map<PohPortal, Device> getPortals()
	{
		return portals;
	}

	/** The nexus's destinations (empty without a nexus). */
	public Set<NexusPortal> getNexus()
	{
		return nexus;
	}

	public JewelleryBoxType getJewelleryBox()
	{
		return jewelleryBox;
	}

	/** Every non-portal device present (nexus, box, mounted items, fairy ring, spirit tree) with its object. */
	public Map<PohDevice, Device> getDevices()
	{
		return devices;
	}

	/** Whether anything was ever detected. */
	public boolean isKnown()
	{
		return location != null;
	}

	// ---- storage form: "v1|loc=NAME|portal=NAME:id:name,...|nexus=NAME,...|box=TYPE|device=DEVICE:id:name,..." ----

	/** This layout as one config string. */
	public String serialize()
	{
		StringBuilder out = new StringBuilder("v1|loc=").append(location == null ? "" : location.name());
		out.append("|portal=");
		portals.forEach((portal, device) -> out.append(portal.name()).append(':').append(encode(device)).append(','));
		out.append("|nexus=");
		nexus.forEach(destination -> out.append(destination.name()).append(','));
		out.append("|box=").append(jewelleryBox.name());
		out.append("|device=");
		devices.forEach((kind, device) -> out.append(kind.name()).append(':').append(encode(device)).append(','));
		return out.toString();
	}

	/** Parses {@link #serialize()}'s form; {@link #EMPTY} for {@code null}, another version or broken text. */
	public static PohLayout parse(String text)
	{
		if (text == null || !text.startsWith("v1|"))
		{
			return EMPTY;
		}
		try
		{
			Map<String, String> fields = new java.util.HashMap<>();
			for (String part : text.substring(3).split("\\|", -1))
			{
				int eq = part.indexOf('=');
				if (eq > 0)
				{
					fields.put(part.substring(0, eq), part.substring(eq + 1));
				}
			}
			String loc = fields.getOrDefault("loc", "");
			HouseLocation location = loc.isEmpty() ? null : HouseLocation.valueOf(loc);
			Map<PohPortal, Device> portals = new EnumMap<>(PohPortal.class);
			for (String entry : items(fields.get("portal")))
			{
				String[] p = entry.split(":", 3);
				portals.put(PohPortal.valueOf(p[0]), new Device(Integer.parseInt(p[1]), p[2]));
			}
			Set<NexusPortal> nexus = EnumSet.noneOf(NexusPortal.class);
			for (String entry : items(fields.get("nexus")))
			{
				nexus.add(NexusPortal.valueOf(entry));
			}
			JewelleryBoxType box = JewelleryBoxType.valueOf(fields.getOrDefault("box", "NONE"));
			Map<PohDevice, Device> devices = new EnumMap<>(PohDevice.class);
			for (String entry : items(fields.get("device")))
			{
				String[] d = entry.split(":", 3);
				devices.put(PohDevice.valueOf(d[0]), new Device(Integer.parseInt(d[1]), d[2]));
			}
			return new PohLayout(location, portals, nexus, box, devices);
		}
		catch (RuntimeException e)
		{
			return EMPTY;
		}
	}

	private static String encode(Device device)
	{
		return device.getObjectId() + ":" + device.getName().replaceAll("[|,:]", " ");
	}

	private static String[] items(String list)
	{
		return list == null || list.isEmpty() ? new String[0] : list.split(",");
	}
}
