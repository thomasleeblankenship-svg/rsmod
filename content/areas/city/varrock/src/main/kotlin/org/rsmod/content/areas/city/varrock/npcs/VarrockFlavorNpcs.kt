package org.rsmod.content.areas.city.varrock.npcs

import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.areas.city.varrock.configs.varrock_npcs
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Simple flavor dialogue for the 3 remaining real Varrock npcs found in the cache with a confirmed
 * Talk-to op (see [org.rsmod.content.areas.city.varrock.configs.VarrockNpcs] for the ones that were
 * excluded and why). Dialogue text is original flavor writing, not verified against official
 * wording.
 */
class VarrockFlavorNpcs : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(varrock_npcs.woman1) { womanDialogue(it.npc) }
        onOpNpc1(varrock_npcs.guard_trainer) { trainerDialogue(it.npc) }
        onOpNpc1(varrock_npcs.granny2) { granny2Dialogue(it.npc) }
    }

    private suspend fun ProtectedAccess.womanDialogue(npc: Npc) =
        startDialogue(npc) {
            chatNpc(happy, "Lovely day for a walk around the square, isn't it?")
            chatPlayer(happy, "It certainly is.")
        }

    private suspend fun ProtectedAccess.trainerDialogue(npc: Npc) =
        startDialogue(npc) { trainerChat() }

    private suspend fun Dialogue.trainerChat() {
        chatNpc(neutral, "Stand up straight, recruit! We don't tolerate slouching in this guard.")
        val choice = choice2("Are you training new guards?", 1, "Sorry, wrong person.", 2)
        when (choice) {
            1 -> {
                chatPlayer(quiz, "Are you training new guards?")
                chatNpc(
                    neutral,
                    "That's right. Varrock's guards don't just appear out of thin air, you know.",
                )
            }
            2 -> chatPlayer(neutral, "Sorry, wrong person.")
        }
    }

    private suspend fun ProtectedAccess.granny2Dialogue(npc: Npc) =
        startDialogue(npc) {
            chatNpc(happy, "Oh, hello there! Are you lost, dear?")
            chatPlayer(neutral, "No, just having a look around.")
            chatNpc(happy, "Well, do enjoy your visit to Varrock.")
        }
}
