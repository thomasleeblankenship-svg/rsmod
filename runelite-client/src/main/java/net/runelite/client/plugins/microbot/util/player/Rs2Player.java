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
 * Hub reads at the end (PvP helpers are in {@link Rs2Pvp}). Animation, potion-timer
 * and combat state are cleared on logout/hop/connection loss.
 */
public final class Rs2Player
{
	/** Time of the last non-idle local-player {@link AnimationChanged}; written on the client thread. */
	public static volatile Instant lastAnimationTime = null;
	/** Id of that animation. */
	public static volatile int lastAnimationID = AnimationID.IDLE;
	/** Stamina effect varbit value; positive while the buff is active. Written on the client thread. */
	public static volatile int staminaBuffTime = -1;
	/** Upstream's potion timers: the effect varbit's value, positive while active. Written on the client thread. */
	public static volatile int antiFireTime = -1;
	public static volatile int superAntiFireTime = -1;
	public static volatile int divineRangedTime = -1;
	public static volatile int divineBastionTime = -1;
	public static volatile int divineCombatTime = -1;
	public static volatile int divineMagicTime = -1;
	public static volatile int goadingTime = -1;
	public static volatile int moonlightTime = -1;
	/** Upstream's poison-varp readings: the varp when it is at or above the venom cutoff, else 0 / -1. */
	public static volatile int antiVenomTime = -1;
	public static volatile int antiPoisonTime = -1;
	/** As upstream: poison-varp values below this mean venom immunity (anti-venom). */
	static final int VENOM_VALUE_CUTOFF = -38;

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
		antiFireTime = -1;
		superAntiFireTime = -1;
		divineRangedTime = -1;
		divineBastionTime = -1;
		divineCombatTime = -1;
		divineMagicTime = -1;
		goadingTime = -1;
		moonlightTime = -1;
		antiVenomTime = -1;
		antiPoisonTime = -1;
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
	/** As upstream: the player's world location as an {@code Rs2WorldPoint} (its point may be {@code null}). */
	public static net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint getRs2WorldPoint()
	{
		return new net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint(getWorldLocation());
	}

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

	/** As upstream: at or below {@code percentage}, fast food (karambwan and the like) first when {@code fastFood}. */
	public static boolean eatAt(int percentage, boolean fastFood)
	{
		if (getHealthPercentage() > percentage)
		{
			return false;
		}
		if (fastFood && !net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.getInventoryFastFood().isEmpty())
		{
			return useFastFood();
		}
		return useFood();
	}

