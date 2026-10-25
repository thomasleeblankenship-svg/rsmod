package org.rsmod.content.other.worldhop

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.logout.LogoutTransfer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.world.list.WorldList
import org.rsmod.api.world.list.WorldListEntry
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player

class WorldHopScriptTest {
    @Test
    fun GameTestState.`hop to online world transfers client and logs out`() =
        runInjectedGameTest(HopDependencies::class, HopTestModule, WorldHopScript::class) { deps ->
            val target = deps.config.world + 1
            deps.worldList.update(
                listOf(world(deps.config.world), world(target, host = "10.0.0.2", flags = 1))
            )

            deps.cheat(player, "hop", target.toString())

            val transfer = client.outgoingMessages.filterIsInstance<LogoutTransfer>().single()
            assertEquals("10.0.0.2", transfer.host)
            assertEquals(target, transfer.id)
            assertEquals(1, transfer.properties)

            advance(ticks = 1)
            assertTrue(player.loggingOut)
        }

    @Test
    fun GameTestState.`hop is rejected while logout is prevented`() =
        runInjectedGameTest(HopDependencies::class, HopTestModule, WorldHopScript::class) { deps ->
            val target = deps.config.world + 1
            deps.worldList.update(listOf(world(deps.config.world), world(target)))
            player.preventLogoutUntil = mapClock.cycle + 10
            player.preventLogoutMessage = "You can't log out until 10 seconds after combat."

            deps.cheat(player, "hop", target.toString())

            assertMessageSent("You can't log out until 10 seconds after combat.")
            assertTrue(client.outgoingMessages.none { it is LogoutTransfer })

            advance(ticks = 1)
            assertFalse(player.loggingOut)
        }

    @Test
    fun GameTestState.`hop to offline world is rejected`() =
        runInjectedGameTest(HopDependencies::class, HopTestModule, WorldHopScript::class) { deps ->
            val target = deps.config.world + 1
            deps.worldList.update(listOf(world(deps.config.world), world(target, online = false)))

            deps.cheat(player, "hop", target.toString())

            assertMessageSent("World $target is offline.")
            assertTrue(client.outgoingMessages.none { it is LogoutTransfer })
        }

    private fun world(
        id: Int,
        host: String? = "127.0.0.1",
        flags: Int = 0,
        online: Boolean = true,
    ): WorldListEntry =
        WorldListEntry(
            id = id,
            host = host,
            activity = null,
            flags = flags,
            location = 0,
            population = 0,
            online = online,
        )

    private class HopDependencies
    @Inject
    constructor(
        val worldList: WorldList,
        val config: ServerConfig,
        private val cheats: CheatCommandMap,
    ) {
        fun cheat(player: Player, command: String, vararg args: String) {
            cheats.execute(player, command, args.toList())
        }
    }

    private object HopTestModule : AbstractModule() {
        override fun configure() {
            bind(WorldList::class.java).`in`(Scopes.SINGLETON)
        }
    }
}
