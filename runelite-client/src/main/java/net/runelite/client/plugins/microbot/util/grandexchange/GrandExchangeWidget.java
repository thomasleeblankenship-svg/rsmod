package net.runelite.client.plugins.microbot.util.grandexchange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * Grand Exchange widget lookups (upstream Microbot 2.6.38): the slot panels, the offer setup
 * buttons, confirm and the collect buttons. Every lookup, including the child step, runs on the
 * client thread; the returned widgets are handles for null checks and for
 * {@link Rs2Widget#clickWidget(Widget)}, which re-resolves them at dispatch.
 */
@Slf4j
public final class GrandExchangeWidget
{
	/** Child of {@code GE_OFFERS} that holds the offer setup buttons. */
	static final int OFFER_CONTAINER_CHILD = 26;
	/** Child of {@code GE_OFFERS} that holds an offer's collect buttons. */
	static final int COLLECT_CONTAINER_CHILD = 24;
	/** Child of {@code GE_OFFERS} for slot {@link GrandExchangeSlots#ONE}; the others follow. */
	static final int FIRST_SLOT_CHILD = 7;

	private GrandExchangeWidget()
	{
	}

	static Widget getOfferContainer()
	{
		return Rs2Widget.getWidget(InterfaceID.GE_OFFERS, OFFER_CONTAINER_CHILD);
	}

	static boolean isOfferTextVisible()
	{
		return Rs2Widget.isWidgetVisible(InterfaceID.GeOffers.SETUP_DESC);
	}

	static Widget getOfferBuyButton(GrandExchangeSlots slot)
	{
		return getSlotChild(slot, 0);
	}

	static Widget getOfferSellButton(GrandExchangeSlots slot)
	{
		return getSlotChild(slot, 1);
	}

	static Widget getQuantityButton_Minus()
	{
		return getOfferChild(1);
	}

	static Widget getQuantityButton_Plus()
	{
		return getOfferChild(2);
	}

	static Widget getQuantityButton_1()
	{
		return getOfferChild(3);
	}

	static Widget getQuantityButton_10()
	{
		return getOfferChild(4);
	}

	static Widget getQuantityButton_100()
	{
		return getOfferChild(5);
	}

	static Widget getQuantityButton_1000()
	{
		return getOfferChild(6);
	}

	static Widget getQuantityButton_X()
	{
		return getOfferChild(7);
	}

	static Widget getPricePerItemButton_Minus()
	{
		return getOfferChild(8);
	}

	static Widget getPricePerItemButton_Plus()
	{
		return getOfferChild(9);
	}

	static Widget getPricePerItemButton_Minus5Percent()
	{
		return getOfferChild(10);
	}

	static Widget getPricePerItemButton_GuidePrice()
	{
		return getOfferChild(11);
	}

	static Widget getPricePerItemButton_X()
	{
		return getOfferChild(12);
	}

	static Widget getPricePerItemButton_Plus5Percent()
	{
		return getOfferChild(13);
	}

	static Widget getPricePerItemButton_MinusXPercent()
	{
		return getOfferChild(14);
	}

	static Widget getPricePerItemButton_PlusXPercent()
	{
		return getOfferChild(15);
	}

	static Widget getChooseItem()
	{
		return getOfferChild(0);
	}

	/** The offer setup's "Confirm" button, or {@code null}. */
	static Widget getConfirm()
	{
		List<Widget> children = MobileActions.read(() ->
		{
			Widget parent = Microbot.getClient().getWidget(InterfaceID.GE_OFFERS, OFFER_CONTAINER_CHILD);
			Widget[] dynamic = parent == null ? null : parent.getDynamicChildren();
			return dynamic == null ? null : Arrays.asList(dynamic);
		}, null);
		return children == null ? null : Rs2Widget.findWidget("Confirm", children, true);
	}

	static boolean hasOfferPriceChanged(long basePrice)
	{
		return basePrice != Rs2GrandExchange.getOfferPrice();
	}

	/** The overview panel of {@code slot}, or {@code null} (also for a {@code null} slot). */
	static Widget getSlot(GrandExchangeSlots slot)
	{
		return slot == null ? null : Rs2Widget.getWidget(InterfaceID.GE_OFFERS, FIRST_SLOT_CHILD + slot.ordinal());
	}

	/** The collect buttons of the offer being viewed (children with a "collect" action). */
	static Widget[] getCollectButtons()
	{
		Widget[] buttons = MobileActions.read(() ->
		{
			Widget parent = Microbot.getClient().getWidget(InterfaceID.GE_OFFERS, COLLECT_CONTAINER_CHILD);
			if (parent == null || parent.isHidden())
			{
				return null;
			}
			List<Widget> found = new ArrayList<>();
			Widget[] children = parent.getChildren();
			for (Widget child : children == null ? new Widget[0] : children)
			{
				String[] actions = child == null ? null : child.getActions();
				if (actions != null && Arrays.stream(actions).filter(Objects::nonNull)
					.anyMatch(a -> a.toLowerCase().contains("collect")))
				{
					found.add(child);
				}
			}
			return found.toArray(new Widget[0]);
		}, null);
		if (buttons == null)
		{
			log.info("Grand Exchange offers interface is not open, cannot retrieve collect buttons.");
			return new Widget[0];
		}
		return buttons;
	}

	private static Widget getOfferChild(int childIndex)
	{
		return child(InterfaceID.GE_OFFERS, OFFER_CONTAINER_CHILD, childIndex);
	}

	private static Widget getSlotChild(GrandExchangeSlots slot, int childIndex)
	{
		return slot == null ? null : child(InterfaceID.GE_OFFERS, FIRST_SLOT_CHILD + slot.ordinal(), childIndex);
	}

	private static Widget child(int group, int component, int index)
	{
		return MobileActions.read(() ->
		{
			Widget parent = Microbot.getClient().getWidget(group, component);
			return parent == null ? null : parent.getChild(index);
		}, null);
	}
}
