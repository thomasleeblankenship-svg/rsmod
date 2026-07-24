package org.rsmod.content.interfaces.trade

import jakarta.inject.Inject
import org.rsmod.api.config.refs.invs
import org.rsmod.api.invtx.invMoveAll
import org.rsmod.api.player.output.UpdateInventory
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.startInvTransmit
import org.rsmod.api.player.stopInvTransmit
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.script.advanced.onOpPlayer4
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.interfaces.trade.configs.trade_components
import org.rsmod.content.interfaces.trade.configs.trade_interfaces
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Player-to-player trading.
 *
 * The flow follows the standard two-screen model: both players build an offer on the first screen
 * (`trademain`), and once both accept, a confirmation screen (`tradeconfirm`) is shown. The
 * exchange only completes after both players accept the confirmation screen; declining or closing
 * at any point returns all offered items to their owners.
 */
class TradeScript
@Inject
constructor(
    private val eventBus: EventBus,
    private val invTypes: InvTypeList,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {
    /** Pending trade requests: requester uid -> requested target uid. */
    private val requests = hashMapOf<PlayerUid, PlayerUid>()

    /** Active trade sessions keyed by participant uid. */
    private val sessions = hashMapOf<PlayerUid, TradeSession>()

    private val Player.tradeOffer: Inventory
        get() = invMap.getOrPut(invTypes[invs.tradeoffer])

    override fun ScriptContext.startup() {
        onOpPlayer4 { attemptTrade(it.target) }

        onIfModalButton(trade_components.side_inventory) { offerOp(it.comsub, it.op) }
        onIfModalButton(trade_components.your_offer) { removeOp(it.comsub, it.op) }
        onIfModalButton(trade_components.accept) { acceptOffer() }
        onIfModalButton(trade_components.decline) { declineTrade() }
        onIfModalButton(trade_components.confirm_accept) { acceptConfirm() }
        onIfModalButton(trade_components.confirm_decline) { declineTrade() }

        onIfClose(trade_interfaces.trade_main) { player.onTradeScreenClose(Stage.Offer) }
        onIfClose(trade_interfaces.trade_confirm) { player.onTradeScreenClose(Stage.Confirm) }

        onPlayerLogout { player.onTradeLogout() }
    }

    /* Request handshake */

    private fun ProtectedAccess.attemptTrade(target: Player) {
        if (player.uid in sessions || target.uid in sessions) {
            mes("Other player is busy at the moment.")
            return
        }
        if (requests[target.uid] == player.uid) {
            openTrade(player, target)
            return
        }
        requests[player.uid] = target.uid
        mes("Sending trade offer...")
        target.mes("${player.displayName} wishes to trade with you.")
    }

    private fun openTrade(player: Player, partner: Player) {
        requests.remove(player.uid)
        requests.remove(partner.uid)

        val opened =
            protectedAccess.launch(partner) {
                openTradeScreen(partner, player)
                val selfOpened = protectedAccess.launch(player) { openTradeScreen(player, partner) }
                if (!selfOpened) {
                    cancelTrade(partner, returnItems = true)
                    return@launch
                }
                sessions[player.uid] = TradeSession(player, partner)
                sessions[partner.uid] = TradeSession(partner, player)
            }
        if (!opened) {
            player.mes("Other player is busy at the moment.")
        }
    }

    private fun ProtectedAccess.openTradeScreen(self: Player, partner: Player) {
        val offer = self.tradeOffer
        invClear(offer)
        self.startInvTransmit(offer)
        UpdateInventory.updateInvOtherFull(self, partner.tradeOffer)

        ifSetEvents(
            trade_components.side_inventory,
            self.inv.indices,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
            IfEvent.Op10,
        )
        ifSetEvents(
            trade_components.your_offer,
            offer.indices,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
            IfEvent.Op10,
        )
        ifSetText(trade_components.title, "Trading with: ${partner.displayName}")
        ifSetText(trade_components.status, "")
        ifOpenMainSidePair(trade_interfaces.trade_main, trade_interfaces.trade_side)
    }

    /* Offer building */

    private suspend fun ProtectedAccess.offerOp(slot: Int, op: IfButtonOp) {
        val session = sessions[player.uid] ?: return
        if (session.stage != Stage.Offer) {
            return
        }
        if (op == IfButtonOp.Op10) {
            objExamine(inv, slot)
            return
        }
        val obj = inv[slot] ?: return
        val count = resolveOpCount(op, invTotalCount(obj)) ?: return
        val transaction =
            invMoveFromSlot(
                from = inv,
                into = player.tradeOffer,
                fromSlot = slot,
                count = count,
                strict = false,
            )
        if (transaction.success) {
            session.onOfferModified()
        }
    }

    private suspend fun ProtectedAccess.removeOp(slot: Int, op: IfButtonOp) {
        val session = sessions[player.uid] ?: return
        if (session.stage != Stage.Offer) {
            return
        }
        val offer = player.tradeOffer
        if (op == IfButtonOp.Op10) {
            objExamine(offer, slot)
            return
        }
        val obj = offer[slot] ?: return
        val count = resolveOpCount(op, invTotalCount(obj, offer)) ?: return
        val transaction =
            invMoveFromSlot(
                from = offer,
                into = inv,
                fromSlot = slot,
                count = count,
                strict = false,
            )
        if (transaction.success) {
            session.onOfferModified()
        }
    }

    private suspend fun ProtectedAccess.resolveOpCount(op: IfButtonOp, all: Int): Int? =
        when (op) {
            IfButtonOp.Op1 -> 1
            IfButtonOp.Op2 -> 5
            IfButtonOp.Op3 -> 10
            IfButtonOp.Op4 -> all
            IfButtonOp.Op5 -> countDialog()
            else -> null
        }

    private fun ProtectedAccess.invTotalCount(
        obj: org.rsmod.game.inv.InvObj,
        inventory: Inventory = inv,
    ): Int = inventory.objs.filterNotNull().filter { it.id == obj.id }.sumOf { it.count }

    private fun TradeSession.onOfferModified() {
        val partnerSession = sessions[partner.uid] ?: return
        accepted = false
        partnerSession.accepted = false
        syncOffers(this)
        player.ifSetText(trade_components.status, "")
        partner.ifSetText(trade_components.status, "")
    }

    private fun syncOffers(session: TradeSession) {
        UpdateInventory.updateInvOtherFull(session.partner, session.player.tradeOffer)
        UpdateInventory.updateInvOtherFull(session.player, session.partner.tradeOffer)
    }

    /* Accept flow */

    private fun ProtectedAccess.acceptOffer() {
        val session = sessions[player.uid] ?: return
        if (session.stage != Stage.Offer) {
            return
        }
        val partnerSession = sessions[session.partner.uid] ?: return
        session.accepted = true
        if (!partnerSession.accepted) {
            ifSetText(trade_components.status, "Waiting for other player...")
            session.partner.ifSetText(trade_components.status, "Other player has accepted.")
            return
        }
        openConfirmation(session, partnerSession)
    }

    private fun openConfirmation(first: TradeSession, second: TradeSession) {
        for (session in listOf(first, second)) {
            session.stage = Stage.Confirm
            session.accepted = false
        }
        for (session in listOf(first, second)) {
            val opened =
                protectedAccess.launch(session.player) {
                    ifSetText(
                        trade_components.confirm_opponent,
                        "Trading with:<br>${session.partner.displayName}",
                    )
                    ifOpenMain(trade_interfaces.trade_confirm)
                }
            if (!opened) {
                cancelTrade(session.player, returnItems = true)
                return
            }
        }
    }

    private fun ProtectedAccess.acceptConfirm() {
        val session = sessions[player.uid] ?: return
        if (session.stage != Stage.Confirm) {
            return
        }
        val partnerSession = sessions[session.partner.uid] ?: return
        session.accepted = true
        if (!partnerSession.accepted) {
            ifSetText(trade_components.status, "Waiting for other player...")
            return
        }
        completeTrade(session)
    }

    private fun ProtectedAccess.completeTrade(session: TradeSession) {
        val self = session.player
        val partner = session.partner
        val selfOffer = self.tradeOffer
        val partnerOffer = partner.tradeOffer

        if (partner.inv.freeSpace() < selfOffer.occupiedSpace()) {
            mes("Other player doesn't have enough inventory space for this trade.")
            partner.mes("You don't have enough inventory space for this trade.")
            session.accepted = false
            sessions[partner.uid]?.accepted = false
            return
        }
        if (self.inv.freeSpace() < partnerOffer.occupiedSpace()) {
            mes("You don't have enough inventory space for this trade.")
            partner.mes("Other player doesn't have enough inventory space for this trade.")
            session.accepted = false
            sessions[partner.uid]?.accepted = false
            return
        }

        endSession(self)
        endSession(partner)

        invMoveInv(from = selfOffer, into = partner.inv)
        invMoveInv(from = partnerOffer, into = self.inv)

        self.mes("Accepted trade.")
        partner.mes("Accepted trade.")

        closeTradeUi(self)
        closeTradeUi(partner)
    }

    /* Cancellation */

    private fun ProtectedAccess.declineTrade() {
        val session = sessions[player.uid] ?: return
        session.partner.mes("Other player declined trade.")
        cancelTrade(player, returnItems = true)
    }

    private fun Player.onTradeScreenClose(stage: Stage) {
        val session = sessions[uid] ?: return
        // Transitioning from the offer screen to the confirmation screen closes `trade_main`;
        // that close event must not cancel the trade.
        if (session.stage != stage) {
            return
        }
        session.partner.mes("Other player declined trade.")
        cancelTrade(this, returnItems = true)
    }

    private fun Player.onTradeLogout() {
        requests.remove(uid)
        val session = sessions[uid] ?: return
        session.partner.mes("Other player declined trade.")
        cancelTrade(this, returnItems = true)
    }

    private fun cancelTrade(player: Player, returnItems: Boolean) {
        val session = sessions[player.uid]
        endSession(player)
        if (returnItems) {
            player.returnOffer()
        }
        closeTradeUi(player)

        val partner = session?.partner ?: return
        if (sessions.containsKey(partner.uid)) {
            endSession(partner)
            partner.returnOffer()
            closeTradeUi(partner)
        }
    }

    private fun endSession(player: Player) {
        sessions.remove(player.uid)
    }

    private fun Player.returnOffer() {
        val offer = tradeOffer
        if (offer.occupiedSpace() == 0) {
            return
        }
        val returned =
            protectedAccess.launch(this) { invMoveInv(from = player.tradeOffer, into = inv) }
        if (!returned) {
            // The player could not be granted protected access (e.g., mid-logout); move the
            // items back directly so they are never lost.
            invMoveAll(from = offer, into = inv)
        }
    }

    private fun closeTradeUi(player: Player) {
        UpdateInventory.updateInvOtherStopTransmit(player, player.tradeOffer)
        player.stopInvTransmit(player.tradeOffer)
        player.ifClose(eventBus)
    }

    private class TradeSession(val player: Player, val partner: Player) {
        var stage: Stage = Stage.Offer
        var accepted: Boolean = false
    }

    private enum class Stage {
        Offer,
        Confirm,
    }
}
