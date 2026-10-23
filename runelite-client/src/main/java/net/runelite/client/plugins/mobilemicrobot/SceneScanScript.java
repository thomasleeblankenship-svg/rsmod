package net.runelite.client.plugins.mobilemicrobot;

import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.GameObject;

/** Read-only scene diagnostics without retaining scene objects across ticks. */
public final class SceneScanScript implements MobileScript
{
    private int scanCount;
    private String status = "Waiting for scene";

    @Override
    public void onGameTick(MobileScriptContext ctx)
    {
        scanCount++;
        if (scanCount % 10 != 1)
        {
            return;
        }
        Scene scene = ctx.client().getScene();
        int objects = 0;
        if (scene != null && scene.getTiles() != null)
        {
            for (Tile[][] plane : scene.getTiles())
            {
                if (plane == null) continue;
                for (Tile[] row : plane)
                {
                    if (row == null) continue;
                    for (Tile tile : row)
                    {
                        if (tile == null || tile.getGameObjects() == null) continue;
                        for (GameObject o : tile.getGameObjects())
                        {
                            if (o != null) objects++;
                        }
                    }
                }
            }
        }
        status = "Scene objects: " + objects + "; free inventory slots: " + ctx.inventory().freeSlots();
    }

    @Override public String status() { return status; }
}
