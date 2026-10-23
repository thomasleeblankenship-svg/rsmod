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
		playerDetectionTimes.clear();
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

	/** Mobile additions: live engine reads are confined to the client thread. */
	public static net.runelite.api.coords.WorldPoint getWorldLocation()
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
		{
			Player player = net.runelite.client.plugins.microbot.Microbot.getClient().getLocalPlayer();
			return player == null ? null : player.getWorldLocation();
		}, null);
	}

	public static int getRealSkillLevel(net.runelite.api.Skill skill)
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
			net.runelite.client.plugins.microbot.Microbot.getClient().getRealSkillLevel(skill), 0);
	}

	public static boolean getSkillRequirement(net.runelite.api.Skill skill, int level)
	{
		return getRealSkillLevel(skill) >= level;
	}

	public static net.runelite.api.QuestState getQuestState(net.runelite.api.Quest quest)
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
			quest.getState(net.runelite.client.plugins.microbot.Microbot.getClient()),
			net.runelite.api.QuestState.NOT_STARTED);
	}

	public static boolean isInteracting()
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
		{
			Player player = net.runelite.client.plugins.microbot.Microbot.getClient().getLocalPlayer();
			return player != null && player.getInteracting() != null;
		}, false);
	}

	public static void waitForAnimation()
	{
		waitForAnimation(5000);
	}

	public static void waitForAnimation(int timeoutMillis)
	{
		if (waitFor(Rs2Player::isAnimating, timeoutMillis))
		{
			waitFor(() -> !isAnimating(), 5000);
		}
	}

	public static boolean waitForXpDrop(net.runelite.api.Skill skill, int timeoutMillis)
	{
		int before = net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
			net.runelite.client.plugins.microbot.Microbot.getClient().getSkillExperience(skill), -1);
		return before >= 0 && waitFor(() -> net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
			net.runelite.client.plugins.microbot.Microbot.getClient().getSkillExperience(skill), -1) > before,
			timeoutMillis);
	}

	public static boolean isStandingOnGameObject()
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
		{
			Client client = net.runelite.client.plugins.microbot.Microbot.getClient();
			Player player = client.getLocalPlayer();
			if (player == null || player.getLocalLocation() == null || player.getWorldView() == null)
			{
				return false;
			}
			net.runelite.api.Tile[][][] tiles = player.getWorldView().getScene().getTiles();
			int x = player.getLocalLocation().getSceneX();
			int y = player.getLocalLocation().getSceneY();
			int plane = player.getWorldLocation().getPlane();
			if (plane < 0 || plane >= tiles.length || x < 0 || x >= tiles[plane].length
				|| y < 0 || y >= tiles[plane][x].length)
			{
				return false;
			}
			net.runelite.api.Tile tile = tiles[plane][x][y];
			if (tile == null)
			{
				return false;
			}
			if (tile.getGameObjects() != null)
			{
				for (net.runelite.api.GameObject object : tile.getGameObjects())
				{
					if (object != null) { return true; }
				}
			}
			return tile.getGroundItems() != null && !tile.getGroundItems().isEmpty();
		}, true);
	}

	private static final java.util.Map<Integer, Long> playerDetectionTimes = new java.util.concurrent.ConcurrentHashMap<>();

	/** The uploaded script logs out here; a separate desktop auto-login plugin supplies world hopping. */
	/**
	 * As upstream: logs out when logged in, through the logout button's exact menu action. The
	 * mobile plugin then stops the script on the resulting game-state change, and does not
	 * auto-login after a requested logout.
	 */
	public static void logout()
	{
		if (!net.runelite.client.plugins.microbot.Microbot.isLoggedIn())
		{
			return;
		}
		net.runelite.client.plugins.microbot.mobile.MobileActions.widget(
			net.runelite.api.widgets.WidgetInfo.LOGOUT_BUTTON.getId(), -1, "Logout");
	}

	/** As upstream: waits up to 5 s for movement to start, then for it to stop. */
	public static void waitForWalking()
	{
		if (!net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue(Rs2Player::isMoving, 100, 5000)) return;
		net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> !Rs2Player.isMoving());
	}

	/** As upstream: waits up to {@code time} ms for movement to start, then up to {@code time} ms to stop. */
	public static void waitForWalking(int time)
	{
		if (!net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue(Rs2Player::isMoving, 100, time)) return;
		net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> !Rs2Player.isMoving(), time);
	}

	public static boolean logoutIfPlayerDetected(int amountOfPlayers, int time)
	{
		return logoutIfPlayerDetected(amountOfPlayers, time, 0);
	}

	public static boolean logoutIfPlayerDetected(int amountOfPlayers, int time, int distance)
	{
		java.util.Set<Integer> detected = net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
		{
			Client client = net.runelite.client.plugins.microbot.Microbot.getClient();
			Player local = client.getLocalPlayer();
			java.util.Set<Integer> ids = new java.util.HashSet<>();
			if (local == null) { return ids; }
			for (Player other : client.getPlayers())
			{
				if (other != null && other != local && (distance <= 0
					|| local.getWorldLocation().distanceTo(other.getWorldLocation()) <= distance))
				{
					ids.add(other.getId());
				}
			}
			return ids;
		}, java.util.Collections.emptySet());
		playerDetectionTimes.keySet().retainAll(detected);
		long now = System.nanoTime();
		if (detected.size() < Math.max(1, amountOfPlayers)) { return false; }
		boolean due = false;
		for (int id : detected)
		{
			long first = playerDetectionTimes.computeIfAbsent(id, ignored -> now);
			if (now - first >= java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(Math.max(0, time)))
			{
				due = true;
			}
		}
		if (!due) { return false; }
		boolean accepted = net.runelite.client.plugins.microbot.mobile.MobileActions.widget(
			net.runelite.api.widgets.WidgetInfo.LOGOUT_BUTTON.getId(), -1, "Logout");
		if (accepted) { playerDetectionTimes.clear(); }
		return accepted;
	}

	/** Bounded, cancellation-aware wait; never sleeps on the client thread. */
	public static boolean waitFor(java.util.function.BooleanSupplier condition, int timeoutMillis)
	{
		Client client = net.runelite.client.plugins.microbot.Microbot.getClient();
		if (client == null || client.isClientThread()) { return false; }
		long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMillis));
		while (net.runelite.client.plugins.microbot.mobile.MobileActions.active() && !Thread.currentThread().isInterrupted())
		{
			if (condition.getAsBoolean()) { return true; }
			if (System.nanoTime() >= deadline) { return false; }
			try { Thread.sleep(50); }
			catch (InterruptedException exception) { Thread.currentThread().interrupt(); return false; }
		}
		return false;
	}


	public static boolean getSkillRequirement(net.runelite.api.Skill skill, int level, boolean boosted)
	{
		int actual = net.runelite.client.plugins.microbot.mobile.MobileActions.read(() -> boosted
			? net.runelite.client.plugins.microbot.Microbot.getClient().getBoostedSkillLevel(skill)
			: net.runelite.client.plugins.microbot.Microbot.getClient().getRealSkillLevel(skill), 0);
		return actual >= level;
	}

	public static boolean isMember()
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
			net.runelite.client.plugins.microbot.Microbot.getClient().getVarpValue(net.runelite.api.VarPlayer.MEMBERSHIP_DAYS) > 0, false);
	}

	public static boolean isInMemberWorld()
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
			net.runelite.client.plugins.microbot.Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.MEMBERS), false);
	}

}
