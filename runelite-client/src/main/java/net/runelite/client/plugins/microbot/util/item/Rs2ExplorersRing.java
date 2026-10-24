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

	// ---- Actions (Claude 1): util-gap batch F. Claude 2's reads above are unchanged. ----

	/**
	 * As upstream: high alchemy on {@code item} with a carried or worn explorer's ring that has
	 * charges. It opens the ring's alchemy interface ("Functions", option 2) when needed, taps the
	 * item there, and confirms the game's high-value warning when it shows. True when tapped.
	 */
	public static boolean highAlch(net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel item)
	{
		if (item == null || !hasRing() || getCharges() <= 0)
		{
			return false;
		}
		if (!isExplorersRingInventoryVisible())
		{
			net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel ring = Rs2Inventory.items(i ->
				java.util.Arrays.stream(RINGS).anyMatch(id -> id == i.getId())).findFirst().orElse(null);
			if (ring == null || !Rs2Inventory.interact(ring, "Functions"))
			{
				return false;
			}
			net.runelite.client.plugins.microbot.util.Global.sleepUntil(
				net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue::hasSelectAnOption, 2_000);
			net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.keyPressForDialogueOption(2);
			if (!net.runelite.client.plugins.microbot.util.Global.sleepUntil(Rs2ExplorersRing::isExplorersRingInventoryVisible, 2_000))
			{
				return false;
			}
		}
		int[] target = net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget items = Microbot.getClient().getWidget(InterfaceID.LumbridgeAlchemy.ITEMS);
			net.runelite.api.widgets.Widget[] children = items == null ? null : items.getDynamicChildren();
			for (int i = 0; children != null && i < children.length; i++)
			{
				if (children[i] != null && children[i].getItemId() == item.getId())
				{
					return new int[]{children[i].getId(), children[i].getIndex()};
				}
			}
			return null;
		}, null);
		if (target == null || !net.runelite.client.plugins.microbot.util.Global.sleepUntil(
			() -> net.runelite.client.plugins.microbot.mobile.MobileActions.widget(target[0], target[1], ""), 2_000))
		{
			return false;
		}
		if (net.runelite.client.plugins.microbot.util.Global.sleepUntil(
			() -> Rs2Widget.hasWidget("Proceed to cast High Alchemy on it"), 1_200))
		{
			net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.keyPressForDialogueOption(1);
		}
		return true;
	}

	/** As upstream: closes the ring's alchemy interface (true when it is not open). */
	public static boolean closeInterface()
	{
		return Microbot.getVarbitValue(net.runelite.api.gameval.VarbitID.LUMBRIDGE_ALCHEMY_HIGH) == 0
			|| Rs2Widget.clickWidget(31653892);
	}
}
