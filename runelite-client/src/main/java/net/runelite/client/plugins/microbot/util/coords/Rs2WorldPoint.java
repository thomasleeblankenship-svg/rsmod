/*
 * Adapted from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/coords/Rs2WorldPoint.java.
 * BSD 2-Clause License; see LICENSE.
 *
 * Adaptation: pathTo(...) reads the scene tiles in one client-thread read and uses Rs2Tile's
 * scene path search (upstream's route-finder rules).
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

    /** As upstream: {@link #pathTo(WorldPoint, boolean)} as checkpoint tiles. */
    public java.util.List<WorldPoint> pathTo(WorldPoint other)
    {
        return pathTo(other, false);
    }

    /**
     * As upstream: the scene path from this point to {@code other} (every tile when {@code fullPath},
     * else checkpoints), or null on different floors, outside the loaded scene or with no path.
     */
    public java.util.List<WorldPoint> pathTo(WorldPoint other, boolean fullPath)
    {
        if (worldPoint == null || other == null || getPlane() != other.getPlane())
        {
            return null;
        }
        net.runelite.api.Tile[] ends = net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
        {
            net.runelite.api.WorldView view = Microbot.getClient().getTopLevelWorldView();
            if (view == null || view.getScene() == null)
            {
                return null;
            }
            LocalPoint source = LocalPoint.fromWorld(view, getX(), getY());
            LocalPoint target = LocalPoint.fromWorld(view, other.getX(), other.getY());
            if (source == null || target == null)
            {
                return null;
            }
            net.runelite.api.Tile[][] tiles = view.getScene().getTiles()[getPlane()];
            return new net.runelite.api.Tile[]{tiles[source.getSceneX()][source.getSceneY()], tiles[target.getSceneX()][target.getSceneY()]};
        }, null);
        if (ends == null)
        {
            return null;
        }
        java.util.List<net.runelite.api.Tile> tiles = fullPath
            ? net.runelite.client.plugins.microbot.util.tile.Rs2Tile.fullPathTo(ends[0], ends[1])
            : net.runelite.client.plugins.microbot.util.tile.Rs2Tile.pathTo(ends[0], ends[1]);
        if (tiles == null)
        {
            return null;
        }
        java.util.List<WorldPoint> points = new java.util.ArrayList<>();
        for (net.runelite.api.Tile tile : tiles)
        {
            WorldPoint point = tile == null ? null : net.runelite.client.plugins.microbot.mobile.MobileActions.read(tile::getWorldLocation, null);
            if (point == null)
            {
                break;
            }
            points.add(point);
        }
        return points;
    }

    /**
     * As upstream: the walked length of the scene path to {@code other}, or {@link Integer#MAX_VALUE}
     * when there is none or it stops short of {@code other}.
     */
    public int distanceToPath(WorldPoint other)
    {
        java.util.List<WorldPoint> checkpoints = other == null ? null : pathTo(other);
        if (checkpoints == null || checkpoints.isEmpty())
        {
            return Integer.MAX_VALUE;
        }
        WorldPoint end = checkpoints.get(checkpoints.size() - 1);
        if (other.getX() != end.getX() || other.getY() != end.getY())
        {
            return Integer.MAX_VALUE;
        }
        WorldPoint previous = getWorldPoint();
        int distance = 0;
        for (WorldPoint next : checkpoints)
        {
            distance += previous.distanceTo2D(next);
            previous = next;
        }
        return distance;
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

