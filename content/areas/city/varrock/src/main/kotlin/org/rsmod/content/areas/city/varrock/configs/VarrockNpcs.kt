@file:Suppress("unused")

package org.rsmod.content.areas.city.varrock.configs

import org.rsmod.api.config.refs.content
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.npc.NpcReferences

typealias varrock_npcs = VarrockNpcs

object VarrockNpcs : NpcReferences() {
    val banker = find("deadman_banker_blue_west")
    val shop_keeper = find("generalshopkeeper2")
    val shop_assistant = find("generalassistant2")
    val granny = find("varrock_granny_1")

    // Real shopkeepers found in the cache (verified via SkillDataExporter): all have Talk-to at
    // op1 and Trade at op3.
    val horvik = find("horvik_the_armourer")
    val zaff = find("zaff")
    val lowe = find("lowe")
    val swordshop1 = find("swordshop1")
    val swordshop2 = find("swordshop2")

    // "aubury" alone has no ops at all in the cache; "aubury_3op" is the real interactive variant
    // (Talk-to/Trade/Teleport).
    val aubury = find("aubury_3op")
}

internal object VarrockNpcEditor : NpcEditor() {
    init {
        edit(varrock_npcs.banker) { contentGroup = content.banker }

        edit(varrock_npcs.shop_keeper) { moveRestrict = indoors }

        edit(varrock_npcs.shop_assistant) { moveRestrict = indoors }

        edit(varrock_npcs.granny) {
            respawnDir = south
            wanderRange = 1
        }
    }
}
