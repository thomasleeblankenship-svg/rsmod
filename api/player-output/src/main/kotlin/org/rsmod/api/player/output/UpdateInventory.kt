package org.rsmod.api.player.output

import java.util.BitSet
import net.rsprot.protocol.common.game.outgoing.inv.InventoryObject
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import net.rsprot.protocol.game.outgoing.inv.UpdateInvPartial
import net.rsprot.protocol.game.outgoing.inv.UpdateInvStopTransmit
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory

public object UpdateInventory {
    /**
     * Inventory ids sent with this flag refer to "another player's" copy of the inventory. Used by
     * interfaces such as trade, where the client renders both the local player's offer and their
     * partner's offer from the same inventory type.
     */
    private const val OTHER_INV_FLAG: Int = 0x8000

    /** @see [UpdateInvFull] */
    public fun updateInvFull(player: Player, inv: Inventory) {
        val highestSlot = inv.lastOccupiedSlot()
        val provider = RspObjProvider(inv.objs)
        val message = UpdateInvFull(-(1234 + inv.type.id), inv.type.id, highestSlot, provider)
        player.client.write(message)
    }

    /**
     * Sends [inv] to [player] as an "other player's" inventory - i.e., the copy belonging to
     * another player, such as a trade partner's offer.
     *
     * @see [UpdateInvFull]
     */
    public fun updateInvOtherFull(player: Player, inv: Inventory) {
        val highestSlot = inv.lastOccupiedSlot()
        val provider = RspObjProvider(inv.objs)
        val flagged = inv.type.id or OTHER_INV_FLAG
        val message = UpdateInvFull(-(1234 + flagged), flagged, highestSlot, provider)
        player.client.write(message)
    }

    /**
     * Stops transmission of an "other player's" inventory previously sent through
     * [updateInvOtherFull].
     */
    public fun updateInvOtherStopTransmit(player: Player, inv: Inventory) {
        player.client.write(UpdateInvStopTransmit(inv.type.id or OTHER_INV_FLAG))
    }

    /** @see [UpdateInvPartial] */
    public fun updateInvPartial(player: Player, inv: Inventory) {
        val changedSlots = inv.modifiedSlots.asSequence().iterator()
        val provider = RspIndexedObjProvider(inv.objs, changedSlots)
        val message = UpdateInvPartial(-1, -(1234 + inv.type.id), inv.type.id, provider)
        player.client.write(message)
    }

    /** @see [UpdateInvStopTransmit] */
    public fun updateInvStopTransmit(player: Player, inv: Inventory) {
        player.client.write(UpdateInvStopTransmit(inv.type.id))
    }

    /**
     * Mostly used for emulation when re-syncing an inventory. [slot] is usually sent as value `0`.
     */
    public fun resendSlot(inv: Inventory, slot: Int) {
        inv.modifiedSlots.set(slot)
    }
}

private fun BitSet.asSequence(): Sequence<Int> = sequence {
    var index = nextSetBit(0)
    while (index >= 0) {
        yield(index)
        index = nextSetBit(index + 1)
    }
}

private class RspObjProvider(private val objs: Array<InvObj?>) : UpdateInvFull.ObjectProvider {
    override fun provide(slot: Int): Long {
        val obj = objs.getOrNull(slot) ?: return InventoryObject.NULL
        return InventoryObject(slot, obj.id, obj.count)
    }
}

private class RspIndexedObjProvider(private val objs: Array<InvObj?>, updateSlots: Iterator<Int>) :
    UpdateInvPartial.IndexedObjectProvider(updateSlots) {
    override fun provide(slot: Int): Long {
        val obj = objs.getOrNull(slot) ?: return InventoryObject(slot, -1, -1)
        return InventoryObject(slot, obj.id, obj.count)
    }
}
