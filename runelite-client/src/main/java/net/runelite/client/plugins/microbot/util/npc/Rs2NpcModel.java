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

	/**
	 * As upstream: NPCs whose name equals ({@code exact}) or contains one of {@code names}, ignoring
	 * case. Adaptation: upstream compares the contains case with the name not lower-cased.
	 */
	public static java.util.function.Predicate<Rs2NpcModel> matches(boolean exact, String... names)
	{
		return npc ->
		{
			String name = npc.getName();
			if (name == null || names == null)
			{
				return false;
			}
			String lower = name.toLowerCase();
			return java.util.Arrays.stream(names).filter(java.util.Objects::nonNull)
				.anyMatch(s -> exact ? name.equalsIgnoreCase(s) : lower.contains(s.toLowerCase()));
		};
	}

	/** As upstream: local distance to the player, {@code Integer.MAX_VALUE} when unknown. */
	public int getDistanceFromPlayer()
	{
		return MobileActions.read(() ->
		{
			net.runelite.api.Player local = net.runelite.client.plugins.microbot.Microbot.getClient().getLocalPlayer();
			net.runelite.api.coords.LocalPoint mine = local == null ? null : local.getLocalLocation();
			net.runelite.api.coords.LocalPoint theirs = npc.getLocalLocation();
			return mine == null || theirs == null ? Integer.MAX_VALUE : theirs.distanceTo(mine);
		}, Integer.MAX_VALUE);
	}

	/** As upstream: within {@code maxDistance} local units of the player. */
	public boolean isWithinDistanceFromPlayer(int maxDistance)
	{
		return getDistanceFromPlayer() <= maxDistance;
	}

	/** As upstream: within {@code maxDistance} tiles of {@code anchor}. */
	public boolean isWithinDistance(net.runelite.api.coords.WorldPoint anchor, int maxDistance)
	{
		net.runelite.api.coords.WorldPoint location = anchor == null ? null : getWorldLocation();
		return location != null && location.distanceTo(anchor) <= maxDistance;
	}

	/** As upstream: health bar ratio over scale as a percentage, -1 without a scale. */
	public double getHealthPercentage()
	{
		int scale = getHealthScale();
		return scale == 0 ? -1 : (double) getHealthRatio() / scale * 100.0;
	}

	/** As upstream: the NPC is interacting with the local player. */
	public boolean isInteractingWithPlayer()
	{
		return MobileActions.read(() -> npc.getInteracting() == net.runelite.client.plugins.microbot.Microbot.getClient().getLocalPlayer(), false);
	}

	/** As upstream: the NPC's pose is not its idle pose. */
	public boolean isMoving()
	{
		return MobileActions.read(() -> npc.getPoseAnimation() != npc.getIdlePoseAnimation(), false);
	}

	/** As upstream: the first overhead prayer icon shown, or {@code null}. */
	public net.runelite.api.HeadIcon getHeadIcon()
	{
		short[] sprites = MobileActions.read(npc::getOverheadSpriteIds, null);
		if (sprites == null)
		{
			return null;
		}
		for (short sprite : sprites)
		{
			if (sprite >= 0 && sprite < net.runelite.api.HeadIcon.values().length)
			{
				return net.runelite.api.HeadIcon.values()[sprite];
			}
		}
		return null;
	}

	/** As upstream: the wrapped live NPC (same as {@link #getNpc}); read it on the client thread. */
	public NPC getRuneliteNpc()
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
