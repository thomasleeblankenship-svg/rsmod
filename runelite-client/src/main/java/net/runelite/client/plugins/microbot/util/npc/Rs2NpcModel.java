package net.runelite.client.plugins.microbot.util.npc;

import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.ActorModel;

/**
 * Microbot's legacy NPC model: a live {@link NPC} wrapper whose every call runs on the client
 * thread (see {@link ActorModel}). Scripts construct it from an NPC they already hold, for example
 * in {@code onNpcSpawned}. Written for RuneMobile; the NPC-specific delegating methods are
 * generated from the {@link NPC} interface.
 */
@SuppressWarnings("deprecation")
public class Rs2NpcModel extends ActorModel implements NPC
{
	private final NPC npc;

	public Rs2NpcModel(NPC npc)
	{
		super(npc);
		this.npc = npc;
	}

	/** The wrapped live NPC; read it on the client thread. */
	public NPC getNpc()
	{
		return npc;
	}

	@Override
	public net.runelite.api.NpcOverrides getChatheadOverrides()
	{
		return MobileActions.<net.runelite.api.NpcOverrides>read(() -> npc.getChatheadOverrides(), null);
	}

	@Override
	public net.runelite.api.NPCComposition getComposition()
	{
		return MobileActions.<net.runelite.api.NPCComposition>read(() -> npc.getComposition(), null);
	}

	@Override
	public int getId()
	{
		return MobileActions.<Integer>read(() -> npc.getId(), 0);
	}

	@Override
	public int getIndex()
	{
		return MobileActions.<Integer>read(() -> npc.getIndex(), 0);
	}

	@Override
	public net.runelite.api.NpcOverrides getModelOverrides()
	{
		return MobileActions.<net.runelite.api.NpcOverrides>read(() -> npc.getModelOverrides(), null);
	}

	@Override
	public int[] getOverheadArchiveIds()
	{
		return MobileActions.<int[]>read(() -> npc.getOverheadArchiveIds(), null);
	}

	@Override
	public short[] getOverheadSpriteIds()
	{
		return MobileActions.<short[]>read(() -> npc.getOverheadSpriteIds(), null);
	}

	@Override
	public net.runelite.api.NPCComposition getTransformedComposition()
	{
		return MobileActions.<net.runelite.api.NPCComposition>read(() -> npc.getTransformedComposition(), null);
	}
}
