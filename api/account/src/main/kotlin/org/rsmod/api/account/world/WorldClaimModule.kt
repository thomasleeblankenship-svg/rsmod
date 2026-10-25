package org.rsmod.api.account.world

import org.rsmod.module.ExtendedModule
import org.rsmod.server.services.Service

/** Binds the services responsible for tracking which world a character is online in. */
public object WorldClaimModule : ExtendedModule() {
    override fun bind() {
        addSetBinding<Service>(WorldClaimReleaseService::class.java)
    }
}
