package org.rsmod.api.net.rsprot

import net.rsprot.protocol.api.NetworkService
import net.rsprot.protocol.api.Session
import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcInfo
import net.rsprot.protocol.game.outgoing.info.playerinfo.PlayerInfo
import net.rsprot.protocol.loginprot.outgoing.LoginResponse
import net.rsprot.protocol.message.OutgoingGameMessage
import org.rsmod.game.client.Client
import org.rsmod.game.entity.Player

@ExperimentalUnsignedTypes
private typealias Service = NetworkService<Player>

@OptIn(ExperimentalUnsignedTypes::class)
class RspClient(
    private var session: Session<Player>,
    private val playerInfo: PlayerInfo,
    private val npcInfo: NpcInfo,
    loginSeed: IntArray,
) : Client<Service, OutgoingGameMessage> {
    private var loginSeed: IntArray = loginSeed.copyOf()

    /**
     * Returns `true` if [key] matches the stream cipher seed of the session this client was last
     * attached to. Reconnecting clients present that seed to prove they own the session.
     */
    fun isReconnectKey(key: IntArray): Boolean = loginSeed.contentEquals(key)

    /**
     * Resets the player and npc info state for a reconnecting client and creates the response that
     * re-initializes the client's player info. Must be called on the game thread, between cycles.
     */
    fun createReconnectResponse(): LoginResponse.ReconnectOk {
        playerInfo.onReconnect()
        npcInfo.onReconnect()
        return LoginResponse.ReconnectOk(playerInfo)
    }

    /**
     * Attaches a reconnected [session] to this client. The player and npc info are kept, as the
     * client retains its game state across a reconnect.
     */
    fun reconnect(session: Session<Player>, loginSeed: IntArray) {
        this.session = session
        this.loginSeed = loginSeed.copyOf()
    }

    override fun close() {
        session.requestClose()
    }

    override fun write(message: OutgoingGameMessage) {
        session.queue(message)
    }

    override fun read(player: Player) {
        session.processIncomingPackets(player)
    }

    override fun flush() {
        session.flush()
    }

    override fun flushHighPriority() {
        session.discardLowPriorityCategoryPackets()
        session.flush()
    }

    override fun unregister(service: Service, player: Player) {
        service.playerInfoProtocol.dealloc(playerInfo)
        service.npcInfoProtocol.dealloc(npcInfo)
    }
}
