package org.rsmod.content.skills.fletching.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.fletchingLvl
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Covers cutting logs into unstrung shortbows and stringing them, plus assembling headless arrows
 * from arrow shafts and feathers. Only the shortbow line is implemented (not longbows) since a
 * knife used on logs can only produce one real output here, with no choice interface to pick
 * between them - the same auto-pick simplification used elsewhere in these skills. Finishing arrows
 * (headless arrow + metal arrowtips into e.g. bronze/iron arrows) is not implemented: no arrowtip
 * item was found anywhere in this cache export, verified via
 * [org.rsmod.server.install.SkillDataExporter], so there is nothing real to wire it to.
 *
 * Level requirements are standard, well-known values; xp amounts are simplified approximations, not
 * verified against official per-item numbers.
 */
class Fletching @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((log, recipe) in BOWS) {
            onOpHeldU(objs.knife, log) { cutBow(log, recipe) }
            onOpHeldU(recipe.unstrung, objs.bow_string) { stringBow(recipe) }
        }

        onOpHeldU(objs.feather, objs.arrow_shaft) { assembleArrows() }
    }

    private suspend fun ProtectedAccess.cutBow(log: ObjType, recipe: BowRecipe) {
        if (player.fletchingLvl < recipe.levelReq) {
            mes("You need a Fletching level of ${recipe.levelReq} to cut this.")
            return
        }
        val removed = invDel(inv, log, 1)
        if (removed.failure) {
            return
        }
        invAdd(inv, recipe.unstrung)
        spam("You cut the wood into an unstrung shortbow.")
        statAdvance(stats.fletching, recipe.cutXp * xpMods.get(player, stats.fletching))
    }

    private suspend fun ProtectedAccess.stringBow(recipe: BowRecipe) {
        if (player.fletchingLvl < recipe.levelReq) {
            mes("You need a Fletching level of ${recipe.levelReq} to string this.")
            return
        }
        val removed = invDel(inv, recipe.unstrung, 1, objs.bow_string, 1)
        if (removed.failure) {
            return
        }
        invAdd(inv, recipe.strung)
        spam("You add a string to the shortbow.")
        statAdvance(stats.fletching, recipe.stringXp * xpMods.get(player, stats.fletching))
    }

    private suspend fun ProtectedAccess.assembleArrows() {
        val count = invTotal(inv, objs.arrow_shaft).coerceAtMost(invTotal(inv, objs.feather))
        if (count <= 0) {
            return
        }
        invDel(inv, objs.arrow_shaft, count, objs.feather, count)
        invAdd(inv, objs.headless_arrow, count)
        spam("You attach feathers to the arrow shafts.")
        statAdvance(stats.fletching, count * ARROW_XP * xpMods.get(player, stats.fletching))
    }

    private data class BowRecipe(
        val unstrung: ObjType,
        val strung: ObjType,
        val levelReq: Int,
        val cutXp: Double,
        val stringXp: Double,
    )

    companion object {
        private const val ARROW_XP = 1.0

        private val BOWS: Map<ObjType, BowRecipe> by lazy {
            mapOf(
                objs.logs to BowRecipe(objs.unstrung_shortbow, objs.shortbow, 5, 5.0, 5.0),
                objs.oak_logs to
                    BowRecipe(objs.unstrung_oak_shortbow, objs.oak_shortbow, 20, 16.5, 16.5),
                objs.willow_logs to
                    BowRecipe(objs.unstrung_willow_shortbow, objs.willow_shortbow, 35, 30.0, 30.0),
                objs.maple_logs to
                    BowRecipe(objs.unstrung_maple_shortbow, objs.maple_shortbow, 50, 50.0, 50.0),
                objs.yew_logs to
                    BowRecipe(objs.unstrung_yew_shortbow, objs.yew_shortbow, 65, 70.0, 70.0),
                objs.magic_logs to
                    BowRecipe(objs.unstrung_magic_shortbow, objs.magic_shortbow, 80, 83.3, 83.3),
            )
        }
    }
}
