package org.rsmod.content.skills.mining.configs

import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.type.editors.obj.ObjEditor
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.seq.SeqType

internal object MiningPickaxes : ObjEditor() {
    init {
        pickaxe(objs.bronze_pickaxe, 1, seqs.human_mining_bronze_pickaxe)
        pickaxe(objs.iron_pickaxe, 1, seqs.human_mining_iron_pickaxe)
        pickaxe(objs.steel_pickaxe, 6, seqs.human_mining_steel_pickaxe)
        pickaxe(objs.black_pickaxe, 11, seqs.human_mining_black_pickaxe)
        pickaxe(objs.mithril_pickaxe, 21, seqs.human_mining_mithril_pickaxe)
        pickaxe(objs.adamant_pickaxe, 31, seqs.human_mining_adamant_pickaxe)
        pickaxe(objs.rune_pickaxe, 41, seqs.human_mining_rune_pickaxe)
        pickaxe(objs.dragon_pickaxe, 61, seqs.human_mining_dragon_pickaxe_pretty)
    }

    private fun pickaxe(type: ObjType, levelReq: Int, anim: SeqType) {
        edit(type) {
            contentGroup = content.mining_pickaxe
            param[params.levelrequire] = levelReq
            param[params.skill_anim] = anim
        }
    }
}
