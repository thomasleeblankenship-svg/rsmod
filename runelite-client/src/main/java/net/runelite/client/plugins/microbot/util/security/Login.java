package net.runelite.client.plugins.microbot.util.security;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

/**
 * World selection for script-initiated hops.
 * <p>
 * Adapted from chsami/Microbot {@code util/security/Login.java} at
 * {@code a2114401bbbc35e9762db9e8aa666d70ffcd24d8} (BSD 2-Clause): {@code getRandomWorld} and
 * {@code getNextWorld} keep upstream's filters (no PvP, high-risk, Deadman, beta, tournament,
 * skill-total or other special worlds; under 1,950 players; seasonal only from a seasonal world)
 * and fallbacks (world 360 members, 383 free). Not ported: logging in ({@code new Login(...)}),
 * which types stored credentials; RuneMobile's own login handles that.
 */
public final class Login
{
	private static final int MAX_PLAYER_COUNT = 1950;

	private Login() { }

	public static int getRandomWorld(boolean isMembers)
	{
		return getRandomWorld(isMembers, null);
	}

	/** As upstream: a random eligible world, optionally in {@code region}. */
	public static int getRandomWorld(boolean isMembers, WorldRegion region)
	{
		List<World> worlds = eligible(isMembers, region);
		if (worlds == null || worlds.isEmpty())
		{
			return fallback(isMembers);
		}
		return worlds.get(ThreadLocalRandom.current().nextInt(worlds.size())).getId();
	}

	public static int getNextWorld(boolean isMembers)
	{
		return getNextWorld(isMembers, null);
	}

	/** As upstream: the eligible world after the current one in list order, wrapping round. */
	public static int getNextWorld(boolean isMembers, WorldRegion region)
	{
		List<World> worlds = eligible(isMembers, region);
		if (worlds == null || worlds.isEmpty())
		{
			return fallback(isMembers);
		}
		int current = currentWorld();
		for (int i = 0; i < worlds.size(); i++)
		{
			if (worlds.get(i).getId() == current)
			{
				return worlds.get((i + 1) % worlds.size()).getId();
			}
		}
		return worlds.get(0).getId();
	}

	private static List<World> eligible(boolean isMembers, WorldRegion region)
	{
		WorldResult result = Microbot.getWorldService() == null ? null : Microbot.getWorldService().getWorlds();
		if (result == null || result.getWorlds() == null)
		{
			return null;
		}
		boolean seasonal = MobileActions.read(() ->
			Microbot.getClient().getWorldType().contains(net.runelite.api.WorldType.SEASONAL), false);
		return result.getWorlds().stream()
			.filter(world -> world.getTypes() != null && isOrdinary(world)
				&& world.getPlayers() >= 0 && world.getPlayers() < MAX_PLAYER_COUNT
				&& seasonal == world.getTypes().contains(WorldType.SEASONAL)
				&& isMembers == world.getTypes().contains(WorldType.MEMBERS)
				&& (region == null || world.getRegion() == region))
			.collect(Collectors.toList());
	}

	private static boolean isOrdinary(World world)
	{
		for (WorldType type : new WorldType[]{WorldType.PVP, WorldType.HIGH_RISK, WorldType.BOUNTY,
			WorldType.SKILL_TOTAL, WorldType.LAST_MAN_STANDING, WorldType.QUEST_SPEEDRUNNING,
			WorldType.BETA_WORLD, WorldType.DEADMAN, WorldType.PVP_ARENA, WorldType.TOURNAMENT,
			WorldType.NOSAVE_MODE, WorldType.LEGACY_ONLY, WorldType.EOC_ONLY, WorldType.FRESH_START_WORLD})
		{
			if (world.getTypes().contains(type))
			{
				return false;
			}
		}
		return true;
	}

	private static int currentWorld()
	{
		return MobileActions.read(() ->
		{
			Client client = Microbot.getClient();
			return client == null ? -1 : client.getWorld();
		}, -1);
	}

	private static int fallback(boolean isMembers)
	{
		return isMembers ? 360 : 383;
	}
}
