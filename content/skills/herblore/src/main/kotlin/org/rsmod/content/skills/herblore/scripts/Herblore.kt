package org.rsmod.content.skills.herblore.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.herbloreLvl
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Covers cleaning grimy herbs, grinding unicorn horn, and mixing potions. The real two-step potion
 * process (herb + vial of water -> unfinished potion, then + secondary ingredient) collapses into
 * one step here: using a clean herb on its secondary ingredient directly checks the inventory for a
 * vial of water and consumes all three at once, since no distinct per-herb "unfinished potion" item
 * was found anywhere in this cache export (verified via
 * [org.rsmod.server.install.SkillDataExporter]
 * - only a single generic `vial_water` item exists). Only recipes with a clearly-confirmed real
 *   potion item id in the cache are wired; several well-known OSRS recipes (Strength potion, Super
 *   restore, Antifire, Ranging potion, Saradomin brew, Super energy) were left out because no
 *   matching finished-potion item could be found here.
 *
 * Level requirements are standard, well-known values; xp amounts are simplified approximations, not
 * verified against official per-potion numbers.
 */
class Herblore @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((grimy, clean) in GRIMY_HERBS) {
            onOpHeld1(grimy) { cleanHerb(grimy, clean) }
        }

        onOpHeldU(objs.pestle_and_mortar, objs.unicorn_horn) { grind() }

        for (recipe in POTIONS) {
            onOpHeldU(recipe.herb, recipe.secondary) { mixPotion(recipe) }
        }
    }

    private suspend fun ProtectedAccess.cleanHerb(grimy: ObjType, clean: ObjType) {
        val removed = invDel(inv, grimy, 1)
        if (removed.failure) {
            return
        }
        invAdd(inv, clean)
        spam("You clean the herb.")
    }

    private suspend fun ProtectedAccess.grind() {
        val removed = invDel(inv, objs.unicorn_horn, 1)
        if (removed.failure) {
            return
        }
        invAdd(inv, objs.unicorn_horn_dust)
        anim(seqs.human_herbing_grind)
        spam("You grind the unicorn horn into dust.")
    }

    private suspend fun ProtectedAccess.mixPotion(recipe: PotionRecipe) {
        if (player.herbloreLvl < recipe.levelReq) {
            mes("You need a Herblore level of ${recipe.levelReq} to mix this potion.")
            return
        }
        if (objs.vial_water !in inv) {
            mes("You need a vial of water to mix this potion.")
            return
        }
        val removed = invDel(inv, recipe.herb, 1, recipe.secondary, 1)
        if (removed.failure) {
            return
        }
        invDel(inv, objs.vial_water, 1)
        invAdd(inv, recipe.potion)
        anim(seqs.human_herbing_vial)
        spam("You mix the ingredients into a potion.")
        statAdvance(stats.herblore, recipe.xp * xpMods.get(player, stats.herblore))
    }

    private data class PotionRecipe(
        val herb: ObjType,
        val secondary: ObjType,
        val potion: ObjType,
        val levelReq: Int,
        val xp: Double,
    )

    companion object {
        private val GRIMY_HERBS: Map<ObjType, ObjType> by lazy {
            mapOf(
                objs.grimy_guam to objs.guam_leaf,
                objs.grimy_marrentill to objs.marrentill,
                objs.grimy_tarromin to objs.tarromin,
                objs.grimy_harralander to objs.harralander,
                objs.grimy_ranarr to objs.ranarr_weed,
                objs.grimy_irit to objs.irit_leaf,
                objs.grimy_avantoe to objs.avantoe,
                objs.grimy_kwuarm to objs.kwuarm,
                objs.grimy_cadantine to objs.cadantine,
            )
        }

        private val POTIONS: List<PotionRecipe> by lazy {
            listOf(
                PotionRecipe(objs.guam_leaf, objs.eye_of_newt, objs.attack_potion4, 1, 25.0),
                PotionRecipe(objs.marrentill, objs.unicorn_horn_dust, objs.antipoison4, 5, 37.5),
                PotionRecipe(
                    objs.harralander,
                    objs.red_spiders_eggs,
                    objs.restore_potion4,
                    22,
                    62.5,
                ),
                PotionRecipe(objs.irit_leaf, objs.white_berries, objs.defence_potion4, 30, 75.0),
                PotionRecipe(objs.ranarr_weed, objs.snape_grass, objs.prayer_potion4, 38, 87.5),
                PotionRecipe(
                    objs.avantoe,
                    objs.unicorn_horn_dust,
                    objs.super_antipoison4,
                    48,
                    106.0,
                ),
                PotionRecipe(objs.kwuarm, objs.limpwurt_root, objs.super_strength4, 55, 125.0),
                PotionRecipe(objs.cadantine, objs.white_berries, objs.super_defence4, 66, 150.0),
            )
        }
    }
}
