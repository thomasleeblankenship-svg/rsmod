package org.rsmod.content.skills.firemaking.configs

import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.type.editors.obj.ObjEditor
import org.rsmod.game.stat.PlayerStatMap
import org.rsmod.game.type.obj.ObjType

internal object FiremakingLogs : ObjEditor() {
    init {
        log(objs.logs, 1, 40.0)
        log(objs.oak_logs, 15, 60.0)
        log(objs.willow_logs, 30, 90.0)
        log(objs.teak_logs, 35, 105.0)
        log(objs.maple_logs, 45, 135.0)
        log(objs.mahogany_logs, 50, 157.5)
        log(objs.yew_logs, 60, 202.5)
        log(objs.magic_logs, 75, 303.8)
        log(objs.redwood_logs, 90, 350.0)
    }

    private fun log(type: ObjType, levelReq: Int, xp: Double) {
        edit(type) {
            contentGroup = content.firemaking_logs
            param[params.levelrequire] = levelReq
            param[params.skill_xp] = PlayerStatMap.toFineXP(xp).toInt()
        }
    }
}
