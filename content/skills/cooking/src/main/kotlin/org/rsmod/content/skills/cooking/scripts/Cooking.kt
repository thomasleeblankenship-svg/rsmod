package org.rsmod.content.skills.cooking.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.objParam
import org.rsmod.api.config.objXpParam
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.cookingLvl
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.cooking.configs.cooking_locs
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Burnt-product and burn-stop-level data is a plain Kotlin lookup table here rather than custom
 * cache params, since custom `ObjType`/`Int` params defined via `ParamBuilder` were not resolved by
 * the time obj editors ran at boot (a pre-existing resolver-ordering issue, not something specific
 * to this skill). Values are simplified approximations, not verified against official per-food
 * burn-rate tables.
 */
class Cooking
@Inject
constructor(private val objTypes: ObjTypeList, private val xpMods: XpModifiers) : PluginScript() {
    override fun ScriptContext.startup() {
        for (food in BURNT_AND_STOP_LEVEL.keys) {
            onOpLocU(cooking_locs.fire, food) { cook(food, onRange = false, it.invSlot) }
            onOpLocU(cooking_locs.range, food) { cook(food, onRange = true, it.invSlot) }
        }
    }

    private suspend fun ProtectedAccess.cook(raw: ObjType, onRange: Boolean, invSlot: Int) {
        val type = objTypes[raw]

        if (player.cookingLvl < type.cookLevelReq) {
            mes("You need a Cooking level of ${type.cookLevelReq} to cook this.")
            return
        }

        anim(if (onRange) seqs.human_cooking else seqs.human_firecooking)
        delay(2)

        val (burnt, stopBurnLevel) = BURNT_AND_STOP_LEVEL.getValue(raw)
        val isBurnt = isBurnt(stopBurnLevel, onRange)
        val product = if (isBurnt) burnt else type.cookProduct

        val removed = invDel(inv, raw, slot = invSlot)
        if (removed.failure) {
            return
        }
        invAdd(inv, product)

        if (isBurnt) {
            spam("You accidentally burn the ${type.name.lowercase()}.")
        } else {
            spam("You successfully cook the ${type.name.lowercase()}.")
            val xp = type.cookXp * xpMods.get(player, stats.cooking)
            statAdvance(stats.cooking, xp)
        }
    }

    /**
     * Simplified burn-chance model: cooking on a range gives a flat +5 effective level over a fire.
     * Not verified against official burn-rate formulas.
     */
    private fun ProtectedAccess.isBurnt(stopBurnLevel: Int, onRange: Boolean): Boolean {
        val effectiveLevel = player.cookingLvl + if (onRange) 5 else 0
        if (effectiveLevel >= stopBurnLevel) {
            return false
        }
        val levelsBelow = stopBurnLevel - effectiveLevel
        val burnChance = (levelsBelow * 8).coerceIn(0, 90)
        return random.of(1, 100) <= burnChance
    }

    companion object {
        val BURNT_AND_STOP_LEVEL: Map<ObjType, Pair<ObjType, Int>> =
            mapOf(
                objs.raw_shrimp to (objs.burnt_fish to 34),
                objs.raw_anchovies to (objs.burnt_fish to 34),
                objs.raw_sardine to (objs.burnt_fish to 38),
                objs.raw_herring to (objs.burnt_fish to 41),
                objs.raw_trout to (objs.burnt_fish to 50),
                objs.raw_salmon to (objs.burnt_fish to 58),
                objs.raw_tuna to (objs.burnt_fish to 65),
                objs.raw_lobster to (objs.burnt_lobster to 74),
                objs.raw_swordfish to (objs.burnt_swordfish to 86),
                objs.raw_shark to (objs.burnt_shark to 99),
            )

        val UnpackedObjType.cookLevelReq: Int by objParam(params.levelrequire)
        val UnpackedObjType.cookXp: Double by objXpParam(params.skill_xp)
        val UnpackedObjType.cookProduct: ObjType by objParam(params.skill_productitem)
    }
}
