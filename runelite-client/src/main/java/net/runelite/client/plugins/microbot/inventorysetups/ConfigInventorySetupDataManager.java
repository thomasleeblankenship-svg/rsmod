package net.runelite.client.plugins.microbot.inventorysetups;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.inventorysetups.serialization.InventorySetupSerializable;

import javax.inject.Inject;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin.CONFIG_GROUP;

@Slf4j
public class ConfigInventorySetupDataManager {
    @Inject
    private Gson gson;
    public static final String CONFIG_KEY_SETUPS_V3_PREFIX = "setupsV3_";
    public static final String CONFIG_KEY_SETUPS_ORDER_V3 = "setupsOrderV3_";


    public List<InventorySetup> loadV3Setups(ConfigManager configManager)
    {
        final String wholePrefix = ConfigManager.getWholeKey(CONFIG_GROUP, null, CONFIG_KEY_SETUPS_V3_PREFIX);
        final List<String> loadedSetupWholeKeys = configManager.getConfigurationKeys(wholePrefix);
        Set<String> loadedSetupKeys = loadedSetupWholeKeys.stream().map(
                key -> key.substring(wholePrefix.length() - CONFIG_KEY_SETUPS_V3_PREFIX.length())
        ).collect(Collectors.toSet());

        Type setupsOrderType = new TypeToken<ArrayList<String>>()
        {

        }.getType();
        final String setupsOrderJson = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_ORDER_V3);
        List<String> setupsOrder = gson.fromJson(setupsOrderJson, setupsOrderType);
        if (setupsOrder == null)
        {
            setupsOrder = new ArrayList<>();
        }

        List<InventorySetup> loadedSetups = new ArrayList<>();
        for (final String configHash : setupsOrder)
        {
            final String configKey = CONFIG_KEY_SETUPS_V3_PREFIX + configHash;
            if (loadedSetupKeys.remove(configKey))
            { // Handles if hash is present only in configOrder.
                final InventorySetup setup = loadV3Setup(configManager, configKey);
                loadedSetups.add(setup);
            }
        }
        for (final String configKey : loadedSetupKeys)
        {
            // Load any remaining setups not present in setupsOrder. Useful if updateConfig crashes midway.
            log.info("Loading setup that was missing from Order key: " + configKey);
            final InventorySetup setup = loadV3Setup(configManager, configKey);
            loadedSetups.add(setup);
        }
        return loadedSetups;
    }

    /** As upstream: setups are stored under the murmur3 hash of their name. */
    private static final com.google.common.hash.HashFunction HASH_FUNCTION = com.google.common.hash.Hashing.murmur3_128();

    /**
     * As upstream's {@code updateConfig}: writes every setup under {@code setupsV3_<name hash>},
     * removes stored setups no longer in the list, and writes the order list.
     */
    public void saveV3Setups(ConfigManager configManager, List<InventorySetup> setups)
    {
        final String wholePrefix = ConfigManager.getWholeKey(CONFIG_GROUP, null, CONFIG_KEY_SETUPS_V3_PREFIX);
        Set<String> oldHashes = configManager.getConfigurationKeys(wholePrefix).stream()
                .map(key -> key.substring(wholePrefix.length())).collect(Collectors.toSet());
        final List<String> order = new ArrayList<>();
        for (final InventorySetup setup : setups)
        {
            final String hash = HASH_FUNCTION.hashUnencodedChars(setup.getName()).toString();
            order.add(hash);
            oldHashes.remove(hash);
            configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_V3_PREFIX + hash,
                    gson.toJson(InventorySetupSerializable.convertFromInventorySetup(setup)));
        }
        for (final String removed : oldHashes)
        {
            configManager.unsetConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_V3_PREFIX + removed);
        }
        configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_SETUPS_ORDER_V3, gson.toJson(order));
    }

    private InventorySetup loadV3Setup(ConfigManager configManager, String configKey)
    {
        final String storedData = configManager.getConfiguration(CONFIG_GROUP, configKey);
        try
        {
            return InventorySetupSerializable.convertToInventorySetup(gson.fromJson(storedData, InventorySetupSerializable.class));
        }
        catch (Exception e)
        {
            log.error(String.format("Exception occurred while loading %s", configKey), e);
            throw e;
        }
    }
}
