/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/npc/models/Rs2NpcModel.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.npc.models;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.api.IEntity;
import net.runelite.client.plugins.microbot.api.actor.Rs2ActorModel;

/**
 * Snapshot of one NPC captured by {@code Rs2NpcCache}.
 * <p>
 * Adaptation: read-only subset plus {@code click}. Combat level, health,
 * death and the interacting actor are captured with the snapshot; line of
 * sight is evaluated when asked. Overhead-prayer helpers are not ported.
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
	public int getAnimation()
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(npc::getAnimation, -1);
	}
	public String getOverheadText()
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.read(npc::getOverheadText, null);
	}
	public boolean click(String action)
	{
		return net.runelite.client.plugins.microbot.mobile.MobileActions.npcAt(id, index, action);
	}

	public int getCombatLevel()
	{
		return combatLevel;
	}

	/** The actor this NPC was interacting with at capture (live; read it on the client thread). */
	public net.runelite.api.Actor getInteracting()
	{
		return interacting;
	}

	/** Whether this NPC was interacting with the local player at capture. */
	public boolean isInteractingWithPlayer()
	{
		return interactingWithPlayer;
	}

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
}
