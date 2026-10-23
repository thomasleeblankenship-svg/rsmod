package net.runelite.client.plugins.microbot.util.math;

import java.util.concurrent.ThreadLocalRandom;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;

/**
 * Random numbers and randomised waits used by scripts. Timing variation only; provides no
 * detection-avoidance guarantee.
 * <p>
 * The samplers ({@code between}, {@code betweenInclusive}, {@code randomGaussian},
 * {@code nextInt}, {@code dice*}) follow chsami/Microbot {@code util/math/Rs2Random.java} at
 * {@code a2114401bbbc35e9762db9e8aa666d70ffcd24d8} (BSD 2-Clause). Waits are mobile: bounded,
 * cancellable, never on the client thread. Not ported: the skewed {@code EWaitDir} waits,
 * mouse-point randomisation and the other anti-ban distributions.
 */
public final class Rs2Random
{
	private Rs2Random() { }

	/** As upstream: uniform in {@code [min(a, b), max(a, b))}; either order is accepted. */
	public static int between(int minimum, int maximum)
	{
		int n = Math.abs(maximum - minimum);
		return Math.min(minimum, maximum) + (n == 0 ? 0 : ThreadLocalRandom.current().nextInt(n));
	}

	/** As upstream: uniform in {@code [min, max]}; throws when {@code min > max}. */
	public static int betweenInclusive(int min, int max)
	{
		if (min > max)
		{
			throw new IllegalArgumentException("min must be <= max");
		}
		return ThreadLocalRandom.current().nextInt(min, max + 1);
	}

	/** As upstream: a normal sample (Marsaglia polar method), floored at zero. */
	public static int randomGaussian(double mean, double stddev)
	{
		double u;
		double v;
		double s;
		do
		{
			u = 2.0 * ThreadLocalRandom.current().nextDouble() - 1.0;
			v = 2.0 * ThreadLocalRandom.current().nextDouble() - 1.0;
			s = u * u + v * v;
		}
		while (s >= 1 || s == 0);
		int value = (int) (mean + stddev * u * Math.sqrt(-2.0 * Math.log(s) / s));
		return Math.max(0, value);
	}

	/** As upstream: Gaussian centred in the range when {@code useGaussian}, else skewed uniform. */
	public static int nextInt(int min, int max, double skewFactor, boolean useGaussian)
	{
		if (min > max)
		{
			throw new IllegalArgumentException("Min cannot be greater than Max.");
		}
		if (useGaussian)
		{
			double mean = (max + min) / 2.0;
			double deviation = (max - min) / 6.0;
			double sample = mean + ThreadLocalRandom.current().nextGaussian() * deviation;
			return (int) Math.round(Math.max(min, Math.min(max, sample)));
		}
		double raw = ThreadLocalRandom.current().nextDouble();
		if (skewFactor != 1)
		{
			raw = Math.pow(raw, skewFactor);
		}
		return (int) Math.round(min + raw * (max - min));
	}

	public static boolean diceFractional(double fractionalChance)
	{
		return ThreadLocalRandom.current().nextDouble() < fractionalChance;
	}

	public static boolean dicePercentage(double percentageChance)
	{
		return ThreadLocalRandom.current().nextDouble() * 100 < percentageChance;
	}

	/** Mobile: a uniform wait in {@code [min, max)}, cancellable. Upstream skews toward {@code min}. */
	public static void wait(int min, int max)
	{
		Global.sleep(between(min, max));
	}

	/** A normally distributed wait, cancellable, never on the client thread. */
	public static void waitEx(double mean, double deviation)
	{
		waitEx((int) Math.round(mean), (int) Math.round(deviation));
	}

	public static void waitEx(int mean, int deviation)
	{
		if (Microbot.getClient() == null || Microbot.getClient().isClientThread()) { return; }
		long remaining = Math.max(0, Math.round(mean + ThreadLocalRandom.current().nextGaussian() * Math.max(0, deviation)));
		while (remaining > 0 && MobileActions.active() && !Thread.currentThread().isInterrupted())
		{
			long slice = Math.min(remaining, 50);
			try { Thread.sleep(slice); }
			catch (InterruptedException exception) { Thread.currentThread().interrupt(); return; }
			remaining -= slice;
		}
	}
}
