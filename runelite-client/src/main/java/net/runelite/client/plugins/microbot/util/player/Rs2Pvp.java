/*
 * Adapted from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/player/Rs2Pvp.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.player;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import org.apache.commons.lang3.ArrayUtils;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Wilderness and PvP reads, as upstream.
 * <p>
 * Mobile adaptations (marked): the local player's combat level is read on the client thread;
 * {@code isAttackable(Player)} builds the player snapshot on the client thread; and
 * {@code isAttackable()} logs at debug level instead of printing to standard output; and the
 * wilderness boxes use a private copy of the Microbot fork's {@code net.runelite.api.geometry.Cuboid},
 * and {@code WorldType.isDeadmanWorld}, which RuneMobile's API does not have.
 */
@Slf4j
public class Rs2Pvp {
    private static final Polygon NOT_WILDERNESS_BLACK_KNIGHTS = new Polygon( // this is black knights castle
            new int[]{2994, 2995, 2996, 2996, 2994, 2994, 2997, 2998, 2998, 2999, 3000, 3001, 3002, 3003, 3004, 3005, 3005,
                    3005, 3019, 3020, 3022, 3023, 3024, 3025, 3026, 3026, 3027, 3027, 3028, 3028, 3029, 3029, 3030, 3030, 3031,
                    3031, 3032, 3033, 3034, 3035, 3036, 3037, 3037},
            new int[]{3525, 3526, 3527, 3529, 3529, 3534, 3534, 3535, 3536, 3537, 3538, 3539, 3540, 3541, 3542, 3543, 3544,
                    3545, 3545, 3546, 3546, 3545, 3544, 3543, 3543, 3542, 3541, 3540, 3539, 3537, 3536, 3535, 3534, 3533, 3532,
                    3531, 3530, 3529, 3528, 3527, 3526, 3526, 3525},
            43
    );
    private static final Polygon FEROX_ENCLAVE = new Polygon(
            new int[]{3128, 3128, 3123, 3123, 3120, 3120, 3117, 3117, 3119, 3119, 3120, 3120, 3125, 3125, 3127, 3127, 3129, 
                    3129, 3137, 3137, 3147, 3147, 3156, 3156, 3161, 3161, 3153, 3153, 3152, 3151, 3150, 3149, 3145, 3145, 3141, 3141},
            new int[]{3609, 3615, 3615, 3620, 3620, 3622, 3622, 3635, 3635, 3640, 3640, 3645, 3645, 3644, 3644, 3645, 3645, 
                    3644, 3644, 3646, 3646, 3647, 3647, 3642, 3642, 3626, 3626, 3623, 3623, 3622, 3621, 3620, 3620, 3615, 3615, 3609},
            36
    );
    private static final Cuboid MAIN_WILDERNESS_CUBOID = new Cuboid(2944, 3525, 0, 3391, 4351, 3);
    private static final Cuboid GOD_WARS_WILDERNESS_CUBOID = new Cuboid(3008, 10112, 0, 3071, 10175, 3);
    private static final Cuboid WILDERNESS_UNDERGROUND_CUBOID = new Cuboid(2944, 9920, 0, 3455, 10879, 3);
    private static final Cuboid HUNTERS_END = new Cuboid(1728, 11520, 0, 1791, 11583, 3);
    private static final Cuboid SKELETAL_TOMB = new Cuboid(1856, 11520, 0, 1919, 11583, 3);
    private static final Cuboid WEB_CHASM = new Cuboid(1600, 11520, 0, 1663, 11583, 3);
    private static final Cuboid CALLISTOS_DEN = new Cuboid(3328, 10304, 0, 3391, 10367, 3);
    private static final Cuboid SILK_CHASM = new Cuboid(3392, 10176, 0, 3455, 10239, 3);
    private static final Cuboid VETIONS_REST = new Cuboid(3264, 10176, 0, 3327, 10239, 3);
    private static final Cuboid WILDERNESS_ESCAPE_CAVES = new Cuboid(3328, 10240, 0, 3391, 10303, 3);
    private static final Cuboid WILDERNESS_BH_CRATER = new Cuboid(3328, 3968, 0, 3519, 4159, 3);
    private static final Cuboid WILDERNESS_BH_CRATER_TWO = new Cuboid(3413, 4053, 0, 3434, 4074, 3);

