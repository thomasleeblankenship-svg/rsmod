package net.runelite.client.plugins.microbot.dabrutus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.runelite.api.Skill;
import org.junit.Test;

public class StyleBlocksArmourTest
{
	@Test
	public void blocksAre3To7Below50And1To3From50()
	{
		for (int roll = 0; roll < 50; roll++)
		{
			int low = StyleBlocks.blockSize(18, roll);
			int high = StyleBlocks.blockSize(50, roll);
			assertTrue(low >= 3 && low <= 7);
			assertTrue(high >= 1 && high <= 3);
		}
	}

	@Test
	public void keepsTheSkillUntilTheBlockEnds()
	{
		StyleBlocks b = new StyleBlocks();
		assertEquals(Skill.STRENGTH, b.pick(18, 18, 18));
		int until = b.until();
		assertTrue(until >= 21 && until <= 25);
		// Strength is no longer the lowest, but its block is not done.
		assertEquals(Skill.STRENGTH, b.pick(18, until - 1, 18));
		// Block done: the lowest (Attack, before Defence on a tie) is next.
		assertEquals(Skill.ATTACK, b.pick(18, until, 18));
	}

	@Test
	public void armourTierByDefence()
	{
		assertEquals("Iron", Armour.target(1).metal);
		assertEquals("Iron", Armour.target(19).metal);
		assertEquals("Mithril", Armour.target(20).metal);
		assertEquals("Adamant", Armour.target(30).metal);
		assertEquals("Rune", Armour.target(40).metal);
		assertEquals("Rune", Armour.target(99).metal);
		assertTrue(Armour.rank("Rune platebody") > Armour.rank("Adamant platebody"));
		assertEquals(0, Armour.rank("Steel platebody"));
		// Rune platebody needs Dragon Slayer I (user 2026-10-09): with it the platebody, without it the chainbody.
		assertEquals("Rune platebody", Armour.best(Armour.target(40).slots[1], true).name);
		assertEquals("Rune chainbody", Armour.best(Armour.target(40).slots[1], false).name);
		assertTrue(Armour.score("Rune platebody") > Armour.score("Rune chainbody"));
		assertTrue(Armour.score("Rune chainbody") > Armour.score("Adamant platebody"));
		assertEquals(0, Armour.score("Steel platebody"));
	}
}
