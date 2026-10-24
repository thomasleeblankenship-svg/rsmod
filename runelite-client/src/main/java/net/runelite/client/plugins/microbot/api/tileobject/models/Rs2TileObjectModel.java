/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/tileobject/models/Rs2TileObjectModel.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.tileobject.models;

import java.util.regex.Pattern;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.IEntity;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;

/**
 * Snapshot of one game, wall, decorative or ground object captured by
 * {@code Rs2TileObjectCache}.
 * <p>
 * Location, id, plane and world view are captured on the client thread. The
 * name follows upstream (impostor-resolved composition name with colour tags
 * stripped) but is resolved lazily on first use, on the client thread, and then
 * kept for the life of this snapshot. Adaptation: the model does not implement
 * {@link TileObject}; of the canvas helpers only the clickbox, tile outline and
 * minimap point are provided.
 */
public class Rs2TileObjectModel implements IEntity
{
	/** Upstream {@code Rs2TextSanitizer.COLOR_TAG_STRIP}. */
	private static final Pattern COLOR_TAG_STRIP = Pattern.compile("(?i)</?col(?:=[^>]*?)?>");

	private final MicrobotReadServices services;
	private final TileObject tileObject;
	private final TileObjectType tileObjectType;
	private final int id;
	private final int plane;
	private final WorldPoint worldLocation;
	private final LocalPoint localLocation;
	private final WorldView worldView;
	private final long hash;
	private volatile boolean nameResolved;
	private volatile String name;

	/** Captures {@code tileObject}; must be called on the client thread. */
	public Rs2TileObjectModel(MicrobotReadServices services, TileObject tileObject)
	{
		this.services = services;
		this.tileObject = tileObject;
		this.tileObjectType = typeOf(tileObject);
		this.id = tileObject.getId();
		WorldPoint objectLocation = tileObject.getWorldLocation();
		this.plane = objectLocation == null ? tileObject.getPlane() : objectLocation.getPlane();
		this.worldView = tileObject.getWorldView();
		this.worldLocation = worldLocationOf(tileObject, worldView);
		this.localLocation = tileObject.getLocalLocation();
		this.hash = tileObject.getHash();
	}

	/**
	 * As upstream: a model of {@code gameObject}, for example from a {@code GameObjectSpawned}
	 * event, with the installed read services; must be called on the client thread.
	 */
	public Rs2TileObjectModel(GameObject gameObject)
	{
		this(MicrobotReadServices.require(), gameObject);
	}

	static TileObjectType typeOf(TileObject tileObject)
	{
		if (tileObject instanceof GameObject)
		{
			return TileObjectType.GAME;
		}
		if (tileObject instanceof WallObject)
		{
			return TileObjectType.WALL;
		}
		if (tileObject instanceof DecorativeObject)
		{
			return TileObjectType.DECORATIVE;
		}
		if (tileObject instanceof GroundObject)
		{
			return TileObjectType.GROUND;
		}
		return TileObjectType.GENERIC;
	}

	/** Upstream rule: a multi-tile game object is located at its scene-min tile. */
	private static WorldPoint worldLocationOf(TileObject tileObject, WorldView wv)
	{
		WorldPoint worldLocation = tileObject.getWorldLocation();
		if (!(tileObject instanceof GameObject))
		{
			return worldLocation;
		}

		Point sceneMin = ((GameObject) tileObject).getSceneMinLocation();
		if (wv == null || sceneMin == null)
		{
			return worldLocation;
		}

		return WorldPoint.fromScene(wv, sceneMin.getX(), sceneMin.getY(),
			worldLocation == null ? tileObject.getPlane() : worldLocation.getPlane());
	}

	/** The live object. Read it only on the client thread. */
	public TileObject getTileObject()
	{
		return tileObject;
	}

	public TileObjectType getTileObjectType()
	{
		return tileObjectType;
	}

	@Override
	public int getId()
	{
		return id;
	}

	public int getPlane()
	{
		return plane;
	}

	/**
	 * Impostor-resolved object name without colour tags; {@code "null"} for
	 * unnamed objects (as the game reports), {@code null} if no composition
	 * exists or the client thread could not be reached.
	 */
	@Override
	public String getName()
	{
		if (!nameResolved)
		{
			name = services.read(this::resolveName).orElse(null);
			nameResolved = true;
		}
		return name;
	}

	private String resolveName()
	{
		ObjectComposition composition = services.getClient().getObjectDefinition(id);
		if (composition != null && composition.getImpostorIds() != null)
		{
			composition = composition.getImpostor();
		}
		if (composition == null)
		{
			return null;
		}
		String raw = composition.getName();
		return raw == null || raw.isEmpty() ? "" : COLOR_TAG_STRIP.matcher(raw).replaceAll("");
	}

	/** Resolve transformations on the client thread; callers must not retain the live definition. */
	public ObjectComposition getObjectComposition()
	{
		return services.read(() ->
		{
			ObjectComposition c = services.getClient().getObjectDefinition(id);
			return c != null && c.getImpostorIds() != null ? c.getImpostor() : c;
		}).orElse(null);
	}

	@Override
	public boolean click(String action)
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.objectAt(id, worldLocation, action);
	}

	@Override
	public boolean click()
	{
		String action = services.read(() ->
		{
			ObjectComposition c = getObjectComposition();
			if (c == null || c.getActions() == null) { return null; }
			for (String a : c.getActions()) { if (a != null && !a.isEmpty()) { return a; } }
			return null;
		}).orElse(null);
		return action != null && click(action);
	}

	@Override
	public WorldPoint getWorldLocation()
	{
		return worldLocation;
	}

	@Override
	public LocalPoint getLocalLocation()
	{
		return localLocation;
	}

	@Override
	public WorldView getWorldView()
	{
		return worldView;
	}

	/**
	 * The object's clickable outline on the canvas, read now on the client thread; {@code null}
	 * when it is off screen or not rendered.
	 */
	public java.awt.Shape getClickbox()
	{
		return services.read(tileObject::getClickbox).orElse(null);
	}

	/** As upstream: the object's scene hash, captured with the snapshot. */
	public long getHash()
	{
		return hash;
	}

	/**
	 * As upstream: the object's point on the minimap, read now on the client thread; {@code null}
	 * when it is off the minimap or the client thread cannot be reached.
	 */
	public Point getMinimapLocation()
	{
		return services.read(tileObject::getMinimapLocation).orElse(null);
	}

	/**
	 * As upstream: the outline of the object's tile on the canvas, read now on the client thread;
	 * {@code null} when it is off screen or the client thread cannot be reached.
	 */
	public java.awt.Polygon getCanvasTilePoly()
	{
		return services.read(tileObject::getCanvasTilePoly).orElse(null);
	}
}
