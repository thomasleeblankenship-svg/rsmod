/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/player/Rs2Player.java
 * (lastAnimationTime, lastAnimationID, handleAnimationChanged, isAnimating,
 * isMoving, getAnimation, getPoseAnimation). BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.player;

import java.time.Duration;
import java.time.Instant;
import net.runelite.api.AnimationID;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.events.AnimationChanged;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;

/**
 * Local-player read helpers with upstream semantics.
 * <p>
 * {@link #isAnimating()} is true when the local player's current animation is
 * not {@link AnimationID#IDLE}, or when a non-idle {@link AnimationChanged} for
 * the local player arrived within the last 600 ms. {@link #isMoving()} is true
 * when the pose animation differs from the idle pose animation. Reads hop to
 * the client thread; with no client or local player they report idle.
 * <p>
 * Adaptation: only the read surface needed by the first upstream script is
 * ported (no waits, actions, run energy, combat, prayer, quests or varbits).
 * Animation history is cleared on logout/hop/connection loss.
 */
public final class Rs2Player
{
	/** Time of the last non-idle local-player {@link AnimationChanged}; written on the client thread. */
	public static volatile Instant lastAnimationTime = null;
	/** Id of that animation. */
	public static volatile int lastAnimationID = AnimationID.IDLE;

	private Rs2Player()
	{
	}

	public static int getLastAnimationID()
	{
		return lastAnimationID;
	}

	/** Event hook (client thread); routed by {@code MicrobotReadServices}. */
	public static void handleAnimationChanged(AnimationChanged event)
	{
		MicrobotReadServices services = MicrobotReadServices.current();
		if (services == null || !(event.getActor() instanceof Player))
		{
			return;
		}

		Player player = (Player) event.getActor();
		if (player != services.getClient().getLocalPlayer())
		{
			return;
		}

		if (player.getAnimation() != AnimationID.IDLE)
		{
			lastAnimationTime = Instant.now();
			lastAnimationID = player.getAnimation();
		}
	}

	/** Forgets animation history (logout, hop, connection loss, provider uninstall). */
	public static void clear()
	{
		lastAnimationTime = null;
		lastAnimationID = AnimationID.IDLE;
	}

	/**
	 * Checks if the player has been animating within the past specified milliseconds.
	 *
	 * @param ms The time window (in milliseconds) to check for recent animations.
	 * @return {@code true} if the player animated within the last {@code ms} milliseconds,
	 *         or if the player's current animation is not {@code AnimationID.IDLE}.
	 */
	public static boolean isAnimating(int ms)
	{
		Instant last = lastAnimationTime;
		return (last != null && Duration.between(last, Instant.now()).toMillis() < ms)
			|| getAnimation() != AnimationID.IDLE;
	}

	/** {@link #isAnimating(int)} with upstream's 600 ms window. */
	public static boolean isAnimating()
	{
		return isAnimating(600);
	}

	/**
	 * Checks if the player is currently moving based on their pose animation.
	 *
	 * @return {@code true} if the pose animation differs from the idle pose animation.
	 */
	public static boolean isMoving()
	{
		MicrobotReadServices services = MicrobotReadServices.current();
		if (services == null)
		{
			return false;
		}
		Client client = services.getClient();
		return services.read(() ->
		{
			Player localPlayer = client.getLocalPlayer();
			if (localPlayer == null)
			{
				return false;
			}
			return localPlayer.getPoseAnimation() != localPlayer.getIdlePoseAnimation();
		}).orElse(false);
	}

	/** Current local-player animation id, or {@code -1} without a client or player. */
	public static int getAnimation()
	{
		MicrobotReadServices services = MicrobotReadServices.current();
		if (services == null)
		{
			return -1;
		}
		Client client = services.getClient();
		return services.read(() ->
		{
			Player localPlayer = client.getLocalPlayer();
			return localPlayer == null ? -1 : localPlayer.getAnimation();
		}).orElse(-1);
	}

	/** Current local-player pose animation id, or {@code -1} without a client or player. */
	public static int getPoseAnimation()
	{
		MicrobotReadServices services = MicrobotReadServices.current();
		if (services == null)
		{
			return -1;
		}
		Client client = services.getClient();
		return services.read(() ->
		{
			Player localPlayer = client.getLocalPlayer();
			return localPlayer == null ? -1 : localPlayer.getPoseAnimation();
		}).orElse(-1);
	}
}
