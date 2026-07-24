package org.rsmod.api.player.randomevent

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.npcs
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.npc.NpcTypeList

/**
 * A minimal, generic hook skill scripts can call after a successful gathering action to roll for a
 * random event spawn. This engine has no shared "player performed a skilling action" event bus
 * (each skill script drives its own action loop independently), so this is called directly from
 * each wired skill's success path rather than through a central dispatcher - a real central bus
 * would be a much larger change.
 *
 * Only one event is implemented: the Mysterious Old Man
 * ([org.rsmod.content.other.randomevents.scripts.MysteriousOldMan]), spawned next to the player for
 * a short time. No reward is granted for interacting with it, matching the base classic behaviour
 * (some later game versions added an xp lamp reward, which is intentionally not implemented here to
 * avoid fabricating reward values).
 */
@Singleton
public class RandomEventTrigger
@Inject
constructor(private val npcTypes: NpcTypeList, private val npcRepo: NpcRepository) {
    public fun ProtectedAccess.tryTriggerMysteriousOldMan() {
        if (random.of(1, TRIGGER_CHANCE) != 1) {
            return
        }
        val npc = Npc(npcTypes[npcs.mysterious_old_man], player.coords)
        npcRepo.add(npc, duration = SPAWN_DURATION_TICKS)
        mes("You notice someone strange watching you from a distance...")
    }

    private companion object {
        private const val TRIGGER_CHANCE = 300
        private const val SPAWN_DURATION_TICKS = 50
    }
}
