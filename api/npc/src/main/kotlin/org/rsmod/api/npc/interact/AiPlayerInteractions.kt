package org.rsmod.api.npc.interact

import jakarta.inject.Inject
import org.rsmod.api.npc.events.interact.AiPlayerDefaultEvents
import org.rsmod.api.npc.events.interact.AiPlayerEvents
import org.rsmod.api.npc.events.interact.ApEvent
import org.rsmod.api.npc.events.interact.OpEvent
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.interact.InteractionOp
import org.rsmod.game.interact.InteractionPlayerOp
import org.rsmod.game.movement.RouteRequestPathingEntity

public class AiPlayerInteractions
@Inject
constructor(private val eventBus: EventBus, private val playerList: PlayerList) {
    public fun interactOp(npc: Npc, target: Player, op: InteractionOp) {
        val opTrigger = hasOpTrigger(npc, target, op)
        val interaction =
            InteractionPlayerOp(
                target = target,
                op = op,
                hasOpTrigger = opTrigger,
                hasApTrigger = false,
            )
        val routeRequest = RouteRequestPathingEntity(target.avatar)
        npc.interaction = interaction
        npc.routeRequest = routeRequest
    }

    public fun interactAp(npc: Npc, target: Player, op: InteractionOp) {
        val apRange = npc.visType.attackRange
        val interaction =
            InteractionPlayerOp(
                target = target,
                op = op,
                hasOpTrigger = false,
                hasApTrigger = true,
                startApRange = apRange,
            )
        val routeRequest = RouteRequestPathingEntity(target.avatar)
        npc.interaction = interaction
        npc.routeRequest = routeRequest
    }

    public fun opTrigger(npc: Npc, target: Player, op: InteractionOp): OpEvent? {
        val event = target.toOp(npc, op)
        if (eventBus.contains(event::class.java, event.id)) {
            return event
        }

        val defaultEvent = target.toDefaultOp(op)
        if (eventBus.contains(defaultEvent::class.java, defaultEvent.id)) {
            return defaultEvent
        }

        return null
    }

    public fun hasOpTrigger(npc: Npc, target: Player, op: InteractionOp): Boolean =
        opTrigger(npc, target, op) != null

    public fun apTrigger(npc: Npc, target: Player, op: InteractionOp): ApEvent? {
        val typeEvent = target.toAp(npc, op)
        if (eventBus.contains(typeEvent::class.java, typeEvent.id)) {
            return typeEvent
        }

        val defaultEvent = target.toDefaultAp(op)
        if (eventBus.contains(defaultEvent::class.java, defaultEvent.id)) {
            return defaultEvent
        }

        return null
    }

    private fun Player.toOp(npc: Npc, op: InteractionOp): AiPlayerEvents.Op =
        when (op) {
            InteractionOp.Op1 -> AiPlayerEvents.Op1(this, npc)
            InteractionOp.Op2 -> AiPlayerEvents.Op2(this, npc)
            InteractionOp.Op3 -> AiPlayerEvents.Op3(this, npc)
            InteractionOp.Op4 -> AiPlayerEvents.Op4(this, npc)
            InteractionOp.Op5 -> AiPlayerEvents.Op5(this, npc)
            InteractionOp.Op6 -> AiPlayerEvents.Op6(this, npc)
            InteractionOp.Op7 -> AiPlayerEvents.Op7(this, npc)
            InteractionOp.Op8 -> AiPlayerEvents.Op8(this, npc)
        }

    private fun Player.toAp(npc: Npc, op: InteractionOp): AiPlayerEvents.Ap =
        when (op) {
            InteractionOp.Op1 -> AiPlayerEvents.Ap1(this, npc)
            InteractionOp.Op2 -> AiPlayerEvents.Ap2(this, npc)
            InteractionOp.Op3 -> AiPlayerEvents.Ap3(this, npc)
            InteractionOp.Op4 -> AiPlayerEvents.Ap4(this, npc)
            InteractionOp.Op5 -> AiPlayerEvents.Ap5(this, npc)
            InteractionOp.Op6 -> AiPlayerEvents.Ap6(this, npc)
            InteractionOp.Op7 -> AiPlayerEvents.Ap7(this, npc)
            InteractionOp.Op8 -> AiPlayerEvents.Ap8(this, npc)
        }

    private fun Player.toDefaultOp(op: InteractionOp): AiPlayerDefaultEvents.Op =
        when (op) {
            InteractionOp.Op1 -> AiPlayerDefaultEvents.Op1(this)
            InteractionOp.Op2 -> AiPlayerDefaultEvents.Op2(this)
            InteractionOp.Op3 -> AiPlayerDefaultEvents.Op3(this)
            InteractionOp.Op4 -> AiPlayerDefaultEvents.Op4(this)
            InteractionOp.Op5 -> AiPlayerDefaultEvents.Op5(this)
            InteractionOp.Op6 -> AiPlayerDefaultEvents.Op6(this)
            InteractionOp.Op7 -> AiPlayerDefaultEvents.Op7(this)
            InteractionOp.Op8 -> AiPlayerDefaultEvents.Op8(this)
        }

    private fun Player.toDefaultAp(op: InteractionOp): AiPlayerDefaultEvents.Ap =
        when (op) {
            InteractionOp.Op1 -> AiPlayerDefaultEvents.Ap1(this)
            InteractionOp.Op2 -> AiPlayerDefaultEvents.Ap2(this)
            InteractionOp.Op3 -> AiPlayerDefaultEvents.Ap3(this)
            InteractionOp.Op4 -> AiPlayerDefaultEvents.Ap4(this)
            InteractionOp.Op5 -> AiPlayerDefaultEvents.Ap5(this)
            InteractionOp.Op6 -> AiPlayerDefaultEvents.Ap6(this)
            InteractionOp.Op7 -> AiPlayerDefaultEvents.Ap7(this)
            InteractionOp.Op8 -> AiPlayerDefaultEvents.Ap8(this)
        }

    public fun resolvePlayer(uid: PlayerUid?): Player? {
        return uid?.resolve(playerList)
    }
}
