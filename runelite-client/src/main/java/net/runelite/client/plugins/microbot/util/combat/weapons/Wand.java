package net.runelite.client.plugins.microbot.util.combat.weapons;

/** Upstream Microbot 2.6.38 weapon range data (Wand), copied unchanged; used by {@code Rs2Combat.getAttackRange}. */
public class Wand extends ManualCastable
{
	public Wand(int id)
	{
		super(id);
		range = 10;
	}
}
