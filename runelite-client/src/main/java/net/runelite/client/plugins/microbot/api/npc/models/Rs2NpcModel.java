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
 * Adaptation: read-only subset. Upstream interaction ({@code click}), combat,
 * line-of-sight and overhead helpers are not ported in this stage.
 */
public class Rs2NpcModel extends Rs2ActorModel implements IEntity
{
	private final NPC npc;
	private final int id;
	private final int index;

	/** Captures {@code npc}; must be called on the client thread. */
	public Rs2NpcModel(Client client, NPC npc)
	{
		super(client, npc);
		this.npc = npc;
		this.id = npc.getId();
		this.index = npc.getIndex();
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

}
