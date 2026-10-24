package net.runelite.client.plugins.microbot.util.combat.weapons;

/** Upstream Microbot 2.6.38 weapon range data (Halberd), copied unchanged; used by {@code Rs2Combat.getAttackRange}. */
public class Halberd extends Weapon
{
	public Halberd(int id)
	{
		super(id);
		range = 2;
		longRangeModifier = 0;
	}
}