	/** As upstream: eats the first fast food (priority 1 in {@code Rs2Food}); false when there is none. */
	public static boolean useFastFood()
	{
		java.util.List<net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel> foods =
			net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.getInventoryFastFood();
		return !foods.isEmpty() && net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.interact(foods.get(0), "Eat");
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

	/**
	 * As upstream: at or below {@code prayerPoints}, drinks a prayer regeneration potion when its
	 * effect is not active, else a blighted super restore in the Wilderness or on a PvP world, else
	 * any prayer potion variant. False when above the threshold or nothing could be drunk.
	 */
	public static boolean drinkPrayerPotionAt(int prayerPoints)
	{
		if (getBoostedSkillLevel(net.runelite.api.Skill.PRAYER) > prayerPoints) return false;
		boolean wilderness = net.runelite.client.plugins.microbot.Microbot.getVarbitValue(net.runelite.api.Varbits.IN_WILDERNESS) == 1;
		boolean pvp = net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
			net.runelite.client.plugins.microbot.Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.PVP), false);
		if (!hasPrayerRegenerationActive()
			&& drinkPotion(net.runelite.client.plugins.microbot.util.misc.Rs2Potion.getPrayerRegenerationPotion())) return true;
		if ((wilderness || pvp) && potion("blighted super restore") != null) return drinkPotion("blighted super restore");
		return drinkPotion(net.runelite.client.plugins.microbot.util.misc.Rs2Potion.getPrayerPotionsVariants().toArray(new String[0]));
	}

	/**
	 * Drinks an anti-poison when neither anti-poison nor anti-venom protection is active; true when
	 * already protected or one was drunk. Mobile: upstream returns early when protection is
	 * <em>not</em> active (an inversion that never drinks when it is needed); this follows the
	 * method's documented intent instead.
	 */
	public static boolean drinkAntiPoisonPotion()
	{
		if (hasAntiPoisonActive() || hasAntiVenomActive()) return true;
		return drinkPotion(net.runelite.client.plugins.microbot.util.misc.Rs2Potion.getAntiPoisonVariants().toArray(new String[0]));
	}

	/**
	 * As upstream: drinks a prayer potion once prayer has dropped by more than one dose restores
	 * (the dose of the best potion carried: moonlight moth mix, moonlight, super restore or prayer
	 * potion) plus 5 points. Mobile: a fixed threshold; upstream randomises it.
	 */
	public static boolean drinkPrayerPotion()
	{
		int maxPrayer = getRealSkillLevel(net.runelite.api.Skill.PRAYER);
		int maxHerblore = getRealSkillLevel(net.runelite.api.Skill.HERBLORE);
		int restore;
		if (potion("moonlight moth mix") != null)
		{
			restore = 22;
		}
		else if (potion("moonlight potion") != null || potion("moonlight moth") != null)
		{
			restore = Math.max(maxPrayer / 4 + 7, (int) Math.floor(maxHerblore * 3.0 / 10.0) + 7);
		}
		else if (potion("super restore") != null || potion("blighted super restore") != null)
		{
			restore = maxPrayer / 4 + 8;
		}
		else
		{
			restore = maxPrayer / 4 + 7;
		}
		return drinkPrayerPotionAt(maxPrayer - restore - 5);
	}

	/** As upstream: an antifire potion unless antifire protection is already active (then true). */
	public static boolean drinkAntiFirePotion()
	{
		return hasAntiFireActive()
			|| drinkPotion(net.runelite.client.plugins.microbot.util.misc.Rs2Potion.getAntifirePotionsVariants().toArray(new String[0]));
	}

	/** As upstream: a goading potion unless its effect is active (then false). */
	public static boolean drinkGoadingPotion()
	{
		return !hasGoadingActive() && drinkPotion(net.runelite.client.plugins.microbot.util.misc.Rs2Potion.getGoadingPotion());
	}

	/**
	 * Upstream hops worlds when another player is nearby. Hopping to avoid other players is not
	 * ported (project policy), so this never hops and always returns false; a script that calls it
	 * simply carries on where it is.
	 */
	public static boolean hopIfPlayerDetected(int quantity, int time, int distance)
	{
		return false;
	}

	/** As upstream: {@link #drinkCombatPotionAt(net.runelite.api.Skill, boolean)} preferring super combat potions. */
	public static boolean drinkCombatPotionAt(net.runelite.api.Skill skill)
	{
		return drinkCombatPotionAt(skill, true);
	}

	/**
	 * As upstream: drinks a potion for {@code skill} unless it is already boosted by more than 20% of
	 * the maximum boost (real level * 0.15 + 5). For Attack, Strength or Defence with
	 * {@code superCombat}, a super combat (or, except for Defence, combat) potion first; then the
	 * skill's own potions. False when nothing was drunk.
	 */
	public static boolean drinkCombatPotionAt(net.runelite.api.Skill skill, boolean superCombat)
	{
		if (skill == null)
		{
			return false;
		}
		int real = getRealSkillLevel(skill);
		int boosted = getBoostedSkillLevel(skill);
		double threshold = (real * 0.15 + 5) * 0.20;
		if (boosted - real > threshold)
		{
			return false;
		}
		boolean melee = skill == net.runelite.api.Skill.ATTACK || skill == net.runelite.api.Skill.STRENGTH
			|| skill == net.runelite.api.Skill.DEFENCE;
		if (superCombat && melee)
		{
			java.util.List<String> combat = new java.util.ArrayList<>(net.runelite.client.plugins.microbot.util.misc.Rs2Potion.getCombatPotionsVariants());
			if (skill == net.runelite.api.Skill.DEFENCE)
			{
				combat.remove("combat potion");
			}
			if (drinkPotion(combat.toArray(new String[0])))
			{
				return true;
			}
		}
		java.util.List<String> own;
		switch (skill)
		{
			case ATTACK:
				own = net.runelite.client.plugins.microbot.util.misc.Rs2Potion.getAttackPotionsVariants();
				break;
			case STRENGTH:
				own = net.runelite.client.plugins.microbot.util.misc.Rs2Potion.getStrengthPotionsVariants();
				break;
			case DEFENCE:
				own = net.runelite.client.plugins.microbot.util.misc.Rs2Potion.getDefencePotionsVariants();
				break;
			case RANGED:
				own = net.runelite.client.plugins.microbot.util.misc.Rs2Potion.getRangePotionsVariants();
				break;
			case MAGIC:
				own = net.runelite.client.plugins.microbot.util.misc.Rs2Potion.getMagicPotionsVariants();
				break;
			default:
				return false;
		}
		return drinkPotion(own.toArray(new String[0]));
	}

	/** The first unnoted potion whose name, without its dose suffix, is one of {@code names} (case-insensitive). */
	private static net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel potion(String... names)
	{
		return net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.items(item ->
		{
			if (item.isNoted() || item.getName() == null) return false;
			String base = item.getName().replaceAll("\\(\\d+\\)$", "").trim();
			return java.util.Arrays.stream(names).anyMatch(name -> name != null && name.equalsIgnoreCase(base));
		}).findFirst().orElse(null);
	}

	private static boolean drinkPotion(String... names)
	{
		net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel potion = potion(names);
		return potion != null && net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.interact(potion, "Drink");
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

	/**
	 * As upstream: teleblocked. Upstream counts the {@code TELEBLOCK} varbit only above 100 (its
	 * event handler drops lower values); read from the varbit cache.
	 */
	public static boolean isTeleBlocked()
	{
		return net.runelite.client.plugins.microbot.Microbot.getVarbitValue(net.runelite.api.gameval.VarbitID.TELEBLOCK_CYCLES) > 100;
	}

	/**
	 * As upstream: whether the client is within {@code randomDelay} ticks of its idle logout, i.e.
	 * {@code min(mouse idle, keyboard idle) >= idle timeout - randomDelay}; read on the client
	 * thread, {@code false} when it cannot be read.
	 */
	public static boolean checkIdleLogout(long randomDelay)
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
		{
			Client client = net.runelite.client.plugins.microbot.Microbot.getClient();
			long idleTicks = Math.min(client.getMouseIdleTicks(), client.getKeyboardIdleTicks());
			return idleTicks >= client.getIdleTimeout() - randomDelay;
		}, false);
	}

	/** As upstream: the first other player whose name contains {@code playerName}, ignoring case. */
	@Deprecated
	public static Rs2PlayerModel getPlayer(String playerName)
	{
		return getPlayer(playerName, false);
	}

	/** As upstream: the first other player whose name equals ({@code exact}) or contains {@code playerName}. */
	@Deprecated
	public static Rs2PlayerModel getPlayer(String playerName, boolean exact)
	{
		if (playerName == null)
		{
			return null;
		}
		String lower = playerName.toLowerCase();
		return getPlayers(player ->
		{
			String name = player.getName();
			return name != null && (exact ? name.equalsIgnoreCase(playerName) : name.toLowerCase().contains(lower));
		}).findFirst().orElse(null);
	}

	/**
	 * As upstream: the item name worn in each equipment kit slot of {@code rs2Player}, read on the
	 * client thread. Adaptation: an empty slot (equipment id -1) has no entry, so {@code get}
	 * returns {@code null} for it.
	 */
	public static java.util.Map<net.runelite.api.kit.KitType, String> getPlayerEquipmentNames(Rs2PlayerModel rs2Player)
	{
		java.util.Map<net.runelite.api.kit.KitType, String> names = new java.util.EnumMap<>(net.runelite.api.kit.KitType.class);
		if (rs2Player == null)
		{
			return names;
		}
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
		{
			Player player = rs2Player.getPlayer();
			net.runelite.api.PlayerComposition composition = player == null ? null : player.getPlayerComposition();
			if (composition == null)
			{
				return names;
			}
			for (net.runelite.api.kit.KitType kit : net.runelite.api.kit.KitType.values())
			{
				int itemId = composition.getEquipmentId(kit);
				net.runelite.api.ItemComposition item = itemId < 0 ? null
					: net.runelite.client.plugins.microbot.Microbot.getClient().getItemDefinition(itemId);
				if (item != null && item.getName() != null)
				{
					names.put(kit, item.getName());
				}
			}
			return names;
		}, names);
	}

	/** As upstream: the item id worn in each kit slot of {@code rs2Player} (-1 for an empty slot); client thread. */
	public static java.util.Map<net.runelite.api.kit.KitType, Integer> getPlayerEquipmentIds(Rs2PlayerModel rs2Player)
	{
		java.util.Map<net.runelite.api.kit.KitType, Integer> ids = new java.util.EnumMap<>(net.runelite.api.kit.KitType.class);
		if (rs2Player == null)
		{
			return ids;
		}
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
		{
			Player player = rs2Player.getPlayer();
			net.runelite.api.PlayerComposition composition = player == null ? null : player.getPlayerComposition();
			for (net.runelite.api.kit.KitType kit : net.runelite.api.kit.KitType.values())
			{
				ids.put(kit, composition == null ? -1 : composition.getEquipmentId(kit));
			}
			return ids;
		}, ids);
	}

	/** As upstream: {@code rs2Player} wears {@code itemId}. */
	public static boolean hasPlayerEquippedItem(Rs2PlayerModel rs2Player, int itemId)
	{
		return getPlayerEquipmentIds(rs2Player).containsValue(itemId);
	}

	/** As upstream: {@code rs2Player} wears one of {@code itemIds}. */
	public static boolean hasPlayerEquippedItem(Rs2PlayerModel rs2Player, int[] itemIds)
	{
		java.util.Collection<Integer> worn = getPlayerEquipmentIds(rs2Player).values();
		return itemIds != null && java.util.Arrays.stream(itemIds).anyMatch(worn::contains);
	}

	/** As upstream: {@code rs2Player} wears an item named {@code itemName}, ignoring case. */
	public static boolean hasPlayerEquippedItem(Rs2PlayerModel rs2Player, String itemName)
	{
		return getPlayerEquipmentNames(rs2Player).values().stream().anyMatch(name -> name.equalsIgnoreCase(itemName));
	}

	/** As upstream: {@code rs2Player} wears an item with one of {@code itemNames}, ignoring case. */
	public static boolean hasPlayerEquippedItem(Rs2PlayerModel rs2Player, java.util.List<String> itemNames)
	{
		return itemNames != null && getPlayerEquipmentNames(rs2Player).values().stream()
			.anyMatch(name -> itemNames.stream().anyMatch(name::equalsIgnoreCase));
	}

	/** As upstream: other players whose health bar is showing (so in combat recently). */
	@Deprecated
	public static java.util.List<Rs2PlayerModel> getPlayersInCombat()
	{
		return getPlayers(player -> healthOf(player)[0] != -1).collect(java.util.stream.Collectors.toList());
	}

	/** As upstream: {@code rs2Player}'s health bar as a percentage, or -1 when no bar is showing. */
	public static int calculateHealthPercentage(Rs2PlayerModel rs2Player)
	{
		int[] health = healthOf(rs2Player);
		if (health[0] == -1 || health[1] == -1 || health[1] == 0)
		{
			return -1;
		}
		return (int) ((health[0] / (double) health[1]) * 100);
	}

	/** {health ratio, health scale} of the player's live model, read on the client thread; -1s when unknown. */
	private static int[] healthOf(Rs2PlayerModel rs2Player)
	{
		if (rs2Player == null)
		{
			return new int[]{-1, -1};
		}
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
		{
			Player player = rs2Player.getPlayer();
			return player == null ? new int[]{-1, -1} : new int[]{player.getHealthRatio(), player.getHealthScale()};
		}, new int[]{-1, -1});
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
	 * Event hook (client thread); routed by {@code MicrobotReadServices}. As upstream for the
	 * potion timers, including the poison varp behind {@link #hasAntiPoisonActive()} and
	 * {@link #hasAntiVenomActive()}.
	 */
	public static void handlePotionTimers(net.runelite.api.events.VarbitChanged event)
	{
		int varbit = event.getVarbitId();
		int value = event.getValue();
		if (varbit == net.runelite.api.Varbits.ANTIFIRE)
		{
			antiFireTime = value;
		}
		if (varbit == net.runelite.api.Varbits.SUPER_ANTIFIRE)
		{
			superAntiFireTime = value;
		}
		if (varbit == net.runelite.api.Varbits.DIVINE_RANGING)
		{
			divineRangedTime = value;
		}
		if (varbit == net.runelite.api.Varbits.DIVINE_BASTION)
		{
			divineBastionTime = value;
		}
		if (varbit == net.runelite.api.Varbits.DIVINE_SUPER_COMBAT)
		{
			divineCombatTime = value;
		}
		if (varbit == net.runelite.api.Varbits.STAMINA_EFFECT)
		{
			staminaBuffTime = value;
		}
		if (varbit == net.runelite.api.Varbits.BUFF_GOADING_POTION)
		{
			goadingTime = value;
		}
		if (varbit == net.runelite.api.Varbits.MOONLIGHT_POTION)
		{
			moonlightTime = value;
		}
		if (varbit == net.runelite.api.Varbits.DIVINE_MAGIC)
		{
			divineMagicTime = value;
		}
		if (event.getVarpId() == net.runelite.api.VarPlayer.POISON)
		{
			antiVenomTime = value >= VENOM_VALUE_CUTOFF ? 0 : value;
			antiPoisonTime = value == 0 ? -1 : value;
		}
	}

	/**
	 * As upstream: {@code antiPoisonTime > 0}. Note that a positive poison varp means the player is
	 * <em>poisoned</em> (immunity is negative), so upstream's name reads inverted; kept as upstream.
	 */
	public static boolean hasAntiPoisonActive()
	{
		return antiPoisonTime > 0;
	}

	/** As upstream: wearing a serpentine helm, or the poison varp below the venom cutoff. */
	public static boolean hasAntiVenomActive()
	{
		if (net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.isWearing("serpentine helm"))
		{
			return true;
		}
		return antiVenomTime < VENOM_VALUE_CUTOFF;
	}

	/** As upstream: the prayer regeneration potion timer varbit is running. */
	public static boolean hasPrayerRegenerationActive()
	{
		return net.runelite.client.plugins.microbot.Microbot.getVarbitValue(
			net.runelite.api.gameval.VarbitID.PRAYER_REGENERATION_POTION_TIMER) > 0;
	}

	public static boolean hasAntiFireActive()
	{
		return antiFireTime > 0 || hasSuperAntiFireActive();
	}

	public static boolean hasSuperAntiFireActive()
	{
		return superAntiFireTime > 0;
	}

	public static boolean hasDivineRangedActive()
	{
		return divineRangedTime > 0 || hasDivineBastionActive();
	}

	public static boolean hasDivineBastionActive()
	{
		return divineBastionTime > 0;
	}

	public static boolean hasDivineCombatActive()
	{
		return divineCombatTime > 0;
	}

	public static boolean hasDivineMagicActive()
	{
		return divineMagicTime > 0;
	}

	public static boolean hasGoadingActive()
	{
		return goadingTime > 0;
	}

	public static boolean hasMoonlightActive()
	{
		return moonlightTime > 0;
	}

	/** As upstream: the boosted level exceeds the real level by more than {@code threshold}. */
	public static boolean hasAttackActive(int threshold)
	{
		return isBoostedAbove(net.runelite.api.Skill.ATTACK, threshold);
	}

	public static boolean hasStrengthActive(int threshold)
	{
		return isBoostedAbove(net.runelite.api.Skill.STRENGTH, threshold);
	}

	public static boolean hasDefenseActive(int threshold)
	{
		return isBoostedAbove(net.runelite.api.Skill.DEFENCE, threshold);
	}

	public static boolean hasMagicActive(int threshold)
	{
		return isBoostedAbove(net.runelite.api.Skill.MAGIC, threshold);
	}

	public static boolean hasRangingPotionActive(int threshold)
	{
		return isBoostedAbove(net.runelite.api.Skill.RANGED, threshold);
	}

	private static boolean isBoostedAbove(net.runelite.api.Skill skill, int threshold)
	{
		return getBoostedSkillLevel(skill) - threshold > getRealSkillLevel(skill);
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

	/** As upstream: the top-level scene is an instance (for example a player-owned house). */
	public static boolean IsInInstance()
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
			net.runelite.client.plugins.microbot.Microbot.getClient().getTopLevelWorldView().getScene().isInstance(), false);
	}

	/** As upstream: the local player's prayer is above 0. */
	public static boolean hasPrayerPoints()
	{
		return getBoostedSkillLevel(net.runelite.api.Skill.PRAYER) > 0;
	}

	/** As upstream: boosted prayer as a whole percentage of the real prayer level. */
	public static int getPrayerPercentage()
	{
		int current = getBoostedSkillLevel(net.runelite.api.Skill.PRAYER);
		int base = getRealSkillLevel(net.runelite.api.Skill.PRAYER);
		return (int) ((current / (double) base) * 100);
	}

	/** As upstream: the local player's local location, read on the client thread; {@code null} without a player. */
	public static net.runelite.api.coords.LocalPoint getLocalLocation()
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
		{
			Player localPlayer = net.runelite.client.plugins.microbot.Microbot.getClient().getLocalPlayer();
			return localPlayer == null ? null : localPlayer.getLocalLocation();
		}, null);
	}

	/** As upstream: within {@code radius} tiles of {@code worldPoint} on its floor. */
	public static boolean isInArea(net.runelite.api.coords.WorldPoint worldPoint, int radius)
	{
		return isInArea(worldPoint, radius, radius);
	}

	/** As upstream: within a {@code xRadius} by {@code yRadius} rectangle around {@code worldPoint} on its floor. */
	public static boolean isInArea(net.runelite.api.coords.WorldPoint worldPoint, int xRadius, int yRadius)
	{
		if (worldPoint == null || xRadius < 0 || yRadius < 0)
		{
			return false;
		}
		net.runelite.api.coords.WorldPoint playerLocation = getWorldLocation();
		if (playerLocation == null || worldPoint.getPlane() != playerLocation.getPlane())
		{
			return false;
		}
		return Math.abs(playerLocation.getX() - worldPoint.getX()) <= xRadius
			&& Math.abs(playerLocation.getY() - worldPoint.getY()) <= yRadius;
	}

	/** As upstream: the local player shows spot animation {@code graphicId}. Read on the client thread. */
	public static boolean hasSpotAnimation(int graphicId)
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
		{
			Player localPlayer = net.runelite.client.plugins.microbot.Microbot.getClient().getLocalPlayer();
			return localPlayer != null && localPlayer.hasSpotAnim(graphicId);
		}, false);
	}

	/** As upstream: stunned, shown by spot animation 245. */
	public static boolean isStunned()
	{
		return hasSpotAnimation(245);
	}

	/** As upstream: the local player's combat level, or 0 without one. */
	public static int getCombatLevel()
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
		{
			Player local = net.runelite.client.plugins.microbot.Microbot.getClient().getLocalPlayer();
			return local == null ? 0 : local.getCombatLevel();
		}, 0);
	}

	/** As upstream: whether the stamina effect (slowed run-energy drain) varbit is set. */
	public static boolean hasStaminaActive()
	{
		return net.runelite.client.plugins.microbot.Microbot.getVarbitValue(net.runelite.api.Varbits.RUN_SLOWED_DEPLETION_ACTIVE) != 0;
	}

	/**
	 * As upstream: other players who could attack the local player and be attacked back, both
	 * standing in the wilderness, given each side's wilderness level; empty outside the wilderness.
	 */
	public static java.util.List<Rs2PlayerModel> getPlayersInCombatLevelRange()
	{
		int localCombatLevel = getCombatLevel();
		int localWildernessLevel = Rs2Pvp.getWildernessLevelFrom(getWorldLocation());
		if (localWildernessLevel == 0)
		{
			return java.util.Collections.emptyList();
		}
		int localMin = Math.max(3, localCombatLevel - localWildernessLevel);
		int localMax = Math.min(126, localCombatLevel + localWildernessLevel);
		return getPlayers(player ->
		{
			int combatLevel = player.getCombatLevel();
			int wildernessLevel = Rs2Pvp.getWildernessLevelFrom(player.getWorldLocation());
			if (wildernessLevel == 0)
			{
				return false;
			}
			int min = Math.max(3, combatLevel - wildernessLevel);
			int max = Math.min(126, combatLevel + wildernessLevel);
			return combatLevel >= localMin && combatLevel <= localMax && localCombatLevel >= min && localCombatLevel <= max;
		}).collect(java.util.stream.Collectors.toList());
	}
}
