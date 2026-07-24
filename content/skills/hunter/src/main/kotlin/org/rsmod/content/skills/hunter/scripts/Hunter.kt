package org.rsmod.content.skills.hunter.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.hunter.configs.impling_npcs
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Catching implings (base overworld types only - see
 * [org.rsmod.content.skills.hunter.configs.ImplingNpcs]). Trap-based hunting (box traps, bird
 * snares, deadfalls, birdhouses) is a distinct loc-placement system not implemented here - it
 * requires spawning and managing player-owned locs the way Firemaking does for fires, which is a
 * larger addition better scoped as its own feature.
 *
 * Success-rate curve is a simplified approximation (see [ProtectedAccess.statRandom]), not verified
 * against official per-impling catch-rate tables. Level requirements are standard, well-known
 * values; xp amounts are simplified approximations.
 */
class Hunter
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val xpMods: XpModifiers,
    private val invisibleLvls: InvisibleLevels,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((npc, recipe) in IMPLINGS) {
            onOpNpc1(npc) { catch(it.npc, recipe) }
        }
    }

    private suspend fun ProtectedAccess.catch(npc: Npc, recipe: ImplingRecipe) {
        if (objs.net !in inv) {
            mes("You need a butterfly net to catch this impling.")
            return
        }
        if (player.hunterLvl < recipe.levelReq) {
            mes("You need a Hunter level of ${recipe.levelReq} to catch this impling.")
            return
        }
        if (inv.isFull()) {
            mes("Your inventory is too full to hold the jar.")
            return
        }

        val caught = statRandom(stats.hunter, CATCH_LOW, CATCH_HIGH, invisibleLvls)
        if (!caught) {
            mes("The impling dodges past you.")
            return
        }

        spam("You manage to catch the impling!")
        invAdd(inv, recipe.jar)
        statAdvance(stats.hunter, recipe.xp * xpMods.get(player, stats.hunter))
        npcRepo.despawn(npc, npc.type.respawnRate)
    }

    private data class ImplingRecipe(val jar: ObjType, val levelReq: Int, val xp: Double)

    companion object {
        private const val CATCH_LOW = 40
        private const val CATCH_HIGH = 230

        private val IMPLINGS: Map<NpcType, ImplingRecipe> by lazy {
            mapOf(
                impling_npcs.baby to ImplingRecipe(objs.impling_jar_baby, 1, 8.7),
                impling_npcs.young to ImplingRecipe(objs.impling_jar_young, 22, 24.0),
                impling_npcs.gourmet to ImplingRecipe(objs.impling_jar_gourmet, 28, 32.0),
                impling_npcs.earth to ImplingRecipe(objs.impling_jar_earth, 36, 42.0),
                impling_npcs.essence to ImplingRecipe(objs.impling_jar_essence, 42, 50.0),
                impling_npcs.eclectic to ImplingRecipe(objs.impling_jar_eclectic, 50, 60.0),
                impling_npcs.nature to ImplingRecipe(objs.impling_jar_nature, 58, 70.0),
                impling_npcs.magpie to ImplingRecipe(objs.impling_jar_magpie, 65, 80.0),
                impling_npcs.ninja to ImplingRecipe(objs.impling_jar_ninja, 74, 92.0),
            )
        }
    }
}
