package net.runelite.client.plugins.microbot.util.tileobject;

import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Upstream 2.6.38's legacy {@link TileObject} wrapper that remembers which kind of scene object it
 * holds. Definition reads run on the client thread. (Scripts written against the newer API use
 * {@code api.tileobject.models.Rs2TileObjectModel} instead.) Its {@code click} action is ported separately.
 */
public class Rs2TileObjectModel implements TileObject {

    public Rs2TileObjectModel(GameObject gameObject) {
        this.tileObject = gameObject;
        this.tileObjectType = TileObjectType.GAME;
    }

    public Rs2TileObjectModel(DecorativeObject tileObject) {
        this.tileObject = tileObject;
        this.tileObjectType = TileObjectType.DECORATIVE;
    }

    public Rs2TileObjectModel(WallObject tileObject) {
        this.tileObject = tileObject;
        this.tileObjectType = TileObjectType.WALL;
    }

    public Rs2TileObjectModel(GroundObject tileObject) {
        this.tileObject = tileObject;
        this.tileObjectType = TileObjectType.GROUND;
    }

    public Rs2TileObjectModel(TileObject tileObject) {
        this.tileObject = tileObject;
        this.tileObjectType = TileObjectType.GENERIC;
    }

    @Getter
    private final TileObjectType tileObjectType;
    private final TileObject tileObject;


    @Override
    public long getHash() {
        return tileObject.getHash();
    }

    @Override
    public int getX() {
        return tileObject.getX();
    }

    @Override
    public int getY() {
        return tileObject.getY();
    }

    @Override
    public int getZ() {
        return tileObject.getZ();
    }

    @Override
    public int getPlane() {
        return tileObject.getPlane();
    }

    @Override
    public WorldView getWorldView() {
        return tileObject.getWorldView();
    }

    @Override
    public int getId() {
        return tileObject.getId();
    }

    @Override
    public @NotNull WorldPoint getWorldLocation() {
        return tileObject.getWorldLocation();
    }

    public String getName() {
        return MobileActions.read(() -> {
            ObjectComposition composition = Microbot.getClient().getObjectDefinition(tileObject.getId());
            if (composition != null && composition.getImpostorIds() != null)
            {
                composition = composition.getImpostor();
            }
            if (composition == null)
                return null;
            return Rs2UiHelper.stripColTags(composition.getName());
        }, null);
    }

    @Override
    public @NotNull LocalPoint getLocalLocation() {
        return tileObject.getLocalLocation();
    }

    @Override
    public @Nullable Point getCanvasLocation() {
        return tileObject.getCanvasLocation();
    }

    @Override
    public @Nullable Point getCanvasLocation(int zOffset) {
        return tileObject.getCanvasLocation();
    }

    @Override
    public @Nullable Polygon getCanvasTilePoly() {
        return tileObject.getCanvasTilePoly();
    }

    @Override
    public @Nullable Point getCanvasTextLocation(Graphics2D graphics, String text, int zOffset) {
        return tileObject.getCanvasTextLocation(graphics, text, zOffset);
    }

    @Override
    public @Nullable Point getMinimapLocation() {
        return tileObject.getMinimapLocation();
    }

    @Override
    public @Nullable Shape getClickbox() {
        return tileObject.getClickbox();
    }

    @Override
    public @Nullable String getOpOverride(int index) {
        return tileObject.getOpOverride(index);
    }

    @Override
    public boolean isOpShown(int index) {
        return tileObject.isOpShown(index);
    }

    public ObjectComposition getObjectComposition() {
        return MobileActions.read(() -> {
            ObjectComposition composition = Microbot.getClient().getObjectDefinition(tileObject.getId());
            if (composition != null && composition.getImpostorIds() != null)
            {
                composition = composition.getImpostor();
            }
            return composition;
        }, null);
    }
}
