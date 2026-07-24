package org.rsmod.content.skills.cooking.configs

import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.type.editors.obj.ObjEditor
import org.rsmod.game.stat.PlayerStatMap
import org.rsmod.game.type.obj.ObjType

/**
 * Burnt-product mapping and burn-stop levels live in
 * [org.rsmod.content.skills.cooking.scripts.Cooking] rather than as cache params (see that file for
 * why), so this editor only wires the cache-native params: content group, level requirement, XP,
 * and cooked product.
 */
internal object CookableFood : ObjEditor() {
    init {
        food(objs.raw_shrimp, objs.shrimps, 1, 30.0)
        food(objs.raw_anchovies, objs.anchovies, 1, 30.0)
        food(objs.raw_sardine, objs.sardine, 1, 40.0)
        food(objs.raw_herring, objs.herring, 5, 50.0)
        food(objs.raw_trout, objs.trout, 15, 70.0)
        food(objs.raw_salmon, objs.salmon, 25, 90.0)
        food(objs.raw_tuna, objs.tuna, 30, 100.0)
        food(objs.raw_lobster, objs.lobster, 40, 120.0)
        food(objs.raw_swordfish, objs.swordfish, 45, 140.0)
        food(objs.raw_shark, objs.shark, 80, 210.0)
    }

    private fun food(raw: ObjType, cooked: ObjType, levelReq: Int, xp: Double) {
        edit(raw) {
            contentGroup = content.cookable_food
            param[params.levelrequire] = levelReq
            param[params.skill_xp] = PlayerStatMap.toFineXP(xp).toInt()
            param[params.skill_productitem] = cooked
        }
    }
}
