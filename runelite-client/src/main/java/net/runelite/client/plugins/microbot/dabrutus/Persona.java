package net.runelite.client.plugins.microbot.dabrutus;

import java.util.Random;
import net.runelite.api.Skill;

/**
 * A per-account playstyle (plan 2026-10-21): traits in 0..1 made from a seed, so every account trains its own way. The
 * traits drift a little each real day (pulled back toward the baseline) and every 2-5 weeks one trait shifts for good.
 * Everything here is a pure function of the seed and the day number, so nothing but the seed and the birth day is saved.
 */
final class Persona
{
	/** Shown on the setup window's Persona tab, in this order: name, low end, high end. */
	enum Trait
	{
		PACE("Pace", "Slow", "Brisk"),
		PATIENCE("Patience", "Restless", "Patient"),
		CAMERA("Camera use", "Still", "Busy"),
		IDLE("Idle habit", "Watches", "Fidgets"),
		EATING("Eats", "Late", "Early"),
		RISK("Risk", "Careful", "Bold"),
		TIDINESS("Bank tidiness", "Messy", "Tidy"),
		RHYTHM("Session rhythm", "Short bursts", "Long sittings");

		final String label;
		final String low;
		final String high;

		Trait(String label, String low, String high)
		{
			this.label = label;
			this.low = low;
			this.high = high;
		}

		String describe(double v)
		{
			return v < 0.5 ? low : high;
		}
	}

	static final double MIN = 0.05;
	static final double MAX = 0.95;
	/** Drift is worked out day by day from birth; ten years is plenty. */
	static final int MAX_DAYS = 3650;
	/** Days between persona re-rolls. */
	static final int REROLL_DAYS = 7;

	private static final String[] ADJECTIVES = {"Methodical", "Restless", "Thrifty", "Steady", "Scrappy", "Patient",
		"Wandering", "Stubborn", "Careful", "Eager", "Quiet", "Brash"};
	private static final String[] NOUNS = {"Tank", "Brawler", "Farmhand", "Drover", "Duelist", "Herder", "Grinder",
		"Squire", "Cowhand", "Rancher"};
	private static final Skill[] LAMP_SKILLS = {Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS, Skill.PRAYER};

	final long seed;
	final String name;
	/** Days since the persona was made. */
	final int day;
	private final double[] baseline;
	private final double[] traits;
	private final Skill[] styleOrder;
	private final Skill lampSkill;

	private Persona(long seed, int day)
	{
		this.seed = seed;
		this.day = Math.max(0, day);
		Random r = new Random(seed);
		int n = Trait.values().length;
		baseline = new double[n];
		for (int i = 0; i < n; i++)
		{
			baseline[i] = 0.12 + r.nextDouble() * 0.76;
		}
		name = "The " + ADJECTIVES[r.nextInt(ADJECTIVES.length)] + " " + NOUNS[r.nextInt(NOUNS.length)];
		styleOrder = shuffled(new Skill[]{Skill.STRENGTH, Skill.ATTACK, Skill.DEFENCE}, r);
		lampSkill = LAMP_SKILLS[r.nextInt(LAMP_SKILLS.length)];
		traits = drift(seed, baseline, Math.min(this.day, MAX_DAYS));
	}

	/** The persona {@code day} days after it was made. */
	static Persona of(long seed, int day)
	{
		return new Persona(seed, day);
	}

	/** One account hash and one saved salt give one seed (SplitMix64 finaliser, so close inputs land far apart). */
	static long seed(long accountHash, long salt)
	{
		long z = accountHash ^ Long.rotateLeft(salt, 29) ^ 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** Whether a re-roll is allowed today; {@code lastReroll} is an epoch day, or a negative number for never. */
	static boolean canReroll(long lastReroll, long today)
	{
		return lastReroll < 0 || today - lastReroll >= REROLL_DAYS;
	}

	/** Days until the next re-roll is allowed (0 = now). */
	static long rerollIn(long lastReroll, long today)
	{
		return canReroll(lastReroll, today) ? 0 : REROLL_DAYS - (today - lastReroll);
	}

	double trait(Trait t)
	{
		return traits[t.ordinal()];
	}

	double baseline(Trait t)
	{
		return baseline[t.ordinal()];
	}

	/** Tie-break order for the Attack/Strength/Defence rotation. */
	Skill[] styleOrder()
	{
		return styleOrder.clone();
	}

	/** The skill this persona gives the Ides of Milk lamp. */
	Skill lampSkill()
	{
		return lampSkill;
	}

	/**
	 * How many levels above the lowest melee skill another skill may still be picked for the next block: 0 at strength 0
	 * (always the lowest, as before 0.6.0), up to 3 for a restless persona at full strength.
	 */
	int styleSlack(int strengthPct)
	{
		double s = Math.max(0, Math.min(100, strengthPct)) / 100.0;
		return (int) Math.round(s * (1 + 2 * (1 - trait(Trait.PATIENCE))));
	}

	/**
	 * Day-by-day drift: each day every trait takes a small random step and is pulled 15% back toward its baseline; at
	 * random 14-35 day intervals one baseline moves 0.08-0.20 for good (a new habit).
	 */
	static double[] drift(long seed, double[] start, int days)
	{
		double[] base = start.clone();
		double[] offset = new double[base.length];
		Random r = new Random(seed * 31 + 7);
		int nextHabit = 14 + r.nextInt(22);
		for (int d = 1; d <= days; d++)
		{
			for (int i = 0; i < base.length; i++)
			{
				offset[i] = offset[i] * 0.85 + r.nextGaussian() * 0.03;
			}
			if (d == nextHabit)
			{
				int i = r.nextInt(base.length);
				double step = (0.08 + r.nextDouble() * 0.12) * (r.nextBoolean() ? 1 : -1);
				base[i] = clamp(base[i] + step);
				nextHabit = d + 14 + r.nextInt(22);
			}
		}
		double[] out = new double[base.length];
		for (int i = 0; i < base.length; i++)
		{
			out[i] = clamp(base[i] + offset[i]);
		}
		return out;
	}

	private static double clamp(double v)
	{
		return Math.max(MIN, Math.min(MAX, v));
	}

	private static Skill[] shuffled(Skill[] a, Random r)
	{
		for (int i = a.length - 1; i > 0; i--)
		{
			int j = r.nextInt(i + 1);
			Skill t = a[i];
			a[i] = a[j];
			a[j] = t;
		}
		return a;
	}
}
