package net.runelite.client.plugins.microbot.dabrutus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import org.junit.Test;

public class MagicGearTest
{
	private static IntUnaryOperator bag(int... idAmount)
	{
		Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < idAmount.length; i += 2)
		{
			m.put(idAmount[i], idAmount[i + 1]);
		}
		return id -> m.getOrDefault(id, 0);
	}

	@Test
	public void catalyticIsMindBelow17ChaosFrom17()
	{
		assertEquals(MagicGear.MIND_RUNE, MagicGear.catalytic(16, false));
		assertEquals(MagicGear.CHAOS_RUNE, MagicGear.catalytic(17, false));
	}

	@Test
	public void bestSpellByLevel()
	{
		assertEquals("Wind Strike", MagicGear.best(1, false).name);
		assertEquals("Fire Strike", MagicGear.best(16, false).name);
		assertEquals("Water Bolt", MagicGear.best(28, false).name);
		assertEquals("Fire Bolt", MagicGear.best(70, false).name);
	}

	@Test
	public void fallsBackWhenAnElementRunsOut()
	{
		// Level 40, chaos runes but only 3 fire (Fire Bolt needs 4) and earth for Earth Bolt.
		IntUnaryOperator runes = bag(MagicGear.CHAOS_RUNE, 100, MagicGear.FIRE_RUNE, 3, MagicGear.EARTH_RUNE, 30);
		assertEquals("Earth Bolt", MagicGear.castable(40, false, runes).name);
		assertEquals(10, MagicGear.castable(40, false, runes).casts(runes));
		// Only chaos runes: Wind Bolt (the staff gives the air).
		assertEquals("Wind Bolt", MagicGear.castable(40, false, bag(MagicGear.CHAOS_RUNE, 5)).name);
	}

	@Test
	public void noCatalyticNoSpell()
	{
		assertNull(MagicGear.castable(40, false, bag(MagicGear.FIRE_RUNE, 400)));
		// Mind runes only work for the strikes, which a level 40 still knows.
		assertEquals("Wind Strike", MagicGear.castable(40, false, bag(MagicGear.MIND_RUNE, 9)).name);
	}

	@Test
	public void blastsOnlyWhenTurnedOn()
	{
		assertEquals("Fire Bolt", MagicGear.best(70, false).name);
		assertEquals("Fire Blast", MagicGear.best(70, true).name);
		assertEquals("Fire Bolt", MagicGear.best(40, true).name); // blasts need 41
		assertEquals(MagicGear.DEATH_RUNE, MagicGear.catalytic(41, true));
		assertEquals(MagicGear.CHAOS_RUNE, MagicGear.catalytic(41, false));
		IntUnaryOperator runes = bag(MagicGear.DEATH_RUNE, 50, MagicGear.CHAOS_RUNE, 50, MagicGear.FIRE_RUNE, 100);
		assertEquals("Fire Blast", MagicGear.castable(60, true, runes).name);
		assertEquals("Fire Bolt", MagicGear.castable(60, false, runes).name);
		// Out of death runes with blasts on: back to the bolts.
		assertEquals("Fire Bolt", MagicGear.castable(60, true, bag(MagicGear.CHAOS_RUNE, 5, MagicGear.FIRE_RUNE, 20)).name);
	}

	@Test
	public void elementRunesBatchByGold()
	{
		assertEquals(1500, MagicGear.elementBuy(0, 20_000));
		assertEquals(6500, MagicGear.elementBuy(800, 2_000_000));
		assertEquals(4000, MagicGear.elementBuy(0, 275_000)); // half way between 50k and 500k
		assertEquals(0, MagicGear.elementBuy(1500, 20_000));
		assertEquals(0, MagicGear.elementBuy(7000, 2_000_000));
	}

	@Test
	public void foodAndArrowsByGold()
	{
		assertEquals(150, GeBuyer.byGold(10_000, 150, 600));
		assertEquals(600, GeBuyer.byGold(500_000, 150, 600));
		assertEquals(350, GeBuyer.byGold(274_490, 150, 600)); // 374.5, rounded to 50
		assertEquals(1000, GeBuyer.byGold(50_000, 1000, 3000));
		assertEquals(2000, GeBuyer.byGold(275_000, 1000, 3000));
		assertEquals(3000, GeBuyer.byGold(900_000, 1000, 3000));
	}
}
