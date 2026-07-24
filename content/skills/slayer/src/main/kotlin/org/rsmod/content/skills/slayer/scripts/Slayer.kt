package org.rsmod.content.skills.slayer.scripts

import org.rsmod.api.config.refs.varps
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.skills.slayer.configs.slayer_masters
import org.rsmod.game.entity.Player
import org.rsmod.game.type.npc.NpcType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Slayer task assignment for all 13 real slayer masters found in the cache. Talking to a master
 * assigns a random task from a simplified per-tier monster list (illustrative of the master's real
 * difficulty tier, not the official per-master probability tables) and stores it persistently via
 * new `slayer_task_id`/`slayer_task_count` varps, following the same custom-varp pattern already
 * used for the bank pin.
 *
 * Kill tracking and task completion are intentionally **not** implemented: this engine has no
 * general "on npc death" hook a content script can subscribe to independently (the only existing
 * one, [org.rsmod.api.death.plugin.NpcDeathScript], is a single fixed handler for the whole death
 * pipeline, and `// TODO: Drop tables` in [org.rsmod.api.death.NpcDeath] shows even drop handling
 * is still a work in progress there). Wiring real completion tracking would mean modifying that
 * shared pipeline, which is out of scope for a single content skill. So a task can be assigned and
 * re-rolled freely, but there are no slayer points or task completion here.
 */
class Slayer : PluginScript() {
    override fun ScriptContext.startup() {
        for (master in MASTERS.keys) {
            onOpNpc3(master) { assign(master) }
        }
    }

    private suspend fun ProtectedAccess.assign(master: NpcType) {
        val tier = MASTERS.getValue(master)

        val taskIndex = random.of(0, tier.tasks.size - 1)
        val task = tier.tasks[taskIndex]
        val count = random.of(task.minCount, task.maxCount)
        player.slayerTaskId = taskIndex
        player.slayerTaskCount = count
        mes("Your new task is to kill ${task.name} ($count).")
    }

    private data class SlayerTask(val name: String, val minCount: Int, val maxCount: Int)

    private data class MasterTier(val tasks: List<SlayerTask>)

    companion object {
        private var Player.slayerTaskId: Int by intVarp(varps.slayer_task_id)
        private var Player.slayerTaskCount: Int by intVarp(varps.slayer_task_count)

        private val NOVICE =
            MasterTier(
                listOf(
                    SlayerTask("Crawling Hands", 15, 50),
                    SlayerTask("Cave bugs", 15, 50),
                    SlayerTask("Cave slimes", 15, 50),
                    SlayerTask("Banshees", 15, 50),
                    SlayerTask("Rock slugs", 15, 50),
                    SlayerTask("Vampyres", 10, 20),
                )
            )

        private val APPRENTICE =
            MasterTier(
                listOf(
                    SlayerTask("Cockroach soldiers", 15, 50),
                    SlayerTask("Cave bats", 15, 50),
                    SlayerTask("Wall beasts", 10, 20),
                    SlayerTask("Barbarian spirits", 15, 50),
                )
            )

        private val ADEPT =
            MasterTier(
                listOf(
                    SlayerTask("Hill giants", 20, 90),
                    SlayerTask("Moss giants", 20, 90),
                    SlayerTask("Ogres", 20, 90),
                    SlayerTask("Lesser demons", 20, 90),
                    SlayerTask("Hobgoblins", 20, 90),
                )
            )

        private val EXPERT =
            MasterTier(
                listOf(
                    SlayerTask("Bloodveld", 30, 90),
                    SlayerTask("Turoth", 30, 90),
                    SlayerTask("Basilisks", 30, 90),
                    SlayerTask("Jellies", 30, 90),
                    SlayerTask("Aberrant spectres", 30, 90),
                )
            )

        private val MASTER =
            MasterTier(
                listOf(
                    SlayerTask("Black demons", 40, 140),
                    SlayerTask("Greater demons", 40, 140),
                    SlayerTask("Dust devils", 40, 140),
                    SlayerTask("Kalphites", 40, 140),
                    SlayerTask("Trolls", 40, 140),
                    SlayerTask("Dagannoth", 40, 140),
                )
            )

        private val ELITE =
            MasterTier(
                listOf(
                    SlayerTask("Abyssal demons", 60, 190),
                    SlayerTask("Dark beasts", 60, 190),
                    SlayerTask("Nechryael", 60, 190),
                    SlayerTask("Gargoyles", 60, 190),
                    SlayerTask("Cave krakens", 60, 190),
                )
            )

        private val WILDERNESS =
            MasterTier(
                listOf(
                    SlayerTask("Chaos druids", 10, 30),
                    SlayerTask("Skeletons", 10, 30),
                    SlayerTask("Spiders", 10, 30),
                    SlayerTask("Revenants", 10, 30),
                )
            )

        private val MASTERS: Map<NpcType, MasterTier> by lazy {
            mapOf(
                slayer_masters.turael to NOVICE,
                slayer_masters.aya to NOVICE,
                slayer_masters.mazchna to APPRENTICE,
                slayer_masters.achtryn to APPRENTICE,
                slayer_masters.vannaka to ADEPT,
                slayer_masters.spria to ADEPT,
                slayer_masters.chaeldar to EXPERT,
                slayer_masters.nieve to MASTER,
                slayer_masters.steve to MASTER,
                slayer_masters.konar to MASTER,
                slayer_masters.duradel to ELITE,
                slayer_masters.kuradal to ELITE,
                slayer_masters.krystilia to WILDERNESS,
            )
        }
    }
}
