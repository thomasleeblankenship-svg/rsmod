/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/player/Rs2Player.java
 * (lastAnimationTime, lastAnimationID, handleAnimationChanged, isAnimating,
 * isMoving, getAnimation, getPoseAnimation), and from a2114401bbbc35e9762db9e8aa666d70ffcd24d8
 * (2.6.38: isInCombat/updateCombatTime, getRunEnergy, getHealthPercentage, getBoostedSkillLevel,
 * hasStaminaBuffActive, isRunEnabled, getWorld, getPlayers, getLocalPlayer, getInteracting).
 * BSD 2-Clause License; see LICENSE.
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
 * Adaptation: a read subset of upstream, plus the mobile additions below and the
 * Hub reads at the end; prayer, potion timers other than stamina, and PvP helpers
 * are not ported. Animation, stamina and combat state are cleared on
 * logout/hop/connection loss.
 */
public final class Rs2Player
{
	/** Time of the last non-idle local-player {@link AnimationChanged}; written on the client thread. */
	public static volatile Instant lastAnimationTime = null;
	/** Id of that animation. */
	public static volatile int lastAnimationID = AnimationID.IDLE;
	/** Stamina effect varbit value; positive while the buff is active. Written on the client thread. */
	public static volatile int staminaBuffTime = -1;

