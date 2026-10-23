/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/player/models/Rs2PlayerModel.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.player.models;

import net.runelite.api.Client;
import net.runelite.api.HeadIcon;
import net.runelite.api.Player;
import net.runelite.client.plugins.microbot.api.IEntity;
import net.runelite.client.plugins.microbot.api.actor.Rs2ActorModel;

/**
 * Snapshot of one player captured by {@code Rs2PlayerCache}.
 * <p>
 * Adaptation: upstream hops to the client thread on each relationship and overhead getter. Like
 * the other mobile models, these values are captured once on the client thread when the snapshot
 * is built. Upstream's {@code click} overloads always return {@code false}; following
 * {@code IEntity}, they are omitted so a call fails to compile. Sailing salvage detection and the
 * no-argument local-player constructor are not ported.
 */
public class Rs2PlayerModel extends Rs2ActorModel implements IEntity
{
	private final Player player;
	private final int id;
	private final int combatLevel;
	private final int animation;
	private final boolean friend;
	private final boolean clanMember;
	private final boolean friendsChatMember;
	private final int skullIcon;
	private final HeadIcon overheadIcon;

	/** Captures {@code player}; must be called on the client thread. */
	public Rs2PlayerModel(Client client, Player player)
	{
		super(client, player);
		this.player = player;
		this.id = player.getId();
		this.combatLevel = player.getCombatLevel();
		this.animation = player.getAnimation();
		this.friend = player.isFriend();
		this.clanMember = player.isClanMember();
		this.friendsChatMember = player.isFriendsChatMember();
		this.skullIcon = player.getSkullIcon();
		this.overheadIcon = player.getOverheadIcon();
	}

	/** The live player. Read it only on the client thread. */
	public Player getPlayer()
	{
		return player;
	}

	@Override
	public int getId()
	{
		return id;
	}

	public int getCombatLevel()
	{
		return combatLevel;
	}

	/** Animation at capture. */
	public int getAnimation()
	{
		return animation;
	}

	/** Whether this player is in your friends list. */
	public boolean isFriend()
	{
		return friend;
	}

	/** Whether this player is a member of your clan. */
	public boolean isClanMember()
	{
		return clanMember;
	}

	/** Whether this player is in your friends chat. */
	public boolean isFriendsChatMember()
	{
		return friendsChatMember;
	}

	/** Skull icon above this player's head, or -1 if none. */
	public int getSkullIcon()
	{
		return skullIcon;
	}

	/** Overhead prayer icon, or {@code null} if none. */
	public HeadIcon getOverheadIcon()
	{
		return overheadIcon;
	}
}
