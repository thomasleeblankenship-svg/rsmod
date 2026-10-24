/*
 * Copied unchanged from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/npc/MonsterLocationDTO.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.npc;

import lombok.Data;

import java.util.List;

/**
 * Helper class to parse the raw JSON location data as-is.
 */
@Data
class MonsterLocationDTO
{
    private String location_name;
    private Integer mapID;
    private List<List<Integer>> coords;
}