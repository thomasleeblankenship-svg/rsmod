package org.rsmod.content.areas.city.varrock.locs

import org.rsmod.api.config.refs.seqs
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.areas.city.varrock.configs.varrock_locs
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The 5 real dungeon/tunnel entrance locs found under Varrock (verified via
 * [org.rsmod.server.install.SkillDataExporter], each with a real op at op1).
 *
 * Real destination coordinates for 3 of the 5 were recovered via a one-off boot-time probe added
 * to [org.rsmod.server.app.WorldLocationExporter] (since removed), which dumped every real map
 * placement of these loc ids. `varrock_sc_tunnel_west`, `varrock_sc_tunnel_east`, and
 * `fai_varrock_bearpit_trapdoor` were each found placed **twice** in the cache - once at a surface
 * coordinate near south Varrock, once at a second coordinate offset by a consistent
 * (dx=+704, dz=+1472) from the first. That offset repeating identically across three independent,
 * unrelated loc instances is strong evidence it's a real surface/underground coordinate pairing
 * baked into the map data (not a coincidence), so those three now teleport the player between the
 * two real, verified endpoints.
 *
 * `varrock_dungeon_pipe_sc` was only found placed underground, with its two instances just 3 tiles
 * apart in the same corridor - consistent with "squeezing past an obstruction" rather than a long
 * teleport, so it nudges the player forward those 3 real tiles.
 *
 * `fai_varrock_manhole_ladder` was only found placed once (underground, at the bottom of the
 * ladder). No matching surface coordinate was found in the probe, so - rather than fabricate a
 * destination and risk teleporting a player into a wall or the void - it still has no teleport,
 * only its real climb animation and a placeholder message.
 */
class VarrockDungeonEntrances : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(varrock_locs.dungeon_pipe) {
            anim(seqs.human_climbing)
            mes("You squeeze through the pipe.")
            teleport(CoordGrid(x = 3153, z = 9906, level = 0))
        }
        onOpLoc1(varrock_locs.tunnel_west) {
            anim(seqs.human_climbing)
            mes("You climb into the western tunnel.")
            teleport(destination(CoordGrid(x = 3138, z = 3516, level = 0)))
        }
        onOpLoc1(varrock_locs.tunnel_east) {
            anim(seqs.human_climbing)
            mes("You climb into the eastern tunnel.")
            teleport(destination(CoordGrid(x = 3141, z = 3513, level = 0)))
        }
        onOpLoc1(varrock_locs.manhole_ladder) {
            anim(seqs.human_climbing)
            mes("You climb up the ladder, but the passage above hasn't been connected yet.")
        }
        onOpLoc1(varrock_locs.bearpit_trapdoor) {
            anim(seqs.human_climbing_down)
            mes("You climb down through the trapdoor.")
            teleport(destination(CoordGrid(x = 3230, z = 3504, level = 0)))
        }
    }

    /**
     * Both endpoints of the surface/underground offset pair are real cache coordinates, so we can
     * teleport in either direction from whichever side the player is standing on.
     */
    private fun ProtectedAccess.destination(surfaceCoord: CoordGrid): CoordGrid {
        val underground = surfaceCoord.translate(xOffset = 704, zOffset = 1472)
        return if (coords == surfaceCoord) underground else surfaceCoord
    }
}
