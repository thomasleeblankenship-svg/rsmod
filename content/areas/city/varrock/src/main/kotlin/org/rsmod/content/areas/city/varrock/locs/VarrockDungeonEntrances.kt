package org.rsmod.content.areas.city.varrock.locs

import org.rsmod.api.config.refs.seqs
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.areas.city.varrock.configs.varrock_locs
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The 5 real dungeon/tunnel entrance locs found under Varrock (verified via
 * [org.rsmod.server.install.SkillDataExporter], each with a real op at op1). These do **not**
 * teleport the player anywhere - finding the real destination coordinates on the other side of each
 * entrance needs a full map decode keyed to these specific loc instances, which the existing world
 * location export didn't capture (it only recorded locs matching a narrower category search).
 * Rather than fabricate a destination and risk teleporting a player into a wall or the void, these
 * just play the real animation for their op (where one was found) and describe what's there.
 */
class VarrockDungeonEntrances : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(varrock_locs.dungeon_pipe) {
            mes(
                "You squeeze into the pipe, but it's a dead end - the way through hasn't been dug yet."
            )
        }
        onOpLoc1(varrock_locs.tunnel_west) { climbInto("You climb into the western tunnel.") }
        onOpLoc1(varrock_locs.tunnel_east) { climbInto("You climb into the eastern tunnel.") }
        onOpLoc1(varrock_locs.manhole_ladder) {
            anim(seqs.human_climbing)
            mes("You climb up the ladder, but the passage above hasn't been connected yet.")
        }
        onOpLoc1(varrock_locs.bearpit_trapdoor) {
            anim(seqs.human_climbing_down)
            mes("You climb down through the trapdoor, but the pit below hasn't been connected yet.")
        }
    }

    private suspend fun ProtectedAccess.climbInto(message: String) {
        anim(seqs.human_climbing)
        mes("$message The passage beyond hasn't been connected yet.")
    }
}
