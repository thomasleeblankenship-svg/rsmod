package org.rsmod.api.net.rsprot.player

import net.rsprot.protocol.api.Session
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

/**
 * Published when [player] has logged in and their network [session] should be attached.
 *
 * @param loginSeed the stream cipher seed sent by the client on login. The client presents it again
 *   when it reconnects, which is how the reconnect is matched to this session.
 */
class SessionStart(val player: Player, val session: Session<Player>, val loginSeed: IntArray) :
    UnboundEvent
