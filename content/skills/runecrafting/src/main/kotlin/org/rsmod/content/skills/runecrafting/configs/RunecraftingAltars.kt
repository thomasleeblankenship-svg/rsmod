package org.rsmod.content.skills.runecrafting.configs

import org.rsmod.api.type.refs.loc.LocReferences

typealias runecrafting_altars = RunecraftingAltars

/**
 * All 14 standard elemental/catalytic rune altars found in the cache (verified via
 * [org.rsmod.server.install.SkillDataExporter], each with a real `Craft-rune` op). The Zeah
 * Mysterious Ruins (ZMI) altar and the Ourania altar are intentionally excluded - both let a player
 * craft whichever rune matches spells they've unlocked, which is a distinct system from a fixed
 * altar-to-rune mapping and is not implemented here. No Soul altar was found in this cache export
 * either.
 */
object RunecraftingAltars : LocReferences() {
    val air = find("air_altar")
    val mind = find("mind_altar")
    val water = find("water_altar")
    val earth = find("earth_altar")
    val fire = find("fire_altar")
    val body = find("body_altar")
    val cosmic = find("cosmic_altar")
    val chaos = find("chaos_altar")
    val nature = find("nature_altar")
    val law = find("law_altar")
    val death = find("death_altar")
    val blood = find("blood_altar")
    val astral = find("astral_altar")
    val wrath = find("wrath_altar")
}
