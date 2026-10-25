package org.rsmod.api.world.list

import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rsmod.api.db.DatabaseConfig
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.migration.FlywayMigration
import org.rsmod.api.db.sqlite.SqliteConnection
import org.rsmod.api.db.sqlite.SqliteDatabase

class WorldListRepositoryTest {
    @TempDir lateinit var tempDir: Path

    private val repository = WorldListRepository()

    @Test
    fun `default world is listed offline with a local host`() = withDatabase {
        val world = repository.selectAll(this, ONLINE_TIMEOUT).single()
        assertEquals(1, world.id)
        assertEquals("127.0.0.1", world.host)
        assertFalse(world.online)
    }

    @Test
    fun `heartbeat lists world as online with its population`() = withDatabase {
        assertTrue(repository.heartbeat(this, world = 1, population = 25))

        val world = repository.selectAll(this, ONLINE_TIMEOUT).single()
        assertTrue(world.online)
        assertEquals(25, world.population)
    }

    @Test
    fun `heartbeat for unregistered world reports failure`() = withDatabase {
        assertFalse(repository.heartbeat(this, world = 2, population = 0))
    }

    @Test
    fun `stale heartbeat lists world as offline`() = withDatabase {
        execute("UPDATE worlds SET last_heartbeat = datetime('now', '-60 seconds') WHERE id = 1")
        assertFalse(repository.selectAll(this, ONLINE_TIMEOUT).single().online)
    }

    @Test
    fun `mark offline clears heartbeat and population`() = withDatabase {
        repository.heartbeat(this, world = 1, population = 25)
        repository.markOffline(this, world = 1)

        val world = repository.selectAll(this, ONLINE_TIMEOUT).single()
        assertFalse(world.online)
        assertEquals(0, world.population)
    }

    @Test
    fun `worlds are ordered by id`() = withDatabase {
        execute("INSERT INTO worlds (id, host) VALUES (3, 'c'), (2, 'b')")
        val ids = repository.selectAll(this, ONLINE_TIMEOUT).map(WorldListEntry::id)
        assertEquals(listOf(1, 2, 3), ids)
    }

    private fun DatabaseConnection.execute(sql: String) {
        prepareStatement(sql).use { it.executeUpdate() }
    }

    private fun withDatabase(block: DatabaseConnection.() -> Unit) {
        val path = tempDir.resolve("test.db").toString()
        val config = DatabaseConfig("jdbc:sqlite:", path, null, null)
        FlywayMigration(config).migrate()
        val database = SqliteDatabase()
        database.connect(SqliteConnection(config))
        try {
            runBlocking { database.withTransaction { block(it) } }
        } finally {
            database.close()
        }
    }

    private companion object {
        private const val ONLINE_TIMEOUT = 30
    }
}
