package net.runelite.client.plugins.microbot.util.item;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.itemcharges.ItemChargeConfig;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * Explorer's ring reads (upstream Microbot 2.6.38): whether the ring is carried or worn, its alch
 * charges (from the item-charges plugin's config) and whether its alchemy interface is open. The
 * actions ({@code highAlch}, {@code closeInterface}) are added by Claude 1.
 */
public final class Rs2ExplorersRing
{
	private static final int[] RINGS = {
		ItemID.LUMBRIDGE_RING_EASY, ItemID.LUMBRIDGE_RING_MEDIUM, ItemID.LUMBRIDGE_RING_HARD, ItemID.LUMBRIDGE_RING_ELITE,
	};

	private Rs2ExplorersRing()
	{
	}

	/** As upstream: an explorer's ring (1-4) is in the inventory or worn. */
	public static boolean hasRing()
	{
		for (int ring : RINGS)
		{
			if (Rs2Inventory.hasItem(ring) || Rs2Equipment.isWearing(ring))
			{
				return true;
			}
		}
		return false;
	}

	/** As upstream: the ring has at least one alch charge left, as the item-charges plugin last saw. */
	public static boolean hasCharges()
	{
		return getCharges() > 0;
	}

	/**
	 * The ring's alch charges from the item-charges config (RS profile, then the legacy global key);
	 * -1 when unknown. Adaptation: upstream also migrates the legacy key; this only reads.
	 */
	public static int getCharges()
	{
		ConfigManager config = Microbot.getConfigManager();
		if (config == null)
		{
			return -1;
		}
		Integer charges = config.getRSProfileConfiguration(ItemChargeConfig.GROUP, ItemChargeConfig.KEY_EXPLORERS_RING, Integer.class);
		if (charges == null)
		{
			charges = config.getConfiguration(ItemChargeConfig.GROUP, ItemChargeConfig.KEY_EXPLORERS_RING, Integer.class);
		}
		return charges == null ? -1 : charges;
	}

	/** As upstream: the ring's alchemy item interface is showing. */
	public static boolean isExplorersRingInventoryVisible()
	{
		return Rs2Widget.isWidgetVisible(InterfaceID.LumbridgeAlchemy.ITEMS);
	}
}
