package org.rsmod.content.skills.crafting.configs

import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences

typealias sheep_npcs = SheepNpcs

typealias tanner_npcs = TannerNpcs

typealias crafting_locs = CraftingLocs

/**
 * Every unsheared sheep variant found in the cache (verified via
 * [org.rsmod.server.install.SkillDataExporter]), all sharing the same real `Shear` op at op1.
 */
object SheepNpcs : NpcReferences() {
    val plain = find("sheepunsheered")
    val plain2 = find("sheepunsheered2")
    val plain3 = find("sheepunsheered3")
    val grey = find("sheepunsheeredg")
    val grey2 = find("sheepunsheered2g")
    val grey3 = find("sheepunsheered3g")
    val white = find("sheepunsheeredw")
    val white2 = find("sheepunsheered2w")
    val white3 = find("sheepunsheered3w")
    val shaggy = find("sheepunsheeredshaggy")
    val shaggy2 = find("sheepunsheeredshaggy2")
    val quest = find("sheep_shearer_the_thing")
}

/** Only one real tanner NPC was found in the cache with a `Tan-hides` op (at op3). */
object TannerNpcs : NpcReferences() {
    val eodan = find("hosdun_eodan")
}

object CraftingLocs : LocReferences() {
    val spinning_wheel = find("spinningwheel")
}
