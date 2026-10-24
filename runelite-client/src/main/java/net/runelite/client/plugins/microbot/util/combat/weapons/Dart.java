package net.runelite.client.plugins.microbot.util.combat.weapons;

import java.util.List;

/** Upstream Microbot 2.6.38 weapon range data (Dart), copied unchanged; used by {@code Rs2Combat.getAttackRange}. */
public class Dart extends Weapon
{
	public Dart(int id)
	{
		super(id);
		range = 3;
	}

	public Dart(List<Integer> weaponData)
	{
		super(weaponData);
	}
}
