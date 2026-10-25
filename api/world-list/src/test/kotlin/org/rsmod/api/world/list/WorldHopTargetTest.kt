package org.rsmod.api.world.list

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorldHopTargetTest {
    private val worldList =
        WorldList().apply {
            update(
                listOf(
                    world(id = 1),
                    world(id = 2),
                    world(id = 3, online = false),
                    world(id = 4, host = null),
                )
            )
        }

    @Test
    fun `online world with host is valid`() {
        val target = worldList.selectHopTarget(currentWorld = 1, targetWorld = 2)
        assertEquals(WorldHopTarget.Valid(world(id = 2), "127.0.0.1"), target)
    }

    @Test
    fun `current world is rejected`() {
        val target = worldList.selectHopTarget(currentWorld = 1, targetWorld = 1)
        assertEquals(WorldHopTarget.CurrentWorld, target)
    }

    @Test
    fun `unregistered world is not found`() {
        val target = worldList.selectHopTarget(currentWorld = 1, targetWorld = 5)
        assertEquals(WorldHopTarget.NotFound, target)
    }

    @Test
    fun `offline world is rejected`() {
        val target = worldList.selectHopTarget(currentWorld = 1, targetWorld = 3)
        assertEquals(WorldHopTarget.Offline, target)
    }

    @Test
    fun `world without host is rejected`() {
        val target = worldList.selectHopTarget(currentWorld = 1, targetWorld = 4)
        assertEquals(WorldHopTarget.NoHost, target)
    }

    private fun world(id: Int, online: Boolean = true, host: String? = "127.0.0.1") =
        WorldListEntry(
            id = id,
            host = host,
            activity = null,
            flags = 0,
            location = 0,
            population = 0,
            online = online,
        )
}
