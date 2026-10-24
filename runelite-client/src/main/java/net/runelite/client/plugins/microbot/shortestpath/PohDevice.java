package net.runelite.client.plugins.microbot.shortestpath;

/**
 * The house device a {@link TransportType#POH} step uses to leave the player-owned house, so the
 * walker's POH executor knows which object and interface to drive. Written for RuneMobile
 * (upstream's {@code PohTransport} carries its own {@code execute()} instead).
 */
public enum PohDevice
{
	/** A portal-room portal: one click, its destination option. */
	PORTAL,
	/** The portal nexus: its destination interface, by destination name. */
	NEXUS,
	/** The ornate jewellery box: its destination interface, by destination name. */
	JEWELLERY_BOX,
	/** A mounted amulet of glory: one click, its destination option. */
	MOUNTED_GLORY,
	/** A mounted Xeric's talisman: one click, its destination option. */
	MOUNTED_XERICS,
	/** A mounted digsite pendant: one click, its destination option. */
	MOUNTED_DIGSITE,
	/** A mounted mythical cape: one click, its destination option. */
	MOUNTED_MYTHICAL,
	/** The house fairy ring: the three-letter code. */
	FAIRY_RING,
	/** The house spirit tree: the "N: Name" destination entry, as outside. */
	SPIRIT_TREE
}
