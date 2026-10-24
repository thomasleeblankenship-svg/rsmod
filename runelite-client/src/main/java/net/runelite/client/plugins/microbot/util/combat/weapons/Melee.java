package net.runelite.client.plugins.microbot.util.combat.weapons;

import java.util.List;

/** Upstream Microbot 2.6.38 weapon range data (Melee), copied unchanged; used by {@code Rs2Combat.getAttackRange}. */
public class Melee extends Weapon
{
	public Melee(List<Integer> weaponData) { super(weaponData); }

	public Integer getSpecialAttackRange() { return this.getRange() + this.getLongRangeModifier(); }
}
