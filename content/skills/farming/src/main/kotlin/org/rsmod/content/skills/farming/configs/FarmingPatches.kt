package org.rsmod.content.skills.farming.configs

import org.rsmod.api.type.refs.loc.LocReferences

typealias farming_patches = FarmingPatches

/**
 * All 7 real weeded-patch types found in the cache (verified via
 * [org.rsmod.server.install.SkillDataExporter]), each with a confirmed `Rake` op at op1, across
 * their 3 weed-density variants. Only weeding is implemented here - the full plant/grow/harvest
 * cycle needs a per-instance crop and growth-stage state system that this exporter-driven workflow
 * doesn't surface (real OSRS patches morph through many crop-specific growth-stage locs tracked via
 * varbits, none of which were reliably identifiable here), so it's out of scope. Farming
 * Guild-locked (Celastrus) and quest-locked patch types are still included since their weeding op
 * is identical and real; more exotic patches (Hespori, Anima, calquat, redwood tree patches) are
 * intentionally excluded to keep scope to the standard patch set.
 */
object FarmingPatches : LocReferences() {
    val veg_1 = find("veg_patch_weeds_1")
    val veg_2 = find("veg_patch_weeds_2")
    val veg_3 = find("veg_patch_weeds_3")
    val herb_1 = find("herb_patch_weeds_1")
    val herb_2 = find("herb_patch_weeds_2")
    val herb_3 = find("herb_patch_weeds_3")
    val tree_1 = find("tree_patch_weeds_1")
    val tree_2 = find("tree_patch_weeds_2")
    val tree_3 = find("tree_patch_weeds_3")
    val bush_1 = find("bush_patch_weeds_1")
    val bush_2 = find("bush_patch_weeds_2")
    val bush_3 = find("bush_patch_weeds_3")
    val cactus_1 = find("cactus_patch_weeds_1")
    val cactus_2 = find("cactus_patch_weeds_2")
    val cactus_3 = find("cactus_patch_weeds_3")
    val hardwood_1 = find("hardwood_tree_patch_weeds_1")
    val hardwood_2 = find("hardwood_tree_patch_weeds_2")
    val hardwood_3 = find("hardwood_tree_patch_weeds_3")
    val celastrus_1 = find("celastrus_patch_weeds_1")
    val celastrus_2 = find("celastrus_patch_weeds_2")
    val celastrus_3 = find("celastrus_patch_weeds_3")
}
