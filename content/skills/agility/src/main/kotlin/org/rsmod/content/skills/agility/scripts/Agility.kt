package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.draynor_course
import org.rsmod.game.type.loc.LocType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Draynor Village rooftop agility course - the only real, fully-confirmed course found in this
 * cache export (see [org.rsmod.content.skills.agility.configs.DraynorRooftopCourse]).
 *
 * This does **not** move the player between obstacles or track course laps/completion - each
 * obstacle can be used independently, repeatedly, for xp, rather than requiring the real one-way
 * traversal sequence with a single level gate and lap-based xp. Building real point-to-point
 * movement between obstacles needs per-obstacle destination tiles this exporter-driven workflow
 * doesn't surface, so it's out of scope here. Level requirement (10) is the standard, well-known
 * value for this course; per-obstacle xp amounts are simplified approximations, not verified
 * against official numbers. There is no fail chance, unlike some real obstacles at low Agility
 * levels.
 */
class Agility @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((obstacle, xp) in OBSTACLES) {
            onOpLoc1(obstacle) { traverse(xp) }
        }
    }

    private suspend fun ProtectedAccess.traverse(xp: Double) {
        if (player.agilityLvl < LEVEL_REQ) {
            mes("You need an Agility level of $LEVEL_REQ to use this obstacle.")
            return
        }
        spam("You make your way across the obstacle.")
        statAdvance(stats.agility, xp * xpMods.get(player, stats.agility))
    }

    companion object {
        private const val LEVEL_REQ = 10

        private val OBSTACLES: Map<LocType, Double> by lazy {
            mapOf(
                draynor_course.wall_climb to 8.0,
                draynor_course.tightrope_1 to 7.5,
                draynor_course.tightrope_2 to 7.5,
                draynor_course.wall_crossing to 7.5,
                draynor_course.wall_scramble to 8.0,
                draynor_course.leap_down to 7.5,
                draynor_course.crate to 5.0,
            )
        }
    }
}
