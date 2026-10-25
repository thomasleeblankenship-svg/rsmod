package org.rsmod.api.world.list

/**
 * A single world as advertised by the [WorldList].
 *
 * @param id The world id (e.g., `301`).
 * @param host The address clients connect to when switching to this world, or `null` if the world
 *   has not been given one and therefore cannot be switched to.
 * @param activity The activity text displayed next to the world (e.g., "Trade"), if any.
 * @param flags The client world-list property flags (e.g., members, pvp).
 * @param location The client world-list location id (e.g., `0` for United States).
 * @param population The player count last reported by the world.
 * @param online `true` if the world has sent a heartbeat within [WorldListService.ONLINE_TIMEOUT].
 */
public data class WorldListEntry(
    val id: Int,
    val host: String?,
    val activity: String?,
    val flags: Int,
    val location: Int,
    val population: Int,
    val online: Boolean,
)
