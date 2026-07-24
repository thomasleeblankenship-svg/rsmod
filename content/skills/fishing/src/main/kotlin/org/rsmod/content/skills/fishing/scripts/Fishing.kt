package org.rsmod.content.skills.fishing.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.synths
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.randomevent.RandomEventTrigger
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.fishing.configs.fishing_npcs
import org.rsmod.game.entity.Npc
import org.rsmod.game.stat.PlayerStatMap
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Only net fishing (shrimp/anchovies) at the verified `newbiefishing` spot is wired. See
 * [org.rsmod.content.skills.fishing.configs.FishingSpots] for why other tools/spots are not yet
 * included.
 */
class Fishing
@Inject
constructor(
    private val xpMods: XpModifiers,
    private val invisibleLvls: InvisibleLevels,
    private val randomEvents: RandomEventTrigger,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(fishing_npcs.newbie_net_spot) { attempt(it.npc) }
    }

    private suspend fun ProtectedAccess.attempt(spot: Npc) {
        if (objs.small_fishing_net !in inv) {
            mes("You need a small fishing net to fish here.")
            return
        }

        if (inv.isFull()) {
            mes("Your inventory is too full to hold any more fish.")
            soundSynth(synths.pillory_wrong)
            return
        }

        anim(seqs.human_fishing_casting)
        spam("You cast out your net...")
        fish(spot)
    }

    private suspend fun ProtectedAccess.fish(spot: Npc) {
        delay(3)

        val caught = statRandom(stats.fishing, NET_LOW, NET_HIGH, invisibleLvls)
        if (!caught) {
            opNpc1(spot)
            return
        }

        val catch = if (random.randomBoolean(3)) objs.raw_anchovies else objs.raw_shrimp
        val xp = catchXp(catch) * xpMods.get(player, stats.fishing)
        spam("You catch some ${objTypeName(catch)}.")
        anim(seqs.human_fish_onspot)
        statAdvance(stats.fishing, xp)
        invAdd(inv, catch)
        with(randomEvents) { tryTriggerMysteriousOldMan() }

        opNpc1(spot)
    }

    private fun objTypeName(type: ObjType): String =
        when (type) {
            objs.raw_shrimp -> "raw shrimps"
            objs.raw_anchovies -> "raw anchovies"
            else -> "fish"
        }

    private fun catchXp(type: ObjType): Double =
        when (type) {
            objs.raw_shrimp -> PlayerStatMap.toFineXP(10.0)
            objs.raw_anchovies -> PlayerStatMap.toFineXP(10.0)
            else -> 0.0
        }

    private companion object {
        const val NET_LOW = 30
        const val NET_HIGH = 220
    }
}
