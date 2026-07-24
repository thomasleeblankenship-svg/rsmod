package org.rsmod.api.player.diary

import org.rsmod.api.config.refs.varps
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

/**
 * A **partial** tracker for the Lumbridge & Draynor achievement diary's easy tier - 3 of the real
 * easy-tier tasks (smithing a bronze bar, stealing from the Baker's stall, spinning a bowstring),
 * not the full official list (~9 tasks). This intentionally does **not** touch the real
 * `lumbridge_diary_easy_complete`/`lumbridge_easy_count` cache varbits, since those have official
 * semantics tied to the full task list that isn't implemented here - writing to them would
 * misrepresent diary state to any other system that reads them. Progress is tracked instead via a
 * new custom varp (`diary_lumbridge_easy_progress`), the same pattern already used for the Slayer
 * task varps.
 *
 * The diary task-list side panel overlay (`// TODO(content): Open achievement diary overlay` in
 * [org.rsmod.content.areas.city.lumbridge.npcs.HatiusCosaintus]) is a separate, unbuilt UI system;
 * this only tracks completion server-side and grants the easy-tier ring once all 3 tracked tasks
 * are done.
 */
public object LumbridgeDraynorDiary {
    public const val TASK_SMITH_BRONZE_BAR: Int = 1
    public const val TASK_STEAL_BAKERS_STALL: Int = 2
    public const val TASK_SPIN_BOWSTRING: Int = 4
    private const val TASK_CLAIMED: Int = 8

    private const val ALL_TASKS =
        TASK_SMITH_BRONZE_BAR or TASK_STEAL_BAKERS_STALL or TASK_SPIN_BOWSTRING

    private var Player.progress: Int by intVarp(varps.diary_lumbridge_easy_progress)

    public fun ProtectedAccess.completeEasyTask(task: Int) {
        if (player.progress and task != 0) {
            return
        }
        player.progress = player.progress or task
        if (player.progress and ALL_TASKS == ALL_TASKS) {
            mes("You have completed the tracked Lumbridge & Draynor easy diary tasks!")
            mes("Speak to Hatius Cosaintus to claim your reward.")
        }
    }

    public fun ProtectedAccess.hasCompletedEasyTasks(): Boolean =
        player.progress and ALL_TASKS == ALL_TASKS

    public fun ProtectedAccess.hasClaimedEasyReward(): Boolean =
        player.progress and TASK_CLAIMED != 0

    public fun ProtectedAccess.markEasyRewardClaimed() {
        player.progress = player.progress or TASK_CLAIMED
    }
}
