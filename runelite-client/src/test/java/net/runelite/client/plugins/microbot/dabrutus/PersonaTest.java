package net.runelite.client.plugins.microbot.dabrutus;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Skill;
import org.junit.Test;

public class PersonaTest
{
	@Test
	public void sameSeedSamePersona()
	{
		Persona a = Persona.of(12345L, 40);
		Persona b = Persona.of(12345L, 40);
		assertEquals(a.name, b.name);
		assertArrayEquals(a.styleOrder(), b.styleOrder());
		assertEquals(a.lampSkill(), b.lampSkill());
		for (Persona.Trait t : Persona.Trait.values())
		{
			assertEquals(a.trait(t), b.trait(t), 0.0);
		}
	}

	@Test
	public void accountsGetDifferentPersonas()
	{
		Set<String> looks = new HashSet<>();
		for (long account = 1; account <= 200; account++)
		{
			Persona p = Persona.of(Persona.seed(account, 42L), 0);
			StringBuilder sb = new StringBuilder();
			for (Persona.Trait t : Persona.Trait.values())
			{
				sb.append(Math.round(p.trait(t) * 20)).append(',');
			}
			looks.add(sb.toString());
		}
		assertEquals(200, looks.size());
		assertNotEquals(Persona.seed(1, 42), Persona.seed(2, 42));
		assertNotEquals(Persona.seed(1, 42), Persona.seed(1, 43));
	}

	@Test
	public void driftMovesButStaysInBounds()
	{
		for (long seed = 0; seed < 50; seed++)
		{
			Persona day0 = Persona.of(seed, 0);
			Persona later = Persona.of(seed, 30);
			boolean moved = false;
			for (Persona.Trait t : Persona.Trait.values())
			{
				moved |= day0.trait(t) != later.trait(t);
				for (int day : new int[]{1, 7, 100, 365, Persona.MAX_DAYS, Persona.MAX_DAYS + 500})
				{
					double v = Persona.of(seed, day).trait(t);
					assertTrue("seed " + seed + " day " + day + " " + t + "=" + v, v >= Persona.MIN && v <= Persona.MAX);
				}
			}
			assertTrue(moved);
			// The name, style order and lamp are part of who the persona is: drift never changes them.
			assertEquals(day0.name, later.name);
			assertArrayEquals(day0.styleOrder(), later.styleOrder());
		}
	}

	@Test
	public void dayToDayStepsAreSmall()
	{
		for (long seed = 0; seed < 20; seed++)
		{
			for (int day = 0; day < 120; day++)
			{
				Persona a = Persona.of(seed, day);
				Persona b = Persona.of(seed, day + 1);
				for (Persona.Trait t : Persona.Trait.values())
				{
					// a daily step plus at most one habit change (0.20)
					assertTrue("seed " + seed + " day " + day + " " + t, Math.abs(a.trait(t) - b.trait(t)) < 0.35);
				}
			}
		}
	}

	@Test
	public void styleOrderHasAllThreeSkills()
	{
		for (long seed = 0; seed < 30; seed++)
		{
			Set<Skill> s = new HashSet<>(java.util.Arrays.asList(Persona.of(seed, 0).styleOrder()));
			assertEquals(Set.of(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE), s);
		}
	}

	@Test
	public void slackGrowsWithStrength()
	{
		for (long seed = 0; seed < 30; seed++)
		{
			Persona p = Persona.of(seed, 0);
			assertEquals(0, p.styleSlack(0));
			assertTrue(p.styleSlack(100) >= 1 && p.styleSlack(100) <= 3);
			assertTrue(p.styleSlack(50) <= p.styleSlack(100));
		}
	}

	@Test
	public void rerollOncePerSevenDays()
	{
		assertTrue(Persona.canReroll(-1, 20_000));
		assertFalse(Persona.canReroll(20_000, 20_000));
		assertFalse(Persona.canReroll(20_000, 20_006));
		assertTrue(Persona.canReroll(20_000, 20_007));
		assertEquals(7, Persona.rerollIn(20_000, 20_000));
		assertEquals(1, Persona.rerollIn(20_000, 20_006));
		assertEquals(0, Persona.rerollIn(20_000, 20_007));
	}
}
