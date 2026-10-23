package net.runelite.client.plugins.microbot.api;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;

/**
 * Service-provider interface for the read-only Microbot API (Claude 2 scope).
 * <p>
 * The runtime singleton {@code net.runelite.client.plugins.microbot.Microbot}
 * (owned by the runtime port) delegates its matching static getters here, for
 * example {@code Microbot.getRs2NpcCache()} returns
 * {@code MicrobotReadServices.require().getRs2NpcCache()}. Scripts never
 * construct caches; there is exactly one installed provider at a time.
 * <p>
 * Threading: every getter may be called from any thread. Cache streams are
 * immutable per-tick snapshots captured on the client thread.
 */
public interface MicrobotReadApi
{
	Client getClient();

	ClientThreadCalls getClientThreadCalls();

	/** Upstream {@code LoginManager.isLoggedIn()}: client present and {@code GameState.LOGGED_IN}. */
	boolean isLoggedIn();

	Rs2NpcCache getRs2NpcCache();

	Rs2TileObjectCache getRs2TileObjectCache();

	Rs2TileItemCache getRs2TileItemCache();
}
