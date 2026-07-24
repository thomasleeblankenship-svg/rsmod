package org.rsmod.content.skills.agility.configs

import org.rsmod.api.type.refs.loc.LocReferences

typealias draynor_course = DraynorRooftopCourse

/**
 * The Draynor Village rooftop agility course - all 7 interactive obstacles found in the cache
 * (verified via [org.rsmod.server.install.SkillDataExporter], each with a real op at op1). The
 * `tightrope_end` piece has no op of its own (purely a visual marker) and is not wired.
 */
object DraynorRooftopCourse : LocReferences() {
    val wall_climb = find("rooftops_draynor_wallclimb")
    val tightrope_1 = find("rooftops_draynor_tightrope_1")
    val tightrope_2 = find("rooftops_draynor_tightrope_2")
    val wall_crossing = find("rooftops_draynor_wallcrossing")
    val wall_scramble = find("rooftops_draynor_wallscramble")
    val leap_down = find("rooftops_draynor_leapdown")
    val crate = find("rooftops_draynor_crate")
}
