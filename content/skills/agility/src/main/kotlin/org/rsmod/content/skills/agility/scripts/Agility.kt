package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.draynor_course
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.seq.SeqType
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
        for ((obstacle, data) in OBSTACLES) {
            onOpLoc1(obstacle) { traverse(data) }
        }
    }

    private suspend fun ProtectedAccess.traverse(data: ObstacleData) {
        if (player.agilityLvl < LEVEL_REQ) {
            mes("You need an Agility level of $LEVEL_REQ to use this obstacle.")
            return
        }
        anim(data.seq)
        spam("You make your way across the obstacle.")
        statAdvance(stats.agility, data.xp * xpMods.get(player, stats.agility))
    }

    private data class ObstacleData(val xp: Double, val seq: SeqType)

    companion object {
        private const val LEVEL_REQ = 10

        /**
         * Balance/Cross obstacles reuse `human_walk_logbalance` (log-balance walk) and Jump/Jump-up
         * reuse `human_jump_hurdle` - no distinct animation was found for the wall-scramble or
         * leap-down obstacles specifically.
         */
        private val OBSTACLES: Map<LocType, ObstacleData> by lazy {
            mapOf(
                draynor_course.wall_climb to ObstacleData(8.0, seqs.human_climbing),
                draynor_course.tightrope_1 to ObstacleData(7.5, seqs.human_walk_logbalance),
                draynor_course.tightrope_2 to ObstacleData(7.5, seqs.human_walk_logbalance),
                draynor_course.wall_crossing to ObstacleData(7.5, seqs.human_walk_logbalance),
                draynor_course.wall_scramble to ObstacleData(8.0, seqs.human_jump_hurdle),
                draynor_course.leap_down to ObstacleData(7.5, seqs.human_jump_hurdle),
                draynor_course.crate to ObstacleData(5.0, seqs.human_climbing_down),
            )
        }
    }
}
