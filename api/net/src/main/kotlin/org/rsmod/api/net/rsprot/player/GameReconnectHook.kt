package org.rsmod.api.net.rsprot.player

import com.github.michaelbull.logging.InlineLogger
import net.rsprot.crypto.xtea.XteaKey
import net.rsprot.protocol.api.login.GameLoginResponseHandler
import net.rsprot.protocol.loginprot.incoming.util.LoginBlock
import net.rsprot.protocol.loginprot.outgoing.LoginResponse
import org.rsmod.api.net.rsprot.RspClient
import org.rsmod.api.net.rsprot.RspCycle
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

/**
 * Re-attaches a reconnecting client to the player it was disconnected from.
 *
 * Must be invoked on the game thread, between cycles (see `AccountRegistry.queueReconnect`). The
 * player keeps their place in the world while their client is disconnected, for the reconnect grace
 * period of the logout process; a reconnect within that period resumes the session without a
 * logout.
 */
class GameReconnectHook(
    private val playerList: PlayerList,
    private val loginBlock: LoginBlock<XteaKey>,
    private val channelResponses: GameLoginResponseHandler<Player>,
) {
    private val Player.rspClient: RspClient?
        get() = client as? RspClient

    fun handle() {
        try {
            handleReconnect()
        } catch (e: Exception) {
            channelResponses.writeFailedResponse(LoginResponse.ConnectFail)
            logger.error(e) { "Error handling reconnect for: '${loginBlock.username}'" }
        }
    }

    private fun handleReconnect() {
        val username = loginBlock.username.lowercase()
        val player = playerList.firstOrNull { it.username == username }
        val key = loginBlock.authentication.key
        val validation = validateReconnect(player) { it.rspClient?.isReconnectKey(key) == true }
        if (validation != ReconnectValidation.Accept || player == null) {
            if (validation == ReconnectValidation.KeyMismatch) {
                logger.warn { "Reconnect key mismatch for player: $player" }
            }
            channelResponses.writeFailedResponse(LoginResponse.ConnectFail)
            return
        }
        reconnect(player)
    }

    private fun reconnect(player: Player) {
        val client = checkNotNull(player.rspClient) { "Unexpected client: ${player.client}" }
        val cycle = checkNotNull(player.clientCycle as? RspCycle) { "Unexpected client cycle." }

        val response = client.createReconnectResponse()
        val session = channelResponses.writeSuccessfulResponse(response, loginBlock)

        client.reconnect(session, loginBlock.seed)
        cycle.reconnect(session)
        player.clientDisconnected.set(false)

        // `setDisconnectionHook` invokes the hook immediately if the session is already inactive,
        // in which case the player simply remains disconnected.
        session.setDisconnectionHook { player.clientDisconnected.set(true) }
        logger.debug { "Player reconnected: $player" }
    }

    private companion object {
        private val logger = InlineLogger()
    }
}
