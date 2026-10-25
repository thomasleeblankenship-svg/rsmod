package net.runelite.client.plugins.microbot.piebaker;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@PluginDescriptor(
        name = PluginDescriptor.Bttqjs + "Pie Baker",
        description = "Automatically bakes pies using the Lunar spellbook",
        tags = {"pie", "baking", "cooking", "magic"},
        version = PieBakerPlugin.version,
        minClientVersion = "2.0.13",
        cardUrl = "",
        iconUrl = "",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class PieBakerPlugin extends Plugin {
    public static final String version = "1.0.0";
    @Inject
    private OverlayManager overlayManager;

    @Inject
    private PieBakerOverlay pieBakerOverlay;

    @Inject
    private PieBakerConfig config;

    @Inject
    private PieBakerScript pieBakerScript;

    private static final Map<String, Integer> RAW_PIES = new HashMap<>();

    static {
        RAW_PIES.put("uncooked berry pie", ItemID.UNCOOKED_REDBERRY_PIE);
        RAW_PIES.put("uncooked meat pie", ItemID.UNCOOKED_MEAT_PIE);
        RAW_PIES.put("raw mud pie", ItemID.UNCOOKED_MUD_PIE);
        RAW_PIES.put("uncooked apple pie", ItemID.UNCOOKED_APPLE_PIE);
        RAW_PIES.put("raw garden pie", ItemID.UNCOOKED_GARDEN_PIE);
        RAW_PIES.put("raw fish pie", ItemID.UNCOOKED_FISH_PIE);
        RAW_PIES.put("uncooked botanical pie", ItemID.UNCOOKED_BOTANICAL_PIE);
        RAW_PIES.put("uncooked mushroom pie", ItemID.UNCOOKED_MUSHROOM_PIE);
        RAW_PIES.put("raw admiral pie", ItemID.UNCOOKED_ADMIRAL_PIE);
        RAW_PIES.put("uncooked dragonfruit pie", ItemID.UNCOOKED_DRAGONFRUIT_PIE);
        RAW_PIES.put("raw wild pie", ItemID.UNCOOKED_WILD_PIE);
        RAW_PIES.put("raw summer pie", ItemID.UNCOOKED_SUMMER_PIE);
    }

    private Instant startTime;
    private int startMagicXP;
    private int startCookingXP;

    @Provides
    PieBakerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PieBakerConfig.class);
    }

    @Override
    protected void startUp() {
        if (overlayManager != null) {
            overlayManager.add(pieBakerOverlay);
        }
        startTime = Instant.now();
        startMagicXP = experience(Skill.MAGIC);
        startCookingXP = experience(Skill.COOKING);
        pieBakerScript.run(config, getSelectedRawPieId());
    }

    @Override
    protected void shutDown() {
        pieBakerScript.shutdown();
        if (overlayManager != null) {
            overlayManager.remove(pieBakerOverlay);
        }
    }

    private int getSelectedRawPieId() {
        return RAW_PIES.getOrDefault(config.selectedPie().toLowerCase(), -1);
    }

    public String getSelectedPie() {
        return config.selectedPie();
    }

    public int getMagicXPPerHour() {
        return calculateXPPerHour(startMagicXP, experience(Skill.MAGIC));
    }

    public int getCookingXPPerHour() {
        return calculateXPPerHour(startCookingXP, experience(Skill.COOKING));
    }

    /** Experience in {@code skill}, read on the client thread (0 when the read fails). */
    private static int experience(Skill skill) {
        return MobileActions.read(() -> Microbot.getClient().getSkillExperience(skill), 0);
    }

    private int calculateXPPerHour(int startXP, int currentXP) {
        if (startTime == null) {
            return 0;
        }
        long elapsedTime = Instant.now().getEpochSecond() - startTime.getEpochSecond();
        if (elapsedTime <= 0) {
            return 0;
        }
        int xpGained = currentXP - startXP;
        return (int) ((long) xpGained * 3600 / elapsedTime);
    }
}
