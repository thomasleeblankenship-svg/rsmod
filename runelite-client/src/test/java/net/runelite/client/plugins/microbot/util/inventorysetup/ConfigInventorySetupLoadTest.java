package net.runelite.client.plugins.microbot.util.inventorysetup;

import java.lang.reflect.Field;
import java.util.List;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.inventorysetups.ConfigInventorySetupDataManager;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Setups saved by the desktop Inventory Setups plugin (v3 config keys) load unchanged. */
public class ConfigInventorySetupLoadTest
{
	private static final String GROUP = "inventorysetups";

	@Test
	public void v3SetupsLoadInTheSavedOrder() throws Exception
	{
		ConfigManager config = mock(ConfigManager.class);
		when(config.getConfigurationKeys(anyString())).thenReturn(List.of(
			GROUP + ".setupsV3_bbb", GROUP + ".setupsV3_aaa"));
		when(config.getConfiguration(GROUP, "setupsOrderV3_")).thenReturn("[\"aaa\",\"bbb\"]");
		when(config.getConfiguration(GROUP, "setupsV3_aaa")).thenReturn(
			"{\"name\":\"Bank trip\",\"inv\":[{\"id\":379,\"q\":1},{\"id\":995,\"q\":1000}],\"eq\":[{\"id\":1277}]}");
		when(config.getConfiguration(GROUP, "setupsV3_bbb")).thenReturn("{\"name\":\"Second\"}");
		ConfigInventorySetupDataManager manager = new ConfigInventorySetupDataManager();
		Field gson = ConfigInventorySetupDataManager.class.getDeclaredField("gson");
		gson.setAccessible(true);
		gson.set(manager, net.runelite.http.api.RuneLiteAPI.GSON); // the client's injected Gson

		List<InventorySetup> setups = manager.loadV3Setups(config);

		assertEquals(2, setups.size());
		assertEquals("Bank trip", setups.get(0).getName());
		assertEquals(379, setups.get(0).getInventory().get(0).getId());
		assertEquals(1000, setups.get(0).getInventory().get(1).getQuantity());
		assertEquals(1277, setups.get(0).getEquipment().get(0).getId());
		assertEquals("Second", setups.get(1).getName());
	}
}