    /**
     * Gets the wilderness level based on a world point
     * Java reimplementation of clientscript 384 [proc,wilderness_level]
     *
     * @param point the point in the world to get the wilderness level for
     * @return the int representing the wilderness level
     */
    public static int getWildernessLevelFrom(WorldPoint point) {
        int regionID = point.getRegionID();
        if (regionID != 12700 && regionID != 12187 && !FEROX_ENCLAVE.contains(point.getX(), point.getY())) {
            if (WILDERNESS_BH_CRATER.contains(point) && !WILDERNESS_BH_CRATER_TWO.contains(point)) {
                return 5;
            } else if (MAIN_WILDERNESS_CUBOID.contains(point)) {
                return NOT_WILDERNESS_BLACK_KNIGHTS.contains(point.getX(), point.getY()) ? 0 : (point.getY() - 3520) / 8 + 1;
            } else if (GOD_WARS_WILDERNESS_CUBOID.contains(point)) {
                return (point.getY() - 9920) / 8 - 1;
            } else if (VETIONS_REST.contains(point)) {
                return 35;
            } else if (SILK_CHASM.contains(point)) {
                return 35;
            } else if (CALLISTOS_DEN.contains(point)) {
                return 40;
            } else if (HUNTERS_END.contains(point)) {
                return 21;
            } else if (SKELETAL_TOMB.contains(point)) {
                return 21;
            } else if (WEB_CHASM.contains(point)) {
                return 29;
            } else if (WILDERNESS_ESCAPE_CAVES.contains(point)) {
                return 33 + (point.getY() % 64 - 6) * 7 / 50;
            } else {
                return WILDERNESS_UNDERGROUND_CUBOID.contains(point) ? (point.getY() - 9920) / 8 + 1 : 0;
            }
        } else {
            return 0;
        }
    }

    /**
     * Checks if the player is in the wilderness
     * 
     * @return True if the player is in the wilderness
     */
    public static boolean isInWilderness() {
        return Microbot.getVarbitValue(Varbits.IN_WILDERNESS) == 1;
    }

    /**
     * Determines if another player is attackable based off of wilderness level and combat levels
     *
     * @param rs2Player the player to determine attackability
     * @return returns true if the player is attackable, false otherwise
     */
    public static boolean isAttackable(Rs2PlayerModel rs2Player) {
        int wildernessLevel = 0;

        if (isDeadmanWorld()) {
            return true;
        }
        if (WorldType.isPvpWorld(Microbot.getClient().getWorldType())) {
            wildernessLevel += 15;
        }
        if (Microbot.getVarbitValue(Varbits.IN_WILDERNESS) == 1) {
            wildernessLevel += getWildernessLevelFrom(Rs2Player.getWorldLocation());
        }
        return wildernessLevel != 0 && Math.abs(localCombatLevel() - rs2Player.getCombatLevel()) <= wildernessLevel;
    }

    public static boolean isAttackable(Player player) {
        // Mobile: the player model is a client-thread snapshot.
        Rs2PlayerModel model = MobileActions.read(() -> new Rs2PlayerModel(Microbot.getClient(), player), null);
        return model != null && isAttackable(model);
    }

    /** Mobile: the Microbot fork's {@code WorldType.isDeadmanWorld}, which RuneMobile's API lacks. */
    private static boolean isDeadmanWorld() {
        return Microbot.getClient().getWorldType().contains(WorldType.DEADMAN);
    }

