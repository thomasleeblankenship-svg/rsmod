/*
 * Copied from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/poh/data/MountedXerics.java.
 * Adaptation: execute() added in batch AQ (the walker's POH steps use their own executor);
 * lint-only edits (PMD suppressions, a shorter array initializer).
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.poh.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.DecorativeObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.worldmap.TeleportLocationData;



@Getter
@RequiredArgsConstructor
public enum MountedXerics implements PohTeleport {
    LOOKOUT(ObjectID.POH_AMULET_XERIC_LOOKOUT, "Xeric's Lookout", TeleportLocationData.XERICS_LOOKOUT.getLocation()),
    GLADE(ObjectID.POH_AMULET_XERIC_GLADE, "Xeric's Glade", TeleportLocationData.XERICS_GLADE.getLocation()),
    INFERNO(ObjectID.POH_AMULET_XERIC_INFERNO, "Xeric's Inferno", TeleportLocationData.XERICS_INFERNO.getLocation()),
    HEART(ObjectID.POH_AMULET_XERIC_HEART, "Xeric's Heart", TeleportLocationData.XERICS_HEART.getLocation()),
//    HONOUR(ObjectID.POH_AMULET_XERIC_GLADE, "Xeric's Honour", TeleportLocationData.XERICS_HONOUR.getLocation()), //Require's Ancient Tablet to be used on Xeric's Talisman
    ;

    private final int objectId;
    private final String destinationName;
    private final WorldPoint destination;

    @SuppressWarnings("PMD.FinalFieldCouldBeStatic") // instance getter implements PohTeleport.getDuration()
    private final int duration = 4;

    public static final Integer[] IDS = {ObjectID.POH_AMULET_XERIC, ObjectID.POH_AMULET_XERIC_LOOKOUT, ObjectID.POH_AMULET_XERIC_GLADE, ObjectID.POH_AMULET_XERIC_INFERNO, ObjectID.POH_AMULET_XERIC_HEART, ObjectID.POH_AMULET_XERIC_HONOUR};

    public static boolean isMountedXerics(DecorativeObject go) {
        if (go == null) return false;
        for (int objId : IDS) {
            if (objId == go.getId()) return true;
        }
        return false;
    }

    @Override
    public String displayInfo() {
        return getClass().getSimpleName() + " -> " + destinationName;
    }

    /** As upstream: the mounted item in the house scene, or {@code null}. */
    public static DecorativeObject getObject() {
        return net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.getDecorativeObject(IDS);
    }

    /**
     * As upstream: the destination's left-click option when the mounted item shows it, else its
     * "Teleport menu" and then the destination in that menu.
     */
    @Override
    public boolean execute() {
        DecorativeObject talisman = getObject();
        if (talisman == null) {
            return false;
        }
        int id = net.runelite.client.plugins.microbot.mobile.MobileActions.read(talisman::getId, -1);
        if (id == objectId) {
            return net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.interact(talisman, destinationName);
        }
        if (getMenuWidget() == null) {
            if (!net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.interact(talisman, "Teleport menu")
                    || !net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> getMenuWidget() != null, 10_000)) {
                return false;
            }
        }
        return net.runelite.client.plugins.microbot.util.widget.Rs2Widget.clickWidget(destinationName);
    }

    private static net.runelite.api.widgets.Widget getMenuWidget() {
        return net.runelite.client.plugins.microbot.util.widget.Rs2Widget.getWidget(net.runelite.api.gameval.InterfaceID.MENU, 3);
    }
}
