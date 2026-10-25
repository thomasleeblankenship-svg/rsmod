package net.runelite.client.plugins.microbot.jewelleryenchant;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.jewelleryenchant.util.ElementalStaff;
import net.runelite.client.plugins.microbot.jewelleryenchant.util.Jewellery;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Enchants jewellery with the standard spellbook's Enchant spells; mobile port notes in {@code MOBILE_PORT.md}. */
public class JewelleryEnchantScript extends Script {
    /** Failed casts in a row before the script gives up and turns the plugin off. */
    private static final int MAX_CAST_FAILURES = 5;

    private int castFailures;

    public boolean run(JewelleryEnchantConfig config) {
        castFailures = 0;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;

                Jewellery jewellery = config.jewellery();

                if (!jewellery.hasRequiredLevel()) {
                    stopPlugin("Magic level too low.");
                    return;
                }

                boolean hasRunes = hasRequiredRunes(jewellery);
                boolean hasJewellery = Rs2Inventory.hasItem(jewellery.getUnenchantedId());

                if (!hasRunes || !hasJewellery) {
                    bankAndRestock(jewellery);
                    return;
                }

                if (Rs2Bank.isOpen()) {
                    Rs2Bank.closeBank();
                    sleepUntil(() -> !Rs2Bank.isOpen());
                    return;
                }

                // Mobile: click the jewellery only once the spell is selected, and on the inventory tab.
                boolean cast = Rs2Magic.cast(jewellery)
                        && Rs2Tab.switchToInventoryTab()
                        && sleepUntil(() -> Rs2Inventory.interact(jewellery.getUnenchantedId(), "Use"), 2_000);
                if (cast) {
                    castFailures = 0;
                } else if (++castFailures >= MAX_CAST_FAILURES) {
                    stopPlugin("Jewellery Enchant stopped: " + jewellery.getName() + " could not be enchanted.");
                }

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
                // Mobile: stop rather than retry a failing step.
                stopPlugin("Jewellery Enchant stopped after an error: " + ex);
            }
        }, 0, 2000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void bankAndRestock(Jewellery jewellery) {
        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            return;
        }

        Rs2Bank.depositAllExcept(item -> item.getId() == Runes.COSMIC.getItemId());
        sleep(300, 500);

        handleStaffEquipping(jewellery);
        if (ensureCorrectRunes(jewellery)) {
            handleJewelleryWithdrawal(jewellery);
        }
    }

    /** Stops with {@code message}, turning the plugin off (not just this loop) so the Microbot Mobile screen shows it. */
    private void stopPlugin(String message) {
        Microbot.showMessage(message);
        shutdown();
        Microbot.stopPlugin(JewelleryEnchantPlugin.class);
    }

    private void handleStaffEquipping(Jewellery jewellery) {
        Runes elementalRune = jewellery.getElementalRune();
        if (isWearingElementalStaffFor(elementalRune)) {
            return;
        }

        int staffToWithdraw = -1;
        for (ElementalStaff staff : ElementalStaff.values()) {
            if (staff.providesRune(elementalRune) && Rs2Bank.hasItem(staff.getItemId())) {
                staffToWithdraw = staff.getItemId();
                break;
            }
        }

        if (staffToWithdraw != -1) {
            Rs2Bank.withdrawAndEquip(staffToWithdraw);
            sleepUntil(() -> isWearingElementalStaffFor(elementalRune), 3000);

            for (ElementalStaff staff : ElementalStaff.values()) {
                if (Rs2Inventory.hasItem(staff.getItemId())) {
                    // Check if the staff in inventory is the one we just equipped. If so, don't deposit it.
                    if (Rs2Equipment.isWearing(staff.getItemId())) continue;

                    Rs2Bank.depositAll(staff.getItemId());
                    sleep(200, 300);
                }
            }
        }
    }

    private boolean isWearingElementalStaffFor(Runes rune) {
        for (ElementalStaff staff : ElementalStaff.values()) {
            if (Rs2Equipment.isWearing(staff.getItemId()) && staff.providesRune(rune)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRequiredRunes(Jewellery jewellery) {
        if (!Rs2Inventory.hasItem(Runes.COSMIC.getItemId())) {
            return false;
        }
        for (Map.Entry<Runes, Integer> entry : jewellery.getRequiredRunes().entrySet()) {
            Runes rune = entry.getKey();
            int amount = entry.getValue();
            if (rune == Runes.COSMIC) continue;
            if (!isWearingElementalStaffFor(rune) && !Rs2Inventory.hasItemAmount(rune.getItemId(), amount)) {
                return false;
            }
        }
        return true;
    }

    /** Withdraws the runes one cast needs; false when the bank is out and the plugin was turned off. */
    private boolean ensureCorrectRunes(Jewellery jewellery) {
        if (!Rs2Inventory.hasItem(Runes.COSMIC.getItemId())) {
            if (Rs2Bank.hasItem(Runes.COSMIC.getItemId())) {
                Rs2Bank.withdrawAll(Runes.COSMIC.getItemId());
                sleepUntil(() -> Rs2Inventory.hasItem(Runes.COSMIC.getItemId()));
            } else {
                stopPlugin("Out of Cosmic runes!");
                return false;
            }
        }
        for (Map.Entry<Runes, Integer> entry : jewellery.getRequiredRunes().entrySet()) {
            Runes rune = entry.getKey();
            int amount = entry.getValue();
            if (rune == Runes.COSMIC) continue;
            boolean needsRuneInInventory = !isWearingElementalStaffFor(rune);
            if (needsRuneInInventory && !Rs2Inventory.hasItemAmount(rune.getItemId(), amount)) {
                if (Rs2Bank.hasBankItem(rune.getItemId(), amount)) {
                    Rs2Bank.withdrawX(rune.getItemId(), amount);
                    sleep(200, 400);
                } else {
                    stopPlugin("Out of " + rune.name() + " runes!");
                    return false;
                }
            }
        }
        return true;
    }

    private void handleJewelleryWithdrawal(Jewellery jewellery) {
        if (!Rs2Bank.hasItem(jewellery.getUnenchantedId())) {
            stopPlugin("You have no " + jewellery.getName() + " left in your bank.");
            return;
        }
        Rs2Bank.withdrawAll(jewellery.getUnenchantedId());
        sleepUntil(() -> Rs2Inventory.hasItem(jewellery.getUnenchantedId()));
    }
}