package org.rsmod.content.skills.smithing.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.diary.LumbridgeDraynorDiary.TASK_SMITH_BRONZE_BAR
import org.rsmod.api.player.diary.LumbridgeDraynorDiary.completeEasyTask
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.randomevent.RandomEventTrigger
import org.rsmod.api.player.stat.smithingLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.smithing.configs.SmeltingRecipe
import org.rsmod.content.skills.smithing.configs.SmeltingRecipes
import org.rsmod.content.skills.smithing.configs.SmithableItems
import org.rsmod.content.skills.smithing.configs.smithing_locs
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Smelting/smithing here auto-selects the highest-tier recipe the player currently has the level
 * and materials for on each click, rather than presenting the official "make-x" interface. Only
 * daggers are implemented for smithing (see [SmithableItems]).
 */
class Smithing
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val xpMods: XpModifiers,
    private val randomEvents: RandomEventTrigger,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(smithing_locs.furnace) { smelt() }
        onOpLoc1(smithing_locs.anvil) { smith() }
    }

    private fun ProtectedAccess.smelt() {
        val recipe =
            SmeltingRecipes.all
                .filter { player.smithingLvl >= it.levelReq }
                .lastOrNull { canSmelt(it) }
        if (recipe == null) {
            mes("You do not have the required ores to smelt a bar here.")
            return
        }

        val removed =
            if (recipe.secondaryOre != null) {
                invDel(inv, recipe.primaryOre, 1, recipe.secondaryOre, 1)
            } else if (recipe.coalCount > 0) {
                invDel(inv, recipe.primaryOre, 1, objs.coal, recipe.coalCount)
            } else {
                invDel(inv, recipe.primaryOre, 1)
            }
        if (removed.failure) {
            return
        }

        anim(seqs.human_smithing)
        spam("You smelt the ore into a bar.")
        invAdd(inv, recipe.bar)
        val xp = SmeltingRecipes.fineXp(recipe) * xpMods.get(player, stats.smithing)
        statAdvance(stats.smithing, xp)
        if (recipe.bar == objs.bronze_bar) {
            completeEasyTask(TASK_SMITH_BRONZE_BAR)
        }
        with(randomEvents) { tryTriggerMysteriousOldMan() }
    }

    private fun ProtectedAccess.canSmelt(recipe: SmeltingRecipe): Boolean {
        if (recipe.primaryOre !in inv) {
            return false
        }
        if (recipe.secondaryOre != null && recipe.secondaryOre !in inv) {
            return false
        }
        if (recipe.coalCount > 0 && inv.count(objTypes[objs.coal]) < recipe.coalCount) {
            return false
        }
        return true
    }

    private fun ProtectedAccess.smith() {
        val recipe =
            SmithableItems.all
                .filter { player.smithingLvl >= it.levelReq }
                .lastOrNull { inv.count(objTypes[it.bar]) >= it.barCount }
        if (recipe == null) {
            mes("You do not have the required bars to smith anything here.")
            return
        }

        val removed = invDel(inv, recipe.bar, recipe.barCount)
        if (removed.failure) {
            return
        }

        anim(seqs.human_smithing)
        spam("You hammer the bar into shape.")
        invAdd(inv, recipe.product)
        val xp = SmithableItems.fineXp(recipe) * xpMods.get(player, stats.smithing)
        statAdvance(stats.smithing, xp)
    }
}
