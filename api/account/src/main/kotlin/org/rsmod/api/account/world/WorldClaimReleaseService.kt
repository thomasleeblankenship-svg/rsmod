package org.rsmod.api.account.world

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.account.character.main.CharacterAccountRepository
import org.rsmod.api.db.sqlite.SqliteDatabase
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.server.services.ListenerService

/**
 * Releases character world claims left behind by a previous run of this world.
 *
 * Characters are claimed by a world on login (see [CharacterAccountRepository.claimWorld]) and
 * released when their logout save completes. If the world stops without saving its players (e.g., a
 * crash), those claims would otherwise block the characters from logging in to any other world.
 * Since no player can be online in this world during startup, every claim it holds is stale.
 */
public class WorldClaimReleaseService
@Inject
constructor(
    private val database: SqliteDatabase,
    private val repository: CharacterAccountRepository,
    private val config: ServerConfig,
) : ListenerService {
    private val logger = InlineLogger()

    override suspend fun signalStartup() {
        val released = database.withTransaction { repository.releaseWorldClaims(it) }
        if (released > 0) {
            logger.warn { "Released $released stale character claim(s) for world ${config.world}." }
        }
    }

    override suspend fun signalShutdown() {}

    override suspend fun startup() {}

    override suspend fun shutdown() {}
}
