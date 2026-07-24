package org.rsmod.content.interfaces.bank.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences

internal typealias pin_interfaces = BankPinInterfaces

internal typealias pin_components = BankPinComponents

object BankPinInterfaces : InterfaceReferences() {
    val keypad = find("bankpin_keypad")
    val settings = find("bankpin_settings")
}

object BankPinComponents : ComponentReferences() {
    val keypad_title = find("bankpin_keypad:title")
    val keypad_instruction = find("bankpin_keypad:instruction")
    val keypad_digit1 = find("bankpin_keypad:digit1")
    val keypad_digit2 = find("bankpin_keypad:digit2")
    val keypad_digit3 = find("bankpin_keypad:digit3")
    val keypad_digit4 = find("bankpin_keypad:digit4")
    val keypad_cancel = find("bankpin_keypad:cancel")
    val keypad_forgotten = find("bankpin_keypad:forgotten")

    val keypad_button_a = find("bankpin_keypad:a")
    val keypad_button_b = find("bankpin_keypad:b")
    val keypad_button_c = find("bankpin_keypad:c")
    val keypad_button_d = find("bankpin_keypad:d")
    val keypad_button_e = find("bankpin_keypad:e")
    val keypad_button_f = find("bankpin_keypad:f")
    val keypad_button_g = find("bankpin_keypad:g")
    val keypad_button_h = find("bankpin_keypad:h")
    val keypad_button_i = find("bankpin_keypad:i")
    val keypad_button_j = find("bankpin_keypad:j")

    val settings_status = find("bankpin_settings:statusoutput")
    val settings_message = find("bankpin_settings:message")
    val settings_set = find("bankpin_settings:set")
    val settings_change = find("bankpin_settings:change")
    val settings_delete = find("bankpin_settings:delete")
    val settings_cancel = find("bankpin_settings:cancel")
}
