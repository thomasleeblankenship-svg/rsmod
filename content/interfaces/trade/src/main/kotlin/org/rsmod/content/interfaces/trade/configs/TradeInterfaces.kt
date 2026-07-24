package org.rsmod.content.interfaces.trade.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences

internal typealias trade_interfaces = TradeInterfaces

internal typealias trade_components = TradeComponents

object TradeInterfaces : InterfaceReferences() {
    val trade_main = find("trademain")
    val trade_side = find("tradeside")
    val trade_confirm = find("tradeconfirm")
}

object TradeComponents : ComponentReferences() {
    val your_offer = find("trademain:your_offer")
    val other_offer = find("trademain:other_offer")
    val accept = find("trademain:accept")
    val decline = find("trademain:decline")
    val status = find("trademain:status")
    val title = find("trademain:title")

    val side_inventory = find("tradeside:side_layer")

    val confirm_accept = find("tradeconfirm:trade2accept")
    val confirm_decline = find("tradeconfirm:trade2decline")
    val confirm_opponent = find("tradeconfirm:tradeopponent")
}
