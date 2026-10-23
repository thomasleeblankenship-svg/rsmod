package net.runelite.client.plugins.microbot.util.bank;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.function.Predicate;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2LogBasket;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/** Mobile bank workflow: live reads, bounded navigation, and observed item transfers. No cached-bank guesses. */
public final class Rs2Bank
{
	public static final int BANK_ITEM_CONTAINER = 786444;

	private Rs2Bank() { }

	public static boolean isOpen()
	{
		return MobileActions.read(() ->
		{
			Widget bank = Microbot.getClient().getWidget(12, 1);
			return bank != null && !bank.isHidden();
		}, false);
	}

	public static boolean openBank()
	{
		if (isOpen()) return true;
		if (!MobileActions.active() || Microbot.getRs2NpcCache() == null || Microbot.getRs2TileObjectCache() == null) return false;
		Rs2NpcModel banker = Microbot.getRs2NpcCache().query().where(n -> n.getName() != null
			&& n.getName().toLowerCase(Locale.ROOT).contains("banker")).nearest();
		if (banker != null && Rs2Inventory.waitFor(() -> MobileActions.npc(banker.getId(), "Bank"), 2_000)
			&& Rs2Inventory.waitFor(Rs2Bank::isOpen, 6_000)) return true;
		Rs2TileObjectModel object = Microbot.getRs2TileObjectCache().query().where(o ->
		{
			String name = o.getName();
			return name != null && (name.toLowerCase(Locale.ROOT).contains("bank booth")
				|| name.toLowerCase(Locale.ROOT).contains("bank chest"));
		}).nearest();
		if (object == null) return false;
		String[] actions = MobileActions.read(() ->
		{
			var composition = object.getObjectComposition();
			return composition == null ? new String[0] : composition.getActions();
		}, new String[0]);
		String action = Arrays.stream(actions == null ? new String[0] : actions)
			.filter(a -> "Bank".equalsIgnoreCase(a) || "Use".equalsIgnoreCase(a)).findFirst().orElse(null);
		return action != null && Rs2Inventory.waitFor(() -> MobileActions.object(object.getId(), action), 2_000)
			&& Rs2Inventory.waitFor(Rs2Bank::isOpen, 6_000);
	}

	public static boolean closeBank()
	{
		if (!isOpen()) return true;
		return Rs2Inventory.waitFor(() -> (MobileActions.widget((12 << 16) | 2, 11, "Close")
			|| MobileActions.widget((12 << 16) | 2, 11, "")), 2_000)
			&& Rs2Inventory.waitFor(() -> !isOpen(), 3_000);
	}

	public static BankLocation getNearestBank()
	{
		WorldPoint origin = location();
		if (origin == null) return null;
		return Arrays.stream(BankLocation.values())
			.filter(bank -> bank.hasRequirements() && (bank.getWorldPoint().getPlane() == origin.getPlane()
				|| Rs2Walker.canReachVertical(origin, bank.getWorldPoint())))
			.min(Comparator.comparingInt(bank -> estimatedBankDistance(origin, bank.getWorldPoint()))).orElse(null);
	}

	/** Same-plane distance plus a conservative stair overhead; not a claimed full shortest-path cost. */
	private static int estimatedBankDistance(WorldPoint origin, WorldPoint destination)
	{
		int horizontal = Math.max(Math.abs(origin.getX() - destination.getX()), Math.abs(origin.getY() - destination.getY()));
		return horizontal + 12 * Math.abs(origin.getPlane() - destination.getPlane());
	}

	public static boolean isNearBank(BankLocation bank, int distance)
	{
		WorldPoint origin = location();
		return bank != null && origin != null && origin.distanceTo(bank.getWorldPoint()) <= Math.max(0, distance);
	}

	public static boolean walkToBankAndUseBank(BankLocation bank)
	{
		if (bank == null || !bank.hasRequirements()) return false;
		if (isNearBank(bank, 8)) return openBank();
		return Rs2Walker.walkTo(bank.getWorldPoint(), 6) && isNearBank(bank, 8) && openBank();
	}

	private static WorldPoint location()
	{
		return MobileActions.read(() -> Microbot.getClient().getLocalPlayer() == null ? null
			: Microbot.getClient().getLocalPlayer().getWorldLocation(), null);
	}

