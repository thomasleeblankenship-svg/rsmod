/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/npc/models/Rs2NpcModel.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.npc.models;

import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.api.IEntity;
import net.runelite.client.plugins.microbot.api.actor.Rs2ActorModel;

/**
 * Snapshot of one NPC captured by {@code Rs2NpcCache}.
 * <p>
 * An {@link net.runelite.api.Actor} as upstream (see {@link Rs2ActorModel}). Combat level,
 * health, death and the interacting actor are captured with the snapshot; line of sight and the
 * overhead prayer icon are read when asked.
 */
public class Rs2NpcModel extends Rs2ActorModel implements IEntity
{
	private final NPC npc;
	private final int id;
	private final int index;
	private final int combatLevel;
	private final int healthRatio;
	private final int healthScale;
	private final boolean dead;
	private final net.runelite.api.Actor interacting;
	private final boolean interactingWithPlayer;

	/** Captures {@code npc}; must be called on the client thread. */
	/** As upstream: a model of {@code npc} with the installed read services' client; client thread only. */
	public Rs2NpcModel(NPC npc)
	{
		this(net.runelite.client.plugins.microbot.api.MicrobotReadServices.require().getClient(), npc);
	}

	public Rs2NpcModel(Client client, NPC npc)
	{
		super(client, npc);
		this.npc = npc;
		this.id = npc.getId();
		this.index = npc.getIndex();
		this.combatLevel = npc.getCombatLevel();
		this.healthRatio = npc.getHealthRatio();
		this.healthScale = npc.getHealthScale();
		this.dead = npc.isDead();
		this.interacting = npc.getInteracting();
		this.interactingWithPlayer = interacting != null && interacting == client.getLocalPlayer();
	}

	/** The live NPC. Read it only on the client thread. */
	public NPC getNpc()
	{
		return npc;
	}

	@Override
	public int getId()
	{
		return id;
	}

	public int getIndex()
	{
		return index;
	}
	@Override
	public int getAnimation()
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(npc::getAnimation, -1);
	}
	@Override
	public String getOverheadText()
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(npc::getOverheadText, null);
	}
	@Override
	public boolean click(String action)
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.npcAt(id, index, action);
	}

	/** As upstream, the first action; mobile reads it from the NPC's (transformed) definition. */
	@Override
	public boolean click()
	{
		String action = net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
		{
			net.runelite.api.NPCComposition c = npc.getTransformedComposition();
			if (c == null || c.getActions() == null) { return null; }
			for (String a : c.getActions()) { if (a != null && !a.isEmpty()) { return a; } }
			return null;
		}, null);
		return action != null && click(action);
	}

	@Override
	public int getCombatLevel()
	{
		return combatLevel;
	}

	/** The actor this NPC was interacting with at capture (live; read it on the client thread). */
	@Override
	public net.runelite.api.Actor getInteracting()
	{
		return interacting;
	}

	/** Whether this NPC was interacting with the local player at capture. */
	public boolean isInteractingWithPlayer()
	{
		return interactingWithPlayer;
	}

	@Override
	public boolean isDead()
	{
		return dead;
	}

	/** Health bar fill as a percentage (0-100), or -1 when no health bar was showing. */
	public double getHealthPercentage()
	{
		return healthScale == 0 ? -1 : (double) healthRatio / healthScale * 100.0;
	}

	/**
	 * Whether the local player can see this NPC across the top-level scene (projectiles, ranged and
	 * magic): true on the same tile, false without a player or location.
	 */
	public boolean hasLineOfSight()
	{
		net.runelite.client.plugins.microbot.api.MicrobotReadServices services =
			net.runelite.client.plugins.microbot.api.MicrobotReadServices.current();
		net.runelite.api.coords.WorldPoint npcLocation = getWorldLocation();
		if (services == null || npcLocation == null)
		{
			return false;
		}
		Client client = services.getClient();
		return services.read(() ->
		{
			net.runelite.api.Player player = client.getLocalPlayer();
			net.runelite.api.coords.WorldPoint playerLocation = player == null ? null : player.getWorldLocation();
			if (playerLocation == null)
			{
				return false;
			}
			if (playerLocation.equals(npcLocation))
			{
				return true;
			}
			net.runelite.api.WorldView view = client.getTopLevelWorldView();
			return view != null && npcLocation.toWorldArea().hasLineOfSightTo(view, playerLocation);
		}).orElse(false);
	}

	/**
	 * As upstream: the NPC's overhead prayer icon (the first set overhead sprite), or {@code null}.
	 * Read live on the client thread. Unlike upstream, an id outside {@link HeadIcon} yields
	 * {@code null} instead of throwing.
	 */
	public HeadIcon getHeadIcon()
	{
		short[] spriteIds = net.runelite.client.plugins.microbot.mobile.MobileActions.read(npc::getOverheadSpriteIds, null);
		if (spriteIds == null)
		{
			return null;
		}
		for (short spriteId : spriteIds)
		{
			if (spriteId == -1)
			{
				continue;
			}
			return spriteId >= 0 && spriteId < HeadIcon.values().length ? HeadIcon.values()[spriteId] : null;
		}
		return null;
	}

	// ---- api batch 1 (Claude 1, user-authorised; Claude 2's file): upstream reads ----

	/** As upstream: within {@code maxDistance} local units of the local player (read on the client thread). */
	public boolean isWithinDistanceFromPlayer(int maxDistance)
	{
		int distance = getDistanceFromPlayer();
		return distance != Integer.MAX_VALUE && distance <= maxDistance;
	}

	/**
	 * As upstream: the local-point distance to the local player, read on the client thread;
	 * {@link Integer#MAX_VALUE} when either location is unknown.
	 */
	public int getDistanceFromPlayer()
	{
		net.runelite.api.coords.LocalPoint npcLocation = getLocalLocation();
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(() ->
		{
			net.runelite.api.Player local = net.runelite.client.plugins.microbot.Microbot.getClient().getLocalPlayer();
			net.runelite.api.coords.LocalPoint player = local == null ? null : local.getLocalLocation();
			return player == null || npcLocation == null ? Integer.MAX_VALUE : npcLocation.distanceTo(player);
		}, Integer.MAX_VALUE);
	}

	/** As upstream: within {@code maxDistance} tiles of {@code anchor}. */
	public boolean isWithinDistance(net.runelite.api.coords.WorldPoint anchor, int maxDistance)
	{
		net.runelite.api.coords.WorldPoint location = getWorldLocation();
		return anchor != null && location != null && location.distanceTo(anchor) <= maxDistance;
	}

	/** As upstream: its pose is not its idle pose (read on the client thread). */
	public boolean isMoving()
	{
		return npc != null && net.runelite.client.plugins.microbot.mobile.MobileActions.read(
			() -> npc.getPoseAnimation() != npc.getIdlePoseAnimation(), false);
	}

	/** As upstream: NPCs named one of {@code names} (exact, any case) or containing one (otherwise). */
	public static java.util.function.Predicate<Rs2NpcModel> matches(boolean exact, String... names)
	{
		return npc ->
		{
			String name = npc.getName();
			if (name == null || names == null)
			{
				return false;
			}
			String lower = name.toLowerCase(java.util.Locale.ROOT);
			return exact ? java.util.Arrays.stream(names).anyMatch(name::equalsIgnoreCase)
				: java.util.Arrays.stream(names).anyMatch(part -> part != null && lower.contains(part.toLowerCase(java.util.Locale.ROOT)));
		};
	}
}
