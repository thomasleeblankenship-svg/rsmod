/*
 * Copied from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/boat/data/Heading.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.boat.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** As upstream: the sixteen boat headings and their values. */
@RequiredArgsConstructor
public enum Heading
{
    SOUTH(0),
    SOUTH_SOUTH_WEST(1),
    SOUTH_WEST(2),
    WEST_SOUTH_WEST(3),
    WEST(4),
    WEST_NORTH_WEST(5),
    NORTH_WEST(6),
    NORTH_NORTH_WEST(7),
    NORTH(8),
    NORTH_NORTH_EAST(9),
    NORTH_EAST(10),
    EAST_NORTH_EAST(11),
    EAST(12),
    EAST_SOUTH_EAST(13),
    SOUTH_EAST(14),
    SOUTH_SOUTH_EAST(15)
    ;


    public static Heading getHeading(int value) {
        for (Heading h : values()) {
            if (h.value == value) return h;
        }
        return null;
    }

    @Getter
    private final int value;
}