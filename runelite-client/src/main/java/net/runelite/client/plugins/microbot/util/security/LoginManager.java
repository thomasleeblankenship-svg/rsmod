package net.runelite.client.plugins.microbot.util.security;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.headless.HeadlessLogin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

/**
 * Logging in for scripts, the break handler and auto-login.
 * <p>
 * Adapted from chsami/Microbot {@code util/security/LoginManager.java} at
 * {@code a2114401bbbc35e9762db9e8aa666d70ffcd24d8} (BSD 2-Clause), keeping its public API, its
 * 1.5 s attempt throttle and its too-many-attempts lockout ({@link LoginLockout}). Mobile logs in to
 * a Jagex account only: the RuneMobile launcher session is already authenticated, so a login is a
 * Play Now on the welcome screen, driven by {@link HeadlessLogin} (which also clicks "Try again" after
 * a disconnect and dismisses the post-login news screen). No username or password is stored or typed;
 * {@code login(username, password, world)} ignores both. Without a Jagex session (autologin disabled)
 * every login returns false.
 * <p>
 * The active profile is RuneLite's current {@link ConfigProfile}. Its {@code isMember} and membership
 * days are refreshed from the game whenever the player is logged in ({@link #markLoggedIn}).
 */
@Slf4j
public final class LoginManager
{
	private static final Object LOGIN_LOCK = new Object();
	private static final AtomicBoolean LOGIN_ATTEMPT_ACTIVE = new AtomicBoolean(false);
	private static final AtomicReference<Instant> LAST_LOGIN_ATTEMPT = new AtomicReference<>(null);
	private static final AtomicReference<GameState> LAST_KNOWN_GAME_STATE = new AtomicReference<>(GameState.UNKNOWN);
	private static final AtomicReference<Instant> LAST_LOGIN_TIMESTAMP = new AtomicReference<>(null);
	/** Upstream's throttle between two login attempts. */
	static final long MIN_ATTEMPT_INTERVAL_MILLIS = 1500;
	/** The too-many-attempts dialog's button, in the 765x503 fixed login space (upstream: 365 on an 804-wide screen, 326). */
	private static final int LOCKOUT_OK_X = 346;
	private static final int LOCKOUT_OK_Y = 326;

	/** Upstream's settable override; when null the RuneLite profile in use is the active profile. */
	public static ConfigProfile activeProfile = null;

	private LoginManager()
	{
		throw new IllegalStateException("Unable to instantiate utility class");
	}

	public static void setActiveProfile(ConfigProfile profile)
	{
		activeProfile = profile;
	}

	/** As upstream: RuneLite's current profile (or the one set with {@link #setActiveProfile}); null before the client is up. */
	public static ConfigProfile getActiveProfile()
	{
		if (activeProfile != null)
		{
			return activeProfile;
		}
		ConfigManager configManager = Microbot.getConfigManager();
		return configManager == null ? null : configManager.getProfile();
	}

	public static Instant getLastLoginTimestamp()
	{
		return LAST_LOGIN_TIMESTAMP.get();
	}

	public static GameState getLastKnownGameState()
	{
		return LAST_KNOWN_GAME_STATE.get();
	}

	/** Records a game-state change: logged in refreshes the login time, the login screen ends an attempt. */
	public static void setLastKnownGameState(GameState gameState)
	{
		LAST_KNOWN_GAME_STATE.set(gameState);
		if (gameState == GameState.LOGGED_IN)
		{
			markLoggedIn();
		}
		else if (gameState == GameState.LOGIN_SCREEN)
		{
			markLoggedOut();
		}
	}

	/** The client's game state, or UNKNOWN before the client is up. */
	public static GameState getGameState()
	{
		Client client = Microbot.getClient();
		return client != null ? client.getGameState() : GameState.UNKNOWN;
	}

	public static boolean isLoggedIn()
	{
		return getGameState() == GameState.LOGGED_IN;
	}

