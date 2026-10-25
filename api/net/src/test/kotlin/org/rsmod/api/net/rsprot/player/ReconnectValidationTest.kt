package org.rsmod.api.net.rsprot.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.Player

class ReconnectValidationTest {
    @Test
    fun `disconnected player with matching key is accepted`() {
        val player = disconnectedPlayer()
        assertEquals(ReconnectValidation.Accept, validateReconnect(player) { true })
    }

    @Test
    fun `missing player is not online`() {
        assertEquals(ReconnectValidation.NotOnline, validateReconnect(null) { true })
    }

    @Test
    fun `player whose drop is not yet detected is still connected`() {
        val player = Player()
        assertEquals(ReconnectValidation.StillConnected, validateReconnect(player) { true })
    }

    @Test
    fun `player with pending logout is rejected`() {
        val player = disconnectedPlayer().apply { pendingLogout = true }
        assertEquals(ReconnectValidation.LoggingOut, validateReconnect(player) { true })
    }

    @Test
    fun `player already logging out is rejected`() {
        val player = disconnectedPlayer().apply { loggingOut = true }
        assertEquals(ReconnectValidation.LoggingOut, validateReconnect(player) { true })
    }

    @Test
    fun `mismatched key is rejected`() {
        val player = disconnectedPlayer()
        assertEquals(ReconnectValidation.KeyMismatch, validateReconnect(player) { false })
    }

    private fun disconnectedPlayer(): Player = Player().apply { clientDisconnected.set(true) }
}
