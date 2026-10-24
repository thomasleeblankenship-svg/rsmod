package net.runelite.client.plugins.microbot.util.poh;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * The last detected {@link PohLayout}, kept per RS profile in the {@code shortestpath} config group
 * so house routing works after a re-login without visiting the house. Written for RuneMobile.
 */
public final class PohLayoutStore
{
	static final String GROUP = "shortestpath";
	static final String KEY = "mobilePohLayout";

	private PohLayoutStore()
	{
	}

	/** The stored layout, or {@link PohLayout#EMPTY} without a config manager or a stored one. */
	public static PohLayout get()
	{
		ConfigManager config = Microbot.getConfigManager();
		return config == null ? PohLayout.EMPTY : PohLayout.parse(config.getRSProfileConfiguration(GROUP, KEY));
	}

	/** Stores {@code layout} for the current RS profile (ignored without a config manager). */
	public static void save(PohLayout layout)
	{
		ConfigManager config = Microbot.getConfigManager();
		if (config != null && layout != null)
		{
			config.setRSProfileConfiguration(GROUP, KEY, layout.serialize());
		}
	}

	/** When the player is in the house: detects it, stores it and returns it; else the stored layout. */
	public static PohLayout refreshIfInHouse()
	{
		PohLayout detected = PohDetector.detect();
		if (detected == null)
		{
			return get();
		}
		save(detected);
		return detected;
	}
}