	/** True while a login requested here is still being driven (until logged in, or the login gives up). */
	public static boolean isLoginAttemptActive()
	{
		if (LOGIN_ATTEMPT_ACTIVE.get() && (HeadlessLogin.isLoginGivenUp() || !HeadlessLogin.isLoginInProgress()))
		{
			LOGIN_ATTEMPT_ACTIVE.set(false);
		}
		return LOGIN_ATTEMPT_ACTIVE.get();
	}

	/** Logged in: stamps the login time, clears any lockout and refreshes the profile's membership. */
	public static void markLoggedIn()
	{
		if (!isLoggedIn())
		{
			return;
		}
		LOGIN_ATTEMPT_ACTIVE.set(false);
		LAST_LOGIN_TIMESTAMP.set(Instant.now());
		LoginLockout.getInstance().recordSuccessfulLogin();
		refreshMembership();
	}

	public static void markLoggedOut()
	{
		LOGIN_ATTEMPT_ACTIVE.set(false);
	}

	/** As upstream: how long the account has been logged in, or zero when logged out. */
	public static Duration getLoginDuration()
	{
		Instant since = getLastLoginTimestamp();
		if (since == null || !isLoggedIn())
		{
			return Duration.of(0, ChronoUnit.MILLIS);
		}
		return Duration.between(since, Instant.now());
	}

	/**
	 * As upstream: logs in to the profile's selected world ({@code -1} a random members world, {@code -2} a
	 * random free world, a world ID that world). With none selected, it stays on the current world unless that
	 * world's membership does not match the account's.
	 */
	public static boolean login()
	{
		ConfigProfile profile = getActiveProfile();
		Integer selected = profile == null ? null : profile.getSelectedWorld();
		boolean member = profile != null && profile.isMember();
		int targetWorld;
		if (selected != null && selected == -1)
		{
			targetWorld = Login.getRandomWorld(true);
		}
		else if (selected != null && selected == -2)
		{
			targetWorld = Login.getRandomWorld(false);
		}
		else if (selected != null && selected > 0)
		{
			targetWorld = selected;
		}
		else if (profile != null && member != isCurrentWorldMembers())
		{
			targetWorld = Login.getRandomWorld(member);
		}
		else
		{
			targetWorld = 0;
		}
		return login(targetWorld);
	}

	/** Logs in to {@code worldId} (0 or less: the world the client already points at). */
	public static boolean login(int worldId)
	{
		ConfigProfile profile = getActiveProfile();
		return login(profile == null ? "" : profile.getName(), null, worldId);
	}

	/**
	 * Upstream's signature. Mobile logs in to the Jagex session the app was launched with, so
	 * {@code username} and {@code encryptedPassword} are not used.
	 *
	 * @return true when a login was started (or the player is already logged in)
	 */
	public static boolean login(String username, String encryptedPassword, int worldId)
	{
		if (isLoggedIn())
		{
			return true;
		}
		final Client client = Microbot.getClient();
		if (client == null)
		{
			log.warn("Cannot login - client is not initialised");
			return false;
		}
		if (isLoginAttemptActive())
		{
			log.debug("Login attempt already active - skipping duplicate request");
			return false;
		}
		if (checkLoginLockout())
		{
			log.debug("Login skipped - login lockout cooldown active ({}s remaining)",
				TimeUnit.MILLISECONDS.toSeconds(LoginLockout.getInstance().remainingMillis()));
			return false;
		}
		synchronized (LOGIN_LOCK)
		{
			Instant lastAttempt = LAST_LOGIN_ATTEMPT.get();
			Instant now = Instant.now();
			if (lastAttempt != null && Duration.between(lastAttempt, now).toMillis() < MIN_ATTEMPT_INTERVAL_MILLIS)
			{
				log.debug("Login throttled - last attempt {}ms ago", Duration.between(lastAttempt, now).toMillis());
				return false;
			}
			LAST_LOGIN_ATTEMPT.set(now);
		}
		World world = worldId > 0 ? findWorld(worldId) : null;
		if (worldId > 0 && world == null && MobileActions.read(client::getWorld, -1) != worldId)
		{
			// Upstream 2.6.47: never log in to another world than the one asked for; callers retry.
			log.warn("Login aborted - could not select world {} (not in the world list)", worldId);
			return false;
		}
		boolean armed = MobileActions.read(() -> HeadlessLogin.requestRelogin(world == null ? 0 : world.getId(),
			world == null ? null : world.getAddress(),
			world != null && world.getTypes() != null && world.getTypes().contains(WorldType.MEMBERS)), false);
		LOGIN_ATTEMPT_ACTIVE.set(armed);
		if (armed)
		{
			log.info("Login started{}", world == null ? "" : " to world " + world.getId());
		}
		return armed;
	}

