package net.runelite.client.plugins.microbot.util.combat.weapons;

import java.util.List;

/** Upstream Microbot 2.6.38 weapon range data (Crossbow), copied unchanged; used by {@code Rs2Combat.getAttackRange}. */
public class Crossbow extends Weapon
{
	public Crossbow(int id)
	{
		super(id);
		range = 7;
	}

	public Crossbow(List<Integer> weaponData)
	{
		super(weaponData);
	}
}
