package org.rsmod.api.world.list

import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks the number of players logged in to this world.
 *
 * The count is maintained on the game thread by [WorldPopulationScript] and read by
 * [WorldListService] when it reports this world's heartbeat.
 */
public class WorldPopulation {
    private val count = AtomicInteger(0)

    public val current: Int
        get() = count.get()

    internal fun increment() {
        count.incrementAndGet()
    }

    internal fun decrement() {
        count.updateAndGet { (it - 1).coerceAtLeast(0) }
    }
}
