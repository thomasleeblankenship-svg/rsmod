package net.runelite.client.plugins.mobilemicrobot;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.headless.JavaBridge;

/**
 * Called solely from RuneLite's client/game thread through GameTick.
 * Never retain Tile or GameObject references across ticks.
 */
public final class MobileScriptContext
{
    private final Client client;
    private final MobileMicrobotConfig config;
    private boolean inputReserved;

    MobileScriptContext(Client client, MobileMicrobotConfig config)
    {
        this.client = client;
        this.config = config;
    }

    public Client client()
    {
        return client;
    }

    public MobileMicrobotConfig config()
    {
        return config;
    }

    public boolean loggedIn()
    {
        return client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null;
    }

    public Rs2Inventory inventory()
    {
        return new Rs2Inventory(this);
    }

    public Rs2Player player()
    {
        return new Rs2Player(this);
    }

    public Rs2GameObject objects()
    {
        return new Rs2GameObject(this);
    }

    public Rs2Walker walker()
    {
        return new Rs2Walker(this);
    }

    public boolean inventoryFull()
    {
        return inventory().isFull();
    }

    // At most one mobile-generated input per game-tick context.
    boolean reserveInput()
    {
        if (inputReserved)
        {
            return false;
        }
        inputReserved = true;
        return true;
    }

    public GameObject nearestGameObject(String exactName, int maxDistance)
    {
        if (!loggedIn() || exactName == null || exactName.isEmpty())
        {
            return null;
        }
        Scene scene = client.getScene();
        Player local = client.getLocalPlayer();
        if (scene == null || local == null)
        {
            return null;
        }
        Tile[][][] tiles = scene.getTiles();
        if (tiles == null)
        {
            return null;
        }
        WorldPoint origin = local.getWorldLocation();
        GameObject nearest = null;
        int best = Math.max(1, Math.min(32, maxDistance)) + 1;
        for (Tile[][] plane : tiles)
        {
            if (plane == null)
            {
                continue;
            }
            for (Tile[] column : plane)
            {
                if (column == null)
                {
                    continue;
                }
                for (Tile tile : column)
                {
                    if (tile == null)
                    {
                        continue;
                    }
                    GameObject[] objects = tile.getGameObjects();
                    if (objects == null)
                    {
                        continue;
                    }
                    for (GameObject object : objects)
                    {
                        if (object == null)
                        {
                            continue;
                        }
                        WorldPoint location = object.getWorldLocation();
                        if (location == null || location.getPlane() != origin.getPlane())
                        {
                            continue;
                        }
                        int distance = origin.distanceTo(location);
                        if (distance >= best)
                        {
                            continue;
                        }
                        ObjectComposition composition = client.getObjectDefinition(object.getId());
                        if (composition == null || !exactName.equalsIgnoreCase(composition.getName()))
                        {
                            continue;
                        }
                        if (clickPoint(object) == null)
                        {
                            continue;
                        }
                        nearest = object;
                        best = distance;
                    }
                }
            }
        }
        return nearest;
    }

    /** Input passes through RuneMobile's EXISTING AWT/engine targeting bridge. */
    public boolean click(GameObject object)
    {
        if (object == null || !loggedIn())
        {
            return false;
        }
        Point point = clickPoint(object);
        if (point == null)
        {
            return false;
        }
        if (!reserveInput())
        {
            return false;
        }
        JavaBridge.onClick(point.getX(), point.getY(), MouseEvent.BUTTON1);
        return true; // input queued, NOT an assertion of a successful game interaction
    }

    private Point clickPoint(GameObject object)
    {
        Shape shape = object.getClickbox();
        if (shape == null)
        {
            return null;
        }
        Rectangle b = shape.getBounds();
        if (b.isEmpty())
        {
            return null;
        }
        final int centerX = b.x + b.width / 2;
        final int centerY = b.y + b.height / 2;
        if (usable(shape, centerX, centerY))
        {
            return new Point(centerX, centerY);
        }
        // Convex model hulls often contain transparent regions; prefer a contained point.
        for (int j = 1; j < 5; j++)
        {
            for (int i = 1; i < 5; i++)
            {
                int x = b.x + b.width * i / 5;
                int y = b.y + b.height * j / 5;
                if (usable(shape, x, y))
                {
                    return new Point(x, y);
                }
            }
        }
        return null;
    }

    private boolean usable(Shape shape, int x, int y)
    {
        return shape.contains(x, y) && x >= 0 && y >= 0
            && x < client.getCanvasWidth() && y < client.getCanvasHeight();
    }
}
