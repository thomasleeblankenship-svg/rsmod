package net.runelite.client.plugins.microbot.util.combat.weapons;

/** Upstream Microbot 2.6.38 weapon range data (Chinchompa), copied unchanged; used by {@code Rs2Combat.getAttackRange}. */
public class Chinchompa extends Weapon
{
	public Chinchompa(int id)
	{
		super(id);
		range = 9;
		longRangeModifier = 1;
	}
}
