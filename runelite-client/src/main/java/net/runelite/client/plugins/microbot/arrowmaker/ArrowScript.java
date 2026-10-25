package net.runelite.client.plugins.microbot.arrowmaker;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

/** Combines arrow, bolt and dart materials through the make menu; mobile port notes in {@code MOBILE_PORT.md}. */
public class ArrowScript extends Script {
    /** The make-menu widget the "1" key answers. */
    private static final int MAKE_MENU_WIDGET = 17694733;

    public boolean run(ArrowConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
				if (!super.run()) return;
				if (!Microbot.isLoggedIn()) return;
                boolean anyMaterials = false;
                if (config.ARROWBool()) {
                    anyMaterials |= checkAndUseItem(config.ARROW().getItem1(), config.ARROW().getItem2());
                }
                if (config.BOLTBool()) {
                    anyMaterials |= checkAndUseItem(config.BOLT().getItem1(), config.BOLT().getItem2());
                }
                if (config.DARTBool()) {
                    anyMaterials |= checkAndUseItem(config.DART().getItem1(), config.DART().getItem2());
                }
                if (config.TIPPINGBool()) {
                    anyMaterials |= checkAndUseItem(config.TIP().getItem1(), config.TIP().getItem2());
                }
                if (config.DRAGONTIPPINGBool()) {
                    anyMaterials |= checkAndUseItem(config.DragonTIP().getItem1(), config.DragonTIP().getItem2());
                }
                if (!anyMaterials && super.isRunning()) {
                    // Mobile: upstream idled forever once the materials ran out.
                    stopPlugin("Arrow Maker stopped: no materials left for the selected items.");
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
                // Mobile: stop rather than retry a failing step every 600 ms.
                stopPlugin("Arrow Maker stopped after an error: " + ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    /** Combines {@code item1} with {@code item2} when both are held; returns whether both were held. */
    private boolean checkAndUseItem(String item1, String item2) {
        if (Rs2Inventory.count(item1) > 0 && Rs2Inventory.count(item2) > 0) {
            // Mobile: press the make key only once the menu is up; a refused combine is retried next loop.
            if (Rs2Inventory.combine(item1, item2)
                    && sleepUntil(() -> Rs2Widget.getWidget(MAKE_MENU_WIDGET) != null, 5_000)) {
                handleSleep(item1, item2);
            }
            return true;
        }
        return false;
    }

    /** Waits out a make action like upstream's 12-14 s sleep, ending early on Stop or when a material runs out. */
    private void handleSleep(String item1, String item2) {
        Rs2Keyboard.keyPress('1');
        sleepUntil(() -> !super.isRunning() || Rs2Inventory.count(item1) == 0 || Rs2Inventory.count(item2) == 0,
                Rs2Random.between(12_000, 14_000));
    }

    /** Stops with {@code message}, turning the plugin off (not just this loop) so the Microbot Mobile screen shows it. */
    private void stopPlugin(String message) {
        Microbot.showMessage(message);
        shutdown();
        Microbot.stopPlugin(ArrowPlugin.class);
    }
}
