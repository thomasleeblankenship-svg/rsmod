package org.rsmod.content.interfaces.bank.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.config.refs.varps
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.interfaces.bank.configs.pin_components
import org.rsmod.content.interfaces.bank.configs.pin_interfaces
import org.rsmod.content.interfaces.bank.openBank
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Bank PIN entry and management.
 *
 * The PIN is stored in the persistent `bankpin` varp as `pin + 1` (so `0` means "no PIN set" and a
 * PIN of `0000` remains representable). A successful keypad entry marks the account as verified for
 * the remainder of the session.
 *
 * Note: The keypad button-to-digit mapping (buttons `a`-`j` mapping to digits `0`-`9` in order)
 * follows the interface component order and has not yet been verified against the official client's
 * shuffled keypad layout. If digits register incorrectly in-game, [keypadDigit] is the single place
 * to adjust.
 */
class BankPinScript
@Inject
constructor(private val eventBus: EventBus, private val protectedAccess: ProtectedAccessLauncher) :
    PluginScript() {
    /** Stored pin, offset by one; `0` means no pin is set. */
    private var Player.pinStore by intVarp(varps.bankpin)

    private val entries = hashMapOf<PlayerUid, PinEntry>()
    private val verified = hashSetOf<PlayerUid>()

    private val keypadButtons: List<ComponentType> =
        listOf(
            pin_components.keypad_button_a,
            pin_components.keypad_button_b,
            pin_components.keypad_button_c,
            pin_components.keypad_button_d,
            pin_components.keypad_button_e,
            pin_components.keypad_button_f,
            pin_components.keypad_button_g,
            pin_components.keypad_button_h,
            pin_components.keypad_button_i,
            pin_components.keypad_button_j,
        )

    override fun ScriptContext.startup() {
        onIfOpen(interfaces.bank_main) { player.onBankOpened() }

        for ((digit, component) in keypadButtons.withIndex()) {
            onIfModalButton(component) { enterDigit(keypadDigit(digit)) }
        }
        onIfModalButton(pin_components.keypad_cancel) { cancelKeypad() }
        onIfModalButton(pin_components.keypad_forgotten) { forgotPin() }

        onIfModalButton(pin_components.settings_set) { startPinEntry(Mode.SetFirst) }
        onIfModalButton(pin_components.settings_change) { startPinEntry(Mode.VerifyThenChange) }
        onIfModalButton(pin_components.settings_delete) { deletePin() }
        onIfModalButton(pin_components.settings_cancel) { ifClose() }

        onCommand("bankpin") {
            desc = "Open bank PIN settings"
            cheat { protectedAccess.launch(player) { openPinSettings() } }
        }

        onPlayerLogout {
            entries.remove(player.uid)
            verified.remove(player.uid)
        }
    }

    /**
     * The digit represented by the keypad button at [buttonIndex] (buttons `a`-`j`). Kept as a
     * dedicated function so the mapping can be corrected in one place once verified against the
     * official client.
     */
    private fun keypadDigit(buttonIndex: Int): Int = buttonIndex

    /* Bank gating */

    private fun Player.onBankOpened() {
        if (pinStore == 0 || uid in verified) {
            return
        }
        // A pin is set and has not been verified this session: replace the bank with the keypad.
        protectedAccess.launch(this) {
            ifClose()
            startPinEntry(Mode.VerifyThenOpenBank)
        }
    }

    /* Keypad flow */

    private fun ProtectedAccess.startPinEntry(mode: Mode) {
        entries[player.uid] = PinEntry(mode)
        openKeypad()
    }

    private fun ProtectedAccess.openKeypad() {
        val entry = entries[player.uid] ?: return
        ifOpenMainModal(pin_interfaces.keypad)
        ifSetText(
            pin_components.keypad_title,
            when (entry.mode) {
                Mode.SetFirst -> "Choose a new PIN."
                Mode.SetConfirm -> "Confirm your new PIN."
                else -> "Bank of Gielinor"
            },
        )
        refreshKeypad(entry)
    }

    private fun ProtectedAccess.refreshKeypad(entry: PinEntry) {
        val ordinal = arrayOf("FIRST", "SECOND", "THIRD", "FOURTH")
        val index = entry.digits.size
        if (index < 4) {
            ifSetText(pin_components.keypad_instruction, "Click the ${ordinal[index]} digit.")
        }
        val digitComponents =
            arrayOf(
                pin_components.keypad_digit1,
                pin_components.keypad_digit2,
                pin_components.keypad_digit3,
                pin_components.keypad_digit4,
            )
        for ((slot, component) in digitComponents.withIndex()) {
            ifSetText(component, if (slot < entry.digits.size) "*" else "?")
        }
    }

    private fun ProtectedAccess.enterDigit(digit: Int) {
        val entry = entries[player.uid] ?: return
        entry.digits += digit
        if (entry.digits.size < 4) {
            refreshKeypad(entry)
            return
        }
        val value = entry.digits.fold(0) { acc, d -> acc * 10 + d }
        entry.digits.clear()
        onPinEntered(entry, value)
    }

    private fun ProtectedAccess.onPinEntered(entry: PinEntry, value: Int) {
        when (entry.mode) {
            Mode.VerifyThenOpenBank,
            Mode.VerifyThenChange -> {
                if (value + 1 != player.pinStore) {
                    entry.attempts++
                    if (entry.attempts >= MAX_ATTEMPTS) {
                        entries.remove(player.uid)
                        ifClose()
                        mes("You have entered an incorrect PIN too many times.")
                        return
                    }
                    mes("The PIN you entered was incorrect. Please try again.")
                    refreshKeypad(entry)
                    return
                }
                verified += player.uid
                if (entry.mode == Mode.VerifyThenChange) {
                    entry.mode = Mode.SetFirst
                    openKeypad()
                    return
                }
                entries.remove(player.uid)
                mes("You have correctly entered your PIN.")
                player.openBank(eventBus)
            }
            Mode.SetFirst -> {
                entry.pending = value
                entry.mode = Mode.SetConfirm
                openKeypad()
            }
            Mode.SetConfirm -> {
                entries.remove(player.uid)
                if (value != entry.pending) {
                    ifClose()
                    mes("The two PINs you entered did not match. Your PIN has not been changed.")
                    return
                }
                player.pinStore = value + 1
                verified += player.uid
                ifClose()
                mes("Your bank PIN has been set.")
            }
        }
    }

    private fun ProtectedAccess.cancelKeypad() {
        entries.remove(player.uid)
        ifClose()
    }

    private suspend fun ProtectedAccess.forgotPin() {
        entries.remove(player.uid)
        ifClose()
        mesbox("Contact a member of staff to recover a forgotten PIN.")
    }

    /* Settings screen */

    private fun ProtectedAccess.openPinSettings() {
        ifOpenMainModal(pin_interfaces.settings)
        val hasPin = player.pinStore != 0
        ifSetText(
            pin_components.settings_status,
            if (hasPin) "You have a PIN set on your bank account." else "No PIN is set.",
        )
    }

    private fun ProtectedAccess.deletePin() {
        if (player.pinStore == 0) {
            mes("You do not have a PIN set.")
            return
        }
        if (player.uid !in verified) {
            mes("You must verify your current PIN before deleting it.")
            return
        }
        player.pinStore = 0
        ifClose()
        mes("Your bank PIN has been deleted.")
    }

    private class PinEntry(var mode: Mode) {
        val digits = mutableListOf<Int>()
        var pending: Int = -1
        var attempts: Int = 0
    }

    private enum class Mode {
        VerifyThenOpenBank,
        VerifyThenChange,
        SetFirst,
        SetConfirm,
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
