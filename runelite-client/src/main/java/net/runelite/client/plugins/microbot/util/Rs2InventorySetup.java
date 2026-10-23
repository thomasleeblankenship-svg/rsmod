package net.runelite.client.plugins.microbot.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Loads a saved inventory setup from the bank, and checks inventory and equipment against it.
 * <p>
 * Mobile adaptation of chsami/Microbot {@code util/Rs2InventorySetup.java} at
 * {@code a2114401bbbc35e9762db9e8aa666d70ffcd24d8} (BSD 2-Clause), keeping its public API
 * (constructors, {@code loadInventory}, {@code loadEquipment}, {@code wearEquipment},
 * {@code doesInventoryMatch}, {@code doesEquipmentMatch}, {@code hasSpellBook}, the item getters,
 * {@code isInventorySetup}, {@link MissingSupplyPolicy}). Every bank and equip action goes through
 * the mobile {@code Rs2Bank}/{@code Rs2Inventory} actions, which observe each transfer.
 * <p>
 * Differences from upstream:
 * <ul>
 *     <li>Inventory slot order is ignored: items are counted by id (by name for fuzzy rows) and
 *     never rearranged, because mobile actions do not drag items between slots.</li>
 *     <li>A quantity matches when the inventory holds at least the setup's amount.</li>
 *     <li>The rune pouch is checked (each rune at least 20% of the setup, as upstream) but not
 *     filled; a pouch that does not match makes {@link #loadInventory()} return false.</li>
 *     <li>Not ported: bank slot locks, bank-mirror epochs, pre-potting and the
 *     "bank items not in setup" helpers.</li>
 * </ul>
 */
@Slf4j
public class Rs2InventorySetup
{
	/** As upstream: what happens when the bank lacks a required item. */
	public enum MissingSupplyPolicy { PAUSE_ALL_SCRIPTS, LOGOUT_AND_STOP }

	/** Upstream's spellbook value meaning "any spellbook". */
	private static final int SPELLBOOK_ANY = 4;
	/** Noted items' definitions point to this template (upstream {@code Rs2ItemModel}). */
	private static final int NOTE_TEMPLATE = 799;

	private final InventorySetup inventorySetup;
	private final ScheduledFuture<?> mainScheduler;
	private MissingSupplyPolicy missingSupplyPolicy = MissingSupplyPolicy.PAUSE_ALL_SCRIPTS;
	private Consumer<String> missingSupplyHandler;

	/** As upstream: the named setup (case-insensitive), else one named "default". */
	public Rs2InventorySetup(String name, ScheduledFuture<?> mainScheduler)
	{
		List<InventorySetup> setups = MInventorySetupsPlugin.getInventorySetups();
		this.inventorySetup = setups.stream().filter(Objects::nonNull).filter(x -> x.getName().equalsIgnoreCase(name)).findFirst()
			.orElse(setups.stream().filter(Objects::nonNull).filter(x -> "default".equals(x.getName())).findFirst().orElse(null));
		this.mainScheduler = mainScheduler;
	}

	/** As upstream: a missing setup shows a message and pauses all scripts. */
	public Rs2InventorySetup(InventorySetup setup, ScheduledFuture<?> mainScheduler)
	{
		this.inventorySetup = setup;
		this.mainScheduler = mainScheduler;
		if (setup == null)
		{
			Microbot.showMessage("Inventory load error!");
			Microbot.pauseAllScripts.compareAndSet(false, true);
		}
	}

	public static boolean isInventorySetup(String name)
	{
		return MInventorySetupsPlugin.getInventorySetups().stream().filter(Objects::nonNull)
			.anyMatch(x -> x.getName().equalsIgnoreCase(name));
	}

	public Rs2InventorySetup withMissingSupplyPolicy(MissingSupplyPolicy policy)
	{
		missingSupplyPolicy = Objects.requireNonNull(policy, "policy");
		return this;
	}

	/** As upstream: called with a description instead of applying the policy. */
	public Rs2InventorySetup withMissingSupplyHandler(Consumer<String> handler)
	{
		missingSupplyHandler = Objects.requireNonNull(handler, "handler");
		return this;
	}

	public boolean isMainSchedulerCancelled()
	{
		return mainScheduler != null && mainScheduler.isCancelled();
	}

	// ---- loading --------------------------------------------------------------------------

	public boolean loadInventory()
	{
		return loadInventory(false);
	}

	/**
	 * Opens the bank (when not open), deposits everything the setup does not list, then withdraws
	 * what is missing. True when {@link #doesInventoryMatch()} afterwards.
	 */
	public boolean loadInventory(boolean skipIfAlreadyMatching)
	{
		if (inventorySetup == null || inventorySetup.getInventory() == null) return false;
		if (skipIfAlreadyMatching && doesInventoryMatch()) return true;
		if (!Rs2Bank.isOpen() && !Rs2Bank.openBank()) return false;
		if (!Rs2Bank.depositAll(item -> !isInSetup(item, getInventoryItems()))) return false;
		for (Map.Entry<String, List<InventorySetupsItem>> group : groupedRows(getInventoryItems()).entrySet())
		{
			if (isMainSchedulerCancelled() || !MobileActions.active()) return false;
			InventorySetupsItem row = group.getValue().get(0);
			int missing = required(group.getValue()) - held(row);
			if (missing <= 0) continue;
			if (!withdraw(row, missing)) return false;
		}
		return doesInventoryMatch();
	}

	public boolean loadEquipment()
	{
		return loadEquipment(false);
	}

	/**
	 * Opens the bank (when not open), deposits worn items the setup does not list, then withdraws
	 * and equips each missing item. True when {@link #doesEquipmentMatch()} afterwards.
	 */
	public boolean loadEquipment(boolean skipIfAlreadyMatching)
	{
		if (inventorySetup == null || inventorySetup.getEquipment() == null) return false;
		if (skipIfAlreadyMatching && doesEquipmentMatch()) return true;
		if (!Rs2Bank.isOpen() && !Rs2Bank.openBank()) return false;
		List<InventorySetupsItem> rows = getEquipmentItems();
		boolean extraGear = Rs2Equipment.get(item -> !isInSetup(item, rows)) != null;
		if (extraGear && (!Rs2Bank.depositEquipment()
			|| !Global.sleepUntil(() -> Rs2Equipment.get(item -> !isInSetup(item, rows)) == null, 3_000)))
		{
			return false;
		}
		for (InventorySetupsItem row : rows)
		{
			if (isMainSchedulerCancelled() || !MobileActions.active()) return false;
			if (Rs2Equipment.isWearing(row.getName(), isExact(row))) continue;
			int amount = Math.max(1, row.getQuantity());
			if (held(row) < amount && !withdraw(row, amount - held(row))) return false;
			Rs2ItemModel item = firstHeld(row);
			if (item == null || !Rs2Bank.wearItem(item.getId())
				|| !Global.sleepUntil(() -> Rs2Equipment.isWearing(row.getName(), isExact(row)), 3_000))
			{
				return false;
			}
		}
		return doesEquipmentMatch();
	}

	/** As upstream: equips each setup equipment item carried in the inventory. */
	public boolean wearEquipment()
	{
		if (inventorySetup == null || inventorySetup.getEquipment() == null) return false;
		for (InventorySetupsItem row : getEquipmentItems())
		{
			if (!Rs2Equipment.isWearing(row.getName(), isExact(row)) && Rs2Inventory.hasItem(row.getId()))
			{
				Rs2Inventory.wield(row.getId());
				Global.sleepUntil(() -> Rs2Equipment.isWearing(row.getName(), isExact(row)), 3_000);
			}
		}
		return doesEquipmentMatch();
	}

	private boolean withdraw(InventorySetupsItem row, int amount)
	{
		int bankCount = Rs2Bank.count(row.getId());
		boolean noted = isNoted(row.getId());
		boolean modeSet = noted ? Rs2Bank.setWithdrawAsNote() : Rs2Bank.setWithdrawAsItem();
		if (!modeSet) return false;
		boolean withdrawn = row.isFuzzy() ? Rs2Bank.withdrawX(row.getName(), amount, false)
			: noted ? Rs2Bank.withdrawX(row.getName(), amount, true)
			: Rs2Bank.withdrawX(row.getId(), amount);
		if (!withdrawn)
		{
			missingSupply(String.format("%s x%d (bank has %d)", row.getName(), amount, bankCount));
		}
		return withdrawn;
	}

	private void missingSupply(String description)
	{
		String message = "[InventorySetup:" + inventorySetup.getName() + "] missing supply: " + description;
		log.warn(message);
		if (missingSupplyHandler != null)
		{
			missingSupplyHandler.accept(message);
		}
		else if (missingSupplyPolicy == MissingSupplyPolicy.LOGOUT_AND_STOP)
		{
			Rs2Player.logout();
		}
		else
		{
			Microbot.pauseAllScripts.compareAndSet(false, true);
		}
	}

	// ---- matching ---------------------------------------------------------------------------

	/** Each setup item is carried in at least its amount (slot order ignored); the rune pouch holds at least 20% of each rune. */
	public boolean doesInventoryMatch()
	{
		if (inventorySetup == null || inventorySetup.getInventory() == null) return false;
		for (List<InventorySetupsItem> group : groupedRows(getInventoryItems()).values())
		{
			InventorySetupsItem row = group.get(0);
			int need = required(group);
			int have = held(row);
			if (have < need)
			{
				log.info("[InventorySetup:{}] missing item: {} | inv={} | need={}", inventorySetup.getName(), row.getName(), have, need);
				return false;
			}
		}
		return runePouchMatches();
	}

	/** As upstream: every setup equipment item is worn (fuzzy rows and Barrows items by name). */
	public boolean doesEquipmentMatch()
	{
		if (inventorySetup == null || inventorySetup.getEquipment() == null) return false;
		for (InventorySetupsItem row : inventorySetup.getEquipment())
		{
			if (row == null || InventorySetupsItem.itemIsDummy(row)) continue;
			if (!Rs2Equipment.isWearing(row.getName(), isExact(row)))
			{
				log.info("[InventorySetup:{}] missing equipment: {}", inventorySetup.getName(), row.getName());
				return false;
			}
		}
		return true;
	}

	private boolean runePouchMatches()
	{
		List<InventorySetupsItem> pouch = inventorySetup.getRune_pouch();
		if (pouch == null || pouch.isEmpty()) return true;
		Map<Runes, Integer> carried = Rs2RunePouch.getRunes();
		for (InventorySetupsItem row : pouch)
		{
			if (row == null || row.getId() == -1 || row.getQuantity() <= 0) continue;
			Runes rune = Runes.byItemId(row.getId());
			int minimum = Math.max(1, (int) Math.ceil(row.getQuantity() * 0.2));
			if (rune != null && carried.getOrDefault(rune, 0) < minimum)
			{
				log.info("[InventorySetup:{}] rune pouch lacks {} (need {})", inventorySetup.getName(), rune, minimum);
				return false;
			}
		}
		return true;
	}

	// ---- setup contents ---------------------------------------------------------------------

	/** As upstream: inventory rows without dummies. */
	public List<InventorySetupsItem> getInventoryItems()
	{
		return rows(inventorySetup == null ? null : inventorySetup.getInventory());
	}

	/** As upstream: equipment rows without dummies. */
	public List<InventorySetupsItem> getEquipmentItems()
	{
		return rows(inventorySetup == null ? null : inventorySetup.getEquipment());
	}

	public List<InventorySetupsItem> getAdditionalItems()
	{
		if (inventorySetup == null || inventorySetup.getAdditionalFilteredItems() == null) return Collections.emptyList();
		return rows(List.copyOf(inventorySetup.getAdditionalFilteredItems().values()));
	}

	/** As upstream: names to keep when depositing (true means fuzzy), from inventory, equipment and additional rows. */
	public Map<String, Boolean> itemsToNotDeposit()
	{
		Map<String, Boolean> keep = new LinkedHashMap<>();
		for (List<InventorySetupsItem> list : List.of(getInventoryItems(), getEquipmentItems(), getAdditionalItems()))
		{
			for (InventorySetupsItem row : list)
			{
				keep.merge(row.getName(), row.isFuzzy(), Boolean::logicalOr);
			}
		}
		return keep;
	}

	/** As upstream: the active spellbook is the setup's (or the setup accepts any). */
	public boolean hasSpellBook()
	{
		if (inventorySetup == null) return false;
		int book = inventorySetup.getSpellBook();
		return book == SPELLBOOK_ANY || book == Microbot.getVarbitValue(VarbitID.SPELLBOOK);
	}

	public InventorySetup getInventorySetup()
	{
		return inventorySetup;
	}

	// ---- helpers ----------------------------------------------------------------------------

	private static List<InventorySetupsItem> rows(List<InventorySetupsItem> source)
	{
		if (source == null) return Collections.emptyList();
		return source.stream().filter(x -> x != null && x.getId() != -1).collect(Collectors.toList());
	}

	/** Rows grouped by id, or by lowercase name for fuzzy rows, in setup order. */
	private static Map<String, List<InventorySetupsItem>> groupedRows(List<InventorySetupsItem> rows)
	{
		return rows.stream().collect(Collectors.groupingBy(Rs2InventorySetup::key, LinkedHashMap::new, Collectors.toList()));
	}

	private static String key(InventorySetupsItem row)
	{
		return row.isFuzzy() ? "f:" + row.getName().toLowerCase(Locale.ROOT) : "i:" + row.getId();
	}

	/** One stack row needs its quantity; several rows of one item need one each (or their summed quantities). */
	private static int required(List<InventorySetupsItem> group)
	{
		if (group.size() == 1) return Math.max(1, group.get(0).getQuantity());
		return group.stream().mapToInt(row -> Math.max(1, row.getQuantity())).sum();
	}

	private static int held(InventorySetupsItem row)
	{
		return Rs2Inventory.itemQuantity(predicate(row));
	}

	private static Rs2ItemModel firstHeld(InventorySetupsItem row)
	{
		return Rs2Inventory.items(predicate(row)).findFirst().orElse(null);
	}

	private static Predicate<Rs2ItemModel> predicate(InventorySetupsItem row)
	{
		String name = row.getName().toLowerCase(Locale.ROOT);
		return row.isFuzzy()
			? item -> item.getName() != null && item.getName().toLowerCase(Locale.ROOT).contains(name)
			: item -> item.getId() == row.getId();
	}

	private static boolean isInSetup(Rs2ItemModel item, List<InventorySetupsItem> rows)
	{
		return rows.stream().anyMatch(row -> predicate(row).test(item));
	}

	private static boolean isExact(InventorySetupsItem row)
	{
		return !row.isFuzzy() && !InventorySetupsItem.isBarrowsItem(row.getName().toLowerCase(Locale.ROOT));
	}

	private static boolean isNoted(int itemId)
	{
		return MobileActions.read(() ->
		{
			ItemComposition definition = Microbot.getClient().getItemDefinition(itemId);
			return definition != null && definition.getNote() == NOTE_TEMPLATE;
		}, false);
	}
}
