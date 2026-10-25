package org.rsmod.api.world.list

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.rsmod.api.db.Database
import org.rsmod.api.server.config.ServerConfig
import org.rsmod.server.services.concurrent.ScheduledService

/**
 * Periodically reports this world's heartbeat and population to the database, and refreshes the
 * [WorldList] snapshot with the state of every registered world.
 *
 * Worlds that share a database discover each other through this service: a world is listed as
 * online while its heartbeat is no older than [ONLINE_TIMEOUT].
 */
public class WorldListService
@Inject
constructor(
    private val database: Database,
    private val repository: WorldListRepository,
    private val worldList: WorldList,
    private val population: WorldPopulation,
    private val config: ServerConfig,
) : ScheduledService {
    private val logger = InlineLogger()

    private var registered = true

    override suspend fun run() {
        try {
            refresh()
        } catch (e: Exception) {
            logger.error(e) { "Could not refresh world list." }
        }
        delay(HEARTBEAT_INTERVAL)
    }

    private suspend fun refresh() {
        val world = config.world
        val entries =
            database.withTransaction { connection ->
                val updated = repository.heartbeat(connection, world, population.current)
                warnIfUnregistered(updated)
                repository.selectAll(connection, ONLINE_TIMEOUT.inWholeSeconds.toInt())
            }
        worldList.update(entries)
    }

    private fun warnIfUnregistered(updated: Boolean) {
        if (!updated && registered) {
            logger.warn { "World ${config.world} is not registered in the `worlds` table." }
        }
        registered = updated
    }

    override fun createExecutor(): ExecutorService {
        val threadFactory = ThreadFactory { runnable ->
            Thread(runnable, SERVICE_THREAD_NAME).apply { isDaemon = false }
        }
        return Executors.newSingleThreadExecutor(threadFactory)
    }

    override suspend fun setup() {}

    override suspend fun startup() {}

    override suspend fun shutdown() {
        try {
            database.withTransaction { repository.markOffline(it, config.world) }
        } catch (e: Exception) {
            logger.error(e) { "Could not mark world ${config.world} as offline." }
        }
    }

    public companion object {
        private const val SERVICE_THREAD_NAME = "world-list"

        /** The interval between each heartbeat and world list refresh. */
        public val HEARTBEAT_INTERVAL: Duration = 5.seconds

        /** How long a world remains listed as online after its last heartbeat. */
        public val ONLINE_TIMEOUT: Duration = 30.seconds
    }
}
