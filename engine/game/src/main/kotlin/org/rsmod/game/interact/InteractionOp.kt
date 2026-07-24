package org.rsmod.game.interact

public enum class InteractionOp(public val slot: Int) {
    Op1(1),
    Op2(2),
    Op3(3),
    Op4(4),
    Op5(5),
    /* Op6-Op8 are only valid for player targets; locs, npcs, and objs cap out at five ops. */
    Op6(6),
    Op7(7),
    Op8(8),
}
