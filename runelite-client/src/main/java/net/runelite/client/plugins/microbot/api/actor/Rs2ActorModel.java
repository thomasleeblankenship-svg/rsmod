/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/actor/Rs2ActorModel.java
 * (getName, getWorldLocation, getLocalLocation, getWorldView,
 * projectActorLocationToMainWorld). BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.actor;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Projection;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.util.ActorModel;

/**
 * A cached actor that is also an {@link Actor}, as upstream.
 * <p>
 * Name, world and local location and world view are captured once on the client thread when the
 * cache snapshot is built, so a script reads consistent values without per-call hops. Every other
 * {@link Actor} method is inherited from {@link ActorModel} and reads the live actor on the client
 * thread (upstream hops per getter the same way); subclasses capture more fields where a query
 * filters on them. The live actor is {@link #getActor()}; read it on the client thread only.
 */
public class Rs2ActorModel extends ActorModel
{
	private final String name;
	private final WorldPoint worldLocation;
	private final LocalPoint localLocation;
	private final WorldView worldView;

	/** Captures {@code actor}; must be called on the client thread. */
	protected Rs2ActorModel(Client client, Actor actor)
	{
		super(actor);
		this.name = actor.getName();
		this.worldView = actor.getWorldView();
		this.worldLocation = mainWorldLocation(client, actor);
		this.localLocation = actor.getLocalLocation();
	}

	@Override
	public String getName()
	{
		return name;
	}

	/** World location at capture, projected to the main world for sub-world-view actors (upstream rule). */
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
	 * Upstream {@code Rs2ActorModel.getWorldLocation}: actors on a non-top-level
	 * world view are projected onto the main world (plane 0, as upstream).
	 * Call on the client thread.
	 */
	public static WorldPoint mainWorldLocation(Client client, Actor actor)
	{
		WorldView wv = actor.getWorldView();
		WorldPoint actorLocation = actor.getWorldLocation();
		if (wv == null || wv.isTopLevel() || actorLocation == null)
		{
			return actorLocation;
		}

		LocalPoint localPoint = LocalPoint.fromWorld(wv, actorLocation);
		if (localPoint == null)
		{
			return actorLocation;
		}

		Projection mainWorldProjection = wv.getMainWorldProjection();
		if (mainWorldProjection == null)
		{
			return actorLocation;
		}

		float[] projection = mainWorldProjection.project(localPoint.getX(), 0, localPoint.getY());
		return WorldPoint.fromLocal(client.getTopLevelWorldView(), (int) projection[0], (int) projection[2], 0);
	}

	/** Local player's location as upstream {@code new Rs2PlayerModel().getWorldLocation()}; {@code null} when absent. */
	public static WorldPoint localPlayerLocation(MicrobotReadServices services)
	{
		Client client = services.getClient();
		return services.read(() ->
		{
			Player player = client.getLocalPlayer();
			return player == null ? null : mainWorldLocation(client, player);
		}).orElse(null);
	}

	/** Local player's world view as upstream {@code new Rs2PlayerModel().getWorldView()}; {@code null} when absent. */
	public static WorldView localPlayerWorldView(MicrobotReadServices services)
	{
		Client client = services.getClient();
		return services.read(() ->
		{
			Player player = client.getLocalPlayer();
			return player == null ? null : player.getWorldView();
		}).orElse(null);
	}
}
