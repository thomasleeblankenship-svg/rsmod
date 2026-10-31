package net.runelite.client.plugins.microbot.dabrutus;

import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * Brutus's telegraphs, read on the client thread the moment they happen (overhead text and animation events), so the
 * dodge tile is worked out from his position and facing at that instant. The script's fast thread clicks it.
 *
 * <p>Cues (wiki, KSP Mad Cow, osrsx-brutus): "*growls*" or the heavy-breath/charge animations = Charge;
 * "*snorts*" or the stomp animations = Slam. Stomp impact graphics (3588-3590) mark slams still landing.
 */
final class BrutusEvents
{
	static final int BRUTUS = 15626;
	static final int BRUTUS_ROUTEFIND = 15627;

	static final int ANIM_CHARGE = 13778;
	static final int ANIM_HEAVY_BREATH = 13780;
	static final int ANIM_DEATH = 13784;
	static final int ANIM_STOMP = 13785;
	static final int ANIM_SPIRIT_STOMP = 13791;
	static final int ANIM_CHARGING_FADE = 13793;
	static final int ANIM_CHARGE_START_FADE = 13795;
	static final int ANIM_STOMP_FADE_IN = 13799;
	static final Set<Integer> CHARGE_ANIMS = Set.of(ANIM_HEAVY_BREATH, ANIM_CHARGE, ANIM_CHARGING_FADE,
		ANIM_CHARGE_START_FADE);
	static final Set<Integer> SLAM_ANIMS = Set.of(ANIM_STOMP, ANIM_SPIRIT_STOMP, ANIM_STOMP_FADE_IN);
	static final Set<Integer> SLAM_IMPACTS = Set.of(3588, 3589, 3590);

	/** A cue arriving this soon after the last one of the same kind belongs to the same special. */
	private static final long SAME_SPECIAL_MS = 2_400;

	enum Kind
	{
		CHARGE, SLAM
	}

	/** One telegraphed special and where to go. Scene coordinates. */
	static final class Special
	{
		final Kind kind;
		final long atMs;
		final int bossMinX;
		final int bossMinY;
		final int fromX;
		final int fromY;
		/** Dodge tile; null when there was none (boxed in). Equal to from when already safe. */
		final int[] to;
		volatile boolean clicked;
		volatile int clicks;

		Special(Kind kind, long atMs, int bossMinX, int bossMinY, int fromX, int fromY, int[] to)
		{
			this.kind = kind;
			this.atMs = atMs;
			this.bossMinX = bossMinX;
			this.bossMinY = bossMinY;
			this.fromX = fromX;
			this.fromY = fromY;
			this.to = to;
		}

		boolean alreadySafe()
		{
			return to != null && to[0] == fromX && to[1] == fromY;
		}

		@Override
		public String toString()
		{
			return kind + " boss@" + bossMinX + "," + bossMinY + " me@" + fromX + "," + fromY
				+ (to == null ? " -> none" : alreadySafe() ? " -> stay" : " -> " + to[0] + "," + to[1]);
		}
	}

	/** The latest special (cleared by nothing: the script compares times). */
	static volatile Special latest;
	static volatile long lastSlamImpactMs;
	static volatile long lastSlamAnimMs;
	static volatile int kills;
	static volatile int charges;
	static volatile int slams;
	private static volatile int lastDeathNpcIndex = -1;
	private static volatile long lastDeathMs;
	/** Set by the script: slams keep a 2-tile gap (true) or only leave his side (false). */
	static volatile boolean slamStandBack = true;
	/** Ranged mode: no need to end a dodge next to him, and slams always keep the 2-tile gap. */
	static volatile boolean ranged;

	private BrutusEvents()
	{
	}

	static void reset()
	{
		latest = null;
		lastSlamImpactMs = lastSlamAnimMs = 0;
		kills = charges = slams = 0;
		lastDeathNpcIndex = -1;
		lastDeathMs = 0;
	}

	static boolean isBrutus(NPC npc)
	{
		return npc != null && (npc.getId() == BRUTUS || npc.getId() == BRUTUS_ROUTEFIND);
	}

