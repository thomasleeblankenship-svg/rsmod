package net.runelite.client.plugins.microbot.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
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
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
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
 *     <li>Not ported: bank slot locks, bank-mirror epochs and the "bank items not in setup"
 *     helpers.</li>
 *     <li>Pre-potting waits for each inventory change instead of upstream's random sleeps.</li>
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

	// ---- pre-potting ----------------------------------------------------------------------

	private static final Pattern DOSE = Pattern.compile("\\((\\d)\\)");

	/** Upstream's boosting potions for {@link #prePot()}: ranging, magic and combat (strongest first), prayer regeneration, stamina. */
	private static List<String> boostingPotionNames()
	{
		List<String> names = new ArrayList<>();
		for (List<String> variants : List.of(Rs2Potion.getRangePotionsVariants(), Rs2Potion.getMagicPotionsVariants(),
			Rs2Potion.getCombatPotionsVariants()))
		{
			List<String> strongestFirst = new ArrayList<>(variants);
			Collections.reverse(strongestFirst);
			names.addAll(strongestFirst);
		}
		names.add(Rs2Potion.getPrayerRegenerationPotion());
		names.add(Rs2Potion.getStaminaPotion());
		return names.stream().map(n -> n.toLowerCase(Locale.ROOT)).collect(Collectors.toList());
	}

	/** As upstream: {@link #prePot(List)} with the standard boosting potions. */
	public boolean prePot()
	{
		return prePot(boostingPotionNames());
	}

	/**
	 * As upstream: with the bank open, drinks each listed potion that the setup's additional items
	 * name (a carried dose first, else one withdrawn from the bank, whose leftover dose or vial is
	 * then deposited), skipping effects already active; uses the setup's chugging barrel instead
	 * when it lists one; heals from the bank's food when below full health; eats an anglerfish when
	 * the setup has one; and, when the inventory was full, stores up to three items first and
	 * withdraws them again after.
	 * <p>
	 * Mobile adaptation: each drink, eat, withdraw and deposit is one dispatch followed by a bounded
	 * wait for the inventory to change, in place of upstream's random sleeps.
	 */
	public boolean prePot(List<String> potionsToPrePot)
	{
		List<InventorySetupsItem> additionalItems = getAdditionalItems();
		if (additionalItems.isEmpty())
		{
			log.warn("[InventorySetup] no additional items to pre-pot");
			return false;
		}

		List<Rs2ItemModel> storedItems = new ArrayList<>();
		if (Rs2Inventory.isFull())
		{
			List<Rs2ItemModel> toStore = Rs2Inventory.items().sorted(Comparator.comparing(Rs2ItemModel::isStackable))
				.limit(3).collect(Collectors.toList());
			for (Rs2ItemModel item : toStore)
			{
				storedItems.add(item);
				changesInventory(() -> Rs2Bank.depositOne(item.getId()));
			}
		}

		boolean hasChugBarrel = additionalItems.stream().anyMatch(item -> item.getId() == ItemID.MM_PREPOT_DEVICE);
		if (hasChugBarrel)
		{
			if (!useChugBarrel())
			{
				return false;
			}
		}
		else
		{
			List<String> setupNames = additionalItems.stream().map(InventorySetupsItem::getName).filter(Objects::nonNull)
				.collect(Collectors.toList());
			List<String> valid = potionsToPrePot.stream()
				.filter(pot -> setupNames.stream().anyMatch(name -> name.equalsIgnoreCase(pot)))
				.collect(Collectors.toList());
			for (Rs2ItemModel potion : findBoostingPotions(valid))
			{
				if (isMainSchedulerCancelled() || !MobileActions.active())
				{
					return false;
				}
				if (!isPotionEffectActive(potion.getName().toLowerCase(Locale.ROOT)))
				{
					drinkPrePot(potion);
				}
			}
		}

		if (Rs2Player.getHealthPercentage() < 100)
		{
			healFromBank(additionalItems);
		}
		boolean hasAnglerfish = additionalItems.stream().anyMatch(item -> item.getId() == ItemID.ANGLERFISH);
		if (hasAnglerfish && Rs2Player.getHealthPercentage() <= 100 && Rs2Bank.getBankItem(ItemID.ANGLERFISH) != null
			&& changesInventory(() -> Rs2Bank.withdrawOne(ItemID.ANGLERFISH)))
		{
			changesInventory(() -> Rs2Inventory.interact(ItemID.ANGLERFISH, "Eat"));
		}

		for (Rs2ItemModel storedItem : storedItems)
		{
			if (isMainSchedulerCancelled() || !MobileActions.active())
			{
				break;
			}
			if (Rs2Inventory.isFull())
			{
				log.warn("[InventorySetup] inventory full; cannot restore all stored items");
				return false;
			}
			changesInventory(() -> storedItem.isStackable() && storedItem.getQuantity() > 1
				? Rs2Bank.withdrawX(storedItem.getId(), storedItem.getQuantity())
				: Rs2Bank.withdrawItem(storedItem.getId()));
		}
		return true;
	}

	/** Drinks one dose of {@code potion}: carried, else withdrawn and its leftover deposited again. */
	private static void drinkPrePot(Rs2ItemModel potion)
	{
		boolean fromInventory = Rs2Inventory.hasItem(potion.getId());
		if (!fromInventory && !changesInventory(() -> Rs2Bank.withdrawOne(potion.getId())))
		{
			return;
		}
		if (!changesInventory(() -> Rs2Inventory.interact(potion.getId(), "Drink")) || fromInventory)
		{
			return;
		}
		Matcher matcher = DOSE.matcher(potion.getName());
		int resultingDose = matcher.find() ? Integer.parseInt(matcher.group(1)) - 1 : -1;
		if (resultingDose > 0)
		{
			String resultingName = DOSE.matcher(potion.getName()).replaceAll("(" + resultingDose + ")");
			Rs2Inventory.items(item -> resultingName.equalsIgnoreCase(item.getName())).findFirst()
				.ifPresent(left -> changesInventory(() -> Rs2Bank.depositOne(left.getId())));
		}
		else if (Rs2Inventory.hasItem(ItemID.VIAL_EMPTY))
		{
			changesInventory(() -> Rs2Bank.depositAll(ItemID.VIAL_EMPTY));
		}
	}

	/** As upstream: withdraws the chugging barrel, drinks from it and deposits it again. */
	private static boolean useChugBarrel()
	{
		if (!Rs2Bank.hasItem(ItemID.MM_PREPOT_DEVICE))
		{
			log.warn("[InventorySetup] chug barrel in setup but not in bank");
			return false;
		}
		if (changesInventory(() -> Rs2Bank.withdrawItem(ItemID.MM_PREPOT_DEVICE)))
		{
			Rs2Inventory.interact(ItemID.MM_PREPOT_DEVICE, "Drink");
			changesInventory(() -> Rs2Bank.depositOne(ItemID.MM_PREPOT_DEVICE));
		}
		return true;
	}

	/** As upstream: banked potions whose name contains a listed name, lowest dose first. */
	private static List<Rs2ItemModel> findBoostingPotions(List<String> potionsToPrePot)
	{
		return Rs2Bank.bankItems().stream()
			.filter(item -> item.getName() != null && potionsToPrePot.stream()
				.anyMatch(name -> item.getName().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))))
			.sorted(Comparator.comparingInt(item ->
			{
				Matcher matcher = DOSE.matcher(item.getName());
				return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
			}))
			.collect(Collectors.toList());
	}

	/** As upstream: whether this potion's effect is already running (ranged, magic, divine combat, prayer regeneration, stamina). */
	private static boolean isPotionEffectActive(String potionName)
	{
		if (matchesAny(potionName, Rs2Potion.getRangePotionsVariants())
			&& (Rs2Player.hasRangingPotionActive(3) || Rs2Player.hasDivineRangedActive()))
		{
			return true;
		}
		if (matchesAny(potionName, Rs2Potion.getMagicPotionsVariants())
			&& (Rs2Player.hasMagicActive(3) || Rs2Player.hasDivineMagicActive()))
		{
			return true;
		}
		if (matchesAny(potionName, Rs2Potion.getCombatPotionsVariants()) && Rs2Player.hasDivineCombatActive())
		{
			return true;
		}
		if (potionName.contains(Rs2Potion.getPrayerRegenerationPotion().toLowerCase(Locale.ROOT))
			&& Rs2Player.hasPrayerRegenerationActive())
		{
			return true;
		}
		return potionName.contains(Rs2Potion.getStaminaPotion().toLowerCase(Locale.ROOT)) && Rs2Player.hasStaminaActive();
	}

	private static boolean matchesAny(String potionName, List<String> variants)
	{
		return variants.stream().map(v -> v.toLowerCase(Locale.ROOT)).anyMatch(potionName::contains);
	}

	/**
	 * As upstream: eats one food from the bank: the setup's own (not karambwan or anglerfish) when
	 * the bank has it, else the bank's highest-healing food.
	 */
	private static void healFromBank(List<InventorySetupsItem> additionalItems)
	{
		Set<String> excluded = Set.of("karambwan", "anglerfish");
		List<Rs2ItemModel> bank = Rs2Bank.bankItems();
		Optional<Rs2Food> fromSetup = additionalItems.stream().map(InventorySetupsItem::getName).filter(Objects::nonNull)
			.map(n -> n.toLowerCase(Locale.ROOT))
			.flatMap(name -> Arrays.stream(Rs2Food.values()).filter(food ->
			{
				String foodName = food.getName().toLowerCase(Locale.ROOT);
				return !excluded.contains(foodName) && name.contains(foodName);
			}))
			.findFirst();
		Rs2ItemModel food = fromSetup.flatMap(f -> bank.stream().filter(item -> item.getId() == f.getId()).findFirst())
			.orElseGet(() -> bank.stream().filter(item -> healOf(item.getId()) > 0)
				.max(Comparator.comparingInt(item -> healOf(item.getId()))).orElse(null));
		if (food == null)
		{
			log.warn("[InventorySetup] no healing food found in bank");
			return;
		}
		if (changesInventory(() -> Rs2Bank.withdrawOne(food.getId())))
		{
			changesInventory(() -> Rs2Inventory.interact(food.getId(), "Eat"));
		}
	}

	private static int healOf(int itemId)
	{
		return Arrays.stream(Rs2Food.values()).filter(f -> f.getId() == itemId).mapToInt(Rs2Food::getHeal).findFirst().orElse(0);
	}

	/** Runs one dispatch, then waits (bounded) for the inventory to change. True when both happened. */
	private static boolean changesInventory(BooleanSupplier action)
	{
		boolean[] sent = new boolean[1];
		boolean changed = Rs2Inventory.waitForInventoryChanges(() -> sent[0] = action.getAsBoolean(), 0, 1_800);
		return sent[0] && changed;
	}
}
