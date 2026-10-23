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

/**
 * Mobile bank workflow: live reads, bounded navigation, and observed item transfers. No cached-bank guesses.
 * <p>
 * The Hub read methods at the end ({@link #bankItems()}, {@code hasItem}, {@code hasAllItems},
 * {@code hasBankItem}, {@code getBankItem}) answer from the bank contents last seen while it was
 * open, as Microbot scripts expect when planning a trip; that memory is cleared on logout. The
 * workflow methods above still read the live bank.
 */
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
		return getNearestBank(location());
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
		if (name == null || name.trim().isEmpty() || !setWithdrawAsItem()) return false;
		return withdrawStack(findBankStack(name, false), action);
	}

	/** First live bank stack whose name matches, as {@code {slot, id}}; exact is case-insensitive equality. */
	private static int[] findBankStack(String name, boolean exact)
	{
		if (name == null || name.trim().isEmpty()) return null;
		String wanted = name.trim().toLowerCase(Locale.ROOT);
		return findBankStack((id, itemName) -> itemName != null
			&& (exact ? itemName.toLowerCase(Locale.ROOT).equals(wanted) : itemName.toLowerCase(Locale.ROOT).contains(wanted)));
	}

	private static int[] findBankStack(int itemId)
	{
		return findBankStack((id, itemName) -> id == itemId);
	}

	private static int[] findBankStack(java.util.function.BiPredicate<Integer, String> matcher)
	{
		return MobileActions.read(() ->
		{
			ItemContainer bank = Microbot.getClient().getItemContainer(InventoryID.BANK);
			if (bank == null || bank.getItems() == null) return null;
			Item[] slots = bank.getItems();
			for (int slot = 0; slot < slots.length; slot++)
			{
				Item stack = slots[slot];
				if (stack == null || stack.getId() < 0 || stack.getQuantity() <= 0) continue;
				ItemComposition definition = Microbot.getClient().getItemDefinition(stack.getId());
				if (matcher.test(stack.getId(), definition == null ? null : definition.getName()))
					return new int[]{slot, stack.getId()};
			}
			return null;
		}, null);
	}

	/** One bank-item action, then waits until the inventory gains items (noted or not). */
	private static boolean withdrawStack(int[] item, String action)
	{
		if (!isOpen() || item == null) return false;
		if (Rs2Inventory.isFull() && !Rs2Inventory.hasItem(item[1])) return false;
		long before = inventoryTotal();
		return Rs2Inventory.waitFor(() -> bankSlotMatches(item[0], item[1]) && MobileActions.bank(item[0], item[1], action), 2_000)
			&& Rs2Inventory.waitFor(() -> inventoryTotal() > before, 3_000);
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

	// ---- Upstream bank actions (Microbot API names, written for mobile) ----
	// Each dispatches through MobileActions (bound script session, one action per game tick) and,
	// unless noted, returns true only once the transfer is observed. Unlike withdrawItem(boolean,
	// String) and withdrawAll(String) above, which force withdraw-as-item for the woodcutting
	// workflow, these follow upstream and keep the bank's current item/note mode.

	public static boolean withdrawOne(int id) { return withdrawStack(findBankStack(id), "Withdraw-1"); }
	public static boolean withdrawOne(String name) { return withdrawOne(name, false); }
	public static boolean withdrawOne(String name, boolean exact) { return withdrawStack(findBankStack(name, exact), "Withdraw-1"); }
	public static boolean withdrawItem(String name) { return withdrawOne(name); }
	public static boolean withdrawItem(int id) { return withdrawOne(id); }

	public static boolean withdrawItem(boolean checkInv, int id)
	{
		return checkInv && Rs2Inventory.hasItem(id) || withdrawOne(id);
	}

	public static boolean withdrawAll(int id) { return withdrawStack(findBankStack(id), "Withdraw-All"); }
	public static boolean withdrawAll(String name, boolean exact) { return withdrawStack(findBankStack(name, exact), "Withdraw-All"); }

	public static boolean withdrawAll(boolean checkInv, String name)
	{
		return withdrawAll(checkInv, name, false);
	}

	public static boolean withdrawAll(boolean checkInv, String name, boolean exact)
	{
		return checkInv && Rs2Inventory.hasItem(name, exact) || withdrawAll(name, exact);
	}

	public static boolean withdrawX(int id, int amount) { return withdrawAmount(findBankStack(id), amount); }
	public static boolean withdrawX(String name, int amount) { return withdrawX(name, amount, false); }
	public static boolean withdrawX(String name, int amount, boolean exact) { return withdrawAmount(findBankStack(name, exact), amount); }

	public static boolean withdrawX(boolean checkInv, int id, int amount)
	{
		return checkInv && Rs2Inventory.hasItemAmount(id, amount) || withdrawX(id, amount);
	}

	public static boolean withdrawX(boolean checkInv, String name, int amount)
	{
		return withdrawX(checkInv, name, amount, false);
	}

	public static boolean withdrawX(boolean checkInv, String name, int amount, boolean exact)
	{
		return checkInv && Rs2Inventory.hasItemAmount(name, amount, false, exact) || withdrawX(name, amount, exact);
	}

	/** As upstream: withdraws only what the inventory lacks; false when the bank has too few. */
	public static boolean withdrawDeficit(int id, int requiredAmount)
	{
		int deficit = requiredAmount - Rs2Inventory.itemQuantity(id);
		return deficit <= 0 || hasBankItem(id, deficit) && withdrawX(id, deficit);
	}

	public static boolean withdrawDeficit(String name, int requiredAmount) { return withdrawDeficit(name, requiredAmount, false); }

	public static boolean withdrawDeficit(String name, int requiredAmount, boolean exact)
	{
		int deficit = requiredAmount - Rs2Inventory.itemQuantity(name, exact);
		return deficit <= 0 || hasBankItem(name, deficit, exact) && withdrawX(name, deficit, exact);
	}

	/**
	 * Withdraw-1/5/10, the bank's remembered Withdraw-{@code amount} option, or Withdraw-X followed
	 * by typing the amount into the prompt (one digit per game tick).
	 */
	private static boolean withdrawAmount(int[] item, int amount)
	{
		if (item == null || amount <= 0) return false;
		if (amount == 1 || amount == 5 || amount == 10 || hasBankAction(item[0], "Withdraw-" + amount))
			return withdrawStack(item, "Withdraw-" + amount);
		if (!isOpen() || Rs2Inventory.isFull() && !Rs2Inventory.hasItem(item[1])) return false;
		long before = inventoryTotal();
		return Rs2Inventory.waitFor(() -> bankSlotMatches(item[0], item[1]) && MobileActions.bank(item[0], item[1], "Withdraw-X"), 2_000)
			&& enterAmount(amount)
			&& Rs2Inventory.waitFor(() -> inventoryTotal() > before, 3_000);
	}

	private static boolean hasBankAction(int slot, String action)
	{
		return MobileActions.read(() ->
		{
			Widget container = Microbot.getClient().getWidget(InterfaceID.Bankmain.ITEMS);
			Widget item = container == null ? null : container.getChild(slot);
			return item != null && item.getActions() != null
				&& Arrays.stream(item.getActions()).anyMatch(action::equalsIgnoreCase);
		}, false);
	}

	/** The bank-side inventory slot offers {@code action} (for example the remembered Deposit-{@code n}). */
	private static boolean hasDepositAction(int slot, String action)
	{
		return MobileActions.read(() ->
		{
			Widget container = Microbot.getClient().getWidget(net.runelite.api.widgets.WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER);
			Widget item = container == null ? null : container.getChild(slot);
			return item != null && item.getActions() != null
				&& Arrays.stream(item.getActions()).anyMatch(action::equalsIgnoreCase);
		}, false);
	}

	/** Waits for the chatbox amount prompt, then types the amount and Enter. */
	private static boolean enterAmount(int amount)
	{
		return Rs2Inventory.waitFor(() -> MobileActions.read(() ->
				Microbot.getClient().getVarcIntValue(net.runelite.api.VarClientInt.INPUT_TYPE) != 0, false), 3_000)
			&& net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard.typeString(Integer.toString(amount))
			&& net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard.enter();
	}

	public static boolean depositOne(int id) { return depositItem(Rs2Inventory.get(id), "Deposit-1"); }
	public static boolean depositOne(String name) { return depositOne(name, false); }

	public static boolean depositOne(String name, boolean exact)
	{
		return depositItem(firstInventory(name, exact), "Deposit-1");
	}

	public static boolean depositX(int id, int amount) { return depositAmount(Rs2Inventory.get(id), amount); }
	public static boolean depositX(String name, int amount) { return depositAmount(firstInventory(name, false), amount); }

	public static boolean depositAll(int id) { return depositAll(i -> i.getId() == id); }
	public static boolean depositAll(String name) { return depositAll(name, false); }

	public static boolean depositAll(String name, boolean exact)
	{
		return depositAll(i -> nameMatches(i.getName(), name, exact));
	}

	/** As upstream: the deposit-inventory button; true at once when the inventory is already empty. */
	public static boolean depositAll()
	{
		if (Rs2Inventory.isEmpty()) return true;
		if (!isOpen()) return false;
		return Rs2Inventory.waitFor(() -> MobileActions.widget(InterfaceID.Bankmain.DEPOSITINV, -1, ""), 2_000)
			&& Rs2Inventory.waitFor(Rs2Inventory::isEmpty, 5_000);
	}

	/** As upstream: the deposit-worn-items button; {@code true} means dispatched. */
	public static boolean depositEquipment()
	{
		return isOpen() && Rs2Inventory.waitFor(() -> MobileActions.widget(InterfaceID.Bankmain.DEPOSITWORN, -1, ""), 2_000);
	}

	public static boolean depositAllExcept(Predicate<Rs2ItemModel> keep) { return depositAll(keep.negate()); }

	public static boolean depositAllExcept(Integer... ids)
	{
		return depositAll(i -> Arrays.stream(ids).noneMatch(id -> id == i.getId()));
	}

	public static boolean depositAllExcept(String... names) { return depositAllExcept(false, names); }

	public static boolean depositAllExcept(java.util.Collection<String> names) { return depositAllExcept(names.toArray(String[]::new)); }

	public static boolean depositAllExcept(boolean exact, String... names)
	{
		return depositAll(i -> Arrays.stream(names).noneMatch(n -> nameMatches(i.getName(), n, exact)));
	}

	private static boolean depositItem(Rs2ItemModel item, String action)
	{
		if (!isOpen() || item == null) return false;
		long before = inventoryTotal();
		return Rs2Inventory.waitFor(() -> MobileActions.inventory(item.getSlot(), item.getId(), action), 2_000)
			&& Rs2Inventory.waitFor(() -> inventoryTotal() < before, 3_000);
	}

	private static boolean depositAmount(Rs2ItemModel item, int amount)
	{
		if (item == null || amount <= 0) return false;
		if (amount == 1 || amount == 5 || amount == 10) return depositItem(item, "Deposit-" + amount);
		if (hasDepositAction(item.getSlot(), "Deposit-" + amount)) return depositItem(item, "Deposit-" + amount);
		if (!isOpen()) return false;
		long before = inventoryTotal();
		return Rs2Inventory.waitFor(() -> MobileActions.inventory(item.getSlot(), item.getId(), "Deposit-X"), 2_000)
			&& enterAmount(amount)
			&& Rs2Inventory.waitFor(() -> inventoryTotal() < before, 3_000);
	}

	/** As upstream: withdraws one and equips it; true at once when already worn. */
	public static boolean withdrawAndEquip(String name)
	{
		if (net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.isWearing(name)) return true;
		return withdrawOne(name) && wearItem(name);
	}

	public static boolean withdrawAndEquip(int id)
	{
		if (net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.isWearing(id)) return true;
		return withdrawOne(id) && wearItem(id);
	}

	/** As upstream: equips an inventory item while the bank is open; {@code true} means dispatched. */
	public static boolean wearItem(String name) { return wearItem(name, false); }

	public static boolean wearItem(String name, boolean exact)
	{
		return isOpen() && Rs2Inventory.equip(firstInventory(name, exact));
	}

	public static boolean wearItem(int id)
	{
		return isOpen() && Rs2Inventory.equip(Rs2Inventory.get(id));
	}

	public static boolean hasWithdrawAsNote() { return withdrawMode() == 1; }
	public static boolean hasWithdrawAsItem() { return withdrawMode() == 0; }

	/** As upstream: the note toggle; observed after clicking. */
	public static boolean setWithdrawAsNote()
	{
		if (!isOpen()) return false;
		if (hasWithdrawAsNote()) return true;
		return Rs2Inventory.waitFor(() -> MobileActions.widget(InterfaceID.Bankmain.NOTE, -1, ""), 2_000)
			&& Rs2Inventory.waitFor(Rs2Bank::hasWithdrawAsNote, 3_000);
	}

	public static BankLocation getNearestBank(WorldPoint origin)
	{
		if (origin == null) return null;
		return Arrays.stream(BankLocation.values())
			.filter(bank -> bank.hasRequirements() && (bank.getWorldPoint().getPlane() == origin.getPlane()
				|| Rs2Walker.canReachVertical(origin, bank.getWorldPoint())))
			.min(Comparator.comparingInt(bank -> estimatedBankDistance(origin, bank.getWorldPoint()))).orElse(null);
	}

	/** As upstream: any bank on this floor within {@code distance} tiles. */
	public static boolean isNearBank(int distance)
	{
		WorldPoint origin = location();
		return origin != null && Arrays.stream(BankLocation.values())
			.anyMatch(b -> b.getWorldPoint().getPlane() == origin.getPlane() && b.getWorldPoint().distanceTo2D(origin) <= distance);
	}

	public static boolean walkToBank() { return walkToBank(getNearestBank()); }
	public static boolean walkToBank(BankLocation bank) { return walkToBank(bank, true); }

	/** As upstream: walks to the bank and returns true when within 4 tiles (or the bank is open). */
	public static boolean walkToBank(BankLocation bank, boolean toggleRun)
	{
		if (bank == null) return false;
		if (isOpen()) return true;
		net.runelite.client.plugins.microbot.util.player.Rs2Player.toggleRunEnergy(toggleRun);
		if (!isNearBank(bank, 4)) Rs2Walker.walkTo(bank.getWorldPoint(), 4);
		return isNearBank(bank, 4);
	}

	public static boolean walkToBankAndUseBank() { return walkToBankAndUseBank(getNearestBank()); }

	public static boolean walkToBankAndUseBank(BankLocation bank, boolean toggleRun)
	{
		if (isOpen()) return true;
		net.runelite.client.plugins.microbot.util.player.Rs2Player.toggleRunEnergy(toggleRun);
		return walkToBankAndUseBank(bank);
	}

	private static Rs2ItemModel firstInventory(String name, boolean exact)
	{
		return Rs2Inventory.items(i -> nameMatches(i.getName(), name, exact)).findFirst().orElse(null);
	}

	private static boolean nameMatches(String candidate, String search, boolean exact)
	{
		if (candidate == null || search == null || search.trim().isEmpty()) return false;
		return exact ? candidate.equalsIgnoreCase(search.trim())
			: candidate.toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT));
	}

	private static long inventoryTotal()
	{
		return Rs2Inventory.items(i -> true).mapToLong(Rs2ItemModel::getQuantity).sum();
	}

	// ---- Hub read APIs: remembered bank contents (Microbot API names, written for mobile) ----

	private static volatile List<Rs2ItemModel> rememberedBank = java.util.Collections.emptyList();

	/** Event hook (client thread); routed by {@code MicrobotReadServices} for the bank container. */
	public static void storeBankItemsInMemory(net.runelite.api.events.ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.BANK.getId() || event.getItemContainer() == null)
		{
			return;
		}
		Item[] slots = event.getItemContainer().getItems();
		List<Rs2ItemModel> seen = new java.util.ArrayList<>();
		for (int slot = 0; slots != null && slot < slots.length; slot++)
		{
			Item stack = slots[slot];
			if (stack == null || stack.getId() < 0 || stack.getQuantity() <= 0)
			{
				continue;
			}
			seen.add(new Rs2ItemModel(stack, Microbot.getClient().getItemDefinition(stack.getId()), null, slot));
		}
		rememberedBank = java.util.Collections.unmodifiableList(seen);
	}

	/** Forgets the remembered bank (logout, hop, connection loss, provider uninstall). */
	public static void clearMemory()
	{
		rememberedBank = java.util.Collections.emptyList();
	}

	/** Bank contents last seen while the bank was open; empty until it has been opened this session. */
	public static List<Rs2ItemModel> bankItems()
	{
		return rememberedBank;
	}

	private static boolean nameMatches(Rs2ItemModel item, String name, boolean exact)
	{
		String itemName = item.getName();
		if (itemName == null || name == null)
		{
			return false;
		}
		return exact ? itemName.equalsIgnoreCase(name)
			: itemName.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT));
	}

	private static int remembered(Predicate<Rs2ItemModel> filter)
	{
		return bankItems().stream().filter(filter).mapToInt(Rs2ItemModel::getQuantity).sum();
	}

	/** Whether at least {@code amount} of item {@code id} is banked. */
	private static boolean banked(int id, int amount)
	{
		return id > 0 && remembered(item -> item.getId() == id) >= Math.max(1, amount);
	}

	/** Whether at least {@code amount} of an item matching {@code name} is banked (one stack). */
	private static boolean banked(String name, boolean exact, int amount)
	{
		return bankItems().stream().anyMatch(item -> nameMatches(item, name, exact) && item.getQuantity() >= Math.max(1, amount));
	}

	public static boolean hasItem(int id)
	{
		return banked(id, 1);
	}

	/** Partial, case-insensitive name. */
	public static boolean hasItem(String name)
	{
		return hasItem(name, false);
	}

	public static boolean hasItem(String name, boolean exact)
	{
		return banked(name, exact, 1);
	}

	/** Whether any of {@code names} (partial) is banked. */
	public static boolean hasItem(java.util.Collection<String> names)
	{
		return hasItem(names, false, 1);
	}

	public static boolean hasItem(java.util.Collection<String> names, boolean exact)
	{
		return hasItem(names, exact, 1);
	}

	public static boolean hasItem(java.util.Collection<String> names, int amount)
	{
		return hasItem(names, false, amount);
	}

	/** Whether any of {@code names} is banked with at least {@code amount} in one stack. */
	public static boolean hasItem(java.util.Collection<String> names, boolean exact, int amount)
	{
		return names.stream().anyMatch(name -> banked(name, exact, amount));
	}

	/** Whether every one of {@code names} is banked with at least {@code amount} in one stack. */
	public static boolean hasAllItems(java.util.Collection<String> names, boolean exact, int amount)
	{
		return names.stream().allMatch(name -> banked(name, exact, amount));
	}

	public static boolean hasItem(int[] ids)
	{
		return hasItem(ids, 1);
	}

	public static boolean hasAllItems(int[] ids)
	{
		return hasAllItems(ids, 1);
	}

	public static boolean hasItem(int[] ids, int amount)
	{
		return Arrays.stream(ids).anyMatch(id -> banked(id, amount));
	}

	public static boolean hasAllItems(int[] ids, int amount)
	{
		return Arrays.stream(ids).allMatch(id -> banked(id, amount));
	}

	/** Partial name, at least one. */
	public static boolean hasBankItem(String name)
	{
		return hasBankItem(name, 1, false);
	}

	public static boolean hasBankItem(String name, boolean exact)
	{
		return hasBankItem(name, 1, exact);
	}

	/** Partial name, at least {@code amount}. */
	public static boolean hasBankItem(String name, int amount)
	{
		return hasBankItem(name, amount, false);
	}

	/** At least {@code amount} of an item matching {@code name}; an amount of 0 or less is always true. */
	public static boolean hasBankItem(String name, int amount, boolean exact)
	{
		return amount <= 0 || banked(name, exact, amount);
	}

	/** At least {@code amount} of item {@code id}; an amount of 0 or less is always true. */
	public static boolean hasBankItem(int id, int amount)
	{
		return amount <= 0 || banked(id, amount);
	}

	public static Rs2ItemModel getBankItem(int itemId)
	{
		return bankItems().stream().filter(item -> item.getId() == itemId).findFirst().orElse(null);
	}

	public static Rs2ItemModel getBankItem(String itemName, boolean exact)
	{
		return bankItems().stream().filter(item -> nameMatches(item, itemName, exact)).findFirst().orElse(null);
	}

	/** Partial, case-insensitive name. */
	public static Rs2ItemModel getBankItem(String itemName)
	{
		return getBankItem(itemName, false);
	}

	/** As upstream: the remembered bank contents (see {@link #bankItems()}) as a stream. */
	public static java.util.stream.Stream<Rs2ItemModel> getAll()
	{
		return bankItems().stream();
	}

	public static java.util.stream.Stream<Rs2ItemModel> getAll(Predicate<Rs2ItemModel> filter)
	{
		return getAll().filter(filter);
	}

	/** As upstream: quantity of the first remembered stack matching {@code name}, else 0. */
	public static int count(String name, boolean exact)
	{
		Rs2ItemModel bankItem = getBankItem(name, exact);
		return bankItem == null ? 0 : bankItem.getQuantity();
	}

	/** As upstream: {@link #count(String, boolean)} with a partial, case-insensitive name. */
	public static int count(String name)
	{
		return count(name, false);
	}
}
