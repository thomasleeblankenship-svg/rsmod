package org.rsmod.api.world.list

/**
 * The most recent snapshot of every world registered in the database.
 *
 * The snapshot is refreshed periodically by [WorldListService], so it may lag behind the database
 * by up to [WorldListService.HEARTBEAT_INTERVAL]. Callers should not cache its values.
 */
public class WorldList {
    @Volatile private var entries: List<WorldListEntry> = emptyList()

    /** All registered worlds, ordered by id. */
    public val worlds: List<WorldListEntry>
        get() = entries

    public operator fun get(id: Int): WorldListEntry? = entries.firstOrNull { it.id == id }

    internal fun update(entries: List<WorldListEntry>) {
        this.entries = entries
    }
}
