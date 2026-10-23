package net.runelite.client.plugins.microbot.api;

import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;

/**
 * Service-provider interface for the read-only Microbot API.
 * <p>
 * The runtime singleton {@code net.runelite.client.plugins.microbot.Microbot}
 * delegates its cache getters here, for example {@code Microbot.getRs2NpcCache()}
 * returns {@code MicrobotReadServices.current().getRs2NpcCache()} (or
 * {@code null} when the runtime is not installed, matching its other getters).
 * Login state is {@code Microbot.isLoggedIn()}, owned by the runtime. Scripts
 * never construct caches; there is exactly one installed provider at a time.
 * <p>
 * Threading: every getter may be called from any thread. Cache streams are
 * immutable per-tick snapshots captured on the client thread.
 */
public interface MicrobotReadApi
{
	Client getClient();

	ClientThread getClientThread();

	Rs2NpcCache getRs2NpcCache();

	Rs2TileObjectCache getRs2TileObjectCache();

	Rs2TileItemCache getRs2TileItemCache();
}
