package org.rsmod.content.other.worldhop

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.output.MiscOutput
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.api.world.list.WorldHopTarget
import org.rsmod.api.world.list.WorldList
import org.rsmod.api.world.list.WorldListEntry
import org.rsmod.api.world.list.selectHopTarget
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Lets players list the online worlds and switch to another world.
 *
 * Switching sends the client a logout transfer to the target world's host and logs the player out
 * of this world. The target world rejects the login with `HopBlocked` (which the client retries)
 * until this world has finished saving the character and released its world claim.
 */
class WorldHopScript
@Inject
constructor(
    private val worldList: WorldList,
    private val config: ServerConfig,
    private val protectedAccess: ProtectedAccessLauncher,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("worlds") {
            desc = "List the online worlds"
            cheat { listWorlds(player) }
        }
        onCommand("hop") {
            desc = "Switch to another world"
            invalidArgs = "Use as ::hop world (ex: ::hop 2)"
            cheat { hop(player, args[0].toInt()) }
        }
    }

    private fun listWorlds(player: Player) {
        val online = worldList.worlds.filter(WorldListEntry::online)
        if (online.isEmpty()) {
            player.mes("The world list is not available yet. Please try again shortly.")
            return
        }
        player.mes("Online worlds:")
        for (world in online) {
            val current = if (world.id == config.world) " (current)" else ""
            val activity = world.activity?.let { " - $it" } ?: ""
            player.mes("  World ${world.id}: ${world.population} players$activity$current")
        }
    }

    private fun hop(player: Player, targetWorld: Int) {
        val target =
            when (val selected = worldList.selectHopTarget(config.world, targetWorld)) {
                is WorldHopTarget.Valid -> selected
                WorldHopTarget.CurrentWorld -> return player.mes("You are already on that world.")
                WorldHopTarget.NotFound -> return player.mes("World $targetWorld does not exist.")
                WorldHopTarget.Offline -> return player.mes("World $targetWorld is offline.")
                WorldHopTarget.NoHost -> {
                    return player.mes("World $targetWorld is not accepting world switches.")
                }
            }
        val launched = protectedAccess.launch(player) { transfer(target) }
        if (!launched) {
            player.mes(constants.dm_busy)
        }
    }

    private fun ProtectedAccess.transfer(target: WorldHopTarget.Valid) {
        // Mirror the checks of a manual logout so the client is only transferred when this world
        // will actually log the player out.
        if (mapClock <= player.preventLogoutUntil) {
            mes(player.preventLogoutMessage ?: "You can't switch worlds right now.")
            return
        }
        logOut()
        if (!player.manualLogout) {
            return
        }
        val world = target.world
        MiscOutput.logoutTransfer(player, target.host, world.id, world.flags)
    }
}
