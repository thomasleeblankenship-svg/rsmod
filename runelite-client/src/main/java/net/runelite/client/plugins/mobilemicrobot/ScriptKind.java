package net.runelite.client.plugins.mobilemicrobot;

import java.util.Set;

/**
 * The on-device scripts the Microbot Mobile panel can run, each with the settings sections
 * ({@link MobileMicrobotConfig} section keys) that belong to it. Items outside any section are
 * shared controls (script, run, webwalker) and always shown.
 */
public enum ScriptKind
{
	DIAGNOSTICS("Diagnostics (read-only)"),
	SCENE_SCAN("Scene scan (read-only)"),
	STATE_MACHINE_EXAMPLE("Upstream state machine example (read-only)"),
	WOODCUTTING("Woodcutting (example)", MobileMicrobotConfig.EXAMPLE_WOODCUTTING_SECTION),
	AUTO_WOODCUTTING("Auto Woodcutting (mobile port)", "general", "inventory", "forestry");

	private final String display;
	private final Set<String> settingsSections;

	ScriptKind(String display, String... settingsSections)
	{
		this.display = display;
		this.settingsSections = Set.of(settingsSections);
	}

	/** The settings sections this script uses. */
	public Set<String> getSettingsSections()
	{
		return settingsSections;
	}

	/**
	 * Whether settings in {@code section} should be shown while the script stored as
	 * {@code selectedName} (an enum name, possibly missing or stale) is selected. Shared items (no
	 * section) always show; an unknown selection falls back to the config's default script.
	 */
	public static boolean isSectionShown(String selectedName, String section)
	{
		if (section == null || section.isEmpty())
		{
			return true;
		}
		ScriptKind selected = DIAGNOSTICS;
		for (ScriptKind kind : values())
		{
			if (kind.name().equals(selectedName))
			{
				selected = kind;
			}
		}
		return selected.settingsSections.contains(section);
	}

	@Override
	public String toString()
	{
		return display;
	}
}
