package org.rsmod.content.other.randomevents.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.npcs
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Mysterious Old Man random event - spawned by
 * [org.rsmod.api.player.randomevent.RandomEventTrigger]. Talking to him gives one of a few cryptic
 * classic lines, then he vanishes; there is no reward, matching the base classic event (some later
 * game versions added an xp lamp, intentionally not implemented here).
 */
class MysteriousOldMan @Inject constructor(private val npcRepo: NpcRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(npcs.mysterious_old_man) { talk(it.npc) }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        mes(LINES[random.of(0, LINES.size - 1)])
        npcRepo.del(npc, DESPAWN_DELAY_TICKS)
    }

    private companion object {
        private const val DESPAWN_DELAY_TICKS = 2

        private val LINES =
            listOf(
                "The elder gods are always watching...",
                "Have you seen my son? He's about your height.",
                "I used to be an adventurer like you, until I took a wrong turn.",
                "Nothing is quite what it seems, is it?",
            )
    }
}