	private static final long COMBAT_TIMEOUT_MS = 10000;
	private static volatile long lastCombatTime = 0;

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
		staminaBuffTime = -1;
		lastCombatTime = 0;
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
		return waitForXpDrop(skill, timeoutMillis, false);
	}

	/** As upstream: waits up to 5 seconds. */
	public static boolean waitForXpDrop(net.runelite.api.Skill skill)
	{
		return waitForXpDrop(skill, 5000, false);
	}

	public static boolean waitForXpDrop(net.runelite.api.Skill skill, boolean inventoryFullCheck)
	{
		return waitForXpDrop(skill, 5000, inventoryFullCheck);
	}

	/** As upstream: also stops waiting, returning {@code true}, once the inventory is full when {@code inventoryFullCheck}. */
	public static boolean waitForXpDrop(net.runelite.api.Skill skill, int timeoutMillis, boolean inventoryFullCheck)
	{
		int before = net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
			net.runelite.client.plugins.microbot.Microbot.getClient().getSkillExperience(skill), -1);
		return before >= 0 && waitFor(() -> net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
			net.runelite.client.plugins.microbot.Microbot.getClient().getSkillExperience(skill), -1) > before
			|| (inventoryFullCheck && net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.isFull()),
			timeoutMillis);
	}

	/**
	 * As upstream: turns run on ({@code true}) or off; {@code true} once run is in that state.
	 * Mobile: taps the minimap run orb through {@code MobileActions} and waits up to 2 seconds for the
	 * run varp to change. Turning run on with no run energy is not attempted.
	 */
	public static boolean toggleRunEnergy(boolean toggle)
	{
		if (isRunEnabled() == toggle) { return true; }
		if (toggle && net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
			net.runelite.client.plugins.microbot.Microbot.getClient().getEnergy(), 0) < 100) { return false; }
		int orb = net.runelite.api.gameval.InterfaceID.Orbs.RUNBUTTON;
		return net.runelite.client.plugins.microbot.util.Global.sleepUntil(() ->
				net.runelite.client.plugins.microbot.mobile.MobileActions.widget(orb, -1, ""), 2_000)
			&& net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> isRunEnabled() == toggle, 2_000);
	}

	/** As upstream: eats when health is at or below {@code percentage}; true when a food action was dispatched. */
	public static boolean eatAt(int percentage)
	{
		return getHealthPercentage() <= percentage && useFood();
	}

	/**
	 * As upstream: eats the first unnoted inventory food, preferring blighted food in the
	 * Wilderness; jugs of wine are drunk. {@code true} means dispatched.
	 */
	public static boolean useFood()
	{
		java.util.List<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> foods =
			net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.getInventoryFood();
		boolean wilderness = net.runelite.client.plugins.microbot.Microbot.getVarbitValue(net.runelite.api.Varbits.IN_WILDERNESS) == 1;
		net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel blighted = null;
		net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel regular = null;
		for (net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel food : foods)
		{
			if (food.isNoted() || food.getName() == null) { continue; }
			boolean isBlighted = food.getName().toLowerCase(java.util.Locale.ROOT).contains("blighted");
			if (isBlighted && blighted == null) { blighted = food; }
			if (!isBlighted && regular == null) { regular = food; }
		}
		net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel chosen = wilderness && blighted != null ? blighted : regular;
		if (chosen == null) { return false; }
		String action = chosen.getName().toLowerCase(java.util.Locale.ROOT).contains("jug of wine") ? "Drink" : "Eat";
		return net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.interact(chosen, action);
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

	// ---- Hub read APIs (upstream 2.6.38) ----

	/**
	 * Event hook (client thread); routed by {@code MicrobotReadServices}. Adaptation: of upstream's
	 * potion timers only the stamina effect is tracked, for {@link #hasStaminaBuffActive()}.
	 */
	public static void handlePotionTimers(net.runelite.api.events.VarbitChanged event)
	{
		if (event.getVarbitId() == net.runelite.api.Varbits.STAMINA_EFFECT)
		{
			staminaBuffTime = event.getValue();
		}
	}

	public static boolean hasStaminaBuffActive()
	{
		return staminaBuffTime > 0;
	}

	/**
	 * Event hook (client thread); routed by {@code MicrobotReadServices}. Same cases as upstream
	 * {@code MicrobotPlugin.onHitsplatApplied}: a hitsplat on the local player that is not someone
	 * else's, or one of the local player's own hitsplats on another player or an NPC.
	 */
	public static void handleHitsplatApplied(net.runelite.api.events.HitsplatApplied event)
	{
		MicrobotReadServices services = MicrobotReadServices.current();
		if (services == null || event.getActor() == null)
		{
			return;
		}
		net.runelite.api.Actor actor = event.getActor();
		if (actor.equals(services.getClient().getLocalPlayer()))
		{
			if (!event.getHitsplat().isOthers())
			{
				updateCombatTime();
			}
		}
		else if ((actor instanceof Player || actor instanceof net.runelite.api.NPC) && event.getHitsplat().isMine())
		{
			updateCombatTime();
		}
	}

	/** Marks the local player as in combat now, if there is one. */
	public static void updateCombatTime()
	{
		MicrobotReadServices services = MicrobotReadServices.current();
		if (services == null)
		{
			return;
		}
		Client client = services.getClient();
		services.read(() ->
		{
			if (client.getLocalPlayer() != null)
			{
				lastCombatTime = System.currentTimeMillis();
			}
			return null;
		});
	}

	/** True within 10 s of the last combat hitsplat involving the local player. */
	public static boolean isInCombat()
	{
		return System.currentTimeMillis() - lastCombatTime < COMBAT_TIMEOUT_MS;
	}

	/** Run energy as a whole percentage (0-100); 0 without a client. */
	public static int getRunEnergy()
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
			net.runelite.client.plugins.microbot.Microbot.getClient().getEnergy(), 0) / 100;
	}

	public static int getBoostedSkillLevel(net.runelite.api.Skill skill)
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
			net.runelite.client.plugins.microbot.Microbot.getClient().getBoostedSkillLevel(skill), 0);
	}

	/**
	 * Boosted over real hitpoints, as a percentage (150.0 boosted, 80.0 drained). Adaptation: 0.0
	 * when the real level cannot be read, where upstream would divide by zero.
	 */
	public static double getHealthPercentage()
	{
		int real = getRealSkillLevel(net.runelite.api.Skill.HITPOINTS);
		return real <= 0 ? 0.0 : (double) (getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS) * 100) / real;
	}

	/** Upstream: varp 173 equals 1. */
	public static boolean isRunEnabled()
	{
		return net.runelite.client.plugins.microbot.Microbot.getVarbitPlayerValue(173) == 1;
	}

	/** Current world; read on the client thread (upstream reads it directly). 0 without a client. */
	public static int getWorld()
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
			net.runelite.client.plugins.microbot.Microbot.getClient().getWorld(), 0);
	}

	/**
	 * Other players matching {@code predicate}, as {@link Rs2PlayerModel} snapshots captured now on
	 * the client thread. Deprecated upstream in favour of {@code Rs2PlayerCache}; adaptation: every
	 * loaded world view is scanned, not only the top-level one.
	 */
	@Deprecated
	public static java.util.stream.Stream<Rs2PlayerModel> getPlayers(java.util.function.Predicate<Rs2PlayerModel> predicate)
	{
		return getPlayers(predicate, false);
	}

	/** {@link #getPlayers(java.util.function.Predicate)}, optionally including the local player. */
	@Deprecated
	public static java.util.stream.Stream<Rs2PlayerModel> getPlayers(java.util.function.Predicate<Rs2PlayerModel> predicate,
		boolean includeLocalPlayer)
	{
		MicrobotReadServices services = MicrobotReadServices.current();
		if (services == null)
		{
			return java.util.stream.Stream.empty();
		}
		Client client = services.getClient();
		java.util.List<Rs2PlayerModel> players = services.read(() ->
		{
			java.util.List<Rs2PlayerModel> result = new java.util.ArrayList<>();
			Player local = client.getLocalPlayer();
			for (int id : services.getWorldViewIds())
			{
				net.runelite.api.WorldView view = client.getWorldView(id);
				if (view == null)
				{
					continue;
				}
				for (Player player : view.players())
				{
					if (player != null && (includeLocalPlayer || player != local))
					{
						result.add(new Rs2PlayerModel(client, player));
					}
				}
			}
			return result;
		}).orElse(java.util.Collections.emptyList());
		return players.stream().filter(predicate);
	}

	/** Whether boosted hitpoints are at least the real hitpoints level. */
	public static boolean isFullHealth()
	{
		return getBoostedSkillLevel(net.runelite.api.Skill.HITPOINTS) >= getRealSkillLevel(net.runelite.api.Skill.HITPOINTS);
	}

	/**
	 * Tiles from the player to {@code endpoint} ({@link net.runelite.api.coords.WorldPoint#distanceTo}:
	 * Chebyshev, {@code Integer.MAX_VALUE} on another floor or without a player). Adaptation:
	 * upstream measures the walking path; RuneMobile uses the straight distance, which upstream
	 * itself falls back to in instances.
	 */
	public static int distanceTo(net.runelite.api.coords.WorldPoint endpoint)
	{
		net.runelite.api.coords.WorldPoint location = getWorldLocation();
		return location == null || endpoint == null ? Integer.MAX_VALUE : location.distanceTo(endpoint);
	}

	/** The local player as a snapshot model, or {@code null} without one. */
	public static Rs2PlayerModel getLocalPlayer()
	{
		MicrobotReadServices services = MicrobotReadServices.current();
		if (services == null)
		{
			return null;
		}
		Client client = services.getClient();
		return services.read(() ->
		{
			Player player = client.getLocalPlayer();
			return player == null ? null : new Rs2PlayerModel(client, player);
		}).orElse(null);
	}

	/**
	 * The actor the local player is interacting with, or {@code null}. Adaptation: upstream wraps an
	 * NPC in {@code Rs2NpcModel}, which is an {@code Actor} there; the mobile model is a snapshot
	 * that is not, so the live actor is returned. Read it on the client thread.
	 */
	public static net.runelite.api.Actor getInteracting()
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
		{
			Player player = net.runelite.client.plugins.microbot.Microbot.getClient().getLocalPlayer();
			return player == null ? null : player.getInteracting();
		}, null);
	}
}