	public static boolean isLoginLockoutScreen()
	{
		Client client = Microbot.getClient();
		return client != null
			&& client.getGameState() == GameState.LOGIN_SCREEN
			&& LoginLockout.isLockoutLoginIndex(client.getLoginIndex());
	}

	/** As upstream: true while the too-many-attempts cooldown holds; dismisses its dialog at most every 10 s. */
	public static boolean checkLoginLockout()
	{
		LoginLockout lockout = LoginLockout.getInstance();
		if (!isLoginLockoutScreen())
		{
			lockout.recordScreenCleared();
			return lockout.isActive();
		}
		if (lockout.recordLockout())
		{
			HopBudget.getInstance().recordLockout();
			log.warn("Too many login attempts - pausing all logins for {}s (lockout #{})",
				TimeUnit.MILLISECONDS.toSeconds(lockout.remainingMillis()), lockout.getConsecutiveLockouts());
		}
		if (lockout.tryClaimDismiss())
		{
			HeadlessLogin.tapLoginScreen(Microbot.getClient(), LOCKOUT_OK_X, LOCKOUT_OK_Y);
		}
		return true;
	}

	public static int getRandomWorld(boolean isMembers, WorldRegion region)
	{
		return Login.getRandomWorld(isMembers, region);
	}

	public static int getRandomWorld(boolean isMembers)
	{
		return Login.getRandomWorld(isMembers);
	}

	public static int getNextWorld(boolean isMembers)
	{
		return Login.getNextWorld(isMembers);
	}

	public static int getNextWorld(boolean isMembers, WorldRegion region)
	{
		return Login.getNextWorld(isMembers, region);
	}

	/** As upstream: true when {@code worldId} is in the world list as a members world. */
	public static boolean isMemberWorld(int worldId)
	{
		World world = worldId > 0 ? findWorld(worldId) : null;
		return world != null && world.getTypes() != null && world.getTypes().contains(WorldType.MEMBERS);
	}

	public static boolean isCurrentWorldMembers()
	{
		Client client = Microbot.getClient();
		return client != null && isMemberWorld(client.getWorld());
	}

	/**
	 * Upstream points the client at a world on the login screen. Mobile does that as part of
	 * {@link #login(int)}; logged out, this starts that login.
	 */
	/** As upstream 2.6.47: true when the change was made (on mobile, a login to that world was started). */
	public static boolean setWorld(int worldNumber)
	{
		return !isLoggedIn() && login(worldNumber);
	}

	private static World findWorld(int worldId)
	{
		try
		{
			WorldResult result = Microbot.getWorldService() == null ? null : Microbot.getWorldService().getWorlds();
			return result == null ? null : result.findWorld(worldId);
		}
		catch (RuntimeException e)
		{
			log.debug("World list unavailable for world {}", worldId, e);
			return null;
		}
	}

	/**
	 * Updates the active profile's membership from the game (the days-left varp and the current world).
	 * Call once the player is in game; the varp is not loaded on the login tick itself.
	 */
	public static void refreshMembership()
	{
		ConfigProfile profile = getActiveProfile();
		if (profile == null)
		{
			return;
		}
		Integer days = MobileActions.read(() ->
		{
			Client client = Microbot.getClient();
			return client == null ? null : client.getVarpValue(VarPlayerID.ACCOUNT_CREDIT);
		}, null);
		if (days != null)
		{
			profile.setMemberExpireDays(days);
			profile.setMemberExpireDaysTimeStemp(System.currentTimeMillis());
			profile.setMember(days > 0 || isCurrentWorldMembers());
		}
	}
}