    /** Mobile: the local player's combat level, read on the client thread (0 without a player). */
    private static int localCombatLevel() {
        return MobileActions.read(() -> {
            Player local = Microbot.getClient().getLocalPlayer();
            return local == null ? 0 : local.getCombatLevel();
        }, 0);
    }

    /**
     * Determines if another player is attackable based off of wilderness level and combat levels
     *
     * @param rs2Player the player to determine attackability
     * @return returns true if the player is attackable, false otherwise
     */
    public static boolean isAttackable(Rs2PlayerModel rs2Player, boolean isDeadManworld, boolean isPvpWorld, int wildernessLevel) {
        return wildernessLevel != 0 && Math.abs(localCombatLevel() - rs2Player.getCombatLevel()) <= wildernessLevel;
    }

    /**
     * Determines if any player is attackable based off of wilderness level and combat levels
     * @return
     */
    public static boolean isAttackable() {
        List<Rs2PlayerModel> players = Rs2Player.getPlayers(player -> true).collect(Collectors.toList());
        int wildernessLevel = 0;
        boolean isDeadManWorld = isDeadmanWorld();
        boolean isPVPWorld = WorldType.isPvpWorld(Microbot.getClient().getWorldType());
        if (Microbot.getVarbitValue(Varbits.IN_WILDERNESS) == 1) {
            wildernessLevel += getWildernessLevelFrom(Rs2Player.getWorldLocation());
        }
        for (Rs2PlayerModel player: players) {
            if (!isAttackable(player, isDeadManWorld, isPVPWorld, wildernessLevel)) continue;
            log.debug("Attackable player {} (combat {}) detected", player.getName(), player.getCombatLevel());
            return true;
        }
        log.debug("No attackable players detected");
        return false;
    }

    public static long calculateRisk(Client client, ItemManager itemManager) {
        if (client.getItemContainer(InventoryID.EQUIPMENT) == null) {
            return 0;
        }
        if (client.getItemContainer(InventoryID.INVENTORY).getItems() == null) {
            return 0;
        }
        Item[] items = ArrayUtils.addAll(Objects.requireNonNull(client.getItemContainer(InventoryID.EQUIPMENT)).getItems(),
                Objects.requireNonNull(client.getItemContainer(InventoryID.INVENTORY)).getItems());
        TreeMap<Long, Item> priceMap = new TreeMap<>(Comparator.comparingLong(Long::longValue));
        for (Item i : items) {
            long value = (itemManager.getItemPrice(i.getId()) * i.getQuantity());

            final ItemComposition itemComposition = itemManager.getItemComposition(i.getId());
            if (!itemComposition.isTradeable() && value == 0) {
                value = (long) itemComposition.getPrice() * i.getQuantity();
                priceMap.put(value, i);
            } else {
                value = itemManager.getItemPrice(i.getId()) * i.getQuantity();
                if (i.getId() > 0 && value > 0) {
                    priceMap.put(value, i);
                }
            }
        }
        return priceMap.keySet().stream().mapToLong(Long::longValue).sum();

    }

    /** Mobile: copy of the Microbot fork's {@code net.runelite.api.geometry.Cuboid} (inclusive bounds). */
    private static final class Cuboid {
        private final WorldPoint southWest;
        private final WorldPoint northEast;

        Cuboid(int x1, int y1, int z1, int x2, int y2, int z2) {
            this.southWest = new WorldPoint(x1, y1, z1);
            this.northEast = new WorldPoint(x2, y2, z2);
        }

        boolean contains(WorldPoint worldPoint) {
            if (worldPoint.getPlane() < southWest.getPlane() || worldPoint.getPlane() > northEast.getPlane()) {
                return false;
            }
            if (worldPoint.getY() < southWest.getY() || worldPoint.getY() > northEast.getY()) {
                return false;
            }
            return worldPoint.getX() >= southWest.getX() && worldPoint.getX() <= northEast.getX();
        }
    }
}
