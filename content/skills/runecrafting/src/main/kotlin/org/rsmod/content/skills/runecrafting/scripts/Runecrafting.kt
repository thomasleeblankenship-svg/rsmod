package org.rsmod.content.skills.runecrafting.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.runecraftingLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.runecrafting.configs.runecrafting_altars
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Crafts a rune per essence in the inventory, with no fail chance (matching real Runecrafting
 * behaviour). The per-level "extra rune per essence" multiplier that exists in the official game is
 * intentionally not implemented - it varies per rune and per level in ways not verified here - so
 * every altar visit converts essence to runes at a flat 1-to-1 ratio. Level requirements and the
 * rune-essence/pure-essence split are standard, well-known values; per-rune XP amounts are a
 * simplified increasing approximation, not verified against official numbers.
 */
class Runecrafting @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((altar, recipe) in ALTARS) {
            onOpLoc1(altar) { craft(recipe) }
        }
    }

    private suspend fun ProtectedAccess.craft(recipe: RuneRecipe) {
        if (player.runecraftingLvl < recipe.levelReq) {
            mes("You need a Runecrafting level of ${recipe.levelReq} to craft this rune.")
            return
        }

        val essenceCount =
            if (recipe.requiresPureEssence) {
                invTotal(inv, objs.pure_essence)
            } else {
                invTotal(inv, objs.rune_essence) + invTotal(inv, objs.pure_essence)
            }
        if (essenceCount <= 0) {
            val essenceName = if (recipe.requiresPureEssence) "pure essence" else "rune essence"
            mes("You do not have any $essenceName to craft with.")
            return
        }

        val pureUsed = invTotal(inv, objs.pure_essence).coerceAtMost(essenceCount)
        val rawUsed = essenceCount - pureUsed
        if (pureUsed > 0) {
            invDel(inv, objs.pure_essence, pureUsed)
        }
        if (rawUsed > 0) {
            invDel(inv, objs.rune_essence, rawUsed)
        }

        invAdd(inv, recipe.rune, essenceCount)
        anim(seqs.human_runecraft)

        val xp = recipe.xp * essenceCount * xpMods.get(player, stats.runecrafting)
        spam(
            "You bind the temple's power into $essenceCount rune${if (essenceCount == 1) "" else "s"}."
        )
        statAdvance(stats.runecrafting, xp)
    }

    private data class RuneRecipe(
        val rune: ObjType,
        val levelReq: Int,
        val xp: Double,
        val requiresPureEssence: Boolean,
    )

    companion object {
        private val ALTARS: Map<LocType, RuneRecipe> =
            mapOf(
                runecrafting_altars.air to RuneRecipe(objs.air_rune, 1, 5.0, false),
                runecrafting_altars.mind to RuneRecipe(objs.mind_rune, 2, 5.5, false),
                runecrafting_altars.water to RuneRecipe(objs.water_rune, 5, 6.0, false),
                runecrafting_altars.earth to RuneRecipe(objs.earth_rune, 9, 6.5, false),
                runecrafting_altars.fire to RuneRecipe(objs.fire_rune, 14, 7.0, false),
                runecrafting_altars.body to RuneRecipe(objs.body_rune, 20, 7.5, false),
                runecrafting_altars.cosmic to RuneRecipe(objs.cosmic_rune, 27, 8.0, true),
                runecrafting_altars.chaos to RuneRecipe(objs.chaos_rune, 35, 8.5, true),
                runecrafting_altars.astral to RuneRecipe(objs.astral_rune, 40, 8.7, true),
                runecrafting_altars.nature to RuneRecipe(objs.nature_rune, 44, 9.0, true),
                runecrafting_altars.law to RuneRecipe(objs.law_rune, 54, 9.5, true),
                runecrafting_altars.death to RuneRecipe(objs.death_rune, 65, 10.0, true),
                runecrafting_altars.blood to RuneRecipe(objs.blood_rune, 77, 10.5, true),
                runecrafting_altars.wrath to RuneRecipe(objs.wrath_rune, 95, 11.0, true),
            )
    }
}
