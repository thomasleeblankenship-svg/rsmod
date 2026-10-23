/*
 * Mobile adaptation of uploaded Auto Woodcutting v1.8.7, source commit
 * 080d9674fe87f282c958c25b5bb8f6b96665fe36. Original algorithms retained;
 * desktop host/lifecycle and client-thread API access adapted for RuneMobile.
 */
package net.runelite.client.plugins.microbot.woodcutting.enums;

import lombok.Getter;

@Getter
public enum WoodcuttingWalkBack {
    INITIAL_LOCATION,
    LAST_LOCATION
}
