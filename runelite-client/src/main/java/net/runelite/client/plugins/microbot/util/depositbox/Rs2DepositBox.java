package net.runelite.client.plugins.microbot.util.depositbox;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

/**
 * Deposit box actions with chsami/Microbot's {@code util/depositbox/Rs2DepositBox} names (at
 * {@code a2114401bbbc35e9762db9e8aa666d70ffcd24d8}, BSD 2-Clause), written for mobile: each deposit
 * is the deposit box inventory's "Deposit-All" option on the item's slot, dispatched through
 * {@link MobileActions}, and counts only once the item has left the inventory. Only the subset
 * scripts use so far is provided (open check, deposit-all by id or predicate, close).
 */
public final class Rs2DepositBox
{
	/** The deposit box interface group (upstream {@code DEPOSITBOX_PARENT_WIDGET_ID}). */
	private static final int GROUP = 192;

	private Rs2DepositBox()
	{
	}

	/** As upstream: the deposit box interface is showing. */
	public static boolean isOpen()
	{
		return MobileActions.read(() ->
		{
			Widget root = Microbot.getClient().getWidget(GROUP, 0);
			return root != null && !root.isHidden();
		}, false);
	}

	/** As upstream: deposits every stack with one of these ids; true when at least one was deposited. */
	public static boolean depositAll(Integer... ids)
	{
		List<Integer> wanted = Arrays.asList(ids);
		return depositAll(item -> wanted.contains(item.getId()));
	}

	/** As upstream: deposits every stack whose name contains one of {@code names} (ignoring case). */
	public static boolean depositAll(List<String> names)
	{
		return depositAll(names, false);
	}

	/** As upstream: deposits every stack named in {@code names}, exactly or by containment (ignoring case). */
	public static boolean depositAll(List<String> names, boolean exact)
	{
		if (names == null || names.isEmpty())
		{
			return false;
		}
		return depositAll(item -> item.getName() != null && names.stream().filter(java.util.Objects::nonNull).anyMatch(name -> exact
			? item.getName().equalsIgnoreCase(name)
			: item.getName().toLowerCase(java.util.Locale.ROOT).contains(name.toLowerCase(java.util.Locale.ROOT))));
	}

	/** As upstream: deposits every stack matching {@code predicate}; true when at least one was deposited. */
	public static boolean depositAll(Predicate<Rs2ItemModel> predicate)
	{
		if (!isOpen())
		{
			return false;
		}
		boolean deposited = false;
		List<Rs2ItemModel> items = Rs2Inventory.items(predicate).collect(Collectors.toList());
		for (Rs2ItemModel item : items)
		{
			if (!Rs2Inventory.hasItem(item.getId()))
			{
				continue; // an earlier Deposit-All took every stack of this id
			}
			if (Global.sleepUntil(() -> MobileActions.widget(InterfaceID.BankDepositbox.INVENTORY, item.getSlot(), "Deposit-All"), 2_000)
				&& Global.sleepUntil(() -> !Rs2Inventory.hasItem(item.getId()), 2_500))
			{
				deposited = true;
			}
		}
		return deposited;
	}

	/** As upstream: presses the interface's Close button; true once it has closed. */
	public static boolean closeDepositBox()
	{
		if (!isOpen())
		{
			return false;
		}
		int[] close = MobileActions.read(() -> findClose(Microbot.getClient().getWidget(GROUP, 0)), null);
		return close != null && Global.sleepUntil(() -> MobileActions.widget(close[0], close[1], "Close"), 2_000)
			&& Global.sleepUntil(() -> !isOpen(), 2_500);
	}

	/** {@code {packedId, childIndex}} of the first widget under {@code widget} offering "Close". */
	private static int[] findClose(Widget widget)
	{
		if (widget == null || widget.isHidden())
		{
			return null;
		}
		if (widget.getActions() != null && Arrays.stream(widget.getActions()).anyMatch("Close"::equalsIgnoreCase))
		{
			return new int[]{widget.getId(), widget.getIndex()};
		}
		for (Widget[] children : new Widget[][]{widget.getStaticChildren(), widget.getDynamicChildren(), widget.getNestedChildren()})
		{
			if (children == null)
			{
				continue;
			}
			for (Widget child : children)
			{
				int[] found = findClose(child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
