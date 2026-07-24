package org.rsmod.api.specials.shield

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj

/**
 * A special attack activated through a worn shield (e.g., dragonfire shield) rather than the
 * wielded weapon.
 *
 * Shield specials are enabled by operating the shield item itself, which sets the player's special
 * attack type to `Shield`. They do not reserve or consume standard special attack energy; any cost
 * (such as item charges) is the responsibility of the implementation.
 *
 * If you intend to cancel the combat interaction, you **must** do so explicitly; otherwise, the
 * interaction may linger.
 */
public interface ShieldSpecialAttack {
    /** Executes this special attack against an [Npc] target. */
    public suspend fun ProtectedAccess.attack(target: Npc, shield: InvObj)

    /** Executes this special attack against a [Player] target. */
    public suspend fun ProtectedAccess.attack(target: Player, shield: InvObj)
}
