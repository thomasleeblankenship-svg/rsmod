/*
 * Copied from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/poh/data/JewelleryBoxType.java.
 * Adaptation: data only; upstream's getObject(), getCurrentJewelleryBoxType() (actions and live-object lookups) are left to the walker's POH executor.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.poh.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.GameObject;
import net.runelite.api.gameval.ObjectID;

import java.util.Arrays;
import java.util.List;

@Getter
@RequiredArgsConstructor
public enum JewelleryBoxType {
    NONE(-1),
    BASIC(ObjectID.POH_JEWELLERY_BOX_1),
    FANCY(ObjectID.POH_JEWELLERY_BOX_2),
    ORNATE(ObjectID.POH_JEWELLERY_BOX_3);

    private final int objectId;

    /**
     * Gets all teleports available for this jewellery box type
     */
    public List<JewelleryBox> getAvailableTeleports() {
        if (this == NONE) {
            return List.of();
        }
        return JewelleryBox.getAvailableTeleports(this);
    }

    /**
     * Gets all jewellery box object IDs (excluding NONE)
     */
    public static Integer[] getJewelleryBoxIds() {
        return Arrays.stream(values())
                .filter(box -> box != NONE)
                .map(JewelleryBoxType::getObjectId)
                .toArray(Integer[]::new);
    }

    /**
     * Gets the jewellery box type for the specified GameObject
     * @param go GameObject to check. If null, returns null.
     * @return JewelleryBoxType or null if not found.
     */
    public static JewelleryBoxType getJewelleryBoxType(GameObject go) {
        if (go == null) {
            return null;
        }
        int objId = go.getId();
        for (JewelleryBoxType jewelleryBoxType : JewelleryBoxType.values()) {
            if (jewelleryBoxType.objectId == objId) {
                return jewelleryBoxType;
            }
        }
        return null;
    }

}
