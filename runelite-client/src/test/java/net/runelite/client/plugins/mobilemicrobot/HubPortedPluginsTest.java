package net.runelite.client.plugins.mobilemicrobot;

import java.util.List;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.autochompykiller.AutoChompyKillerPlugin;
import net.runelite.client.plugins.microbot.birdhunter.BirdHunterPlugin;
import net.runelite.client.plugins.microbot.HueycoatlPrayer.HueyPrayerPlugin;
import net.runelite.client.plugins.microbot.fishingtrawler.FishingTrawlerPlugin;
import net.runelite.client.plugins.microbot.salamanders.SalamanderPlugin;
import net.runelite.client.plugins.microbot.toweroflife_creaturecreation.TowerOfLifeCCPlugin;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The Hub plugins copied unchanged into the client are hosted as Hub plugins and never start on their own. */
public class HubPortedPluginsTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final List<Class<? extends Plugin>> PORTED = List.of(
		AutoChompyKillerPlugin.class, FishingTrawlerPlugin.class, BirdHunterPlugin.class,
		TowerOfLifeCCPlugin.class, SalamanderPlugin.class, HueyPrayerPlugin.class);

	@Test
	public void portedPluginsAreHubPluginsAndDisabledByDefault() throws Exception
	{
		for (Class<? extends Plugin> type : PORTED)
		{
			PluginDescriptor descriptor = type.getAnnotation(PluginDescriptor.class);
			assertNotNull(type.getName(), descriptor);
			assertFalse(type.getName(), descriptor.enabledByDefault());
			assertTrue(type.getName(), HubPluginHost.isHubPlugin(type.getDeclaredConstructor().newInstance()));
		}
	}

	@Test
	public void hostNamesDropTheAuthorTag() throws Exception
	{
		for (Class<? extends Plugin> type : PORTED)
		{
			String name = HubPluginHost.name(type.getDeclaredConstructor().newInstance());
			assertFalse(name, name.isEmpty() || name.contains("<") || name.startsWith("["));
		}
	}
}
