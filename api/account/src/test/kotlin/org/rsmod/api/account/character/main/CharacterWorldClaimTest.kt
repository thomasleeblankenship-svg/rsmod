package org.rsmod.api.account.character.main

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rsmod.api.db.DatabaseConfig
import org.rsmod.api.db.DatabaseConnection
import org.rsmod.api.db.migration.FlywayMigration
import org.rsmod.api.db.sqlite.SqliteConnection
import org.rsmod.api.db.sqlite.SqliteDatabase
import org.rsmod.api.realm.Realm
import org.rsmod.api.realm.RealmConfig
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.game.entity.Player
import org.rsmod.game.type.mod.ModLevelTypeList
import org.rsmod.game.type.mod.UnpackedModLevelType
import org.rsmod.game.type.varp.VarpTypeList
import org.rsmod.map.CoordGrid

class CharacterWorldClaimTest {
    @TempDir lateinit var tempDir: Path

    @Test
    fun `claim succeeds when unclaimed or already held by the same world`() = withDatabase {
        val world1 = repository(world = 1)
        val characterId = insertCharacter(world1)

        assertTrue(world1.claimWorld(this, characterId))
        assertEquals(1, selectWorldId(characterId))
        assertTrue(world1.claimWorld(this, characterId))
    }

    @Test
    fun `claim fails while another world holds the character`() = withDatabase {
        val world1 = repository(world = 1)
        val world2 = repository(world = 2)
        val characterId = insertCharacter(world1)
        world2.releaseWorldClaims(this)

        assertTrue(world1.claimWorld(this, characterId))
        assertFalse(world2.claimWorld(this, characterId))
        assertEquals(1, selectWorldId(characterId))
    }

    @Test
    fun `save releases only the claim held by the saving world`() = withDatabase {
        val world1 = repository(world = 1)
        val world2 = repository(world = 2)
        val characterId = insertCharacter(world1)
        val accountId = selectAccountId(characterId)
        world2.releaseWorldClaims(this)

        world1.claimWorld(this, characterId)
        world2.save(this, savePlayer(), accountId, characterId)
        assertEquals(1, selectWorldId(characterId))

        world1.save(this, savePlayer(), accountId, characterId)
        assertNull(selectWorldId(characterId))
    }

    @Test
    fun `release clears stale claims of the starting world only`() = withDatabase {
        val world1 = repository(world = 1)
        val world2 = repository(world = 2)
        val first = insertCharacter(world1, loginName = "first")
        val second = insertCharacter(world1, loginName = "second")
        world2.releaseWorldClaims(this)
        world1.claimWorld(this, first)
        world2.claimWorld(this, second)

        assertEquals(1, world1.releaseWorldClaims(this))
        assertNull(selectWorldId(first))
        assertEquals(2, selectWorldId(second))
    }

    private fun savePlayer(): Player =
        Player().apply { modLevel = UnpackedModLevelType(0, 0, 0, "player") }

    private fun repository(world: Int): CharacterAccountRepository {
        val realm = Realm("test").apply { updateConfig(realmConfig) }
        val config = ServerConfig(realm = "test", world = world, firstLaunch = false)
        val applier = CharacterAccountApplier(ModLevelTypeList(mutableMapOf()))
        return CharacterAccountRepository(
            objectMapper = ObjectMapper(),
            realm = realm,
            serverConfig = config,
            applier = applier,
            varpTypes = VarpTypeList(mutableMapOf()),
        )
    }

    private fun DatabaseConnection.insertCharacter(
        repository: CharacterAccountRepository,
        loginName: String = "test",
    ): Int {
        val accountId = checkNotNull(repository.insertOrSelectAccountId(this, loginName, "hash"))
        return checkNotNull(repository.insertAndSelectCharacterId(this, accountId))
    }

    private fun DatabaseConnection.selectAccountId(characterId: Int): Int =
        selectInt("SELECT account_id FROM characters WHERE id = ?", characterId)!!

    private fun DatabaseConnection.selectWorldId(characterId: Int): Int? =
        selectInt("SELECT world_id FROM characters WHERE id = ?", characterId)

    private fun DatabaseConnection.selectInt(sql: String, id: Int): Int? =
        prepareStatement(sql).use {
            it.setInt(1, id)
            it.executeQuery().use { result ->
                check(result.next()) { "Row not found: $id" }
                result.getInt(1).takeUnless { result.wasNull() }
            }
        }

    private fun withDatabase(block: DatabaseConnection.() -> Unit) {
        val config =
            DatabaseConfig("jdbc:sqlite:", tempDir.resolve("test.db").toString(), null, null)
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
        private val realmConfig =
            RealmConfig(
                id = 1,
                loginMessage = null,
                loginBroadcast = null,
                baseXpRate = 1.0,
                globalXpRate = 1.0,
                spawnCoord = CoordGrid.NULL,
                respawnCoord = CoordGrid.NULL,
                devMode = false,
                requireRegistration = false,
                ignorePasswords = false,
                autoAssignDisplayNames = false,
            )
    }
}
