package org.rsmod.content.skills.farming.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.farming.configs.farming_patches
import org.rsmod.game.type.loc.LocType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Weeding farming patches - see [org.rsmod.content.skills.farming.configs.FarmingPatches] for what
 * is and isn't covered. This does not implement planting, growing, or harvesting crops (see that
 * class doc for why), so xp here only comes from clearing weeds, which is a small part of real
 * Farming training. The patch does not change state after weeding (no confirmed "cleared" loc id
 * for most of these types), so it can be weeded repeatedly rather than needing to regrow weeds
 * first. Xp values are simplified approximations, not verified against official numbers.
 */
class Farming @Inject constructor(private val xpMods: XpModifiers) : PluginScript() {
    override fun ScriptContext.startup() {
        for (patch in PATCHES) {
            onOpLoc1(patch) { weed() }
        }
    }

    private suspend fun ProtectedAccess.weed() {
        if (objs.rake !in inv) {
            mes("You need a rake to clear these weeds.")
            return
        }
        anim(seqs.human_farming)
        spam("You clear the weeds from the patch.")
        statAdvance(stats.farming, WEED_XP * xpMods.get(player, stats.farming))
    }

    companion object {
        private const val WEED_XP = 6.0

        private val PATCHES: List<LocType> by lazy {
            listOf(
                farming_patches.veg_1,
                farming_patches.veg_2,
                farming_patches.veg_3,
                farming_patches.herb_1,
                farming_patches.herb_2,
                farming_patches.herb_3,
                farming_patches.tree_1,
                farming_patches.tree_2,
                farming_patches.tree_3,
                farming_patches.bush_1,
                farming_patches.bush_2,
                farming_patches.bush_3,
                farming_patches.cactus_1,
                farming_patches.cactus_2,
                farming_patches.cactus_3,
                farming_patches.hardwood_1,
                farming_patches.hardwood_2,
                farming_patches.hardwood_3,
                farming_patches.celastrus_1,
                farming_patches.celastrus_2,
                farming_patches.celastrus_3,
            )
        }
    }
}
