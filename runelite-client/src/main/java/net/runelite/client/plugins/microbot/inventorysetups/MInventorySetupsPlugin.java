package net.runelite.client.plugins.microbot.inventorysetups;

import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Inventory setups for scripts, without the Inventory Setups panel.
 * <p>
 * Mobile replacement for chsami/Microbot's {@code MInventorySetupsPlugin} at
 * {@code a2114401bbbc35e9762db9e8aa666d70ffcd24d8} (BSD 2-Clause), keeping its plugin class (Hub
 * scripts look it up and check that it is enabled), {@link #CONFIG_GROUP} and the static
 * {@link #getInventorySetups()}. Setups are read on demand from the same config keys the desktop
 * plugin writes ({@code setupsV3_*}, in {@code setupsOrderV3_} order) through
 * {@link ConfigInventorySetupDataManager}. Not ported: the panel, bank filtering and highlighting,
 * bank-tag layouts, and creating or editing setups.
 */
@Slf4j
@PluginDescriptor(
	name = "Inventory Setups (Microbot)",
	description = "Saved inventory setups for Microbot scripts",
	tags = {"microbot", "inventory", "setups"},
	enabledByDefault = true,
	hidden = true
)
public class MInventorySetupsPlugin extends Plugin
{
	public static final String CONFIG_GROUP = "inventorysetups";

	private static volatile MInventorySetupsPlugin active;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ConfigInventorySetupDataManager dataManager;

	@Override
	protected void startUp()
	{
		active = this;
	}

	@Override
	protected void shutDown()
	{
		if (active == this)
		{
			active = null;
		}
	}

	/**
	 * As upstream: every saved setup, in the saved order. Empty when this plugin is not running or a
	 * stored setup cannot be read (the error is logged).
	 */
	public static List<InventorySetup> getInventorySetups()
	{
		MInventorySetupsPlugin plugin = active;
		if (plugin == null)
		{
			return Collections.emptyList();
		}
		try
		{
			return plugin.dataManager.loadV3Setups(plugin.configManager);
		}
		catch (RuntimeException e)
		{
			log.warn("Inventory setups could not be read", e);
			return Collections.emptyList();
		}
	}
}
