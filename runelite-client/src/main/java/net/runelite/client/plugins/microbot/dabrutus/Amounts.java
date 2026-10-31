package net.runelite.client.plugins.microbot.dabrutus;

import java.util.Random;

/**
 * Per-account supply amounts (user 2026-10-21): each persona carries its own share of the per-trip settings (food,
 * arrows, casts) and buys its own GE batch sizes, with a little variation from trip to trip. The settings stay the
 * centre; the persona strength scales how far it strays (0% = exactly the settings, as before 0.6.0). A value is fixed
 * for the whole trip, so the bank, the leave checks and the GE plan all see the same number.
 */
final class Amounts
{
	enum Kind
	{
		FOOD(0.90, 1.10, 0.08),
		ARROWS(0.75, 1.35, 0.10),
		CASTS(0.75, 1.35, 0.10),
		/** Mind, chaos or death runes ("Casts per GE buy"). */
		RUNES(0.70, 1.40, 0.15),
		/** Water, earth and fire runes. */
		ELEMENTS(0.70, 1.40, 0.15);

		final double low;
		final double high;
		/** Trip-to-trip variation at full strength, as a fraction. */
		final double jitter;

		Kind(double low, double high, double jitter)
		{
			this.low = low;
			this.high = high;
			this.jitter = jitter;
		}
	}

	private Amounts()
	{
	}

	/** This account's lasting share for {@code kind}: per-trip carry ({@code buy} false) or GE batch ({@code buy} true). */
	static double factor(long seed, Kind kind, boolean buy)
	{
		Random r = new Random(seed ^ (0x5DEECE66DL * (kind.ordinal() + 1)) ^ (buy ? 0x2545F4914F6CDD1DL : 0));
		r.nextDouble();
		if (kind == Kind.FOOD && buy)
		{
			return 0.70 + r.nextDouble() * 0.70;
		}
		return kind.low + r.nextDouble() * (kind.high - kind.low);
	}

	/** A number in -1..1 that stays the same for one trip. */
	static double roll(long seed, int trip, Kind kind, boolean buy)
	{
		Random r = new Random(seed * 1_000_003L + trip * 7_919L + kind.ordinal() * 31L + (buy ? 17 : 0));
		return r.nextDouble() * 2 - 1;
	}

	/**
	 * The amount for one trip: {@code base} (the setting) moved by the account's share and this trip's roll, both scaled
	 * by the persona strength, then rounded the persona's way and kept within {@code min..max}.
	 */
	static int perTrip(int base, double factor, double roll, Kind kind, int strengthPct, boolean tidy, int min, int max)
	{
		double s = strength(strengthPct);
		double v = base * (1 + (factor - 1) * s) * (1 + roll * kind.jitter * s);
		int step = kind == Kind.FOOD || s == 0 || !tidy ? 1 : base >= 500 ? 50 : 25;
		return clamp(round(v, step), min, max);
	}

	/**
	 * One GE batch: {@code base} (the gold-scaled size, or "Casts per GE buy") moved the same way. A tidy persona buys
	 * round numbers (50s, or 100s from 1,000), a messy one exact ones. Never below {@code min}.
	 */
	static int batch(int base, double factor, double roll, Kind kind, int strengthPct, boolean tidy, int min)
	{
		double s = strength(strengthPct);
		double v = base * (1 + (factor - 1) * s) * (1 + roll * kind.jitter * s);
		int step = s == 0 ? 1 : tidy ? (v >= 1_000 ? 100 : 50) : 1;
		return Math.max(min, round(v, step));
	}

	private static double strength(int pct)
	{
		return Math.max(0, Math.min(100, pct)) / 100.0;
	}

	private static int round(double v, int step)
	{
		return (int) Math.max(step, Math.round(v / step) * step);
	}

	private static int clamp(int v, int min, int max)
	{
		return Math.max(min, Math.min(max, v));
	}
}