	/** Client thread. */
	static void onOverhead(NPC npc, String text)
	{
		if (!isBrutus(npc) || text == null)
		{
			return;
		}
		String t = text.toLowerCase();
		if (t.contains("growl"))
		{
			cue(npc, Kind.CHARGE, "overhead '" + text + "'");
		}
		else if (t.contains("snort"))
		{
			cue(npc, Kind.SLAM, "overhead '" + text + "'");
		}
	}

	/** Client thread. */
	static void onAnimation(NPC npc)
	{
		if (!isBrutus(npc))
		{
			return;
		}
		int anim = npc.getAnimation();
		if (anim == ANIM_DEATH)
		{
			countKill(npc);
			return;
		}
		if (SLAM_ANIMS.contains(anim))
		{
			lastSlamAnimMs = System.currentTimeMillis();
		}
		// The animation is a backup for a missed overhead: only the wind-ups start a dodge.
		if (anim == ANIM_HEAVY_BREATH || anim == ANIM_CHARGE_START_FADE)
		{
			cue(npc, Kind.CHARGE, "animation " + anim);
		}
		else if (anim == ANIM_STOMP_FADE_IN)
		{
			cue(npc, Kind.SLAM, "animation " + anim);
		}
	}

	static void onSlamImpact()
	{
		lastSlamImpactMs = System.currentTimeMillis();
	}

	/** Recent game messages (time, text), newest last: the script logs them when a click did not do what it should. */
	private static final java.util.Deque<Object[]> MESSAGES = new java.util.concurrent.ConcurrentLinkedDeque<>();

	/** Client thread: a game message (they are not in client.log otherwise). */
	/** When the game said the bow has no ammo it can fire ("no ammo left in your quiver", "can't use that ammo"). */
	private static final java.util.Deque<Long> NO_AMMO = new java.util.concurrent.ConcurrentLinkedDeque<>();

	/** "No ammo" game messages in the last {@code windowMs}. */
	static int noAmmoRecent(long windowMs)
	{
		long cutoff = System.currentTimeMillis() - windowMs;
		while (!NO_AMMO.isEmpty() && NO_AMMO.peekFirst() < cutoff)
		{
			NO_AMMO.pollFirst();
		}
		return NO_AMMO.size();
	}

	static void clearNoAmmo()
	{
		NO_AMMO.clear();
		NO_RUNES.clear();
	}

	/** When the game said there are not enough runes for the autocast spell. */
	private static final java.util.Deque<Long> NO_RUNES = new java.util.concurrent.ConcurrentLinkedDeque<>();

	static int noRunesRecent(long windowMs)
	{
		long cutoff = System.currentTimeMillis() - windowMs;
		while (!NO_RUNES.isEmpty() && NO_RUNES.peekFirst() < cutoff)
		{
			NO_RUNES.pollFirst();
		}
		return NO_RUNES.size();
	}

	static void onGameMessage(String text)
	{
		String low = text == null ? "" : text.toLowerCase();
		if (low.contains("no ammo left") || low.contains("can't use that ammo") || low.contains("cannot use that ammo"))
		{
			NO_AMMO.addLast(System.currentTimeMillis());
		}
		if (low.contains("do not have enough") && low.contains("rune"))
		{
			NO_RUNES.addLast(System.currentTimeMillis());
		}
		MESSAGES.addLast(new Object[]{System.currentTimeMillis(), text});
		while (MESSAGES.size() > 20)
		{
			MESSAGES.pollFirst();
		}
	}

	/** Game messages since {@code sinceMs}, joined with " | "; "none" when there were none. */
	static String messagesSince(long sinceMs)
	{
		StringBuilder sb = new StringBuilder();
		for (Object[] m : MESSAGES)
		{
			if ((Long) m[0] >= sinceMs)
			{
				sb.append(sb.length() > 0 ? " | " : "").append(m[1]);
			}
		}
		return sb.length() == 0 ? "none" : sb.toString();
	}

	/** Client thread: an NPC despawned dead, or the death animation started. Counted once per NPC. */
	static void countKill(NPC npc)
	{
		long now = System.currentTimeMillis();
		if (npc.getIndex() == lastDeathNpcIndex && now - lastDeathMs < 15_000)
		{
			return;
		}
		lastDeathNpcIndex = npc.getIndex();
		lastDeathMs = now;
		kills++;
		Microbot.log(DaBrutusScript.tag() + "kill " + kills);
	}

