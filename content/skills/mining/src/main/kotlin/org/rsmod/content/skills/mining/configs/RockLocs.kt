package org.rsmod.content.skills.mining.configs

import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.type.editors.loc.LocEditor
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.game.stat.PlayerStatMap
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.obj.ObjType

internal typealias rocks = RockLocs

internal object RockLocs : LocReferences() {
    val clayrock1 = find("clayrock1")
    val clayrock2 = find("clayrock2")
    val copperrock1 = find("copperrock1")
    val copperrock2 = find("copperrock2")
    val tinrock1 = find("tinrock1")
    val tinrock2 = find("tinrock2")
    val ironrock1 = find("ironrock1")
    val ironrock2 = find("ironrock2")
    val silverrock1 = find("silverrock1")
    val silverrock2 = find("silverrock2")
    val coalrock1 = find("coalrock1")
    val coalrock2 = find("coalrock2")
    val goldrock1 = find("goldrock1")
    val goldrock2 = find("goldrock2")
    val mithrilrock1 = find("mithrilrock1")
    val mithrilrock2 = find("mithrilrock2")
    val adamantiterock1 = find("adamantiterock1")
    val adamantiterock2 = find("adamantiterock2")
    val runiterock1 = find("runiterock1")
    val runiterock2 = find("runiterock2")
}

internal object RockLocEditor : LocEditor() {
    init {
        clay(rocks.clayrock1)
        clay(rocks.clayrock2)
        ore(rocks.copperrock1, 1, 17.5, objs.copper_ore, 3, 50)
        ore(rocks.copperrock2, 1, 17.5, objs.copper_ore, 3, 50)
        ore(rocks.tinrock1, 1, 17.5, objs.tin_ore, 3, 50)
        ore(rocks.tinrock2, 1, 17.5, objs.tin_ore, 3, 50)
        ore(rocks.ironrock1, 15, 35.0, objs.iron_ore, 6, 90)
        ore(rocks.ironrock2, 15, 35.0, objs.iron_ore, 6, 90)
        ore(rocks.silverrock1, 20, 40.0, objs.silver_ore, 60, 100)
        ore(rocks.silverrock2, 20, 40.0, objs.silver_ore, 60, 100)
        ore(rocks.coalrock1, 30, 50.0, objs.coal, 30, 50)
        ore(rocks.coalrock2, 30, 50.0, objs.coal, 30, 50)
        ore(rocks.goldrock1, 40, 65.0, objs.gold_ore, 90, 150)
        ore(rocks.goldrock2, 40, 65.0, objs.gold_ore, 90, 150)
        ore(rocks.mithrilrock1, 55, 80.0, objs.mithril_ore, 100, 160)
        ore(rocks.mithrilrock2, 55, 80.0, objs.mithril_ore, 100, 160)
        ore(rocks.adamantiterock1, 70, 95.0, objs.adamantite_ore, 400, 500)
        ore(rocks.adamantiterock2, 70, 95.0, objs.adamantite_ore, 400, 500)
        ore(rocks.runiterock1, 85, 125.0, objs.runite_ore, 2000, 2400)
        ore(rocks.runiterock2, 85, 125.0, objs.runite_ore, 2000, 2400)
    }

    private fun clay(type: LocType) {
        ore(type, 1, 5.0, objs.clay, 3, 50)
    }

    private fun ore(
        type: LocType,
        levelReq: Int,
        xp: Double,
        product: ObjType,
        respawnLow: Int,
        respawnHigh: Int,
    ) {
        edit(type) {
            contentGroup = content.ore
            param[params.levelrequire] = levelReq
            param[params.skill_xp] = PlayerStatMap.toFineXP(xp).toInt()
            param[params.skill_productitem] = product
            param[params.respawn_time_low] = respawnLow
            param[params.respawn_time_high] = respawnHigh
        }
    }
}
