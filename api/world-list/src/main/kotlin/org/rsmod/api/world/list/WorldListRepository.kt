package org.rsmod.api.world.list

import org.rsmod.api.db.DatabaseConnection

/** Reads and writes the `worlds` table backing the [WorldList]. */
public class WorldListRepository {
    /**
     * Reports that [world] is online with [population] players.
     *
     * @return `false` if [world] is not registered in the `worlds` table.
     */
    public fun heartbeat(connection: DatabaseConnection, world: Int, population: Int): Boolean {
        val update =
            connection.prepareStatement(
                """
                    UPDATE worlds
                    SET population = ?, last_heartbeat = CURRENT_TIMESTAMP
                    WHERE id = ?
                """
                    .trimIndent()
            )
        update.use {
            it.setInt(1, population)
            it.setInt(2, world)
            return it.executeUpdate() > 0
        }
    }

    /** Marks [world] as offline so that it is immediately removed from other worlds' lists. */
    public fun markOffline(connection: DatabaseConnection, world: Int) {
        val update =
            connection.prepareStatement(
                "UPDATE worlds SET population = 0, last_heartbeat = NULL WHERE id = ?"
            )
        update.use {
            it.setInt(1, world)
            it.executeUpdate()
        }
    }

    /**
     * Selects every registered world. A world is reported as [WorldListEntry.online] when its last
     * heartbeat is no older than [onlineTimeoutSeconds].
     */
    public fun selectAll(
        connection: DatabaseConnection,
        onlineTimeoutSeconds: Int,
    ): List<WorldListEntry> {
        // Note: `datetime` is sqlite-specific and may need to be adapted for other database
        // engines.
        val select =
            connection.prepareStatement(
                """
                    SELECT id, host, activity, flags, location, population,
                        last_heartbeat >= datetime('now', ?) AS online
                    FROM worlds
                    ORDER BY id
                """
                    .trimIndent()
            )
        select.use {
            it.setString(1, "-$onlineTimeoutSeconds seconds")
            it.executeQuery().use { resultSet ->
                val entries = mutableListOf<WorldListEntry>()
                while (resultSet.next()) {
                    entries +=
                        WorldListEntry(
                            id = resultSet.getInt("id"),
                            host = resultSet.getString("host")?.takeUnless(String::isBlank),
                            activity = resultSet.getString("activity")?.takeUnless(String::isBlank),
                            flags = resultSet.getInt("flags"),
                            location = resultSet.getInt("location"),
                            population = resultSet.getInt("population"),
                            online = resultSet.getBoolean("online"),
                        )
                }
                return entries
            }
        }
    }
}
