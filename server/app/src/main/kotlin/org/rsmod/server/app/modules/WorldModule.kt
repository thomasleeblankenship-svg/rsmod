package org.rsmod.server.app.modules

import com.google.inject.Provides
import com.google.inject.Scopes
import jakarta.inject.Singleton
import org.rsmod.game.World
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PlayerList
import org.rsmod.module.ExtendedModule

public object WorldModule : ExtendedModule() {
    override fun bind() {
        bind(PlayerList::class.java).`in`(Scopes.SINGLETON)
        bind(NpcList::class.java).`in`(Scopes.SINGLETON)
    }

    /**
     * [World] lives in `engine/game`, which intentionally has no injection framework dependency,
     * so it is constructed through this provider instead of an `@Inject` constructor.
     */
    @Provides
    @Singleton
    public fun provideWorld(players: PlayerList, npcs: NpcList): World = World(players, npcs)
}
