package net.runelite.client.plugins.microbot.util.combat.weapons;

/** Upstream Microbot 2.6.38 weapon range data (Longbow), copied unchanged; used by {@code Rs2Combat.getAttackRange}. */
public class Longbow extends Weapon
{
	public Longbow(int id)
	{
		super(id);
		range = 10;
		longRangeModifier = 0;
	}
}
