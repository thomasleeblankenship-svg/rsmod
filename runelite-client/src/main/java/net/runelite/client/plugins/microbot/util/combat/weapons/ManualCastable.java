package net.runelite.client.plugins.microbot.util.combat.weapons;

import lombok.extern.slf4j.Slf4j;

/** Upstream Microbot 2.6.38 weapon range data (ManualCastable), copied unchanged; used by {@code Rs2Combat.getAttackRange}. */
@Slf4j
public class ManualCastable extends Weapon
{
	public ManualCastable(int id)
	{
		super(id);
	}

	public int getRange(String attackStyle, boolean showWhileManualCasting)
	{
		// Only display while auto-casting, unless user wants to see it always
		return attackStyle.equals("Casting") || showWhileManualCasting ? range : 0;
	}
}
