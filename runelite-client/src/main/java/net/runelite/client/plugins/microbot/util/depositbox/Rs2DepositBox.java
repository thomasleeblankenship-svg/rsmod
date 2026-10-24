package net.runelite.client.plugins.microbot.util.depositbox;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
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
 * {@link MobileActions}, and counts only once the item has left the inventory. Also the single and
 * X deposits, the deposit-inventory and deposit-worn buttons, and walking to the nearest usable box
 * (never one in the Wilderness).
 */
public final class Rs2DepositBox
{
	/** The deposit box interface group (upstream {@code DEPOSITBOX_PARENT_WIDGET_ID}). */
	private static final int GROUP = 192;

	private Rs2DepositBox()
	{
	}

	/** As upstream: the deposit box interface's bounds while it is open, else {@code null}. */
	public static java.awt.Rectangle getDepositBoxBounds()
	{
		return MobileActions.read(() ->
		{
			Widget root = Microbot.getClient().getWidget(GROUP, 0);
			return root == null || root.isHidden() ? null : root.getBounds();
		}, null);
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

	/** As upstream: "Deposit" on the nearest deposit box, then waits (up to 2.5 s) for it to open. */
	public static boolean openDepositBox()
	{
		Microbot.status = "Opening deposit box";
		if (isOpen())
		{
			return true;
		}
		if (Microbot.getRs2TileObjectCache() == null)
		{
			return false;
		}
		net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel box = Microbot.getRs2TileObjectCache().query()
			.where(o -> o.getName() != null && o.getName().toLowerCase(java.util.Locale.ROOT).contains("deposit box")).nearest();
		if (box == null || !Global.sleepUntil(() -> MobileActions.objectAt(box.getId(), box.getWorldLocation(), "Deposit"), 2_000))
		{
			return false;
		}
		return Global.sleepUntil(Rs2DepositBox::isOpen, 2_500);
	}

	/** As upstream: deposits every stack whose name is not one of {@code names} (ignoring case). */
	public static boolean depositAllExcept(String... names)
	{
		return depositAllExcept(Arrays.asList(names), true);
	}

	/** As upstream: {@link #depositAllExcept(List, boolean)} by containment. */
	public static boolean depositAllExcept(List<String> names)
	{
		return depositAllExcept(names, false);
	}

	/** As upstream: deposits every stack not named in {@code names}, exactly or by containment (ignoring case). */
	public static boolean depositAllExcept(List<String> names, boolean exact)
	{
		if (names == null)
		{
			return false;
		}
		return depositAll(item -> item.getName() == null || names.stream().filter(java.util.Objects::nonNull).noneMatch(name -> exact
			? item.getName().equalsIgnoreCase(name)
			: item.getName().toLowerCase(java.util.Locale.ROOT).contains(name.toLowerCase(java.util.Locale.ROOT))));
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

	// ---- Util action batch R (Claude 1): single deposits, buttons, walking ----

	/** The deposit box's wait for the inventory to change after a deposit. */
	private static final int DEPOSIT_WAIT_MILLIS = 2_500;

	/** As upstream: the "Deposit inventory" button; true once the inventory changed (or was already empty). */
	public static boolean depositAll()
	{
		if (Rs2Inventory.isEmpty()) return true;
		return isOpen() && Rs2Inventory.waitForInventoryChanges(
			() -> MobileActions.widget(InterfaceID.BankDepositbox.DEPOSIT_INV, -1, ""), 0, 5_000);
	}

	/** As upstream: deposits every stack whose name is one of {@code names} (ignoring case). */
	public static boolean depositAll(String... names)
	{
		java.util.Set<String> wanted = Arrays.stream(names).filter(java.util.Objects::nonNull)
			.map(n -> n.toLowerCase(java.util.Locale.ROOT)).collect(Collectors.toSet());
		return depositAll(item -> item.getName() != null && wanted.contains(item.getName().toLowerCase(java.util.Locale.ROOT)));
	}

	/** As upstream: the "Deposit worn items" button. */
	public static boolean depositEquipment()
	{
		return isOpen() && Global.sleepUntil(() -> MobileActions.widget(InterfaceID.BankDepositbox.DEPOSIT_WORN, -1, ""), 2_000);
	}

	/** As upstream: "Deposit-1" on the first item whose name contains {@code itemName}. */
	public static boolean depositOne(String itemName)
	{
		return depositOne(itemName, false);
	}

	/** As upstream: "Deposit-1" on the first item with this ID. */
	public static boolean depositOne(int itemId)
	{
		return depositOne(Rs2Inventory.get(itemId));
	}

	/** As upstream: "Deposit-1" on the first item named {@code itemName}, exactly or containing it. */
	public static boolean depositOne(String itemName, boolean exact)
	{
		return depositOne(named(itemName, exact));
	}

	/** As upstream: "Deposit-1" on this inventory item; true once its stack shrank. */
	public static boolean depositOne(Rs2ItemModel rs2Item)
	{
		return deposit(rs2Item, "Deposit-1", 0);
	}

	/** As upstream: "Deposit-X" with {@code amount} on the first item with this ID. */
	public static boolean depositX(int itemId, int amount)
	{
		return depositX(Rs2Inventory.get(itemId), amount);
	}

	/** As upstream: "Deposit-X" on the first item whose name contains {@code itemName}. */
	public static boolean depositX(String itemName, int amount)
	{
		return depositX(itemName, amount, false);
	}

	/** As upstream: "Deposit-X" on the first item named {@code itemName}, exactly or containing it. */
	public static boolean depositX(String itemName, int amount, boolean exact)
	{
		return depositX(named(itemName, exact), amount);
	}

	/** As upstream: "Deposit-X", then types {@code amount} into the prompt; true once the stack shrank. */
	public static boolean depositX(Rs2ItemModel rs2Item, int amount)
	{
		return amount > 0 && deposit(rs2Item, "Deposit-X", amount);
	}

	private static Rs2ItemModel named(String name, boolean exact)
	{
		if (name == null) return null;
		String wanted = name.toLowerCase(java.util.Locale.ROOT);
		return Rs2Inventory.items(i -> i.getName() != null && (exact ? i.getName().equalsIgnoreCase(name)
			: i.getName().toLowerCase(java.util.Locale.ROOT).contains(wanted))).findFirst().orElse(null);
	}

	/** One deposit option on the item's slot; for "Deposit-X", {@code amount} is typed into the prompt. */
	private static boolean deposit(Rs2ItemModel item, String option, int amount)
	{
		if (item == null || !isOpen() || !Rs2Inventory.hasItem(item.getId())) return false;
		long before = carried(item.getId());
		if (!Global.sleepUntil(() -> MobileActions.widget(InterfaceID.BankDepositbox.INVENTORY, item.getSlot(), option), 2_000))
		{
			return false;
		}
		if (amount > 0 && !(Global.sleepUntil(() -> MobileActions.read(() ->
				Microbot.getClient().getVarcIntValue(net.runelite.api.VarClientInt.INPUT_TYPE) != 0, false), 3_000)
			&& net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard.typeString(Integer.toString(amount))
			&& net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard.enter()))
		{
			return false;
		}
		return Global.sleepUntil(() -> carried(item.getId()) < before, DEPOSIT_WAIT_MILLIS);
	}

	private static long carried(int itemId)
	{
		return Rs2Inventory.items(i -> i.getId() == itemId).mapToLong(Rs2ItemModel::getQuantity).sum();
	}

	/**
	 * As upstream, without its path search: the usable deposit box nearest {@code worldPoint}
	 * (straight-line, with a cost per plane), or null. A box in the Wilderness is never returned.
	 */
	public static DepositBoxLocation getNearestDepositBox(WorldPoint worldPoint, int maxObjectSearchRadius)
	{
		if (worldPoint == null) return null;
		return Arrays.stream(DepositBoxLocation.values())
			.filter(box -> !box.isInWilderness() && box.hasRequirements())
			.min(java.util.Comparator.comparingInt(box -> Math.max(Math.abs(worldPoint.getX() - box.getWorldPoint().getX()),
				Math.abs(worldPoint.getY() - box.getWorldPoint().getY())) + 12 * Math.abs(worldPoint.getPlane() - box.getWorldPoint().getPlane())))
			.orElse(null);
	}

	/** As upstream: {@link #getNearestDepositBox(WorldPoint, int)} from the player. */
	public static DepositBoxLocation getNearestDepositBox()
	{
		return getNearestDepositBox(net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation(), 20);
	}

	/** As upstream: walks (with run on) to within 4 tiles of this deposit box; true when there. */
	public static boolean walkToDepositBox(DepositBoxLocation depositBoxLocation)
	{
		if (isOpen()) return true;
		if (depositBoxLocation == null || depositBoxLocation.isInWilderness()) return false;
		net.runelite.client.plugins.microbot.util.player.Rs2Player.toggleRunEnergy(true);
		Microbot.status = "Walking to nearest deposit box " + depositBoxLocation.name();
		net.runelite.client.plugins.microbot.util.walker.Rs2Walker.walkTo(depositBoxLocation.getWorldPoint(), 4);
		WorldPoint here = net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
		return here != null && depositBoxLocation.getWorldPoint().distanceTo2D(here) <= 4;
	}

	/** As upstream: {@link #walkToDepositBox(DepositBoxLocation)} to the nearest box. */
	public static boolean walkToDepositBox()
	{
		return walkToDepositBox(getNearestDepositBox());
	}

	/** As upstream: walks to the nearest deposit box and opens it once within 8 tiles. */
	public static boolean walkToAndUseDepositBox()
	{
		return walkToAndUseDepositBox(getNearestDepositBox());
	}

	/** As upstream: opens this deposit box when within 8 tiles, else walks towards it (false). */
	public static boolean walkToAndUseDepositBox(DepositBoxLocation depositBoxLocation)
	{
		if (isOpen()) return true;
		if (depositBoxLocation == null || depositBoxLocation.isInWilderness()) return false;
		net.runelite.client.plugins.microbot.util.player.Rs2Player.toggleRunEnergy(true);
		WorldPoint here = net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
		if (here != null && depositBoxLocation.getWorldPoint().distanceTo(here) <= 8)
		{
			return openDepositBox();
		}
		Microbot.status = "Walking to nearest deposit box " + depositBoxLocation.name();
		net.runelite.client.plugins.microbot.util.walker.Rs2Walker.walkTo(depositBoxLocation.getWorldPoint());
		return false;
	}

	/** As upstream: with no more than {@code emptySlotCount} free slots, deposits at the nearest box; else walks back. */
	public static boolean bankItemsAndWalkBackToOriginalPosition(List<String> itemNames, WorldPoint initialPlayerLocation, int emptySlotCount)
	{
		return bankItemsAndWalkBackToOriginalPosition(itemNames, false, initialPlayerLocation, emptySlotCount, 4);
	}

	/** As upstream: banks when the inventory is full, else walks back to {@code initialPlayerLocation}. */
	public static boolean bankItemsAndWalkBackToOriginalPosition(List<String> itemNames, WorldPoint initialPlayerLocation)
	{
		return bankItemsAndWalkBackToOriginalPosition(itemNames, false, initialPlayerLocation, 0, 4);
	}

	/** As upstream, by containment. */
	public static boolean bankItemsAndWalkBackToOriginalPosition(List<String> itemNames, WorldPoint initialPlayerLocation, int emptySlotCount, int distance)
	{
		return bankItemsAndWalkBackToOriginalPosition(itemNames, false, initialPlayerLocation, emptySlotCount, distance);
	}

	/**
	 * As upstream: when no more than {@code emptySlotCount} slots are free, walks to and opens the
	 * nearest deposit box and deposits {@code itemNames} (false meanwhile); otherwise returns to
	 * within {@code distance} (at most 10) of {@code initialPlayerLocation}. True once back with room.
	 */
	public static boolean bankItemsAndWalkBackToOriginalPosition(List<String> itemNames, boolean exactItemNames,
		WorldPoint initialPlayerLocation, int emptySlotCount, int distance)
	{
		if (Rs2Inventory.getEmptySlots() <= emptySlotCount)
		{
			if (walkToAndUseDepositBox())
			{
				depositAll(itemNames, exactItemNames);
			}
			return false;
		}
		int range = Math.min(distance, 10);
		WorldPoint here = net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
		if (here == null || initialPlayerLocation == null) return false;
		if (initialPlayerLocation.distanceTo(here) > range
			|| !net.runelite.client.plugins.microbot.util.tile.Rs2Tile.isTileReachable(initialPlayerLocation))
		{
			net.runelite.client.plugins.microbot.util.walker.Rs2Walker.walkTo(initialPlayerLocation, range);
		}
		else
		{
			net.runelite.client.plugins.microbot.util.walker.Rs2Walker.walkFastCanvas(initialPlayerLocation);
		}
		WorldPoint after = net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
		return Rs2Inventory.getEmptySlots() > emptySlotCount && after != null && initialPlayerLocation.distanceTo(after) <= range;
	}

	// ---- Read batch 16e (upstream 2.6.38) ----

	/** Upstream's deposit box inventory container (192:24). */
	private static final int INVENTORY_CONTAINER = 24;

	/** As upstream: the deposit box root widget (192:0) while open, else {@code null}. */
	public static Widget getDepositBoxWidget()
	{
		return isOpen() ? net.runelite.client.plugins.microbot.util.widget.Rs2Widget.getWidget(GROUP, 0) : null;
	}

	/** As upstream: the deposit box's item slot widgets while open, else {@code null}. */
	public static List<Widget> getItems()
	{
		if (!isOpen())
		{
			return null;
		}
		return MobileActions.read(() ->
		{
			Widget container = Microbot.getClient().getWidget(GROUP, INVENTORY_CONTAINER);
			Widget[] children = container == null ? null : container.getDynamicChildren();
			return children == null ? null : Arrays.asList(children);
		}, null);
	}

	/** As upstream: the slot widget at {@code slotId}, or {@code null}. */
	public static Widget getItemWidget(int slotId)
	{
		List<Widget> items = getItems();
		return items == null || slotId < 0 || slotId >= items.size() ? null : items.get(slotId);
	}
}
