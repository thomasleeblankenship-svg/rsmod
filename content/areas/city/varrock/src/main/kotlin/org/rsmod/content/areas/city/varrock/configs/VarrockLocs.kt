package org.rsmod.content.areas.city.varrock.configs

import org.rsmod.api.config.refs.content
import org.rsmod.api.type.editors.loc.LocEditor
import org.rsmod.api.type.refs.loc.LocReferences

typealias varrock_locs = VarrockLocs

object VarrockLocs : LocReferences() {
    val bankbooth = find("fai_varrock_bankbooth")

    // Dungeon/tunnel entrance locs found in the cache (verified via SkillDataExporter), each with
    // a real op at op1. No teleport is wired for these - see VarrockDungeonEntrances.kt doc.
    val dungeon_pipe = find("varrock_dungeon_pipe_sc")
    val tunnel_west = find("varrock_sc_tunnel_west")
    val tunnel_east = find("varrock_sc_tunnel_east")
    val manhole_ladder = find("fai_varrock_manhole_ladder")
    val bearpit_trapdoor = find("fai_varrock_bearpit_trapdoor")
}

internal object VarrockLocEditor : LocEditor() {
    init {
        edit(varrock_locs.bankbooth) { contentGroup = content.bank_booth }
    }
}
