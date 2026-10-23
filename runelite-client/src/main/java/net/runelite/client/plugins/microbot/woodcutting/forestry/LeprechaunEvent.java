/*
 * Mobile adaptation of uploaded Auto Woodcutting v1.8.7, source commit
 * 080d9674fe87f282c958c25b5bb8f6b96665fe36. Original algorithms retained;
 * desktop host/lifecycle and client-thread API access adapted for RuneMobile.
 */
package net.runelite.client.plugins.microbot.woodcutting.forestry;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.microbot.woodcutting.enums.ForestryEvents;
import org.slf4j.event.Level;

import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;
@Slf4j
public class LeprechaunEvent implements BlockingEvent {

    private final AutoWoodcuttingPlugin plugin;

    public LeprechaunEvent(AutoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        try {
            if (plugin == null || !plugin.isActive()) return false;
            if (Microbot.getClient() == null || !Microbot.isLoggedIn()) return false;
            var leprechaun = Microbot.getRs2NpcCache().query()
                    .withId(NpcID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN)
                    .nearest();
            return leprechaun != null;
        } catch (Exception e) {
            log.error("LeprechaunEvent: Exception in validate method", e);
            return false;
        }
    }

    @Override
    public boolean execute() {
        Microbot.log("LeprechaunEvent: Executing Leprechaun event");
        plugin.currentForestryEvent = ForestryEvents.RAINBOW;
        Rs2Walker.setTarget(null); // stop walking, stop moving to bank for example
        while (plugin.isActive() && !Thread.currentThread().isInterrupted() && this.validate()) {
            log.info("LeprechaunEvent: Leprechaun event still valid, continuing execution get opbject");
            var endOfRainbow = Microbot.getRs2TileObjectCache().query().withId(ObjectID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN_RAINBOW).nearest();
            if (endOfRainbow == null) {
                log.warn("LeprechaunEvent: End of the rainbow not found, retrying...");
                sleepGaussian(900, 300);
                continue; // If the end of the rainbow is not found, we cannot proceed with the event
            }
            // Move to the end of the rainbow
            var location = endOfRainbow.getWorldLocation();
            if (!Rs2Player.getWorldLocation().equals(location)) {
                Microbot.log("LeprechaunEvent: Walking to the end of the rainbow at " + location, Level.INFO);
                Rs2Walker.walkFastCanvas(location);
                waitUntil(() -> Rs2Player.getWorldLocation().equals(location), 5000);
            }
        }
        plugin.incrementForestryEventCompleted();
        return true;

        //TODO: Implement interaction with the leprechaun for banking
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
    /** Bound every wait to this host, so stopping cannot leave an old Forestry task waiting. */
    private boolean waitUntil(java.util.function.BooleanSupplier condition, long timeoutMillis) {
        boolean completed = net.runelite.client.plugins.microbot.util.Global.sleepUntil(
                () -> !plugin.isActive() || Thread.currentThread().isInterrupted() || condition.getAsBoolean(), (int) Math.min(Integer.MAX_VALUE, Math.max(0, timeoutMillis)));
        return completed && plugin.isActive() && !Thread.currentThread().isInterrupted();
    }

}
