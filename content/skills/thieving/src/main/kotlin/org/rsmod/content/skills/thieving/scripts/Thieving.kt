package org.rsmod.content.skills.thieving.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.locParam
import org.rsmod.api.config.locXpParam
import org.rsmod.api.config.npcParam
import org.rsmod.api.config.npcXpParam
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.thieving.configs.pickpocket_npcs
import org.rsmod.content.skills.thieving.configs.steal_locs
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Pickpocketing and stealing from stalls. Success-rate curve is a simplified single-value
 * approximation (see [ProtectedAccess.statRandom]), not verified against official per-target rate
 * tables. On failure, players are "stunned" via a short forced delay rather than a dedicated stun
 * status effect - no such system exists yet in this engine, and official thieving also applies
 * retaliation damage on a failed pickpocket, which is intentionally not implemented here to avoid
 * fabricating a per-npc damage table.
 */
class Thieving
@Inject
constructor(
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
    private val invisibleLvls: InvisibleLevels,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc3(pickpocket_npcs.man) { pickpocket(it.npc, it.npc.visType) }
        onOpNpc3(pickpocket_npcs.woman) { pickpocket(it.npc, it.npc.visType) }
        onOpNpc3(pickpocket_npcs.guard) { pickpocket(it.npc, it.npc.visType) }

        onOpLoc1(steal_locs.tea_stall) { steal(it.loc, it.type) }
    }

    private suspend fun ProtectedAccess.pickpocket(npc: Npc, type: UnpackedNpcType) {
        if (player.thievingLvl < type.pickpocketLevelReq) {
            mes("You need a Thieving level of ${type.pickpocketLevelReq} to steal from this npc.")
            return
        }

        val success = statRandom(stats.thieving, PICKPOCKET_LOW, PICKPOCKET_HIGH, invisibleLvls)
        if (!success) {
            mes("You fail to pick the ${type.name.lowercase()}'s pocket.")
            delay(2)
            return
        }

        val xp = type.pickpocketXp * xpMods.get(player, stats.thieving)
        val gp = random.of(1, type.pickpocketLevelReq * 2 + 5)
        spam("You pick the ${type.name.lowercase()}'s pocket.")
        statAdvance(stats.thieving, xp)
        invAddOrDrop(objRepo, objs.coins, gp)
    }

    private suspend fun ProtectedAccess.steal(loc: BoundLocInfo, type: UnpackedLocType) {
        if (player.thievingLvl < type.stealLevelReq) {
            mes("You need a Thieving level of ${type.stealLevelReq} to steal from this stall.")
            return
        }

        val success = statRandom(stats.thieving, STEAL_LOW, STEAL_HIGH, invisibleLvls)
        if (!success) {
            mes("You fail to steal anything from the stall.")
            delay(2)
            return
        }

        val xp = type.stealXp * xpMods.get(player, stats.thieving)
        val gp = random.of(1, type.stealLevelReq * 3)
        spam("You steal from the stall.")
        statAdvance(stats.thieving, xp)
        invAddOrDrop(objRepo, objs.coins, gp)

        val respawnTime = random.of(type.stealRespawnLow, type.stealRespawnHigh)
        locRepo.del(loc, respawnTime)
    }

    companion object {
        val UnpackedNpcType.pickpocketLevelReq: Int by npcParam(params.levelrequire)
        val UnpackedNpcType.pickpocketXp: Double by npcXpParam(params.skill_xp)

        val UnpackedLocType.stealLevelReq: Int by locParam(params.levelrequire)
        val UnpackedLocType.stealXp: Double by locXpParam(params.skill_xp)
        val UnpackedLocType.stealRespawnLow: Int by locParam(params.respawn_time_low)
        val UnpackedLocType.stealRespawnHigh: Int by locParam(params.respawn_time_high)

        private const val PICKPOCKET_LOW = 40
        private const val PICKPOCKET_HIGH = 230
        private const val STEAL_LOW = 60
        private const val STEAL_HIGH = 240
    }
}
