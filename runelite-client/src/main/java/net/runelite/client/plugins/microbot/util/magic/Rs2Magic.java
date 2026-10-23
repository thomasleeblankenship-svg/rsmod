/*
 * Copied from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/magic/Rs2Magic.java.
 * BSD 2-Clause License; see LICENSE. Mobile adaptation: only the read methods (canCast(Spell),
 * spellbook, autocast, Shadow Veil, thrall, staff/tome lookups and rune counting). Casting,
 * alching, superheat, NPC contact, pouch repair and the widget-based canCast/quickCanCast belong
 * with the actions.
 */
package net.runelite.client.plugins.microbot.util.magic;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.magic.thralls.Rs2Thrall;

/** Magic reads: spellbook, autocast, rune availability and requirement checks. */
public class Rs2Magic {
    private static final RuneFilter DEFAULT_RUNE_FILTER = RuneFilter.builder().build();

    /**
     * Determines if the player can cast the given spell by verifying the
     * player's active spellbook and the availability of the required runes.
     *
     * @param spell the spell that needs to be checked for rune availability
     * @return true if the player has the correct spellbook, high enough level and enough runes to cast the spell
     */
    public static boolean canCast(Spell spell) {
        return spell.hasRequirements() && hasRequiredRunes(spell);
    }

    public static Rs2Spellbook getSpellbook() {
        return Rs2Spellbook.getCurrentSpellbook();
    }

    public static boolean isSpellbook(Rs2Spellbook spellbook) {
        return getSpellbook() == spellbook;
    }

    public static boolean isShadowVeilActive() {
        return Microbot.getVarbitValue(VarbitID.ARCEUUS_SHADOW_VEIL_ACTIVE) == 1;
    }

    public static boolean isThrallActive() {
        return Rs2Thrall.isActive();
    }

    private static final int ANCIENT_VARBIT_OFFSET = 10;
    /**
     * Gets the currently selected auto-cast spell based on varbit 276.
     *
     * @return the matching Rs2CombatSpells or null if none matches.
     */
    public static Rs2CombatSpells getCurrentAutoCastSpell() {
        final int autoCastSpell = Microbot.getVarbitValue(VarbitID.AUTOCAST_SPELL) - (isSpellbook(Rs2Spellbook.ANCIENT) ? ANCIENT_VARBIT_OFFSET : 0);
        return Arrays.stream(Rs2CombatSpells.values())
                .filter(spell -> spell.getVarbitValue() == autoCastSpell)
                .findAny().orElse(null);
    }
    
    public static Rs2Spells getRs2Spell(String spellName) {
        return Arrays.stream(Rs2Spells.values())
                .filter(spell -> spell.getName().toLowerCase().contains(spellName.toLowerCase()))
                .findFirst().orElse(null);
    }
    
    public static Rs2Staff getRs2Staff(int itemID) {
        return Rs2Staff.byItemId(itemID);
    }

    public static Rs2Tome getRs2Tome(int itemID) {
        return Rs2Tome.byItemId(itemID);
    }

    public static List<Rs2Staff> findStavesByRunes(List<Runes> runes) {
        return Stream.of(Rs2Staff.values())
                .filter(staff -> staff.getRunes().containsAll(runes))
                .collect(Collectors.toList());
    }