	public static boolean depositAll(Predicate<Rs2ItemModel> predicate)
	{
		if (!isOpen()) return false;
		List<Rs2ItemModel> selected = Rs2Inventory.getList(predicate);
		Set<Integer> depositedIds = new HashSet<>();
		for (Rs2ItemModel item : selected)
		{
			// Deposit-All removes every stack with this id, not only this captured slot.
			if (!depositedIds.add(item.getId())) continue;
			int before = inventoryQuantity(item.getId());
			if (before == 0) continue;
			if (!Rs2Inventory.waitFor(() -> Rs2Inventory.items(i -> i.getSlot() == item.getSlot() && i.getId() == item.getId()).findAny().isPresent()
				&& MobileActions.inventory(item.getSlot(), item.getId(), "Deposit-All"), 2_000)
				|| !Rs2Inventory.waitFor(() -> inventoryQuantity(item.getId()) < before, 3_000)) return false;
		}
		return Rs2Inventory.count(predicate) == 0;
	}

	public static boolean withdrawItem(boolean checkInventory, String name)
	{
		if (checkInventory && Rs2Inventory.hasItem(name)) return true;
		return withdraw(name, "Withdraw-1");
	}

	public static boolean withdrawAll(String name)
	{
		return withdraw(name, "Withdraw-All");
	}

	private static boolean withdraw(String name, String action)
	{
		if (!isOpen() || name == null || name.trim().isEmpty() || Rs2Inventory.isFull() || !setWithdrawAsItem()) return false;
		int[] item = MobileActions.read(() ->
		{
			ItemContainer bank = Microbot.getClient().getItemContainer(InventoryID.BANK);
			if (bank == null || bank.getItems() == null) return null;
			Item[] slots = bank.getItems();
			for (int slot = 0; slot < slots.length; slot++)
			{
				Item stack = slots[slot];
				if (stack == null || stack.getId() < 0 || stack.getQuantity() <= 0) continue;
				ItemComposition definition = Microbot.getClient().getItemDefinition(stack.getId());
				if (definition != null && definition.getName() != null
					&& definition.getName().toLowerCase(Locale.ROOT).contains(name.trim().toLowerCase(Locale.ROOT)))
					return new int[]{slot, stack.getId()};
			}
			return null;
		}, null);
		if (item == null) return false;
		int before = inventoryQuantity(item[1]);
		return Rs2Inventory.waitFor(() -> bankSlotMatches(item[0], item[1]) && MobileActions.bank(item[0], item[1], action), 2_000)
			&& Rs2Inventory.waitFor(() -> inventoryQuantity(item[1]) > before, 3_000);
	}

	private static boolean bankSlotMatches(int slot, int id)
	{
		return MobileActions.read(() ->
		{
			ItemContainer bank = Microbot.getClient().getItemContainer(InventoryID.BANK);
			return bank != null && bank.getItems() != null && slot >= 0 && slot < bank.getItems().length
				&& bank.getItems()[slot] != null && bank.getItems()[slot].getId() == id;
		}, false);
	}

	/** Firemaking/fletching need real items, not notes. Observe the mode after toggling. */
	public static boolean setWithdrawAsItem()
	{
		if (!isOpen()) return false;
		if (withdrawMode() == 0) return true;
		return Rs2Inventory.waitFor(() -> MobileActions.widget(InterfaceID.Bankmain.NOTE, -1, ""), 2_000)
			&& Rs2Inventory.waitFor(() -> withdrawMode() == 0, 3_000);
	}

	private static int withdrawMode()
	{
		return MobileActions.read(() -> Microbot.getClient().getVarbitValue(VarbitID.BANK_WITHDRAWNOTES), -1);
	}

	public static int count(int itemId)
	{
		return MobileActions.read(() ->
		{
			ItemContainer bank = Microbot.getClient().getItemContainer(InventoryID.BANK);
			if (bank == null || bank.getItems() == null || !isOpen()) return 0;
			return Arrays.stream(bank.getItems()).filter(i -> i != null && i.getId() == itemId).mapToInt(Item::getQuantity).sum();
		}, 0);
	}

	public static boolean emptyLogBasket() { return Rs2LogBasket.emptyLogBasketAtBank(); }

	private static int inventoryQuantity(int id)
	{
		return Rs2Inventory.items(i -> i.getId() == id).mapToInt(Rs2ItemModel::getQuantity).sum();
	}
}
