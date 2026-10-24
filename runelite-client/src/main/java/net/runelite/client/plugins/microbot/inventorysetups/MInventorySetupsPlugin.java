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
 * {@link ConfigInventorySetupDataManager}. {@link #addInventorySetup(String)} saves the current
 * inventory and equipment as a setup, in the same format. Not ported: the panel, bank filtering and
 * highlighting, bank-tag layouts, bank slot locks and the rune pouch, bolt pouch and quiver contents
 * of a saved setup.
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
	/** As upstream: longer setup names are cut to this length. */
	public static final int MAX_SETUP_NAME_LENGTH = 50;
	private static final int NUM_INVENTORY_ITEMS = 28;
	private static final int NUM_EQUIPMENT_ITEMS = 14;
	/** Upstream's default when no spellbook is read. */
	private static final int SPELLBOOK_STANDARD = 0;

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

	/**
	 * As upstream: saves the current inventory and equipment as the setup {@code name} (cut to
	 * {@link #MAX_SETUP_NAME_LENGTH}), replacing the containers of an existing setup of that name
	 * (case-insensitive) and keeping its notes. Nothing happens for an empty name, or when the
	 * containers cannot be read.
	 */
	public void addInventorySetup(String name)
	{
		if (name == null || name.isEmpty())
		{
			return;
		}
		String setupName = name.length() > MAX_SETUP_NAME_LENGTH ? name.substring(0, MAX_SETUP_NAME_LENGTH) : name;
		Object[] captured = net.runelite.client.plugins.microbot.mobile.MobileActions.read(() -> new Object[]{
			normalizedContainer(net.runelite.api.gameval.InventoryID.INV, NUM_INVENTORY_ITEMS),
			normalizedContainer(net.runelite.api.gameval.InventoryID.WORN, NUM_EQUIPMENT_ITEMS),
			net.runelite.client.plugins.microbot.Microbot.getClient().getVarbitValue(net.runelite.api.gameval.VarbitID.SPELLBOOK)}, null);
		if (captured == null)
		{
			return;
		}
		@SuppressWarnings("unchecked")
		List<InventorySetupsItem> inventory = (List<InventorySetupsItem>) captured[0];
		@SuppressWarnings("unchecked")
		List<InventorySetupsItem> equipment = (List<InventorySetupsItem>) captured[1];
		List<InventorySetup> setups = new java.util.ArrayList<>(dataManager.loadV3Setups(configManager));
		InventorySetup existing = setups.stream().filter(java.util.Objects::nonNull)
			.filter(x -> setupName.equalsIgnoreCase(x.getName())).findFirst().orElse(null);
		InventorySetup setup = new InventorySetup(inventory, equipment, null, null, null, new java.util.HashMap<>(),
			existing == null ? setupName : existing.getName(), existing == null ? "" : existing.getNotes(),
			null, false, null, false, false, captured[2] == null ? SPELLBOOK_STANDARD : (Integer) captured[2], false, -1);
		int index = setups.indexOf(existing);
		if (index >= 0)
		{
			setups.set(index, setup);
		}
		else
		{
			setups.add(setup);
		}
		dataManager.saveV3Setups(configManager, setups);
	}

	/** As upstream's {@code getNormalizedContainer}: one row per slot, empty slots as dummy items. Client thread only. */
	private static List<InventorySetupsItem> normalizedContainer(int containerId, int size)
	{
		net.runelite.api.Client client = net.runelite.client.plugins.microbot.Microbot.getClient();
		net.runelite.api.ItemContainer container = client.getItemContainer(containerId);
		net.runelite.api.Item[] items = container == null ? null : container.getItems();
		List<InventorySetupsItem> rows = new java.util.ArrayList<>(size);
		for (int i = 0; i < size; i++)
		{
			if (items == null || i >= items.length || items[i].getId() == -1)
			{
				rows.add(InventorySetupsItem.getDummyItem());
				continue;
			}
			net.runelite.api.ItemComposition definition = client.getItemDefinition(items[i].getId());
			rows.add(new InventorySetupsItem(items[i].getId(), definition == null ? "" : definition.getName(),
				items[i].getQuantity(), false, InventorySetupsStackCompareID.None, false, i));
		}
		return rows;
	}
}
