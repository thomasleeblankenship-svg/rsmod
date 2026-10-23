package net.runelite.client.plugins.mobilemicrobot;

import net.runelite.api.GameObject;
import net.runelite.api.Player;

/**
 * MVP example: stand near a tree with an axe; target visible trees; stop
 * when inventory fills. No banking, dropping, pathfinding or anti-ban claims.
 */
public final class WoodcuttingScript implements MobileScript
{
    private int cooldownTicks;
    private int attempts;
    private String status = "Looking for trees";

    @Override
    public void onGameTick(MobileScriptContext context)
    {
        if (context.inventoryFull())
        {
            status = "Inventory full: stopped";
            return;
        }
        Player player = context.client().getLocalPlayer();
        if (player == null)
        {
            status = "Waiting for player";
            return;
        }
        // Avoid sending a second click while the player is moving or animating.
        if (player.getAnimation() != -1 || context.player().isMoving())
        {
            cooldownTicks = 4;
            status = "Working or walking";
            return;
        }
        if (cooldownTicks-- > 0)
        {
            return;
        }
        GameObject tree = context.objects().findNearest(context.config().treeType().objectName(),
            context.config().maxDistance());
        if (tree == null)
        {
            status = "No visible " + context.config().treeType().objectName() + " within range";
            cooldownTicks = 3;
            return;
        }
        if (context.objects().interact(tree))
        {
            attempts++;
            status = "Requested tree click #" + attempts;
            cooldownTicks = 6;
        }
        else
        {
            status = "Could not target tree";
            cooldownTicks = 4;
        }
    }

    @Override
    public String status()
    {
        return status;
    }
}
