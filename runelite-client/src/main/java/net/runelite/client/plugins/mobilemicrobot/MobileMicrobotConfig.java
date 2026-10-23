package net.runelite.client.plugins.mobilemicrobot;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("mobilemicrobot")
public interface MobileMicrobotConfig extends Config
{
	@ConfigItem(keyName = "selectedScript", name = "Script", description = "Which on-device script to run", position = 0)
	default ScriptKind selectedScript()
	{
		return ScriptKind.DIAGNOSTICS;
	}

	@ConfigItem(keyName = "runScript", name = "Run script", description = "Start or stop the selected script", position = 1)
	default boolean runScript()
	{
		return false;
	}

	@ConfigItem(keyName = "treeType", name = "Woodcutting tree", description = "Tree type for the example woodcutting script", position = 2)
	default TreeType treeType()
	{
		return TreeType.NORMAL;
	}

	@ConfigItem(keyName = "maxDistance", name = "Max tree distance", description = "Maximum number of tiles to search from the player", position = 3)
	default int maxDistance()
	{
		return 8;
	}
}
