package net.runelite.client.plugins.microbot.util.combat.weapons;

/** Upstream Microbot 2.6.38 weapon range data (Ballista), copied unchanged; used by {@code Rs2Combat.getAttackRange}. */
public class Ballista extends Weapon
{
	public Ballista(int id)
	{
		super(id);
		range = 9;
		longRangeModifier = 1;
	}
}
