package org.rsmod.content.skills.hunter.configs

import org.rsmod.api.type.refs.npc.NpcReferences

typealias impling_npcs = ImplingNpcs

/**
 * The 9 base-world impling types found in the cache (verified via
 * [org.rsmod.server.install.SkillDataExporter]), each with a real `Catch` op. The Puro-Puro-only
 * "_maze" variants and higher (dragon/dryad-type) implings are not included here.
 */
object ImplingNpcs : NpcReferences() {
    val baby = find("ii_impling_type_1")
    val young = find("ii_impling_type_2")
    val gourmet = find("ii_impling_type_3")
    val earth = find("ii_impling_type_4")
    val essence = find("ii_impling_type_5")
    val eclectic = find("ii_impling_type_6")
    val nature = find("ii_impling_type_7")
    val magpie = find("ii_impling_type_8")
    val ninja = find("ii_impling_type_9")
}
