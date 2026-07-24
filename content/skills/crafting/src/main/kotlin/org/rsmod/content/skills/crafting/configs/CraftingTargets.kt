package org.rsmod.content.skills.crafting.configs

import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences

typealias tanner_npcs = TannerNpcs

typealias crafting_locs = CraftingLocs

/** Only one real tanner NPC was found in the cache with a `Tan-hides` op (at op3). */
object TannerNpcs : NpcReferences() {
    val eodan = find("hosdun_eodan")
}

object CraftingLocs : LocReferences() {
    val spinning_wheel = find("spinningwheel")
}
