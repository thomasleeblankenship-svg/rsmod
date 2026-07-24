package org.rsmod.content.areas.city.varrock.npcs

import jakarta.inject.Inject
import org.rsmod.api.config.refs.BaseInvs
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.content.areas.city.varrock.configs.varrock_npcs
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.inv.InvType
import org.rsmod.game.type.npc.NpcType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The 5 real Varrock shopkeepers found in the cache (verified via
 * [org.rsmod.server.install.SkillDataExporter]): Horvik (armour), Zaff (staffs), Lowe (archery),
 * the sword shop's two staff, and Aubury (runes). Other well-known Varrock shopkeepers (Thessalia's
 * clothes shop, Baraek's) exist in the cache but were left out - no matching shop inventory symbol
 * could be confirmed for them.
 */
class VarrockShops @Inject constructor(private val shops: Shops) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((npc, shop) in SHOPS) {
            onOpNpc1(npc) { shopDialogue(it.npc, shop) }
            onOpNpc3(npc) { player.openShop(it.npc, shop) }
        }
    }

    private fun Player.openShop(npc: Npc, shop: ShopDef) {
        shops.open(this, npc, shop.name, shop.inv)
    }

    private suspend fun ProtectedAccess.shopDialogue(npc: Npc, shop: ShopDef) =
        startDialogue(npc) { shopKeeper(npc, shop) }

    private suspend fun Dialogue.shopKeeper(npc: Npc, shop: ShopDef) {
        chatNpc(happy, "Can I help you at all?")
        val choice = choice2("Yes please. What are you selling?", 1, "No thanks.", 2)
        if (choice == 1) {
            access.player.openShop(npc, shop)
        } else {
            chatPlayer(neutral, "No thanks.")
        }
    }

    private data class ShopDef(val name: String, val inv: InvType)

    companion object {
        private val SHOPS: Map<NpcType, ShopDef> by lazy {
            mapOf(
                varrock_npcs.horvik to ShopDef("Horvik's Armoury", BaseInvs.armourshop),
                varrock_npcs.zaff to ShopDef("Zaff's Superior Staffs", BaseInvs.staffshop),
                varrock_npcs.lowe to ShopDef("Lowe's Archery Emporium", BaseInvs.archeryshop),
                varrock_npcs.swordshop1 to ShopDef("Varrock Sword Shop", BaseInvs.swordshop),
                varrock_npcs.swordshop2 to ShopDef("Varrock Sword Shop", BaseInvs.swordshop),
                varrock_npcs.aubury to ShopDef("Aubury's Rune Shop", BaseInvs.runeshop),
            )
        }
    }
}
