package org.rsmod.content.skills.mining.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.locParam
import org.rsmod.api.config.locXpParam
import org.rsmod.api.config.objParam
import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.synths
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.levelmod.InvisibleLevels
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Success-rate curve is a simplified approximation (pickaxe tier bonus is a plain Kotlin lookup
 * table, not a cache param - custom `ObjType`/`Int` params defined via `ParamBuilder` were not
 * resolved by the time obj editors ran at boot, a pre-existing resolver-ordering issue), not
 * verified against official per-ore/per-pickaxe rate tables.
 */
class Mining
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val locRepo: LocRepository,
    private val xpMods: XpModifiers,
    private val invisibleLvls: InvisibleLevels,
    private val mapClock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(content.ore) { attempt(it.loc, it.type) }
    }

    private fun ProtectedAccess.attempt(rock: BoundLocInfo, type: UnpackedLocType) {
        if (player.miningLvl < type.oreLevelReq) {
            mes("You need a Mining level of ${type.oreLevelReq} to mine this rock.")
            return
        }

        val pickaxe = findPickaxe(player, objTypes)
        if (pickaxe == null) {
            mes("You need a pickaxe to mine this rock.")
            mes("You do not have a pickaxe which you have the mining level to use.")
            return
        }

        if (inv.isFull()) {
            val product = objTypes[type.oreProduct]
            mes("Your inventory is too full to hold any more ${product.name.lowercase()}.")
            soundSynth(synths.pillory_wrong)
            return
        }

        anim(objTypes[pickaxe].pickaxeMiningAnim)
        spam("You swing your pickaxe at the rock.")
        mine(rock, type, pickaxe)
    }

    private fun ProtectedAccess.mine(rock: BoundLocInfo, type: UnpackedLocType, pickaxe: InvObj) {
        val tier = PICKAXE_TIER[pickaxe.id] ?: 0
        val low = (40 + tier).coerceAtMost(255)
        val high = (220 + tier).coerceAtMost(255)
        val mined = statRandom(stats.mining, low, high, invisibleLvls)
        if (!mined) {
            return
        }

        val product = objTypes[type.oreProduct]
        val xp = type.oreXp * xpMods.get(player, stats.mining)
        spam("You manage to mine some ${product.name.lowercase()}.")
        statAdvance(stats.mining, xp)
        invAdd(inv, product)

        val respawnTime = random.of(type.oreRespawnLow, type.oreRespawnHigh)
        locRepo.del(rock, respawnTime)
    }

    companion object {
        val UnpackedObjType.pickaxeLevelReq: Int by objParam(params.levelrequire)
        val UnpackedObjType.pickaxeMiningAnim: org.rsmod.game.type.seq.SeqType by
            objParam(params.skill_anim)

        val PICKAXE_TIER: Map<Int, Int> by lazy {
            mapOf(
                objs.bronze_pickaxe.id to 0,
                objs.iron_pickaxe.id to 10,
                objs.steel_pickaxe.id to 20,
                objs.black_pickaxe.id to 25,
                objs.mithril_pickaxe.id to 30,
                objs.adamant_pickaxe.id to 40,
                objs.rune_pickaxe.id to 55,
                objs.dragon_pickaxe.id to 70,
            )
        }

        val UnpackedLocType.oreLevelReq: Int by locParam(params.levelrequire)
        val UnpackedLocType.oreProduct: ObjType by locParam(params.skill_productitem)
        val UnpackedLocType.oreXp: Double by locXpParam(params.skill_xp)
        val UnpackedLocType.oreRespawnLow: Int by locParam(params.respawn_time_low)
        val UnpackedLocType.oreRespawnHigh: Int by locParam(params.respawn_time_high)

        fun findPickaxe(player: Player, objTypes: ObjTypeList): InvObj? {
            val worn = player.wornPickaxe(objTypes)
            val carried = player.carriedPickaxe(objTypes)
            if (worn != null && carried != null) {
                if (objTypes[worn].pickaxeLevelReq >= objTypes[carried].pickaxeLevelReq) {
                    return worn
                }
                return carried
            }
            return worn ?: carried
        }

        private fun Player.wornPickaxe(objTypes: ObjTypeList): InvObj? {
            val righthand = righthand ?: return null
            return righthand.takeIf { objTypes[it].isUsablePickaxe(miningLvl) }
        }

        private fun Player.carriedPickaxe(objTypes: ObjTypeList): InvObj? {
            return inv.filterNotNull { objTypes[it].isUsablePickaxe(miningLvl) }
                .maxByOrNull { objTypes[it].pickaxeLevelReq }
        }

        private fun UnpackedObjType.isUsablePickaxe(miningLevel: Int): Boolean =
            isContentType(content.mining_pickaxe) && miningLevel >= pickaxeLevelReq
    }
}
