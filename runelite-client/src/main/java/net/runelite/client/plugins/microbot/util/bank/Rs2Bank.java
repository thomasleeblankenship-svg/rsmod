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
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

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

	/** As upstream: "Bank" on this banker (see {@code Rs2Npc.getBankerNPC()}), then waits for the bank. */
	public static boolean openBank(net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel npc)
	{
		Microbot.status = "Opening bank";
		if (isOpen())
		{
			return true;
		}
		return npc != null && net.runelite.client.plugins.microbot.util.npc.Rs2Npc.interact(npc, "Bank")
			&& Rs2Inventory.waitFor(Rs2Bank::isOpen, 6_000);
	}

	/** As upstream: {@link #openBank(net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel)} on a raw NPC. */
	public static boolean openBank(net.runelite.api.NPC npc)
	{
		return npc != null && openBank(new net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel(npc));
	}

	/**
	 * As upstream: opens the bank through this booth, chest or deposit object ("Bank", else "Use"),
	 * then waits for the bank.
	 */
	public static boolean openBank(net.runelite.api.TileObject object)
	{
		Microbot.status = "Opening bank";
		if (isOpen())
		{
			return true;
		}
		if (object == null)
		{
			return false;
		}
		Object[] target = MobileActions.read(() ->
		{
			var composition = Microbot.getClient().getObjectDefinition(object.getId());
			String[] actions = composition == null ? null : composition.getActions();
			String action = actions == null ? null : Arrays.stream(actions)
				.filter(a -> "Bank".equalsIgnoreCase(a) || "Use".equalsIgnoreCase(a)).findFirst().orElse(null);
			return action == null ? null : new Object[]{object.getId(), object.getWorldLocation(), action};
		}, null);
		return target != null
			&& Rs2Inventory.waitFor(() -> MobileActions.objectAt((int) target[0], (WorldPoint) target[1], (String) target[2]), 2_000)
			&& Rs2Inventory.waitFor(Rs2Bank::isOpen, 6_000);
	}

	/**
	 * As upstream, where it hovers over a bank only when natural mouse is on: humanised input is
	 * not ported, so this never moves anything and returns false.
	 */
	public static boolean preHover()
	{
		return false;
	}

	/** As upstream: "Empty" on a carried gem bag (open or closed) into the bank. */
	public static boolean emptyGemBag()
	{
		Rs2ItemModel bag = Rs2Inventory.items(i -> i.getId() == net.runelite.api.gameval.ItemID.GEM_BAG
			|| i.getId() == net.runelite.api.gameval.ItemID.GEM_BAG_OPEN).findFirst().orElse(null);
		return bag != null && Rs2Inventory.interact(bag, "Empty");
	}

	/** As upstream: withdraws one rune pouch of any kind the bank holds. */
	public static boolean withdrawRunePouch()
	{
		for (int id : net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch.POUCH_IDS)
		{
			if (hasItem(id))
			{
				return withdrawOne(id);
			}
		}
		return false;
	}

	/** As upstream: "Empty" on a carried herb sack (open or closed) into the bank. */
	public static boolean emptyHerbSack()
	{
		Rs2ItemModel sack = Rs2Inventory.items(i -> i.getId() == net.runelite.api.gameval.ItemID.SLAYER_HERB_SACK
			|| i.getId() == net.runelite.api.gameval.ItemID.SLAYER_HERB_SACK_OPEN).findFirst().orElse(null);
		return sack != null && Rs2Inventory.interact(sack, "Empty");
	}

	/** As upstream: "Empty" on a carried seed box (open or closed) into the bank. */
	public static boolean emptySeedBox()
	{
		Rs2ItemModel box = Rs2Inventory.items(i -> i.getId() == net.runelite.api.gameval.ItemID.SEED_BOX
			|| i.getId() == net.runelite.api.gameval.ItemID.SEED_BOX_OPEN).findFirst().orElse(null);
		return box != null && Rs2Inventory.interact(box, "Empty");
	}

	/** The bank's looting bag view (15:6 "Deposit loot", 15:8 close, 15:11 the bag's items). */
	private static final int LOOTING_BAG_DEPOSIT = 983046;
	private static final int LOOTING_BAG_CLOSE = 983048;
	private static final int LOOTING_BAG_ITEMS = 983051;

	/**
	 * As upstream: with the bank open and an open looting bag carried, "View" the bag, press
	 * "Deposit loot", wait for the view to empty, then close it. Each step is one dispatch, and
	 * every wait is bounded.
	 */
	public static boolean depositLootingBag()
	{
		if (!Rs2Inventory.contains(net.runelite.api.gameval.ItemID.LOOTING_BAG_OPEN) || !isOpen())
		{
			return false;
		}
		if (!Rs2Inventory.interact(net.runelite.api.gameval.ItemID.LOOTING_BAG_OPEN, "View")
			|| !Rs2Inventory.waitFor(() -> Rs2Widget.getWidget(LOOTING_BAG_DEPOSIT) != null, 3_000)
			|| !Rs2Widget.clickWidget(LOOTING_BAG_DEPOSIT))
		{
			return false;
		}
		Rs2Inventory.waitFor(Rs2Bank::isLootingBagViewEmpty, 1_000);
		return Rs2Widget.clickWidget(LOOTING_BAG_CLOSE);
	}

	/** True when the looting bag view shows no items (or is not open). */
	static boolean isLootingBagViewEmpty()
	{
		return MobileActions.read(() ->
		{
			Widget items = Microbot.getClient().getWidget(LOOTING_BAG_ITEMS);
			Widget[] children = items == null ? null : items.getChildren();
			return children == null || children.length == 0 || children[0] == null;
		}, true);
	}

	/** The bank side inventory (15:3), whose slots carry "Lock-slot" / "Unlock-slot" when locking is on. */
	private static final int BANK_SIDE_ITEMS = InterfaceID.Bankside.ITEMS;

	/** The lock option this bank-inventory slot offers ("Lock-slot" or "Unlock-slot"), or null. */
	private static String lockOption(int slot)
	{
		return MobileActions.read(() ->
		{
			Widget container = Microbot.getClient().getWidget(BANK_SIDE_ITEMS);
			Widget item = container == null ? null : container.getChild(slot);
			String[] actions = item == null ? null : item.getActions();
			return actions == null ? null : Arrays.stream(actions)
				.filter(a -> "Lock-slot".equalsIgnoreCase(a) || "Unlock-slot".equalsIgnoreCase(a)).findFirst().orElse(null);
		}, null);
	}

	/**
	 * As upstream: toggles the bank-inventory lock of the item named {@code itemName} (exact or
	 * containing it). False when the bank is closed, the item is missing or slot locking is off (the
	 * slot then offers no lock option).
	 */
	public static boolean toggleItemLock(String itemName, boolean exact)
	{
		Rs2ItemModel item = itemName == null ? null : firstInventory(itemName, exact);
		return item != null && toggleLock(item.getSlot());
	}

	private static boolean toggleLock(int slot)
	{
		if (!isOpen() || slot < 0 || slot >= 28)
		{
			return false;
		}
		String option = lockOption(slot);
		if (option == null)
		{
			return false;
		}
		String after = "Lock-slot".equalsIgnoreCase(option) ? "Unlock-slot" : "Lock-slot";
		return Rs2Inventory.waitFor(() -> MobileActions.widget(BANK_SIDE_ITEMS, slot, option), 2_000)
			&& Rs2Inventory.waitFor(() -> after.equalsIgnoreCase(lockOption(slot)), 2_000);
	}

	/** As upstream: locks every given bank-inventory slot that holds an item and is not locked yet. */
	public static boolean lockAllBySlot(int... slots)
	{
		boolean any = false;
		for (int slot : slots == null ? new int[0] : slots)
		{
			if (slot >= 0 && slot < 28 && Rs2Inventory.getItemInSlot(slot) != null && "Lock-slot".equalsIgnoreCase(lockOption(slot))
				&& toggleLock(slot))
			{
				any = true;
			}
		}
		return any;
	}

	/** As upstream: unlocks every locked bank-inventory slot; false when none was locked. */
	public static boolean toggleAllLocks()
	{
		boolean any = false;
		for (int slot = 0; slot < 28; slot++)
		{
			if ("Unlock-slot".equalsIgnoreCase(lockOption(slot)) && toggleLock(slot))
			{
				any = true;
			}
		}
		return any;
	}

	/** As upstream: opens bank tab {@code index} (0 is the main tab). */
	public static boolean openTab(int index)
	{
		if (!isOpen() || index < 0 || index > 9)
		{
			return false;
		}
		return Rs2Inventory.waitFor(() -> MobileActions.widget(InterfaceID.Bankmain.TABS, 10 + index, ""), 2_000);
	}

	/** As upstream: opens the main bank tab. */
	public static boolean openMainTab()
	{
		return openTab(0);
	}

	/**
	 * As upstream: scrolls the bank so item {@code slotId} (its index in the bank item container) is
	 * in view. Mobile: scrolls to the slot widget's own position rather than upstream's tab-count
	 * arithmetic. Only while a script session is active; false when the bank or slot is missing.
	 */
	public static boolean scrollBankToSlot(int slotId)
	{
		if (!MobileActions.active() || !isOpen())
		{
			return false;
		}
		return MobileActions.read(() ->
		{
			Widget container = Microbot.getClient().getWidget(InterfaceID.Bankmain.ITEMS);
			Widget item = container == null ? null : container.getChild(slotId);
			if (item == null)
			{
				return false;
			}
			int scrollY = Math.max(0, item.getRelativeY() - container.getHeight() / 2);
			Microbot.getClient().setVarcIntValue(net.runelite.api.VarClientInt.BANK_SCROLL, scrollY);
			Microbot.getClient().runScript(net.runelite.api.ScriptID.UPDATE_SCROLLBAR,
				InterfaceID.Bankmain.SCROLLBAR, InterfaceID.Bankmain.ITEMS, scrollY);
			container.setScrollY(scrollY);
			return true;
		}, false);
	}

	/** As upstream: withdraws {@code amount} of {@code name} and wears it (true when already worn). */
	public static boolean withdrawXAndEquip(String name, int amount)
	{
		if (net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.isWearing(name))
		{
			return true;
		}
		withdrawX(name, amount);
		return Rs2Inventory.waitFor(() -> Rs2Inventory.hasItem(name), 3_000) && wearItem(name);
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
		if (isNearBankOnFoot(bank, 8)) return openBank();
		if (!Rs2Walker.walkTo(bank.getWorldPoint(), 6)) return false;
		closeInIfWalledOff(bank, 8);
		return isNearBankOnFoot(bank, 8) && openBank();
	}

	/**
	 * Mobile: {@link #isNearBank(BankLocation, int)} that also needs a short walking route. Straight-line
	 * distance alone counts a player outside a bank's back wall as near (Draynor, beside Diango), where the
	 * booths and bankers cannot be reached, so the script retried {@link #openBank()} there forever. When no
	 * route can be computed the straight-line answer stands, as upstream.
	 */
	public static boolean isNearBankOnFoot(BankLocation bank, int distance)
	{
		if (!isNearBank(bank, distance)) return false;
		WorldPoint origin = location();
		return origin == null || nearOnFoot(walkingTiles(origin, bank.getWorldPoint()), origin.distanceTo(bank.getWorldPoint()));
	}

	// The full route length, or Integer.MAX_VALUE unless the search completed (a search cut short under load
	// returns a partial path that must not read as a detour), and on the client thread, where it cannot run.
	private static int walkingTiles(WorldPoint origin, WorldPoint goal)
	{
		net.runelite.api.Client client = Microbot.getClient();
		if (client != null && client.isClientThread()) return Integer.MAX_VALUE;
		net.runelite.client.plugins.microbot.shortestpath.PathResult route =
			net.runelite.client.plugins.microbot.shortestpath.MobilePathfinder.find(origin, goal);
		return route != null && route.isFound() ? Math.max(0, route.getTiles().size() - 1) : Integer.MAX_VALUE;
	}

	/**
	 * Walking-route check for {@link #isNearBankOnFoot}: a wall is in the way when the route is both more than
	 * {@link #WALL_DETOUR_SLACK_TILES} steps longer than the straight line and more than twice it. Walking
	 * round bank booths stays near (Lumbridge top floor: 10 steps for 6 straight); a wall does not (Draynor
	 * south side: 20 steps for 7 straight). {@link Integer#MAX_VALUE} means no complete route was found, which
	 * keeps the straight-line answer.
	 */
	public static boolean nearOnFoot(int walkingSteps, int straightTiles)
	{
		if (walkingSteps == Integer.MAX_VALUE) return true;
		return walkingSteps - straightTiles <= WALL_DETOUR_SLACK_TILES || walkingSteps <= 2 * straightTiles;
	}

	// Mobile: a route this many steps longer than the straight line (and over twice it) means a wall is in the way.
	static final int WALL_DETOUR_SLACK_TILES = 4;

	// Mobile: the walker's arrival radius is straight-line, so it can stop on the wrong side of a bank wall;
	// then walk on to the bank tile itself, which routes through the entrance.
	private static void closeInIfWalledOff(BankLocation bank, int distance)
	{
		if (isNearBank(bank, distance) && !isNearBankOnFoot(bank, distance))
		{
			Rs2Walker.walkTo(bank.getWorldPoint(), 1);
		}
	}

	/** As upstream: banks at the nearest bank when the inventory is full, else walks back (within 4 tiles). */
	public static boolean bankItemsAndWalkBackToOriginalPosition(java.util.Collection<String> itemNames, WorldPoint initialRs2PlayerLocation)
	{
		return bankItemsAndWalkBackToOriginalPosition(itemNames, false, getNearestBank(), initialRs2PlayerLocation, 0, 4);
	}

	/** As upstream: as above, banking when no more than {@code emptySlotCount} slots are empty. */
	public static boolean bankItemsAndWalkBackToOriginalPosition(java.util.Collection<String> itemNames, WorldPoint initialRs2PlayerLocation,
		int emptySlotCount)
	{
		return bankItemsAndWalkBackToOriginalPosition(itemNames, false, getNearestBank(), initialRs2PlayerLocation, emptySlotCount, 4);
	}

	/** As upstream: as above, walking back to within {@code distance} tiles. */
	public static boolean bankItemsAndWalkBackToOriginalPosition(java.util.Collection<String> itemNames, WorldPoint initialRs2PlayerLocation,
		int emptySlotCount, int distance)
	{
		return bankItemsAndWalkBackToOriginalPosition(itemNames, false, getNearestBank(), initialRs2PlayerLocation, emptySlotCount, distance);
	}

	/**
	 * As upstream: when at most {@code emptySlotCount} inventory slots are empty, walks to
	 * {@code bankLocation}, opens it and deposits every stack named in {@code itemNames} (an all-digit
	 * entry is an item id), returning false; otherwise walks back to {@code initialRs2PlayerLocation}
	 * (within {@code distance}, at most 10) and returns whether the player is there with room. One
	 * step per call, as upstream, so a script calls it from its loop. {@code exactItemNames} is
	 * ignored for deposits, as upstream (names match partially).
	 */
	public static boolean bankItemsAndWalkBackToOriginalPosition(java.util.Collection<String> itemNames, boolean exactItemNames,
		BankLocation bankLocation, WorldPoint initialRs2PlayerLocation, int emptySlotCount, int distance)
	{
		if (Rs2Inventory.emptySlotCount() <= emptySlotCount)
		{
			if (walkToBankAndUseBank(bankLocation) && itemNames != null)
			{
				for (String itemName : itemNames)
				{
					if (itemName == null)
					{
						continue;
					}
					if (itemName.matches("\\d+"))
					{
						depositAll(Integer.parseInt(itemName));
					}
					else
					{
						depositAll(itemName, false);
					}
				}
			}
			return false;
		}
		if (initialRs2PlayerLocation == null)
		{
			return false;
		}
		int within = Math.min(distance, 10);
		WorldPoint here = location();
		if (here == null || initialRs2PlayerLocation.distanceTo(here) > within
			|| !net.runelite.client.plugins.microbot.util.tile.Rs2Tile.isTileReachable(initialRs2PlayerLocation))
		{
			Rs2Walker.walkTo(initialRs2PlayerLocation, within);
		}
		else
		{
			Rs2Walker.walkFastCanvas(initialRs2PlayerLocation);
		}
		WorldPoint after = location();
		return Rs2Inventory.emptySlotCount() > emptySlotCount && after != null && initialRs2PlayerLocation.distanceTo(after) <= within;
	}

	/**
	 * As upstream (deprecated there): at the Grand Exchange, opens the bank, deposits everything and,
	 * noted, withdraws all of each tradeable item {@code npcName}'s loot records hold, except food
	 * (an "Eat" option) and {@code itemsToNotSell}; then closes the bank. True when anything was
	 * withdrawn. The records come from {@link Microbot#getAggregateLootRecords()}, which is always
	 * empty as upstream, so today this deposits and withdraws nothing.
	 */
	@Deprecated
	public static boolean withdrawLootItems(String npcName, List<String> itemsToNotSell)
	{
		if (net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange.walkToGrandExchange() && !openBank())
		{
			return false;
		}
		depositAll();
		if (!setWithdrawAsNote())
		{
			return false;
		}
		boolean itemFound = false;
		for (net.runelite.client.plugins.loottracker.LootTrackerRecord lootRecord : Microbot.getAggregateLootRecords())
		{
			if (lootRecord == null || !lootRecord.getTitle().equalsIgnoreCase(npcName) || lootRecord.getItems() == null)
			{
				continue;
			}
			for (net.runelite.client.plugins.loottracker.LootTrackerItem item : lootRecord.getItems())
			{
				if (itemsToNotSell != null && itemsToNotSell.stream().anyMatch(x -> x != null && x.trim().equalsIgnoreCase(item.getName())))
				{
					continue;
				}
				ItemComposition composition = definition(item.getId());
				if (composition == null)
				{
					return false;
				}
				String[] actions = composition.getInventoryActions();
				if (actions != null && Arrays.stream(actions).anyMatch(x -> x != null && x.equalsIgnoreCase("eat")))
				{
					continue;
				}
				int itemId = item.getId();
				boolean isNoted = composition.getNote() == 799;
				if (!composition.isTradeable() && !isNoted)
				{
					continue;
				}
				if (isNoted)
				{
					int unnoted = item.getId() - 1; // as upstream: the unnoted id
					ItemComposition unnotedComposition = definition(unnoted);
					if (unnotedComposition == null)
					{
						return false;
					}
					if (!unnotedComposition.isTradeable())
					{
						continue;
					}
					itemId = unnoted;
				}
				if (withdrawAll(itemId))
				{
					itemFound = true;
				}
			}
		}
		closeBank();
		return itemFound;
	}

	private static ItemComposition definition(int id)
	{
		return MobileActions.read(() -> Microbot.getClient().getItemDefinition(id), null);
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
	/** As upstream: withdraws all but one of the stack with this id ("Withdraw-All-but-1"). */
	public static boolean withdrawAllButOne(int id) { return withdrawStack(findBankStack(id), "Withdraw-All-but-1"); }
	/** As upstream: all but one of the item whose name contains {@code name}. */
	public static boolean withdrawAllButOne(String name) { return withdrawAllButOne(name, false); }
	/** As upstream: all but one of the item named {@code name}, exactly or containing it. */
	public static boolean withdrawAllButOne(String name, boolean exact) { return withdrawStack(findBankStack(name, exact), "Withdraw-All-but-1"); }
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

	/** As upstream: one of each id; true when every deposit was observed. */
	public static boolean depositItems(int... ids)
	{
		boolean all = true;
		for (int id : ids)
		{
			all &= depositOne(id);
		}
		return all;
	}

	/** As upstream: empties a carried fish barrel (closed or open) into the bank. */
	public static boolean emptyFishBarrel()
	{
		Rs2ItemModel barrel = Rs2Inventory.items(i -> i.getId() == net.runelite.api.gameval.ItemID.FISH_BARREL_CLOSED
			|| i.getId() == net.runelite.api.gameval.ItemID.FISH_BARREL_OPEN).findFirst().orElse(null);
		return isOpen() && barrel != null && Rs2Inventory.waitFor(() -> MobileActions.inventory(barrel.getSlot(), barrel.getId(), "Empty"), 2_000);
	}
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

	// ---- Batch AR (Claude 1): bank route reads ----

	/**
	 * As upstream: the nearest bank with its requirements met. A bank within
	 * {@code maxObjectSearchRadius} tiles on this floor wins (upstream scans booths in that radius);
	 * otherwise {@link #getNearestBank(WorldPoint)} chooses.
	 */
	public static BankLocation getNearestBank(WorldPoint worldPoint, int maxObjectSearchRadius)
	{
		if (worldPoint == null) return null;
		BankLocation close = Arrays.stream(BankLocation.values())
			.filter(bank -> bank.getWorldPoint().getPlane() == worldPoint.getPlane()
				&& bank.getWorldPoint().distanceTo2D(worldPoint) <= Math.max(0, maxObjectSearchRadius) && bank.hasRequirements())
			.min(Comparator.comparingInt(bank -> bank.getWorldPoint().distanceTo2D(worldPoint))).orElse(null);
		return close != null ? close : getNearestBank(worldPoint);
	}

	/** As upstream: the walk path from the player to the nearest bank (50-tile search), or an empty list. */
	public static List<WorldPoint> getPathToNearestBank()
	{
		return getPathToNearestBank(location(), 50);
	}

	/** As upstream: {@link #getPathToNearestBank(WorldPoint, int)} with a 50-tile search. */
	public static List<WorldPoint> getPathToNearestBank(WorldPoint worldPoint)
	{
		return getPathToNearestBank(worldPoint, 50);
	}

	/** As upstream: the walk path to {@link #getNearestBank(WorldPoint, int)}, or an empty list when none is reachable. */
	public static List<WorldPoint> getPathToNearestBank(WorldPoint worldPoint, int maxObjectSearchRadius)
	{
		NearestBankRoute route = routeTo(worldPoint, getNearestBank(worldPoint, maxObjectSearchRadius));
		return route == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(route.getPath());
	}

	/** As upstream: the nearest bank (20-tile search) and the path to it, or null when none is reachable. */
	public static NearestBankRoute getNearestBankRoute(WorldPoint worldPoint)
	{
		return routeTo(worldPoint, getNearestBank(worldPoint, 20));
	}

	private static NearestBankRoute routeTo(WorldPoint origin, BankLocation bank)
	{
		if (origin == null || bank == null) return null;
		List<WorldPoint> path = Rs2Walker.getWalkPath(origin, bank.getWorldPoint());
		return path == null || path.isEmpty() ? null : new NearestBankRoute(path, bank);
	}

	/**
	 * As upstream: a chosen bank and the walk path to it. Adaptation: upstream's {@code getRoute()}
	 * (its route model, not ported) is not provided.
	 */
	public static final class NearestBankRoute
	{
		private final List<WorldPoint> path;
		private final BankLocation bank;

		private NearestBankRoute(List<WorldPoint> path, BankLocation bank)
		{
			this.path = List.copyOf(path);
			this.bank = bank;
		}

		/** As upstream: the walk path, start to bank. */
		public List<WorldPoint> getPath()
		{
			return path;
		}

		/** As upstream: the chosen bank. */
		public BankLocation getBank()
		{
			return bank;
		}
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
		if (!isNearBankOnFoot(bank, 4))
		{
			Rs2Walker.walkTo(bank.getWorldPoint(), 4);
			closeInIfWalledOff(bank, 4);
		}
		return isNearBankOnFoot(bank, 4);
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

	// ---- Util action batch Q (Claude 1): withdraw-as, equipment deposits, collection box ----

	/** As upstream: switches the bank to withdraw notes ({@code true}) or items; observed after the click. */
	public static boolean setWithdrawAs(boolean noted)
	{
		return noted ? setWithdrawAsNote() : setWithdrawAsItem();
	}

	/** As upstream: one of each item; true when every withdrawal was confirmed. */
	public static boolean withdrawItems(int... ids)
	{
		boolean success = true;
		for (int id : ids)
		{
			success &= withdrawOne(id);
		}
		return success;
	}

	/** As upstream: withdraws the whole stack whose name contains {@code name}, then wears it. */
	public static boolean withdrawAllAndEquip(String name)
	{
		if (net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.isWearing(name)) return true;
		return withdrawAll(name) && Rs2Inventory.waitFor(() -> Rs2Inventory.hasItem(name), 3_000) && wearItem(name);
	}

	/** As upstream: withdraws the whole stack with this ID, then wears it. */
	public static boolean withdrawAllAndEquip(int id)
	{
		if (net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.isWearing(id)) return true;
		return withdrawAll(id) && Rs2Inventory.waitFor(() -> Rs2Inventory.hasItem(id), 3_000) && wearItem(id);
	}

	/** As upstream: deposits a carried rune pouch of any kind. */
	public static boolean depositRunePouch()
	{
		for (int id : net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch.POUCH_IDS)
		{
			if (Rs2Inventory.hasItem(id))
			{
				return depositOne(id);
			}
		}
		return false;
	}

	/** As upstream: the bank's "Empty containers" button (log basket, herb sack and the like). */
	public static boolean emptyContainers()
	{
		return isOpen() && Rs2Widget.getWidget(InterfaceID.Bankmain.DEPOSITCONTAINERS) != null
			&& Rs2Inventory.waitForInventoryChanges(() -> Rs2Widget.clickWidget(InterfaceID.Bankmain.DEPOSITCONTAINERS), 0, 3_000);
	}

	/** As upstream: unequips the item in this slot and deposits it. */
	public static boolean depositEquippedItem(net.runelite.api.EquipmentInventorySlot equipmentSlot)
	{
		return isOpen() && depositEquipped(List.of(net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.get(equipmentSlot)));
	}

	/** As upstream: unequips the worn item with this ID and deposits it. */
	public static boolean depositEquippedItem(int itemId)
	{
		return isOpen() && depositEquipped(java.util.Collections.singletonList(
			net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.get(itemId)));
	}

	/** As upstream: unequips the worn item whose name contains {@code itemName} and deposits it. */
	public static boolean depositEquippedItem(String itemName)
	{
		return depositEquippedItem(itemName, false);
	}

	/** As upstream: unequips the worn item named {@code itemName} (exactly, or containing it) and deposits it. */
	public static boolean depositEquippedItem(String itemName, boolean exact)
	{
		return isOpen() && depositEquipped(java.util.Collections.singletonList(wornNamed(itemName, exact)));
	}

	/** As upstream: unequips the items in these slots and deposits them; slots with nothing worn are skipped. */
	public static boolean depositEquippedItems(net.runelite.api.EquipmentInventorySlot... equipmentSlots)
	{
		if (!isOpen()) return false;
		List<Rs2ItemModel> worn = new java.util.ArrayList<>();
		for (net.runelite.api.EquipmentInventorySlot slot : equipmentSlots == null ? new net.runelite.api.EquipmentInventorySlot[0] : equipmentSlots)
		{
			worn.add(net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.get(slot));
		}
		return depositEquipped(worn);
	}

	/** As upstream: unequips the worn items with these IDs and deposits them. */
	public static boolean depositEquippedItems(int... itemIds)
	{
		if (!isOpen()) return false;
		List<Rs2ItemModel> worn = new java.util.ArrayList<>();
		for (int id : itemIds == null ? new int[0] : itemIds)
		{
			worn.add(net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.get(id));
		}
		return depositEquipped(worn);
	}

	/** As upstream: unequips the worn items whose names contain these and deposits them. */
	public static boolean depositEquippedItems(String... itemNames)
	{
		if (!isOpen()) return false;
		List<Rs2ItemModel> worn = new java.util.ArrayList<>();
		for (String name : itemNames == null ? new String[0] : itemNames)
		{
			worn.add(wornNamed(name, false));
		}
		return depositEquipped(worn);
	}

	private static Rs2ItemModel wornNamed(String name, boolean exact)
	{
		if (name == null) return null;
		String wanted = name.toLowerCase(Locale.ROOT);
		return net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.get(item -> item.getName() != null
			&& (exact ? item.getName().equalsIgnoreCase(name) : item.getName().toLowerCase(Locale.ROOT).contains(wanted)));
	}

	/**
	 * Upstream's core: unequip each, wait for it in the inventory, then deposit each. Mobile
	 * adaptation: when the inventory lacks the free slots, nothing is moved and false is returned,
	 * instead of upstream's banking and re-withdrawing other items to make room.
	 */
	private static boolean depositEquipped(List<Rs2ItemModel> worn)
	{
		List<Rs2ItemModel> items = worn.stream().filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toList());
		if (items.isEmpty()) return true;
		if (Rs2Inventory.emptySlotCount() < items.size()) return false;
		for (Rs2ItemModel item : items)
		{
			if (!net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.unEquip(item.getId())
				|| !Rs2Inventory.waitFor(() -> Rs2Inventory.hasItem(item.getId()), 3_000))
			{
				return false;
			}
		}
		boolean deposited = true;
		for (Rs2ItemModel item : items)
		{
			deposited &= depositOne(item.getId());
		}
		return deposited;
	}

	/** The collection box (402): its title row, and the "to inventory" and "to bank" buttons. */
	private static final int COLLECTION_BOX_GROUP = 402;
	private static final int COLLECTION_BOX_TITLE = 2;
	private static final int COLLECTION_TO_INVENTORY = (COLLECTION_BOX_GROUP << 16) | 3;
	private static final int COLLECTION_TO_BANK = (COLLECTION_BOX_GROUP << 16) | 4;

	/** As upstream: whether the collection box title is shown. */
	public static boolean isCollectionBoxVisible()
	{
		return Rs2Widget.hasWidgetText("Collection box", COLLECTION_BOX_GROUP, COLLECTION_BOX_TITLE, false);
	}

	/** As upstream: {@link #isCollectionBoxVisible()}. */
	public static boolean collectionBoxIsOpen()
	{
		return isCollectionBoxVisible();
	}

	/**
	 * As upstream: "Collect" on the nearest bank booth or chest, else on a banker, then waits for the
	 * collection box. True when it is open.
	 */
	public static boolean openCollectionBox()
	{
		if (collectionBoxIsOpen()) return true;
		net.runelite.api.TileObject bank = net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.findBank();
		boolean sent;
		if (bank != null)
		{
			sent = net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.interact(bank, "Collect");
		}
		else
		{
			net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel banker = net.runelite.client.plugins.microbot.util.npc.Rs2Npc.getBankerNPC();
			sent = banker != null && net.runelite.client.plugins.microbot.util.npc.Rs2Npc.interact(banker, "Collect");
		}
		return sent && Rs2Inventory.waitFor(Rs2Bank::collectionBoxIsOpen, 5_000);
	}

	/** As upstream: opens the collection box and sends everything in it to the bank. */
	public static boolean bankAllCollectionBoxItems()
	{
		return openCollectionBox() && Rs2Widget.clickWidget(COLLECTION_TO_BANK);
	}

	/** As upstream: opens the collection box and takes everything in it into the inventory. */
	public static boolean inventoryAllCollectionBoxItems()
	{
		return openCollectionBox() && Rs2Widget.clickWidget(COLLECTION_TO_INVENTORY);
	}

	/** As upstream: closes the collection box (its close button, child 3 of 402:2) and waits for it to go. */
	public static boolean closeCollectionBox()
	{
		Widget title = Rs2Widget.getWidget(COLLECTION_BOX_GROUP, COLLECTION_BOX_TITLE);
		if (title == null) return true;
		Widget close = MobileActions.read(() ->
		{
			Widget[] children = title.getDynamicChildren();
			return children != null && children.length > 3 ? children[3] : null;
		}, null);
		return close != null && Rs2Widget.clickWidget(close) && Rs2Inventory.waitFor(() -> !collectionBoxIsOpen(), 5_000);
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

	// ---- Read batch 14 (Claude 2, util-gap plan): upstream 2.6.38 bank reads ----

	/** Bank snapshots applied since login (see {@link #getBankLiveEpoch}). */
	private static final java.util.concurrent.atomic.AtomicInteger BANK_LIVE_EPOCH = new java.util.concurrent.atomic.AtomicInteger();

	/**
	 * As upstream: a counter raised for every bank container snapshot (so a script can wait for a
	 * fresh one after opening the bank); 0 after logout. Fed by {@code MicrobotReadServices}.
	 */
	public static int getBankLiveEpoch()
	{
		return BANK_LIVE_EPOCH.get();
	}

	/** Raises {@link #getBankLiveEpoch} for a bank container event; client thread, from {@code MicrobotReadServices}. */
	public static void noteBankSnapshot(net.runelite.api.events.ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.BANK.getId() && event.getItemContainer() != null)
		{
			BANK_LIVE_EPOCH.incrementAndGet();
		}
	}

	/** Resets {@link #getBankLiveEpoch} (logout). */
	public static void resetBankLiveEpoch()
	{
		BANK_LIVE_EPOCH.set(0);
	}

	/** As upstream: the first remembered bank item matching {@code filter}. */
	public static Rs2ItemModel get(Predicate<Rs2ItemModel> filter)
	{
		return getAll(filter).findFirst().orElse(null);
	}

	/** As upstream: the remembered bank item with {@code id}. */
	public static Rs2ItemModel get(int id)
	{
		return get(item -> item.getId() == id);
	}

	/** As upstream: the remembered bank item named {@code name}, ignoring case. */
	public static Rs2ItemModel get(String name)
	{
		return name == null ? null : get(item -> item.getName().equalsIgnoreCase(name));
	}

	/** As upstream: the open bank tab (0 is "all items"). */
	public static int getCurrentTab()
	{
		return Microbot.getVarbitValue(VarbitID.BANK_CURRENTTAB);
	}

	/** As upstream: tab {@code index} is the open one. */
	public static boolean isTabOpen(int index)
	{
		return getCurrentTab() == index;
	}

	/** As upstream: the bank PIN keypad is showing. */
	public static boolean isBankPinWidgetVisible()
	{
		return Rs2Widget.isWidgetVisible(net.runelite.api.widgets.ComponentID.BANK_PIN_CONTAINER);
	}

	/** As upstream: the remembered bank holds a rune pouch of any type. */
	public static boolean hasRunePouch()
	{
		return Arrays.stream(net.runelite.client.plugins.microbot.util.inventory.RunePouchType.values())
			.anyMatch(pouch -> hasItem(pouch.getItemId()));
	}

	/** As upstream: the bank tab widgets (dynamic children of the tab bar); empty when closed. */
	public static List<Widget> getTabs()
	{
		return childrenOf(net.runelite.api.widgets.ComponentID.BANK_TAB_CONTAINER);
	}

	/** As upstream: the bank item slot widgets; empty when closed. */
	public static List<Widget> getItems()
	{
		return childrenOf(net.runelite.api.widgets.ComponentID.BANK_ITEM_CONTAINER);
	}

	/** As upstream: the widget of bank slot {@code slotId}, or {@code null}. */
	public static Widget getItemWidget(int slotId)
	{
		List<Widget> items = getItems();
		return slotId < 0 || slotId >= items.size() ? null : items.get(slotId);
	}

	/** As upstream: the canvas bounds of bank slot {@code slotId}, or {@code null}. */
	public static java.awt.Rectangle getItemBounds(int slotId)
	{
		Widget widget = getItemWidget(slotId);
		return widget == null ? null : MobileActions.read(widget::getBounds, null);
	}

	/**
	 * As upstream: the tab (1-9) holding bank slot {@code itemSlotId}, from the tab size varbits; 0
	 * for the untabbed part, -1 for a slot outside the remembered bank. Adaptation: "outside" is
	 * measured against the remembered items, where upstream reads the bank-size widget.
	 */
	public static int getItemTabForBankItem(int itemSlotId)
	{
		if (itemSlotId < 0 || itemSlotId >= getAll().count())
		{
			return -1;
		}
		int[] tabs = {
			VarbitID.BANK_TAB_1, VarbitID.BANK_TAB_2, VarbitID.BANK_TAB_3,
			VarbitID.BANK_TAB_4, VarbitID.BANK_TAB_5, VarbitID.BANK_TAB_6,
			VarbitID.BANK_TAB_7, VarbitID.BANK_TAB_8, VarbitID.BANK_TAB_9,
		};
		int total = 0;
		for (int i = 0; i < tabs.length; i++)
		{
			total += Microbot.getVarbitValue(tabs[i]);
			if (itemSlotId < total)
			{
				return i + 1;
			}
		}
		return 0;
	}

	private static List<Widget> childrenOf(int componentId)
	{
		return MobileActions.read(() ->
		{
			Widget parent = Microbot.getClient().getWidget(componentId);
			Widget[] children = parent == null ? null : parent.getDynamicChildren();
			return children == null ? java.util.Collections.<Widget>emptyList() : Arrays.asList(children);
		}, java.util.Collections.emptyList());
	}

	// ---- Read batch 16f (upstream 2.6.38) ----

	/** Upstream's bank interface group; its load marks a bank opening. */
	private static final int BANK_GROUP = InterfaceID.BANKMAIN;

	private static volatile long bankLastOpenedAt;

	/** Records a bank opening when the bank interface loads; client thread, from {@code MicrobotReadServices}. */
	public static void noteBankWidgetLoaded(net.runelite.api.events.WidgetLoaded event)
	{
		if (event.getGroupId() == BANK_GROUP)
		{
			bankLastOpenedAt = System.currentTimeMillis();
		}
	}

	/**
	 * As upstream: when the bank was last seen opening, in epoch milliseconds; 0 if not this session.
	 * Upstream also restores it from the RuneScape profile; this port has no persisted bank mirror.
	 */
	public static long getBankLastOpenedAt()
	{
		return bankLastOpenedAt;
	}

	/** As upstream: a bank snapshot is available (here only a live one: there is no restored profile mirror). */
	public static boolean hasBankMirrorSnapshot()
	{
		return getBankLiveEpoch() > 0;
	}

	/** As upstream: the banked item whose name contains {@code name} (ignoring case) with the shortest name. */
	public static Rs2ItemModel findBankItem(String name)
	{
		if (name == null)
		{
			return null;
		}
		String lower = name.toLowerCase();
		return getAll().filter(item -> item.getName() != null && item.getName().toLowerCase().contains(lower) && item.getQuantity() >= 1)
			.min(Comparator.comparingInt(item -> item.getName().length())).orElse(null);
	}

	/**
	 * As upstream: the bank row for a saved item id: that id, else its linked note id, else the exact
	 * (members, then plain) name of its definition, so an id that drifted still finds its stack.
	 */
	public static Rs2ItemModel getBankItemForSavedId(int id)
	{
		if (id <= 0)
		{
			return null;
		}
		Rs2ItemModel direct = get(item -> item.getId() == id);
		if (direct != null)
		{
			return direct;
		}
		ItemComposition definition = MobileActions.read(() -> Microbot.getClient().getItemDefinition(id), null);
		if (definition == null)
		{
			return null;
		}
		int linked = definition.getLinkedNoteId();
		Rs2ItemModel alternative = linked > 0 && linked != id ? get(item -> item.getId() == linked) : null;
		if (alternative != null)
		{
			return alternative;
		}
		String membersName = definition.getMembersName();
		String lookup = membersName != null && !membersName.isEmpty() ? membersName : definition.getName();
		return lookup == null || lookup.isEmpty() ? null
			: getAll().filter(item -> lookup.equalsIgnoreCase(item.getName()) && item.getQuantity() >= 1).findAny().orElse(null);
	}

	/** As upstream: the withdraw mode is noted ({@code noted}) or as items. */
	public static boolean isWithdrawAs(boolean noted)
	{
		return (Microbot.getVarbitValue(VarbitID.BANK_WITHDRAWNOTES) == 1) == noted;
	}

	/** As upstream: the bank's item count text (top of the interface), or 0. */
	public static int getBankItemCount()
	{
		Widget count = Rs2Widget.getWidget(InterfaceID.Bankmain.OCCUPIEDSLOTS);
		String text = count == null ? null : MobileActions.read(count::getText, null);
		if (text == null)
		{
			return 0;
		}
		try
		{
			return Integer.parseInt(text.trim());
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	/** As upstream: the main tab (0) is open. */
	public static boolean isMainTabOpen()
	{
		return isTabOpen(0);
	}

	/** As upstream: tab {@code index}'s widget (tab bar child {@code index + 10}), or {@code null}. */
	public static Widget getTabWidget(int index)
	{
		List<Widget> tabs = getTabs();
		return index < 0 || index + 10 >= tabs.size() ? null : tabs.get(index + 10);
	}

	/** As upstream: bank-side inventory slot {@code slot} (0 to 27) is locked. */
	public static boolean isLockedSlot(int slot)
	{
		if (slot < 0 || slot >= 28)
		{
			return false;
		}
		return MobileActions.read(() ->
		{
			Widget container = Microbot.getClient().getWidget(net.runelite.api.widgets.ComponentID.BANK_INVENTORY_ITEM_CONTAINER);
			Widget[] children = container == null ? null : container.getChildren();
			return children != null && slot < children.length && isWidgetLocked(children[slot]);
		}, false);
	}

	/** As upstream: every locked bank-side inventory slot. */
	public static List<Integer> findLockedSlots()
	{
		return MobileActions.read(() ->
		{
			Widget container = Microbot.getClient().getWidget(net.runelite.api.widgets.ComponentID.BANK_INVENTORY_ITEM_CONTAINER);
			Widget[] children = container == null ? null : container.getChildren();
			List<Integer> locked = new java.util.ArrayList<>();
			for (int i = 0; children != null && i < children.length; i++)
			{
				if (isWidgetLocked(children[i]))
				{
					locked.add(i);
				}
			}
			return locked;
		}, java.util.Collections.emptyList());
	}

	/** Upstream's lock test: the slot offers "Unlock-slot". */
	private static boolean isWidgetLocked(Widget widget)
	{
		String[] actions = widget == null ? null : widget.getActions();
		return actions != null && Arrays.stream(actions).anyMatch("Unlock-slot"::equalsIgnoreCase);
	}
}
