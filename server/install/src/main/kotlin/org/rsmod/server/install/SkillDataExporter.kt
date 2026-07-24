package org.rsmod.server.install

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import org.openrs2.cache.Cache
import org.rsmod.api.cache.types.loc.LocTypeDecoder
import org.rsmod.api.cache.types.npc.NpcTypeDecoder
import org.rsmod.api.cache.types.obj.ObjTypeDecoder
import org.rsmod.server.shared.DirectoryConstants

/**
 * CLI tool: scans the full decoded cache (every npc/loc/obj *type*, not just placed map instances)
 * and groups anything relevant to skill training into skill_data.json, to speed up building new
 * skills.
 *
 * For each candidate, both the cache's human-readable *display* name and its internal *symbol* name
 * (the string you pass to `find(...)` when writing content code) are included side by side -
 * conflating the two silently produced zero-result bugs twice tonight (bank booths, mining rocks in
 * the world location exporter), so this tool always reports both explicitly.
 *
 * Categorization is driven by the npc/loc's own cache-defined right-click op text (e.g. "Mine",
 * "Net", "Steal-from", "Pickpocket"), which is the same signal the real client uses to decide what
 * options to show - not a name-pattern guess.
 *
 * Run via `gradlew exportSkillData` (see root build.gradle.kts).
 */
fun main() {
    val gameCachePath = DirectoryConstants.CACHE_PATH.resolve("game")
    val npcSymbols = readSymbols(DirectoryConstants.SYMBOL_PATH.resolve("npc.sym"))
    val locSymbols = readSymbols(DirectoryConstants.SYMBOL_PATH.resolve("loc.sym"))
    val objSymbols = readSymbols(DirectoryConstants.SYMBOL_PATH.resolve("obj.sym"))

    Cache.open(gameCachePath).use { cache ->
        val npcs = NpcTypeDecoder.decodeAll(cache)
        val locs = LocTypeDecoder.decodeAll(cache)
        val objs = ObjTypeDecoder.decodeAll(cache)

        val npcEntries = mutableListOf<Map<String, Any?>>()
        for ((id, type) in npcs) {
            val category = categorize(type.op) ?: continue
            npcEntries +=
                entry(id, type.name, npcSymbols[id], category, type.op, type.attack, type.hitpoints)
        }

        val locEntries = mutableListOf<Map<String, Any?>>()
        for ((id, type) in locs) {
            val category = categorize(type.op) ?: continue
            locEntries += entry(id, type.name, locSymbols[id], category, type.op)
        }

        // Tools are not op-driven (an axe has no relevant right-click op on its own) so they're
        // grouped instead by common tool-name keywords, for a starting candidate list.
        val toolKeywords =
            listOf("axe", "pickaxe", "hatchet", "hammer", "rod", "net", "harpoon", "tinderbox")
        val objEntries = mutableListOf<Map<String, Any?>>()
        for ((id, type) in objs) {
            val lower = type.name.lowercase()
            if (toolKeywords.none { lower.contains(it) }) continue
            objEntries += entry(id, type.name, objSymbols[id], null, null)
        }

        val output =
            mapOf(
                "npcs" to npcEntries,
                "locs" to locEntries,
                "toolCandidateObjs" to objEntries,
                "npcCount" to npcEntries.size,
                "locCount" to locEntries.size,
                "objCount" to objEntries.size,
                "notes" to
                    mapOf(
                        "symbolName" to
                            ("The internal cache name to pass to find(...) - null means no " +
                                "matching entry was found in the .sym file for that id, in which " +
                                "case find(name) cannot be used by name and the type must be " +
                                "referenced another way."),
                        "displayName" to
                            "The human-readable name shown in-game - NOT the same as symbolName.",
                        "categories" to
                            "woodcutting, mining, fishing, thieving, farming, agility, crafting, " +
                                "construction, runecrafting - inferred from the cache's own op text.",
                    ),
            )

        val mapper = ObjectMapper()
        val file = File("skill_data.json")
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, output)
        println(
            "Wrote ${npcEntries.size} npcs, ${locEntries.size} locs, ${objEntries.size} tool " +
                "candidates to ${file.absolutePath}"
        )
    }
}

private fun readSymbols(path: Path): Map<Int, String> {
    if (!path.exists()) return emptyMap()
    return path
        .readLines()
        .mapNotNull { line ->
            val parts = line.split('\t', limit = 2)
            val id = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val name = parts.getOrNull(1) ?: return@mapNotNull null
            id to name
        }
        .toMap()
}

private fun entry(
    id: Int,
    displayName: String,
    symbolName: String?,
    category: String?,
    ops: Array<String?>?,
    attack: Int? = null,
    hitpoints: Int? = null,
): Map<String, Any?> =
    mapOf(
        "id" to id,
        "displayName" to displayName,
        "symbolName" to symbolName,
        "category" to category,
        "ops" to ops?.filterNotNull(),
        "attack" to attack,
        "hitpoints" to hitpoints,
    )

private val skillVerbs =
    mapOf(
        "chop down" to "woodcutting",
        "mine" to "mining",
        "net" to "fishing",
        "bait" to "fishing",
        "lure" to "fishing",
        "cage" to "fishing",
        "harpoon" to "fishing",
        "pickpocket" to "thieving",
        "steal-from" to "thieving",
        "steal" to "thieving",
        "rake" to "farming",
        "plant" to "farming",
        "harvest" to "farming",
        "pick" to "farming",
        "climb" to "agility",
        "jump" to "agility",
        "swing" to "agility",
        "balance" to "agility",
        "cross" to "agility",
        "spin" to "crafting",
        "craft-rune" to "runecrafting",
        "build" to "construction",
    )

private fun categorize(ops: Array<String?>?): String? {
    val opSet = ops?.filterNotNull()?.map { it.lowercase() } ?: return null
    for ((verb, category) in skillVerbs) {
        if (opSet.any { it.contains(verb) }) return category
    }
    return null
}
