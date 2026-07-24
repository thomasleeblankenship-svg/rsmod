package org.rsmod.api.combat.scripts

import org.rsmod.api.config.refs.timers
import org.rsmod.api.script.onPlayerTimer
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Removes a player's pk skull once its timer expires. */
internal class PkSkullScript : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerTimer(timers.pk_skull) {
            player.skullIcon = null
            rebuildAppearance()
        }
    }
}
