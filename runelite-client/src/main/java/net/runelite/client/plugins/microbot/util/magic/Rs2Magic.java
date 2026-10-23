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
}
