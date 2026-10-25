package org.rsmod.api.world.list

import org.rsmod.plugin.module.PluginModule
import org.rsmod.server.services.Service

/** Binds the [WorldList] and the service that keeps it up to date. */
public class WorldListModule : PluginModule() {
    override fun bind() {
        bindInstance<WorldList>()
        bindInstance<WorldPopulation>()
        bindInstance<WorldListRepository>()
        bindInstance<WorldListService>()
        addSetBinding<Service>(WorldListService::class.java)
    }
}
