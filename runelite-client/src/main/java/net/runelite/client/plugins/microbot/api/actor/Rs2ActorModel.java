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

/**
 * Immutable client-thread snapshot of an {@link Actor}'s read state.
 * <p>
 * Adaptation: upstream wraps the live actor, implements {@link Actor} and hops
 * to the client thread on each getter. Here name, locations and world view are
 * captured once on the client thread when the cache snapshot is built, so a
 * background script reads consistent values without per-call hops. The live
 * actor stays reachable through {@link #getActor()} for client-thread use only;
 * the model does not implement {@link Actor}.
 */
public class Rs2ActorModel
{
	private final Actor actor;
	private final String name;
	private final WorldPoint worldLocation;
	private final LocalPoint localLocation;
	private final WorldView worldView;

	/** Captures {@code actor}; must be called on the client thread. */
	protected Rs2ActorModel(Client client, Actor actor)
	{
		this.actor = actor;
		this.name = actor.getName();
		this.worldView = actor.getWorldView();
		this.worldLocation = mainWorldLocation(client, actor);
		this.localLocation = actor.getLocalLocation();
	}

	/** The live actor. Read it only on the client thread. */
	public Actor getActor()
	{
		return actor;
	}

	public String getName()
	{
		return name;
	}

	/** World location at capture, projected to the main world for sub-world-view actors (upstream rule). */
	public WorldPoint getWorldLocation()
	{
		return worldLocation;
	}

	public LocalPoint getLocalLocation()
	{
		return localLocation;
	}

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
		return services.getClientThreadCalls().call(() ->
		{
			Player player = client.getLocalPlayer();
			return player == null ? null : mainWorldLocation(client, player);
		}).orElse(null);
	}

	/** Local player's world view as upstream {@code new Rs2PlayerModel().getWorldView()}; {@code null} when absent. */
	public static WorldView localPlayerWorldView(MicrobotReadServices services)
	{
		Client client = services.getClient();
		return services.getClientThreadCalls().call(() ->
		{
			Player player = client.getLocalPlayer();
			return player == null ? null : player.getWorldView();
		}).orElse(null);
	}
}
