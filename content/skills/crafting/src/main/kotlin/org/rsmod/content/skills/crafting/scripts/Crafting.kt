package org.rsmod.content.skills.crafting.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.crafting.configs.crafting_locs
import org.rsmod.content.skills.crafting.configs.tanner_npcs
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Covers spinning wool/flax, tanning cowhide, gem cutting, and leathercrafting. Shearing sheep is
 * **not** covered here - a real, complete implementation already exists at
 * [org.rsmod.content.generic.npcs.sheep.Sheep] (found after this skill's first pass already
 * duplicated it; the duplicate has been removed). Real OSRS gives no xp for tanning, so that step
 * awards none here either.
 *
 * Tanning is simplified to a flat 1gp-per-hide conversion of every cowhide in the inventory at
 * once, rather than the real quantity-selection interface. Gem cutting has no fail chance here
 * (matching the mid/high tier gems covered), and leathercrafting auto-selects the highest-tier item
 * the player can currently afford and has the level for, the same simplification already used by
 * [org.rsmod.content.skills.smithing.scripts.Smithing] instead of a real "make-X" interface.
 *
 * Level requirements are standard, well-known values; xp amounts are simplified approximations, not
 * verified against official per-item numbers.
 */
class Crafting
@Inject
constructor(private val objTypes: ObjTypeList, private val xpMods: XpModifiers) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc3(tanner_npcs.eodan) { tan() }

        onOpLocU(crafting_locs.spinning_wheel, objs.wool) {
            spin(objs.wool, objs.ball_of_wool, 1, 2.5)
        }
        onOpLocU(crafting_locs.spinning_wheel, objs.flax) {
            spin(objs.flax, objs.bow_string, 10, 15.0)
        }

        for ((gem, cut) in GEMS) {
            onOpHeldU(objs.chisel, gem) { cutGem(gem, cut) }
        }

        onOpHeldU(objs.needle, objs.leather) { craftLeather() }
    }

    private suspend fun ProtectedAccess.tan() {
        val hides = invTotal(inv, objs.cow_hide)
        if (hides <= 0) {
            mes("You do not have any cowhides to tan.")
            return
        }
        val cost = hides * TAN_COST_PER_HIDE
        if (!invTakeFee(cost)) {
            val plural = if (hides == 1) "" else "s"
            mes("You need $cost coins to tan $hides cowhide$plural.")
            return
        }
        invDel(inv, objs.cow_hide, hides)
        invAdd(inv, objs.leather, hides)
        spam("Eodan tans your cowhides into leather.")
    }

    private suspend fun ProtectedAccess.spin(
        raw: ObjType,
        product: ObjType,
        levelReq: Int,
        xp: Double,
    ) {
        if (player.craftingLvl < levelReq) {
            mes("You need a Crafting level of $levelReq to spin this.")
            return
        }
        val removed = invDel(inv, raw, 1)
        if (removed.failure) {
            return
        }
        invAdd(inv, product)
        anim(seqs.human_spinningwheel)
        val name = objTypes[raw].name.lowercase()
        spam("You spin the $name into something more useful.")
        statAdvance(stats.crafting, xp * xpMods.get(player, stats.crafting))
    }

    private suspend fun ProtectedAccess.cutGem(gem: ObjType, cut: ObjType) {
        val recipe = GEM_RECIPES.getValue(gem)
        if (player.craftingLvl < recipe.levelReq) {
            mes("You need a Crafting level of ${recipe.levelReq} to cut this gem.")
            return
        }
        val removed = invDel(inv, gem, 1)
        if (removed.failure) {
            return
        }
        invAdd(inv, cut)
        anim(seqs.human_crafting)
        spam("You cut the gem.")
        statAdvance(stats.crafting, recipe.xp * xpMods.get(player, stats.crafting))
    }

    private suspend fun ProtectedAccess.craftLeather() {
        val recipe =
            LEATHER_ITEMS.entries
                .filter { player.craftingLvl >= it.value.levelReq }
                .lastOrNull { invTotal(inv, objs.leather) >= it.value.leatherCount }
                ?.toPair()
        if (recipe == null) {
            mes("You do not have enough leather to craft anything here.")
            return
        }
        val (product, data) = recipe
        val removed = invDel(inv, objs.leather, data.leatherCount, objs.thread, 1)
        if (removed.failure) {
            return
        }
        invAdd(inv, product)
        anim(seqs.human_crafting)
        spam("You craft the leather into shape.")
        statAdvance(stats.crafting, data.xp * xpMods.get(player, stats.crafting))
    }

    private data class GemRecipe(val levelReq: Int, val xp: Double)

    private data class LeatherRecipe(val levelReq: Int, val leatherCount: Int, val xp: Double)

    companion object {
        private const val TAN_COST_PER_HIDE = 1

        private val GEMS: Map<ObjType, ObjType> by lazy {
            mapOf(
                objs.uncut_sapphire to objs.sapphire,
                objs.uncut_emerald to objs.emerald,
                objs.uncut_ruby to objs.ruby,
                objs.uncut_diamond to objs.diamond,
                objs.uncut_dragonstone to objs.dragonstone,
                objs.uncut_onyx to objs.onyx,
            )
        }

        private val GEM_RECIPES: Map<ObjType, GemRecipe> by lazy {
            mapOf(
                objs.uncut_sapphire to GemRecipe(20, 4.0),
                objs.uncut_emerald to GemRecipe(27, 5.5),
                objs.uncut_ruby to GemRecipe(34, 8.5),
                objs.uncut_diamond to GemRecipe(43, 10.8),
                objs.uncut_dragonstone to GemRecipe(55, 21.5),
                objs.uncut_onyx to GemRecipe(67, 16.9),
            )
        }

        private val LEATHER_ITEMS: Map<ObjType, LeatherRecipe> by lazy {
            mapOf(
                objs.leather_gloves to LeatherRecipe(1, 1, 13.8),
                objs.leather_boots to LeatherRecipe(1, 1, 16.3),
                objs.leather_cowl to LeatherRecipe(1, 1, 4.0),
                objs.leather_vambraces to LeatherRecipe(11, 1, 12.2),
                objs.leather_body to LeatherRecipe(14, 1, 25.0),
                objs.leather_chaps to LeatherRecipe(18, 1, 27.0),
                objs.hardleather_body to LeatherRecipe(28, 1, 35.0),
            )
        }
    }
}
