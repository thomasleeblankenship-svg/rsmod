package net.runelite.client.plugins.microbot.util.combat.weapons;

import java.util.List;

/** Upstream Microbot 2.6.38 weapon range data (PoweredStaff), copied unchanged except an added @Override; used by {@code Rs2Combat.getAttackRange}. */
public class PoweredStaff extends Weapon
{
	public PoweredStaff(int id)
	{
		super(id);
		range = 7;
	}

	public PoweredStaff(List<Integer> weaponData)
	{
		super(weaponData);
	}


	@Override
	public int getRange(String attackStyle)
	{
		return attackStyle.equals("Defensive") ? range + longRangeModifier : range;
	}
}
