package net.runelite.client.plugins.microbot.util.death;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * Death recovery reads (upstream Microbot 2.6.38 {@code Rs2Death}, read half): which recovery
 * interface is open and the items it offers, read on the client thread. The grave, Death's Office
 * and reclaim actions, and the death-state tracking, are not ported here.
 */
public final class Rs2Death
{
	private Rs2Death()
	{
	}

	/** As upstream: a gravestone's contents interface is open. */
	public static boolean isGraveOpen()
	{
		return Rs2Widget.isWidgetVisible(InterfaceID.GravestoneGeneric.CONTENT);
	}

	/** As upstream: the grave's free items (empty when it is not open). */
	public static List<Rs2ItemModel> getGraveFreeItems()
	{
		return readItemContainer(InterfaceID.GravestoneGeneric.FREEITEMS);
	}

	/** As upstream: the grave's items that cost a fee (empty when it is not open). */
	public static List<Rs2ItemModel> getGravePaidItems()
	{
		return readItemContainer(InterfaceID.GravestoneGeneric.PAYITEMS);
	}

	/** As upstream: Death's Office (or the gravestone retrieval) item interface is open. */
	public static boolean isDeathsOfficeOpen()
	{
		return Rs2Widget.isWidgetVisible(InterfaceID.DeathOffice.ITEMS_CONTAINER)
			|| Rs2Widget.isWidgetVisible(InterfaceID.GravestoneRetrieval.ITEMS_CONTAINER);
	}

	/** As upstream: the items Death holds, from whichever retrieval interface is open. */
	public static List<Rs2ItemModel> getDeathsOfficeItems()
	{
		return readItemContainer(Rs2Widget.isWidgetVisible(InterfaceID.DeathOffice.ITEMS_CONTAINER)
			? InterfaceID.DeathOffice.ITEMS : InterfaceID.GravestoneRetrieval.ITEMS);
	}

	/** The items shown in container {@code componentId}'s dynamic children (slot = child index). */
	private static List<Rs2ItemModel> readItemContainer(int componentId)
	{
		return MobileActions.read(() ->
		{
			Widget container = Microbot.getClient().getWidget(componentId);
			Widget[] children = container == null ? null : container.getDynamicChildren();
			List<Rs2ItemModel> items = new ArrayList<>();
			for (int slot = 0; children != null && slot < children.length; slot++)
			{
				Widget child = children[slot];
				int itemId = child == null ? -1 : child.getItemId();
				if (itemId > 0)
				{
					ItemComposition definition = Microbot.getClient().getItemDefinition(itemId);
					items.add(new Rs2ItemModel(new Item(itemId, Math.max(1, child.getItemQuantity())), definition, null, slot));
				}
			}
			return items;
		}, Collections.emptyList());
	}
}
