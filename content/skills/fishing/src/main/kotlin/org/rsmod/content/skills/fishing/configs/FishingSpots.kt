package org.rsmod.content.skills.fishing.configs

import org.rsmod.api.type.refs.npc.NpcReferences

internal typealias fishing_npcs = FishingSpots

/**
 * Only the beginner net-fishing spot (`newbiefishing`) has been verified against the real cache's
 * op text ("Net"). Bait/lure/cage/harpoon spots exist in the live game under area-specific names
 * that have not been identified here, so they are intentionally not wired yet.
 */
internal object FishingSpots : NpcReferences() {
    val newbie_net_spot = find("0_48_48_newbiefishing")
}
