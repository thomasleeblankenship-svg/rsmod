package org.rsmod.content.skills.thieving.configs

import org.rsmod.api.config.refs.params
import org.rsmod.api.type.editors.loc.LocEditor
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.game.stat.PlayerStatMap
import org.rsmod.game.type.npc.NpcType

typealias pickpocket_npcs = PickpocketNpcs

typealias steal_locs = StealLocs

/**
 * Pickpocket targets. Ops confirmed via [org.rsmod.server.install.SkillDataExporter]: `man`/
 * `woman` and `fai_varrock_guard` all have a real `Pickpocket` op at slot 3.
 */
object PickpocketNpcs : NpcReferences() {
    val man = find("man")
    val woman = find("woman")
    val guard = find("fai_varrock_guard")
}

internal object PickpocketNpcEditor : NpcEditor() {
    init {
        pickpocket(pickpocket_npcs.man, 1, 8.0)
        pickpocket(pickpocket_npcs.woman, 1, 8.0)
        pickpocket(pickpocket_npcs.guard, 40, 46.8)
    }

    private fun pickpocket(type: NpcType, levelReq: Int, xp: Double) {
        edit(type) {
            param[params.levelrequire] = levelReq
            param[params.skill_xp] = PlayerStatMap.toFineXP(xp).toInt()
        }
    }
}

/**
 * Steal-from stall targets. `tea_stall` (id 635) op confirmed via the same exporter to have a real
 * `Steal-from` op. Other real stalls (veg, silk, baker's, etc.) exist in the cache and can be added
 * the same way using skill_data.json's "thieving" loc category.
 */
object StealLocs : LocReferences() {
    val tea_stall = find("tea_stall")
    val bakers_stall = find("cakethiefstall")
}

internal object StealLocEditor : LocEditor() {
    init {
        edit(steal_locs.tea_stall) {
            param[params.levelrequire] = 5
            param[params.skill_xp] = PlayerStatMap.toFineXP(16.0).toInt()
            param[params.respawn_time_low] = 3
            param[params.respawn_time_high] = 5
        }
        edit(steal_locs.bakers_stall) {
            param[params.levelrequire] = 5
            param[params.skill_xp] = PlayerStatMap.toFineXP(16.0).toInt()
            param[params.respawn_time_low] = 3
            param[params.respawn_time_high] = 5
        }
    }
}
