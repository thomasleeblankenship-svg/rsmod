package net.runelite.client.plugins.microbot.util.math;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Upstream sampler semantics; pure maths, no client. */
public class Rs2RandomTest
{
	@Test
	public void betweenIsHalfOpenAndAcceptsEitherOrder()
	{
		for (int i = 0; i < 2_000; i++)
		{
			int a = Rs2Random.between(3, 6);
			assertTrue(a >= 3 && a < 6);
			int b = Rs2Random.between(6, 3);
			assertTrue(b >= 3 && b < 6);
			int c = Rs2Random.between(-5, -3);
			assertTrue(c >= -5 && c < -3);
		}
		assertEquals(7, Rs2Random.between(7, 7));
	}

	@Test
	public void betweenInclusiveReachesBothEnds()
	{
		boolean low = false;
		boolean high = false;
		for (int i = 0; i < 2_000 && !(low && high); i++)
		{
			int v = Rs2Random.betweenInclusive(1, 3);
			assertTrue(v >= 1 && v <= 3);
			low |= v == 1;
			high |= v == 3;
		}
		assertTrue(low && high);
	}

	@Test(expected = IllegalArgumentException.class)
	public void betweenInclusiveRejectsReversedBounds()
	{
		Rs2Random.betweenInclusive(3, 1);
	}

	@Test
	public void gaussianIsFlooredAndNextIntStaysInRange()
	{
		for (int i = 0; i < 2_000; i++)
		{
			assertTrue(Rs2Random.randomGaussian(-100, 5) >= 0);
			int g = Rs2Random.nextInt(10, 20, 1, true);
			assertTrue(g >= 10 && g <= 20);
			int k = Rs2Random.nextInt(10, 20, 2.5, false);
			assertTrue(k >= 10 && k <= 20);
		}
	}

	@Test
	public void diceExtremes()
	{
		assertFalse(Rs2Random.dicePercentage(0));
		assertTrue(Rs2Random.dicePercentage(100));
		assertFalse(Rs2Random.diceFractional(0));
		assertTrue(Rs2Random.diceFractional(1));
	}
}
