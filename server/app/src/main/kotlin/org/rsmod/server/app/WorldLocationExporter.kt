package org.rsmod.server.app

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Injector
import java.io.File
import org.rsmod.api.player.interact.LocInteractions
import org.rsmod.api.player.interact.NpcInteractions
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.game.entity.NpcList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.vars.VarPlayerIntMap
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey

/**
 * Temporary one-off tool: dumps loc/npc placements from the fully-loaded game map into
 * world_locations.json, categorized by cache name pattern. Not part of the normal boot sequence -
 * invoked once from [GameServer] behind the `rsmod.exportWorldLocations` system property, then
 * removed.
 */
object WorldLocationExporter {
    fun export(injector: Injector) {
        val locZones = injector.getInstance(LocZoneStorage::class.java)
        val locRepo = injector.getInstance(LocRepository::class.java)
        val locTypes = injector.getInstance(LocTypeList::class.java)
        val locInteractions = injector.getInstance(LocInteractions::class.java)
        val npcInteractions = injector.getInstance(NpcInteractions::class.java)
        val npcList = injector.getInstance(NpcList::class.java)

        // A default (all-zero) var map resolves the visual variant a brand-new character with no
        // quest/world-state flags set would see - the same fallback the interaction code itself
        // uses when `multiVarValue` is unset. This is what lets categorization see the *visible*
        // loc (e.g. the real bank booth model) instead of the placeholder base type the raw map
        // data references.
        val defaultVars = VarPlayerIntMap()

        val locEntries = mutableListOf<Map<String, Any?>>()
        for (packed in locZones.mapLocs.zoneKeys()) {
            val zoneKey = ZoneKey(packed)
            for (loc in locRepo.findAll(zoneKey)) {
                val baseType = locTypes[loc.id] ?: continue
                val bound = BoundLocInfo(loc, baseType)
                val visLoc = locInteractions.multiLoc(bound, baseType, defaultVars)
                val visType = visLoc?.let { locTypes[it.id] } ?: baseType
                val category = categorizeLoc(visType.name) ?: continue
                val regionX = zoneKey.x / (64 / ZoneGrid.LENGTH)
                val regionZ = zoneKey.z / (64 / ZoneGrid.LENGTH)
                locEntries +=
                    mapOf(
                        "category" to category,
                        "name" to visType.name,
                        "locId" to (visLoc?.id ?: loc.id),
                        "regionId" to ((regionX shl 8) or regionZ),
                        "chunkId" to packed,
                        "x" to loc.coords.x,
                        "y" to loc.coords.z,
                        "level" to loc.coords.level,
                    )
            }
        }

        val npcEntries = mutableListOf<Map<String, Any?>>()
        for (npc in npcList) {
            val type = npcInteractions.multiNpc(npc.visType, defaultVars) ?: npc.visType
            val category = categorizeNpc(type.name, type.op) ?: continue
            val zoneKey = ZoneKey.from(npc.coords)
            val regionX = zoneKey.x / (64 / ZoneGrid.LENGTH)
            val regionZ = zoneKey.z / (64 / ZoneGrid.LENGTH)
            npcEntries +=
                mapOf(
                    "category" to category,
                    "name" to type.name,
                    "npcId" to type.id,
                    "regionId" to ((regionX shl 8) or regionZ),
                    "chunkId" to zoneKey.packed,
                    "x" to npc.coords.x,
                    "y" to npc.coords.z,
                    "level" to npc.coords.level,
                )
        }

        val output =
            mapOf(
                "locs" to locEntries,
                "npcs" to npcEntries,
                "locCount" to locEntries.size,
                "npcCount" to npcEntries.size,
            )

        val mapper = ObjectMapper()
        val file = File("world_locations.json")
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, output)
        println(
            "Wrote ${locEntries.size} loc entries and ${npcEntries.size} npc entries to " +
                file.absolutePath
        )
    }

    private fun categorizeLoc(name: String): String? {
        val lower = name.lowercase()
        return when {
            lower == "furnace" || lower == "anvil" -> "smithing"
            Regex("^[a-z]+rock[12]$").matches(lower) || lower.endsWith("_rocks") -> "mining_rock"
            lower.contains("bankbooth") ||
                lower.contains("bank_booth") ||
                lower == "bank" ||
                lower.contains("bankchest") ||
                lower.contains("bank_chest") -> "bank"
            lower.contains("patch") -> "farming_patch"
            (lower.contains("dungeon") || lower.contains("cave")) &&
                (lower.contains("entrance") ||
                    lower.contains("trapdoor") ||
                    lower.contains("ladder") ||
                    lower.contains("hole")) -> "dungeon_entrance"
            lower.contains("stall") -> "shop"
            lower.contains("tree") || lower.contains("stump") -> "tree"
            else -> null
        }
    }

    private fun categorizeNpc(name: String, ops: Array<String?>): String? {
        val lower = name.lowercase()
        val opSet = ops.filterNotNull().map { it.lowercase() }.toSet()
        return when {
            lower.contains("bank") -> "bank"
            "trade" in opSet || lower.contains("shopkeeper") || lower.contains("shop assistant") ->
                "shop"
            "net" in opSet ||
                "bait" in opSet ||
                "lure" in opSet ||
                "cage" in opSet ||
                "harpoon" in opSet ||
                lower.contains("fishing") -> "fishing_spot"
            else -> null
        }
    }
}
