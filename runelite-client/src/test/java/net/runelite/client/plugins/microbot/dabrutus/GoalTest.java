package net.runelite.client.plugins.microbot.dabrutus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.runelite.api.Skill;
import org.junit.Test;

public class GoalTest
{
	private static final Skill[] ADS = {Skill.ATTACK, Skill.DEFENCE, Skill.STRENGTH};

	@Test
	public void reachedPerMode()
	{
		Setup s = new Setup();
		s.attack = 40;
		s.strength = 45;
		s.defence = 30;
		s.ranged = 50;
		s.magic = 33;
		assertFalse(s.reached(DaBrutusConfig.CombatMode.MELEE, 40, 44, 30, 1, 1));
		assertTrue(s.reached(DaBrutusConfig.CombatMode.MELEE, 40, 45, 30, 1, 1));
		assertTrue(s.reached(DaBrutusConfig.CombatMode.RANGED, 1, 1, 1, 50, 1));
		assertFalse(s.reached(DaBrutusConfig.CombatMode.MAGIC, 99, 99, 99, 99, 32));
	}

	@Test
	public void combatAtGoal()
	{
		Setup s = new Setup();
		s.attack = 40;
		s.strength = 40;
		s.defence = 40;
		// A fresh account at 40/40/40 (Prayer 1) lands at Hitpoints ~40: 0.25 * (40 + 40) + 0.325 * 80 = 46.
		int c = Setup.combatAtGoal(DaBrutusConfig.CombatMode.MELEE, s, 1, 1, 1, 10, 1, 1, 1);
		assertTrue("combat " + c, c >= 45 && c <= 47);
		// Already past the goal: today's levels.
		assertEquals(net.runelite.api.Experience.getCombatLevel(60, 60, 60, 60, 1, 1, 43),
			Setup.combatAtGoal(DaBrutusConfig.CombatMode.MELEE, s, 60, 60, 60, 60, 1, 1, 43));
	}

	@Test
	public void strengthZeroIsTheOldRule()
	{
		// Before 0.6.0: always the lowest, ties Strength, Attack, Defence.
		assertEquals(Skill.STRENGTH, StyleBlocks.lowest(10, 10, 10));
		assertEquals(Skill.ATTACK, StyleBlocks.lowest(10, 11, 10));
		assertEquals(Skill.DEFENCE, StyleBlocks.lowest(12, 11, 10));
	}

	@Test
	public void personaOrderAndSlack()
	{
		int[] none = {99, 99, 99};
		// Order Attack, Defence, Strength; slack 2: Attack at 12 is within 2 of the lowest (10), so it goes first.
		assertEquals(Skill.ATTACK, StyleBlocks.choose(12, 10, 11, ADS, 2, none));
		// Slack 1: Attack is too far ahead; Defence (11) is within 1 of 10 and comes before Strength.
		assertEquals(Skill.DEFENCE, StyleBlocks.choose(12, 10, 11, ADS, 1, none));
		// Slack 0: the lowest only.
		assertEquals(Skill.STRENGTH, StyleBlocks.choose(12, 10, 11, ADS, 0, none));
	}

	@Test
	public void skillsAtTheirGoalAreSkipped()
	{
		// Strength is the lowest but already at its goal of 10.
		assertEquals(Skill.DEFENCE, StyleBlocks.choose(20, 10, 15, ADS, 0, new int[]{40, 10, 40}));
		// Every goal reached: the same rule over all three (persona order), so the rotation keeps working on "Keep
		// farming loot".
		assertEquals(Skill.ATTACK, StyleBlocks.choose(40, 40, 40, ADS, 0, new int[]{40, 40, 40}));
	}

	@Test
	public void blocksStopAtTheGoal()
	{
		StyleBlocks b = new StyleBlocks();
		b.configure(ADS, 0, new int[]{11, 99, 99});
		assertEquals(Skill.ATTACK, b.pick(10, 20, 20));
		assertEquals(11, b.until());
		// At the goal the block ends even though a 3-7 level block would carry on.
		assertEquals(Skill.DEFENCE, b.pick(11, 20, 12));
	}

	@Test
	public void everyPersonaReachesTheGoal()
	{
		int[] goal = {40, 45, 35};
		for (long seed = 0; seed < 1000; seed++)
		{
			Persona p = Persona.of(seed, 0);
			StyleBlocks b = new StyleBlocks();
			b.configure(p.styleOrder(), p.styleSlack(100), goal);
			int[] lv = {1, 1, 1};
			int maxGap = 0;
			for (int step = 0; step < 500 && (lv[0] < goal[0] || lv[1] < goal[1] || lv[2] < goal[2]); step++)
			{
				Skill s = b.pick(lv[0], lv[1], lv[2]);
				int i = s == Skill.ATTACK ? 0 : s == Skill.STRENGTH ? 1 : 2;
				assertTrue("seed " + seed + " trained " + s + " past its goal", lv[i] < goal[i]);
				lv[i]++;
				int lo = Math.min(lv[0], Math.min(lv[1], lv[2]));
				int hi = Math.max(lv[0], Math.max(lv[1], lv[2]));
				if (lv[0] < goal[0] && lv[1] < goal[1] && lv[2] < goal[2])
				{
					maxGap = Math.max(maxGap, hi - lo);
				}
			}
			assertTrue("seed " + seed + " ended at "
				+ lv[0] + "/" + lv[1] + "/" + lv[2], lv[0] == goal[0] && lv[1] == goal[1] && lv[2] == goal[2]);
			// Blocks are at most 7 levels and the slack at most 3: the skills never drift far apart.
			assertTrue("seed " + seed + " gap " + maxGap, maxGap <= 11);
		}
	}
}
