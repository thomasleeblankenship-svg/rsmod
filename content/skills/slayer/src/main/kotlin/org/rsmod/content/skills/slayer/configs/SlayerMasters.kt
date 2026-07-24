package org.rsmod.content.skills.slayer.configs

import org.rsmod.api.type.refs.npc.NpcReferences

typealias slayer_masters = SlayerMasters

/**
 * All 13 real slayer masters found in the cache (verified via
 * [org.rsmod.server.install.SkillDataExporter]), each with a real `Assignment` op at op3.
 */
object SlayerMasters : NpcReferences() {
    val turael = find("slayer_master_1_tureal")
    val aya = find("slayer_master_1_aya")
    val mazchna = find("slayer_master_2_mazchna")
    val achtryn = find("slayer_master_2_achtryn_vis")
    val vannaka = find("slayer_master_3")
    val chaeldar = find("slayer_master_4")
    val duradel = find("slayer_master_5_duradel")
    val kuradal = find("slayer_master_5_kuradal")
    val krystilia = find("slayer_master_7")
    val konar = find("slayer_master_8")
    val spria = find("slayer_master_9_active")
    val nieve = find("slayer_master_nieve")
    val steve = find("slayer_master_steve")
}
