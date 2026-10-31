package net.runelite.client.plugins.microbot.dabrutus;

import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.Skill;

/**
 * Trains Attack, Strength and Defence in blocks (user 2026-10-03): the lowest skill is trained 3-7 levels before
 * switching; once that skill is level 50+, blocks are 1-3 levels. The block size is picked at random each switch.
 * 0.6.0: the persona sets the tie-break order and a slack (a skill up to that many levels above the lowest may be
 * picked), and skills already at their goal are skipped; a block never runs past the goal.
 */
final class StyleBlocks
{
	private static final Skill[] DEFAULT_ORDER = {Skill.STRENGTH, Skill.ATTACK, Skill.DEFENCE};

	private Skill skill;
	private int until;
	private Skill[] order = DEFAULT_ORDER;
	private int slack;
	/** Attack, Strength, Defence goals (99 = none). */
	private int[] targets = {99, 99, 99};

	/** Persona order, slack in levels, and the Attack/Strength/Defence goals. */
	synchronized void configure(Skill[] order, int slack, int[] targets)
	{
		this.order = order == null || order.length != 3 ? DEFAULT_ORDER : order.clone();
		this.slack = Math.max(0, slack);
		this.targets = targets == null || targets.length != 3 ? new int[]{99, 99, 99} : targets.clone();
	}

	/** The skill to train now; picks a new block once the current one reached its level. */
	synchronized Skill pick(int attack, int strength, int defence)
	{
		if (skill != null && level(skill, attack, strength, defence) < Math.min(until, target(skill)))
		{
			return skill;
		}
		skill = choose(attack, strength, defence, order, slack, targets);
		int lvl = level(skill, attack, strength, defence);
		int cap = target(skill) > lvl ? target(skill) : 99;
		until = Math.min(cap, lvl + blockSize(lvl, ThreadLocalRandom.current().nextInt(1_000)));
		return skill;
	}

	/** The level the current block ends at (0 before the first pick). */
	synchronized int until()
	{
		return until;
	}

	synchronized void reset()
	{
		skill = null;
		until = 0;
	}

	/** Below 50: 3-7 levels; 50+: 1-3 levels. {@code roll} is any non-negative number. */
	static int blockSize(int level, int roll)
	{
		return level >= 50 ? 1 + roll % 3 : 3 + roll % 5;
	}

	/** The lowest of the three; ties go Strength, then Attack, then Defence. */
	static Skill lowest(int attack, int strength, int defence)
	{
		return choose(attack, strength, defence, DEFAULT_ORDER, 0, new int[]{99, 99, 99});
	}

	/**
	 * The first skill in {@code order} that is below its goal and at most {@code slack} levels above the lowest such
	 * skill. With every goal reached, the same rule over all three.
	 */
	static Skill choose(int attack, int strength, int defence, Skill[] order, int slack, int[] targets)
	{
		boolean any = false;
		int low = Integer.MAX_VALUE;
		for (Skill s : order)
		{
			int lvl = level(s, attack, strength, defence);
			if (lvl < goal(s, targets))
			{
				any = true;
				low = Math.min(low, lvl);
			}
		}
		if (!any)
		{
			for (Skill s : order)
			{
				low = Math.min(low, level(s, attack, strength, defence));
			}
		}
		for (Skill s : order)
		{
			int lvl = level(s, attack, strength, defence);
			if ((!any || lvl < goal(s, targets)) && lvl <= low + slack)
			{
				return s;
			}
		}
		return order[0];
	}

	private int target(Skill s)
	{
		return goal(s, targets);
	}

	private static int goal(Skill s, int[] targets)
	{
		return s == Skill.ATTACK ? targets[0] : s == Skill.STRENGTH ? targets[1] : targets[2];
	}

	private static int level(Skill s, int attack, int strength, int defence)
	{
		return s == Skill.STRENGTH ? strength : s == Skill.ATTACK ? attack : defence;
	}
}