	private static void cue(NPC npc, Kind kind, String why)
	{
		long now = System.currentTimeMillis();
		Special last = latest;
		if (last != null && last.kind == kind && now - last.atMs < SAME_SPECIAL_MS)
		{
			return;
		}
		Special s = plan(npc, kind, now);
		if (s == null)
		{
			return;
		}
		latest = s;
		if (kind == Kind.CHARGE)
		{
			charges++;
		}
		else
		{
			slams++;
		}
		Microbot.log(DaBrutusScript.tag() + "SPECIAL " + s + " (" + why + ")");
	}

	/** Client thread: his footprint, our tile and the collision map, and the dodge tile from them. */
	private static Special plan(NPC npc, Kind kind, long now)
	{
		Client client = Microbot.getClient();
		Player me = client.getLocalPlayer();
		WorldView wv = client.getTopLevelWorldView();
		if (me == null || wv == null)
		{
			return null;
		}
		LocalPoint bc = npc.getLocalLocation();
		LocalPoint pc = me.getLocalLocation();
		if (bc == null || pc == null)
		{
			return null;
		}
		// The local point of a 3x3 NPC is its centre tile.
		int minX = bc.getSceneX() - (BrutusDodge.SIZE - 1) / 2;
		int minY = bc.getSceneY() - (BrutusDodge.SIZE - 1) / 2;
		int px = pc.getSceneX();
		int py = pc.getSceneY();
		// While a dodge walk is under way, plan from where we are going: that is where the next hit lands on us.
		LocalPoint dest = client.getLocalDestinationLocation();
		if (dest != null && Math.max(Math.abs(dest.getSceneX() - px), Math.abs(dest.getSceneY() - py)) <= 3)
		{
			px = dest.getSceneX();
			py = dest.getSceneY();
		}
		BrutusDodge.Walls walls = walls(wv);
		int[] to;
		if (kind == Kind.CHARGE)
		{
			Set<Long> danger = BrutusDodge.chargeDanger(minX, minY, BrutusDodge.toward(minX, minY, px, py),
				BrutusDodge.facing(npc.getOrientation()));
			to = BrutusDodge.pick(px, py, minX, minY, danger, 1, BrutusDodge.MAX_CHARGE_STEPS, !ranged, walls);
		}
		else
		{
			boolean standBack = slamStandBack || ranged;
			Set<Long> danger = BrutusDodge.slamDanger(minX, minY, px, py);
			to = BrutusDodge.pick(px, py, minX, minY, danger, standBack ? 2 : 1, BrutusDodge.MAX_SLAM_STEPS,
				!standBack, walls);
		}
		return new Special(kind, now, minX, minY, px, py, to);
	}

	/** Collision of the current scene (client thread). Out-of-scene tiles are walls. */
	static BrutusDodge.Walls walls(WorldView wv)
	{
		CollisionData[] maps = wv.getCollisionMaps();
		if (maps == null || maps[wv.getPlane()] == null)
		{
			return (x, y, dx, dy) -> true;
		}
		int[][] flags = maps[wv.getPlane()].getFlags();
		return (x, y, dx, dy) ->
		{
			int tx = x + dx;
			int ty = y + dy;
			if (x < 0 || y < 0 || tx < 0 || ty < 0 || x >= flags.length || tx >= flags.length
				|| y >= flags[x].length || ty >= flags[tx].length)
			{
				return false;
			}
			int from = flags[x][y];
			int to = flags[tx][ty];
			if ((to & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0)
			{
				return false;
			}
			if (dx > 0)
			{
				return (from & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0 && (to & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0;
			}
			if (dx < 0)
			{
				return (from & CollisionDataFlag.BLOCK_MOVEMENT_WEST) == 0 && (to & CollisionDataFlag.BLOCK_MOVEMENT_EAST) == 0;
			}
			if (dy > 0)
			{
				return (from & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0 && (to & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0;
			}
			return (from & CollisionDataFlag.BLOCK_MOVEMENT_SOUTH) == 0 && (to & CollisionDataFlag.BLOCK_MOVEMENT_NORTH) == 0;
		};
	}
}
