/*
 * Copied from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/magic/RuneFilter.java.
 * BSD 2-Clause License; see LICENSE. Unchanged.
 */
package net.runelite.client.plugins.microbot.util.magic;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RuneFilter {
    @Builder.Default
    private final boolean includeInventory = true;
    @Builder.Default
    private final boolean includeEquipment = true;
    @Builder.Default
    private final boolean includeRunePouch = true;
    @Builder.Default
    private final boolean includeBank = false;
    @Builder.Default
    private final boolean includeComboRunes = true;
}