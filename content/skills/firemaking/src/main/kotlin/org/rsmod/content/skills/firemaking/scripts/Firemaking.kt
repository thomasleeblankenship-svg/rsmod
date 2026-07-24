package org.rsmod.content.skills.firemaking.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.objParam
import org.rsmod.api.config.objXpParam
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.firemakingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.firemaking.configs.fm_locs
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Firemaking
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val locRepo: LocRepository,
    private val xpMods: XpModifiers,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (log in logs) {
            onOpHeldU(objs.tinderbox, log) { attempt(log) }
        }
    }

    private suspend fun ProtectedAccess.attempt(log: ObjType) {
        val type = objTypes[log]

        if (player.firemakingLvl < type.logLevelReq) {
            mes("You need a Firemaking level of ${type.logLevelReq} to light these logs.")
            return
        }

        if (locRepo.findExact(player.coords, fm_locs.fire) != null) {
            mes("You can't light a fire here.")
            return
        }

        anim(seqs.human_createfire)
        spam("You attempt to light the logs.")
        delay(3)

        val removed = invDel(inv, log)
        if (removed.failure) {
            return
        }

        val xp = type.logXp * xpMods.get(player, stats.firemaking)
        statAdvance(stats.firemaking, xp)
        spam("The fire catches and the logs begin to burn.")

        locRepo.add(player.coords, fm_locs.fire, 200, LocAngle.North, LocShape.CentrepieceStraight)
    }

    companion object {
        val logs =
            listOf(
                objs.logs,
                objs.oak_logs,
                objs.willow_logs,
                objs.teak_logs,
                objs.maple_logs,
                objs.mahogany_logs,
                objs.yew_logs,
                objs.magic_logs,
                objs.redwood_logs,
            )

        val UnpackedObjType.logLevelReq: Int by objParam(params.levelrequire)
        val UnpackedObjType.logXp: Double by objXpParam(params.skill_xp)
    }
}
