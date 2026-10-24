/*
 * Copied from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/poh/data/MountedMythical.java.
 * Adaptation: data only; upstream's execute(), getObject() (actions and live-object lookups) are left to the walker's POH executor;
 * lint-only edits (PMD suppressions, a shorter array initializer).
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.poh.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.DecorativeObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.worldmap.TeleportLocationData;


@Getter
@RequiredArgsConstructor
public enum MountedMythical implements PohTeleport {
    MYTHS_GUILD(ObjectID.POH_TROPHY_MYTHICAL_CAPE, "Myths guild", TeleportLocationData.MYTHS_GUILD.getLocation()),
    ;

    private final int objectId;
    private final String destinationName;
    private final WorldPoint destination;

    @SuppressWarnings("PMD.FinalFieldCouldBeStatic") // instance getter implements PohTeleport.getDuration()
    private final int duration = 4;

    public static boolean isMountedMythsCape(DecorativeObject go) {
        if (go == null) return false;
        return go.getId() == MYTHS_GUILD.getObjectId();
    }

    @Override
    public String displayInfo() {
        return getClass().getSimpleName() + " -> " + destinationName;
    }

    /** As upstream: the mounted item in the house scene, or {@code null}. */
    public static DecorativeObject getObject() {
        return net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.getDecorativeObject(MYTHS_GUILD.getObjectId());
    }
}
