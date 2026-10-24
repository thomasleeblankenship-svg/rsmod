/*
 * Copied from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/poh/data/MountedGlory.java.
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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum MountedGlory implements PohTeleport {
    EDGEVILLE("Edgeville", TeleportLocationData.EDGEVILLE.getLocation()),
    KARAMJA("Karamja", TeleportLocationData.KARAMJA.getLocation()),
    DRAYNOR_VILLAGE("Draynor Village", TeleportLocationData.DRAYNOR_VILLAGE.getLocation()),
    AL_KHARID("Al Kharid", TeleportLocationData.AL_KHARID.getLocation()),
    ;

    private final String destinationName;
    private final WorldPoint destination;

    @SuppressWarnings("PMD.FinalFieldCouldBeStatic") // instance getter implements PohTeleport.getDuration()
    private final int duration = 4;


    public static List<PohTeleport> getTransports() {
        return Arrays.stream(values()).collect(Collectors.toList());
    }

    public static boolean isMountedGlory(DecorativeObject go) {
        if (go == null) return false;
        return ObjectID.POH_TROPHY_AMULETOFGLORY_4 == go.getId();
    }

    @Override
    public String displayInfo() {
        return "MountedGlory -> " + destinationName;
    }
}
