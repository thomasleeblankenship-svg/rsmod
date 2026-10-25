/*
 * Copied from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/poh/data/PohTeleport.java.
 * Adaptation: execute() is the action (batch AQ); the walker's POH steps run through their own executor.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.poh.data;

import net.runelite.api.coords.WorldPoint;

public interface PohTeleport {

    WorldPoint getDestination();

    /**
     * Gets the duration of the teleport equal to the amount of tiles that can be ran in the same time.
     *
     * @return number of tiles that can be ran in the same time.
     */
    int getDuration();

    /**
     * Returns a string containing information about the PohTransport, including its Method and Destination.,
     *
     * @return PohTeleportMethod -> Destination.toString()
     */
    String displayInfo();

    /**
     * Enum value used to trace back the PohTransport from the Enum class it originates from
     *
     * @return Enum's value.name()
     */
    String name();

    /**
     * As upstream: uses this teleport from inside the house (the device must be in the scene). True
     * once the teleport was dispatched. Wilderness destinations are refused (Batch AQ, Claude 1).
     */
    boolean execute();
}
