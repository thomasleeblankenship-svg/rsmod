package net.runelite.client.plugins.microbot.breakhandler;

import net.runelite.client.plugins.Plugin;

/**
 * Type-only stand-in for upstream's break handler plugin, so scripts that ask
 * {@code Microbot.isPluginEnabled(BreakHandlerPlugin.class)} compile.
 * <p>
 * It deliberately has no {@code @PluginDescriptor}, so RuneLite never loads it: on mobile the
 * break handler is never enabled, {@code Microbot.getPlugin(BreakHandlerPlugin.class)} returns
 * {@code null}, and starting it does nothing. See {@link BreakHandlerScript}.
 */
public final class BreakHandlerPlugin extends Plugin
{
}
