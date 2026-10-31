package net.runelite.client.plugins.microbot.dabrutus;

import java.util.Random;

/**
 * Per-account upgrade timing for bows, arrows and spells (user 2026-10-21): each upgrade is taken a few levels after it
 * unlocks, by an amount the persona sets separately for every item. So one account may get the Willow shortbow at 20
 * and keep iron arrows until 26, another wait for the bow and switch arrows at once, and a slow persona may skip a
 * spell altogether when the next one unlocks first. The starting item (Shortbow, bronze arrows, Wind Strike) is never
 * delayed, and 0% persona strength means no delay (as before 0.6.0).
 */
final class GearLag
{
	enum Kind
	{
		BOW(5), ARROW(8), SPELL(6);

		/** Most levels an upgrade can wait at full strength. */
		final int max;

		Kind(int max)
		{
			this.max = max;
		}
	}

	static final GearLag NONE = new GearLag(0, 0, 0);

	private final long seed;
	/** 0..1: persona strength times how unhurried the persona is. */
	private final double scale;

	private GearLag(long seed, double strength, double unhurried)
	{
		this.seed = seed;
		this.scale = Math.max(0, Math.min(1, strength * (0.5 + 0.8 * unhurried)));
	}

	static GearLag of(Persona p, int strengthPct)
	{
		double s = Math.max(0, Math.min(100, strengthPct)) / 100.0;
		return new GearLag(p.seed, s, 1 - p.trait(Persona.Trait.PACE));
	}

	/**
	 * Levels the item at {@code index} (its position in {@link RangedGear#BOWS}, {@link RangedGear#ARROWS} or
	 * {@link MagicGear#SPELLS}) waits after its requirement. Fixed per account and item.
	 */
	int levels(Kind kind, int index)
	{
		if (scale == 0)
		{
			return 0;
		}
		Random r = new Random(seed ^ (0x9E3779B97F4A7C15L * (kind.ordinal() * 64 + index + 1)));
		r.nextInt();
		return (int) Math.round(scale * kind.max * r.nextDouble());
	}

	/** The level the item is first used at: its requirement plus the wait; the starting item is never delayed. */
	int usedFrom(Kind kind, int index, int requirement, boolean starter)
	{
		return starter ? requirement : requirement + levels(kind, index);
	}

	/** Average wait in levels over {@code count} items, for the setup window. */
	double average(Kind kind, int count)
	{
		double sum = 0;
		for (int i = 0; i < count; i++)
		{
			sum += levels(kind, i);
		}
		return count == 0 ? 0 : sum / count;
	}
}
