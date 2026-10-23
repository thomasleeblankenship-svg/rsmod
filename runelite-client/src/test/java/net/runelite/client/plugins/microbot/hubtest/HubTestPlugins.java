package net.runelite.client.plugins.microbot.hubtest;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/** Hub-shaped test plugins (in the Microbot package, {@code isExternal = true}); never loaded by RuneLite. */
public final class HubTestPlugins
{
	private HubTestPlugins()
	{
	}

	@PluginDescriptor(name = "<html>[<font color=green>T</font>] Hub A", isExternal = true, enabledByDefault = false)
	public static class HubA extends Plugin
	{
	}

	@PluginDescriptor(name = "Hub B", isExternal = true, enabledByDefault = false)
	public static class HubB extends Plugin
	{
	}

	@PluginDescriptor(name = "Not external", enabledByDefault = false)
	public static class NotExternal extends Plugin
	{
	}
}
