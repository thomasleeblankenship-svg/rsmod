package org.rsmod.api.world.list

/** The outcome of selecting a world to switch to with [WorldList.selectHopTarget]. */
public sealed class WorldHopTarget {
    /** The world can be switched to. [host] is the address the client should connect to. */
    public data class Valid(val world: WorldListEntry, val host: String) : WorldHopTarget()

    /** The target is the world the player is already in. */
    public data object CurrentWorld : WorldHopTarget()

    /** No world with the requested id is registered. */
    public data object NotFound : WorldHopTarget()

    /** The world is registered but has not sent a recent heartbeat. */
    public data object Offline : WorldHopTarget()

    /** The world is online but has no host configured, so clients cannot connect to it. */
    public data object NoHost : WorldHopTarget()
}

/**
 * Selects [targetWorld] as the destination of a world switch from [currentWorld].
 *
 * @see [WorldHopTarget]
 */
public fun WorldList.selectHopTarget(currentWorld: Int, targetWorld: Int): WorldHopTarget {
    if (targetWorld == currentWorld) {
        return WorldHopTarget.CurrentWorld
    }
    val world = this[targetWorld] ?: return WorldHopTarget.NotFound
    if (!world.online) {
        return WorldHopTarget.Offline
    }
    val host = world.host ?: return WorldHopTarget.NoHost
    return WorldHopTarget.Valid(world, host)
}
