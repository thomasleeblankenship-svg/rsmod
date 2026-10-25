package org.rsmod.api.net.rsprot.player

import org.rsmod.game.entity.Player

/** The outcome of validating a reconnect request against the player it targets. */
internal sealed class ReconnectValidation {
    data object Accept : ReconnectValidation()

    /** No player with the requested username is in this world. */
    data object NotOnline : ReconnectValidation()

    /**
     * The player's previous connection has not been detected as dropped yet. The client keeps
     * retrying, so a later attempt succeeds once the drop is detected.
     */
    data object StillConnected : ReconnectValidation()

    /** The player's logout has already started; they must log in again instead. */
    data object LoggingOut : ReconnectValidation()

    /** The key presented by the client does not belong to the player's previous session. */
    data object KeyMismatch : ReconnectValidation()
}

/**
 * Validates a reconnect for [player] (the online player with the requested username, if any).
 *
 * @param isReconnectKey whether the key presented by the client matches the seed of the player's
 *   previous session.
 */
internal fun validateReconnect(player: Player?, isReconnectKey: (Player) -> Boolean) =
    when {
        player == null -> ReconnectValidation.NotOnline
        player.pendingLogout || player.loggingOut -> ReconnectValidation.LoggingOut
        !player.clientDisconnected.get() -> ReconnectValidation.StillConnected
        !isReconnectKey(player) -> ReconnectValidation.KeyMismatch
        else -> ReconnectValidation.Accept
    }
