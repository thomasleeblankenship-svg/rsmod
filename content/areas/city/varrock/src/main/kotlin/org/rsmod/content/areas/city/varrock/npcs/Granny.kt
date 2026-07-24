package org.rsmod.content.areas.city.varrock.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.areas.city.varrock.configs.varrock_npcs
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class Granny : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(varrock_npcs.granny) { grannyDialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.grannyDialogue(npc: Npc) = startDialogue(npc) { chat() }

    private suspend fun Dialogue.chat() {
        chatNpc(happy, "Hello dear, lovely day, isn't it?")
        val choice = choice2("Yes, lovely weather.", 1, "I'm looking for the bank.", 2)
        when (choice) {
            1 -> chatPlayer(happy, "Yes, lovely weather.")
            2 -> {
                chatPlayer(neutral, "I'm looking for the bank.")
                chatNpc(neutral, "Just follow the square around, dear, you can't miss it.")
            }
        }
    }
}
