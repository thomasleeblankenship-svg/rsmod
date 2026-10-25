package org.rsmod.api.world.list

import jakarta.inject.Inject
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class WorldPopulationScript @Inject constructor(private val population: WorldPopulation) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { population.increment() }
        onPlayerLogout { population.decrement() }
    }
}
