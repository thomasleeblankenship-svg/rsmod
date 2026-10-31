package net.runelite.client.plugins.microbot.dabrutus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import org.junit.Test;

/** Supplies, GE batches and upgrade timing differ per account (user 2026-10-21) and stay sane. */
public class PerAccountTest
{
	@After
	public void reset()
	{
		RangedGear.lag = GearLag.NONE;
		MagicGear.lag = GearLag.NONE;
	}

	@Test
	public void strengthZeroKeepsTheSettings()
	{
		for (Amounts.Kind k : Amounts.Kind.values())
		{
			assertEquals(16, Amounts.perTrip(16, 1.3, 0.9, k, 0, true, 1, 26));
			assertEquals(1_437, Amounts.batch(1_437, 0.7, -0.8, k, 0, true, 1));
		}
		GearLag none = GearLag.of(Persona.of(5, 0), 0);
		for (int i = 0; i < 5; i++)
		{
			assertEquals(0, none.levels(GearLag.Kind.ARROW, i));
		}
	}

	@Test
	public void perTripStaysInRangeAndIsFixedForATrip()
	{
		for (long seed = 0; seed < 300; seed++)
		{
			for (int trip = 1; trip < 40; trip++)
			{
				double f = Amounts.factor(seed, Amounts.Kind.FOOD, false);
				int food = Amounts.perTrip(16, f, Amounts.roll(seed, trip, Amounts.Kind.FOOD, false), Amounts.Kind.FOOD,
					100, false, 1, 26);
				// 16 * 0.90 * 0.92 = 13.2 up to 16 * 1.10 * 1.08 = 19.0
				assertTrue("seed " + seed + " food " + food, food >= 13 && food <= 19);
				assertEquals(Amounts.roll(seed, trip, Amounts.Kind.FOOD, false),
					Amounts.roll(seed, trip, Amounts.Kind.FOOD, false), 0.0);
				int arrows = Amounts.perTrip(300, Amounts.factor(seed, Amounts.Kind.ARROWS, false),
					Amounts.roll(seed, trip, Amounts.Kind.ARROWS, false), Amounts.Kind.ARROWS, 100, true, 50, 5000);
				assertTrue("arrows " + arrows, arrows >= 200 && arrows <= 450 && arrows % 25 == 0);
			}
		}
	}

	@Test
	public void accountsBuyDifferentBatches()
	{
		Set<Integer> sizes = new HashSet<>();
		for (long seed = 0; seed < 100; seed++)
		{
			int q = Amounts.batch(4_000, Amounts.factor(seed, Amounts.Kind.ELEMENTS, true),
				Amounts.roll(seed, 3, Amounts.Kind.ELEMENTS, true), Amounts.Kind.ELEMENTS, 100, false, 750);
			assertTrue("batch " + q, q >= 750 && q <= 4_000 * 1.4 * 1.15 + 1);
			sizes.add(q);
		}
		assertTrue("only " + sizes.size() + " different batch sizes", sizes.size() > 80);
		// A tidy persona buys round numbers.
		int tidy = Amounts.batch(4_000, 1.13, 0.3, Amounts.Kind.ELEMENTS, 100, true, 750);
		assertEquals(0, tidy % 100);
	}

	@Test
	public void upgradesWaitPerItemAndNeverBlockTheStarter()
	{
		boolean bowAndArrowsDiffer = false;
		for (long seed = 0; seed < 200; seed++)
		{
			GearLag lag = GearLag.of(Persona.of(seed, 0), 100);
			RangedGear.lag = lag;
			MagicGear.lag = lag;
			// The starting items are always there.
			assertEquals("Shortbow", RangedGear.bestBow(1).name);
			// Bronze always; iron (also level 1) only once this account's wait for it is over.
			int tier = RangedGear.bestArrowTier(RangedGear.bestBow(1), 1);
			assertEquals(lag.levels(GearLag.Kind.ARROW, 1) == 0 ? 1 : 0, tier);
			assertEquals("Wind Strike", MagicGear.best(1, false).name);
			// Never more than the kind's most levels late.
			assertEquals("Willow shortbow", RangedGear.bestBow(20 + GearLag.Kind.BOW.max).name);
			assertEquals("Maple shortbow", RangedGear.bestBow(30 + GearLag.Kind.BOW.max).name);
			assertEquals("Fire Bolt", MagicGear.best(35 + GearLag.Kind.SPELL.max, false).name);
			// A new bow can keep the old arrows: the waits are separate.
			bowAndArrowsDiffer |= lag.levels(GearLag.Kind.BOW, 1) != lag.levels(GearLag.Kind.ARROW, 3);
			// The catalytic rune follows the spell actually used.
			for (int level = 1; level <= 60; level++)
			{
				assertEquals(MagicGear.best(level, false).catalytic, MagicGear.catalytic(level, false));
			}
		}
		assertTrue(bowAndArrowsDiffer);
	}
}
