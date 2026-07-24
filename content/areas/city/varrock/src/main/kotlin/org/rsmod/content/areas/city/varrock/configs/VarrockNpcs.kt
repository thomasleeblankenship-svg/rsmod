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
