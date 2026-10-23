/*
 * Adapted from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/coords/Rs2WorldPoint.java.
 * BSD 2-Clause License; see LICENSE.
 *
 * Adaptation: pathTo(...) and distanceToPath(...) are not ported; they need Rs2Tile.pathTo,
 * which mobile does not have. Callers fail to compile instead of silently getting null.
 */
package net.runelite.client.plugins.microbot.util.coords;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.Objects;

@Slf4j
public class Rs2WorldPoint {
    @Setter
    private WorldPoint worldPoint;

    // Constructor accepting coordinates
    public Rs2WorldPoint(int x, int y, int plane) {
        this.worldPoint = new WorldPoint(x, y, plane);
    }

    // Constructor accepting a WorldPoint instance
    public Rs2WorldPoint(WorldPoint worldPoint) {
        this.worldPoint = worldPoint;

    }

    // Getter methods
    public int getX() {
        return worldPoint.getX();
    }

    public int getY() {
        return worldPoint.getY();
    }

    public int getPlane() {
        return worldPoint.getPlane();
    }

    public WorldPoint getWorldPoint() {
        return worldPoint;
    }

    /**
     * Converts an instanced worldPoint coordinate to a global worldpoint
     * this can be used for getting objects in instanced room by location
     * @param worldPoint
     * @return
     */
    public static WorldPoint convertInstancedWorldPoint(WorldPoint worldPoint) {
        if (worldPoint == null) return null;

        LocalPoint l = Rs2LocalPoint.fromWorldInstance(worldPoint);
        // If we're in PoH this function still gets called by handleDoors for the destination tile of the teleport.
        // Since that tile is not in the instance map, it returns null.
        // We can just return the worldPoint as is OR we can return null.
        if (l == null) return null;
        WorldPoint globalWorldPoint = WorldPoint.fromLocal(Microbot.getClient(), l);

        return globalWorldPoint;
    }


    public static WorldPoint toLocalInstance(WorldPoint worldPoint) {
        if (worldPoint == null) return null;


        return WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(), worldPoint).stream().findFirst().orElse(null);
    }

    /**
     * Calculate the distance quikcly with Chebyshev distance
     * https://iq.opengenus.org/euclidean-vs-manhattan-vs-chebyshev-distance/
     * @param a
     * @param b
     * @return
     */
    public static int quickDistance(WorldPoint a, WorldPoint b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(normalizeY(a) - normalizeY(b));
        return Math.max(dx, dy);
    }

    /**
     * Normalize a point to use for comparison
     * This is used for caves
     * @param point
     * @return
     */
    public static int normalizeY(WorldPoint point) {
        int y = point.getY();

        if (y > 6400) {
            return y - 6400;
        }
        return y;
    }

    // Override equals, hashCode, and toString if necessary
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof Rs2WorldPoint) {
            Rs2WorldPoint other = (Rs2WorldPoint) obj;
            return Objects.equals(worldPoint, other.worldPoint);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(worldPoint);
    }

    @Override
    public String toString() {
        return String.valueOf(worldPoint);
    }
}