    private static int limitSum(int i, int j) {
        try {
            return Math.addExact(i, j);
        } catch (ArithmeticException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static Map<Runes, Integer> addInventoryRunes(Map<Runes, Integer> runes) {
        for (Rs2ItemModel item : Rs2Inventory.all()) {
            Runes rune = Runes.byItemId(item.getId());
            if (rune != null) {
                runes.merge(rune, item.getQuantity(), Rs2Magic::limitSum);
            }
        }
        return runes;
    }

    private static Map<Runes, Integer> addEquipmentRunes(Map<Runes, Integer> runes) {
        final Rs2ItemModel equippedWeapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
        if (equippedWeapon != null) {
            Rs2Staff equippedStaff = getRs2Staff(equippedWeapon.getId());
            if (equippedStaff != Rs2Staff.NONE) {
                equippedStaff.getRunes().forEach(rune -> runes.put(rune, Integer.MAX_VALUE));
            }
        }

        // Remove runes provided by equipped tomes
        final Rs2ItemModel equippedShield = Rs2Equipment.get(EquipmentInventorySlot.SHIELD);
        if (equippedShield != null) {
            Rs2Tome equippedTome = getRs2Tome(equippedShield.getId());
            if (equippedTome != Rs2Tome.NONE) {
                equippedTome.getRunes().forEach(rune -> runes.put(rune, Integer.MAX_VALUE));
            }
        }
        return runes;
    }

    private static Map<Runes, Integer> addBankRunes(Map<Runes, Integer> runes) {
        Rs2Bank.bankItems().stream()
                .flatMap(item -> Arrays.stream(Runes.values())
                        .filter(r -> r.getItemId() == item.getId())
                        .map(rune -> Map.entry(rune, item.getQuantity())))
                .forEach(entry -> runes.merge(entry.getKey(), entry.getValue(), Rs2Magic::limitSum));
        return runes;
    }

    private static Map<Runes, Integer> addComboRunes(Map<Runes, Integer> runes) {
        final Map<Runes, Integer> baseContributions = new HashMap<>();
        runes.forEach((rune, quantity) -> {
            for (Runes baseRune : rune.getBaseRunes()) {
                baseContributions.merge(baseRune, quantity, Rs2Magic::limitSum);
            }
        });
        baseContributions.forEach((baseRune, quantity) ->
                runes.merge(baseRune, quantity, Rs2Magic::limitSum));
        return runes;
    }

    /**
     * Calculates the available runes
     *
     * @param runeFilter which Inventories to search for rune
     * @return A {@link Map} where the key is {@link ItemID} of the rune,
     * and the value is an {@code Integer} representing the quantity of that rune available
     * or {@code Integer.MAX_VALUE} if the rune is provided by equipment.
     */
    public static Map<Runes, Integer> getRunes(RuneFilter runeFilter) {
        final Map<Runes, Integer> availableRunes = runeFilter.isIncludeRunePouch() && Rs2Inventory.hasRunePouch() ?
                Rs2RunePouch.getRunes() : new HashMap<>();

        if (runeFilter.isIncludeInventory()) addInventoryRunes(availableRunes);
        if (runeFilter.isIncludeEquipment()) addEquipmentRunes(availableRunes);
        if (runeFilter.isIncludeBank()) addBankRunes(availableRunes);
        if (runeFilter.isIncludeComboRunes()) addComboRunes(availableRunes);

        return availableRunes;
    }

    /**
     * Calculates the available runes
     *
     * @return A {@link Map} where the key is {@link ItemID} of the rune,
     * and the value is an {@code Integer} representing the quantity of that rune available
     * or {@code Integer.MAX_VALUE} if the rune is provided by equipment.
     */
    public static Map<Runes, Integer> getRunes() {
        return getRunes(DEFAULT_RUNE_FILTER);
    }

    @Deprecated(since = "Use getMissingRunes")
    public static Map<Runes, Integer> getRequiredRunes(Spell spell, int casts, boolean checkRunePouch) {
        final RuneFilter filter = RuneFilter.builder().includeRunePouch(checkRunePouch).build();
        return getMissingRunes(spell, casts, filter);
    }

    /**
     * Calculates the required runes to cast {@code spell} for {@code casts}
     * amount of times.
     *
     * @return A {@link Map} where the key is {@link ItemID} of the rune,
     * and the value is an {@link Integer} representing the quantity of that runes required
     */
    public static Map<Runes, Integer> getRequiredRunes(Spell spell, int casts) {
        return spell.getRequiredRunes(casts);
    }

    public static Map<Runes, Integer> getRequiredRunes(Spell spell) {
        return spell.getRequiredRunes();
    }

    /**
     * Calculates how many runes we are missing in Inventory, Equipment, Rune Pouch & Bank
     * to meet the Rune Requirements {@code reqRunes}
     *
     * @param reqRunes the Rune Requirements (ItemID -> requiredQuantity)
     * @param runeFilter which Inventories to search for runes
     * @return A {@link Map} where the key is {@link ItemID} of the rune, and the
     * value is an {@link Integer} representing the quantity of missing runes.
     */
    public static Map<Runes, Integer> getMissingRunes(Map<Runes, Integer> reqRunes, RuneFilter runeFilter) {
        if (reqRunes.isEmpty()) return reqRunes;

        final Map<Runes, Integer> runes = getRunes(runeFilter);
        final Map<Runes, Integer> diff = new HashMap<>(reqRunes);
        diff.replaceAll((key, value) -> Math.max(0, value - runes.getOrDefault(key, 0)));
        diff.keySet().removeIf(e -> diff.get(e) <= 0);

        return diff;
    }

    public static Map<Runes, Integer> getMissingRunes(Map<Runes, Integer> reqRunes) {
        return getMissingRunes(reqRunes, DEFAULT_RUNE_FILTER);
    }

    /**
     * Mobile adaptation: upstream credits blighted ice sacks for Ice spells in the Wilderness; that
     * PvP check is not ported, so the full rune cost is always required.
     */
    public static Map<Runes, Integer> getMissingRunes(Spell spell, int casts, RuneFilter runeFilter) {
        return getMissingRunes(getRequiredRunes(spell, Math.max(0, casts)), runeFilter);
    }

    public static Map<Runes, Integer> getMissingRunes(Spell spell, int casts) {
        return getMissingRunes(spell, casts, DEFAULT_RUNE_FILTER);
    }

    public static Map<Runes, Integer> getMissingRunes(Spell spell) {
        return getMissingRunes(spell, 1);
    }

    /**
     * Checks if the player has the required runes to cast a specified spell.
     *
     * @param reqRunes the Rune Requirements (ItemID -> requiredQuantity)
     * @param runeFilter which Inventories to search for runes
     * @return true if all required runes are available; false otherwise.
     */
    public static boolean hasRequiredRunes(Map<Runes, Integer> reqRunes, RuneFilter runeFilter) {
        return getMissingRunes(reqRunes, runeFilter).isEmpty();
    }

    public static boolean hasRequiredRunes(Map<Runes, Integer> reqRunes) {
        return hasRequiredRunes(reqRunes, DEFAULT_RUNE_FILTER);
    }

    public static boolean hasRequiredRunes(Spell spell, int casts, RuneFilter runeFilter) {
        return getMissingRunes(spell, casts, runeFilter).isEmpty();
    }

    public static boolean hasRequiredRunes(Spell spell, int casts) {
        return hasRequiredRunes(spell, casts, DEFAULT_RUNE_FILTER);
    }

    public static boolean hasRequiredRunes(Spell spell, RuneFilter runeFilter) {
        return hasRequiredRunes(spell, 1, runeFilter);
    }

    public static boolean hasRequiredRunes(Spell spell) {
        return hasRequiredRunes(spell, 1);
    }

    // ---- Casting (Claude 1). Mobile: each step is one MobileActions script action for the bound
    // session, and waits are on observed state (spell selected, inventory tab open), so upstream's
    // sleepMin/sleepMax pauses are accepted and ignored. Not ported: the jewellery-enchant
    // submenu, NPC Contact and pouch repair. ----

    /** Spells cast with their own option (teleports, Bones to ..., spells listing "Cast"); others take a target. */
    private static boolean castsDirectly(MagicAction spell) {
        String name = spell.getName().toLowerCase(java.util.Locale.ROOT);
        return name.contains("teleport") || name.contains("bones to")
                || (spell.getActions() != null && Arrays.stream(spell.getActions()).anyMatch(a -> a != null && a.equalsIgnoreCase("cast")));
    }

    /** Spell definitions that list this action, for the rune and level check. */
    private static Rs2Spells spellFor(MagicAction action) {
        return Arrays.stream(Rs2Spells.values()).filter(spell -> spell.getMagicAction() == action).findFirst().orElse(null);
    }

    /**
     * As upstream: the spellbook is right, the level and runes suffice (when a spell definition
     * exists for it), and the spellbook widget is present. Opens the magic tab.
     */
    public static boolean canCast(MagicAction spell) {
        if (spell == null || !isSpellbook(spell.getSpellbook())) return false;
        Rs2Spells definition = spellFor(spell);
        if (definition != null && !canCast(definition)) return false;
        if (!net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.switchTo(net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.MAGIC)) return false;
        int widgetId = spell.getWidgetId();
        return widgetId != -1 && net.runelite.client.plugins.microbot.mobile.MobileActions.read(() -> {
            net.runelite.api.widgets.Widget widget = Microbot.getClient().getWidget(widgetId);
            return widget != null && !widget.isHidden();
        }, false);
    }

    public static boolean cast(Spell spell) {
        return spell != null && cast(spell.getMagicAction());
    }

    public static boolean cast(Spell spell, String option, int identifier) {
        return spell != null && cast(spell.getMagicAction(), option, identifier);
    }

    public static boolean cast(MagicAction spell) {
        return cast(spell, "Cast", 1);
    }

    /**
     * As upstream: casts a direct spell, or selects a target spell (true once it is selected; the
     * caller then picks the target).
     */
    public static boolean cast(MagicAction spell, String option, int identifier) {
        if (!canCast(spell)) {
            Microbot.log("Unable to cast " + (spell == null ? "null" : spell.getName()));
            return false;
        }
        int widgetId = spell.getWidgetId();
        if (castsDirectly(spell)) {
            return Global.sleepUntil(() -> net.runelite.client.plugins.microbot.mobile.MobileActions.widget(widgetId, -1, option)
                    || net.runelite.client.plugins.microbot.mobile.MobileActions.widget(widgetId, -1, ""), 2_000);
        }
        return Global.sleepUntil(() -> net.runelite.client.plugins.microbot.mobile.MobileActions.selectSpell(widgetId, option, identifier), 2_000)
                && Global.sleepUntil(() -> net.runelite.client.plugins.microbot.mobile.MobileActions.read(() -> Microbot.getClient().isWidgetSelected(), false), 2_000);
    }

    public static boolean quickCast(Spell spell) {
        return cast(spell);
    }

    /**
     * As upstream: casts the spellbook entry with this displayed name (for spells without a
     * {@link MagicAction}, such as other spellbooks' home teleports). The widget's presence is the
     * capability check; cooldowns and the spellbook are the caller's (the pathfinder's) business.
     */
    public static boolean quickCast(String spellName) {
        if (spellName == null || spellName.isBlank()
                || !net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.switchTo(net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.MAGIC)) {
            return false;
        }
        String wanted = spellName.trim();
        Integer widgetId = net.runelite.client.plugins.microbot.mobile.MobileActions.read(() -> {
            net.runelite.api.widgets.Widget book = Microbot.getClient().getWidget(SPELLBOOK_GROUP, 0);
            net.runelite.api.widgets.Widget spell = book == null ? null : findSpell(book, wanted);
            return spell == null ? null : spell.getId();
        }, null);
        return widgetId != null && Global.sleepUntil(() -> net.runelite.client.plugins.microbot.mobile.MobileActions.widget(widgetId, -1, "Cast")
                || net.runelite.client.plugins.microbot.mobile.MobileActions.widget(widgetId, -1, ""), 2_000);
    }

    /** The spellbook interface group. */
    private static final int SPELLBOOK_GROUP = 218;

    static net.runelite.api.widgets.Widget findSpell(net.runelite.api.widgets.Widget widget, String name) {
        if (widget == null || widget.isHidden()) return null;
        if (name.equalsIgnoreCase(net.runelite.client.util.Text.removeTags(String.valueOf(widget.getName())).trim())) return widget;
        net.runelite.api.widgets.Widget[] children = widget.getStaticChildren();
        if (children == null) return null;
        for (net.runelite.api.widgets.Widget child : children) {
            net.runelite.api.widgets.Widget found = findSpell(child, name);
            if (found != null) return found;
        }
        return null;
    }

    /** Mobile: the same as {@link #cast(MagicAction)}; upstream clicks the widget directly. */
    public static boolean quickCast(MagicAction spell) {
        return cast(spell);
    }

    public static boolean castOn(Spell spell, net.runelite.api.Actor actor) {
        return spell != null && castOn(spell.getMagicAction(), actor);
    }

    /** As upstream: selects {@code spell} and applies it to an NPC or another player. */
    public static boolean castOn(MagicAction spell, net.runelite.api.Actor actor) {
        if (actor == null || !cast(spell)) return false;
        if (actor instanceof net.runelite.api.NPC) {
            int index = ((net.runelite.api.NPC) actor).getIndex();
            return Global.sleepUntil(() -> net.runelite.client.plugins.microbot.mobile.MobileActions.spellOnNpc(index), 2_000);
        }
        if (actor instanceof net.runelite.api.Player) {
            int id = ((net.runelite.api.Player) actor).getId();
            return Global.sleepUntil(() -> net.runelite.client.plugins.microbot.mobile.MobileActions.spellOnPlayer(id), 2_000);
        }
        return false;
    }

    /** As upstream: High Level Alchemy from 55 Magic, else Low Level Alchemy. */
    public static void alch(Rs2ItemModel item) {
        alch(item, 0, 0);
    }

    public static void alch(Rs2ItemModel item, int sleepMin, int sleepMax) {
        int level = net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
                Microbot.getClient().getRealSkillLevel(net.runelite.api.Skill.MAGIC), 0);
        castOnItem(level >= 55 ? MagicAction.HIGH_LEVEL_ALCHEMY : MagicAction.LOW_LEVEL_ALCHEMY, item);
    }

    public static void alch(String itemName) {
        alch(Rs2Inventory.get(itemName));
    }

    public static void alch(String itemName, int sleepMin, int sleepMax) {
        alch(Rs2Inventory.get(itemName), sleepMin, sleepMax);
    }

    public static void superHeat(Rs2ItemModel item) {
        castOnItem(MagicAction.SUPERHEAT_ITEM, item);
    }

    public static void superHeat(Rs2ItemModel item, int sleepMin, int sleepMax) {
        superHeat(item);
    }

    public static void superHeat(String itemName) {
        superHeat(Rs2Inventory.get(itemName));
    }

    public static void superHeat(String itemName, int sleepMin, int sleepMax) {
        superHeat(itemName);
    }

    public static void superHeat(int id) {
        superHeat(Rs2Inventory.get(id));
    }

    public static void superHeat(int id, int sleepMin, int sleepMax) {
        superHeat(id);
    }

    /** Selects {@code spell}, waits for the inventory tab the game opens, then targets {@code item}. */
    static boolean castOnItem(MagicAction spell, Rs2ItemModel item) {
        if (item == null || !cast(spell)) return false;
        net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.switchTo(net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.INVENTORY);
        return Global.sleepUntil(() -> net.runelite.client.plugins.microbot.mobile.MobileActions.inventory(item.getSlot(), item.getId(), "Use"), 2_000);
    }

    /**
     * Upstream configures the spellbook's filter settings once. Mobile: always true; a spell hidden
     * by the filters makes {@link #canCast(MagicAction)} false instead.
     */
    public static boolean oneTimeSpellBookCheck() {
        return true;
    }
}
