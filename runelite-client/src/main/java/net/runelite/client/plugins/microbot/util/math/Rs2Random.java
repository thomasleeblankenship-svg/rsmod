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
 * {@code nextInt}, {@code dice*}, {@code gaussRand}, {@code truncatedGauss}, {@code skewedRand},
 * {@code normalRange},
 * {@code logNormalBounded}, {@code fancyNormalSample}, {@code truncatedNormalSample}) follow
 * chsami/Microbot {@code util/math/Rs2Random.java} at
 * {@code a2114401bbbc35e9762db9e8aa666d70ffcd24d8} (BSD 2-Clause); they only return numbers
 * (scripts use them for thresholds and wait lengths) and move no input. Waits are mobile: bounded,
 * cancellable, never on the client thread. Not ported: the skewed {@code EWaitDir} waits and
 * mouse-point randomisation.
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

	/** Upstream's default cutoff (in standard deviations) for {@link #truncatedGauss}. */
	private static final double GAUSS_CUTOFF = 4;

	/** As upstream: a uniform double in {@code (0, 1)}, never exactly zero. */
	public static double nzRandom()
	{
		return Math.max(ThreadLocalRandom.current().nextDouble(), 1.0e-320);
	}

	/** As upstream: a normal sample with this mean and standard deviation (Box-Muller). */
	public static double gaussRand(double mean, double dev)
	{
		double len = dev * Math.sqrt(-2 * Math.log(nzRandom()));
		return mean + len * Math.cos(2 * Math.PI * ThreadLocalRandom.current().nextDouble());
	}

	/**
	 * As upstream: a half-normal sample scaled into {@code [left, right)}, its tail cut at
	 * {@code cutoff} deviations (4 when {@code cutoff <= 0}), so values cluster near {@code left}.
	 */
	public static double truncatedGauss(double left, double right, double cutoff)
	{
		double limit = cutoff <= 0 ? GAUSS_CUTOFF : cutoff;
		double result;
		do
		{
			result = Math.abs(Math.sqrt(-2 * Math.log(nzRandom())) * Math.cos(2 * Math.PI * ThreadLocalRandom.current().nextDouble()));
		}
		while (result >= limit);
		return result / limit * (right - left) + left;
	}

	/**
	 * As upstream: a sample in {@code [lo, hi]} clustered around {@code mode}: a half-normal tail
	 * (cut at {@code cutoff} deviations, 4 when {@code cutoff <= 0}) towards one bound, the side
	 * picked in proportion to its width.
	 */
	public static double skewedRand(double mode, double lo, double hi, double cutoff)
	{
		double limit = cutoff <= 0 ? GAUSS_CUTOFF : cutoff;
		double top = ThreadLocalRandom.current().nextDouble() * (hi - lo) > mode - lo ? hi : lo;
		double result;
		do
		{
			result = Math.abs(Math.sqrt(-2 * Math.log(nzRandom())) * Math.cos(2 * Math.PI * ThreadLocalRandom.current().nextDouble()));
		}
		while (result >= limit);
		return result / limit * (top - mode) + mode;
	}

	/** As upstream: {@link #skewedRand(double, double, double, double)} rounded. */
	public static long skewedRand(long mode, long lo, long hi, double cutoff)
	{
		return Math.round(skewedRand((double) mode, (double) lo, (double) hi, cutoff));
	}

	/** As upstream: a sample in {@code [min, max]} clustered around the midpoint. */
	public static double normalRange(double min, double max, double cutoff)
	{
		double limit = cutoff <= 0 ? GAUSS_CUTOFF : cutoff;
		double offset = truncatedGauss(0, (max - min) / 2, limit);
		return (max + min) / 2.0 + (ThreadLocalRandom.current().nextBoolean() ? offset : -offset);
	}

	/** As upstream: {@link #normalRange(double, double, double)} rounded. */
	public static long normalRange(long min, long max, double cutoff)
	{
		return Math.round(normalRange((double) min, (double) max, cutoff));
	}

	/** As upstream: {@link #truncatedGauss(double, double, double)} rounded. */
	public static long truncatedGauss(long left, long right, double cutoff)
	{
		return Math.round(truncatedGauss((double) left, (double) right, cutoff));
	}

	/**
	 * As upstream: a right-skewed log-normal sample in {@code [min, max]}, its median the geometric
	 * mean of the bounds and about 95% of samples inside before the tails are clamped.
	 */
	public static int logNormalBounded(int min, int max)
	{
		if (max <= min)
		{
			return Math.max(min, max);
		}
		int low = Math.max(1, min);
		double logMin = Math.log(low);
		double logMax = Math.log(max);
		double mu = (logMin + logMax) / 2.0;
		double sigma = (logMax - logMin) / 3.92;
		double sample = Math.exp(mu + sigma * ThreadLocalRandom.current().nextGaussian());
		if (sample < low)
		{
			return low;
		}
		return sample > max ? max : (int) sample;
	}

	/**
	 * As upstream: a truncated normal sample in the range around one of two means (at 33% and 66%
	 * of it), the lower picked with probability 0.8.
	 */
	public static double fancyNormalSample(double lowerBound, double upperBound)
	{
		double mean = ThreadLocalRandom.current().nextDouble() < 0.8
			? lowerBound + (upperBound - lowerBound) * 0.33
			: lowerBound + (upperBound - lowerBound) * 0.66;
		return truncatedNormalSample(lowerBound, upperBound, mean, null);
	}

	/** As upstream: {@link #fancyNormalSample(double, double)} rounded and kept inside the bounds. */
	public static int fancyNormalSample(int lowerBound, int upperBound)
	{
		double value = fancyNormalSample((double) lowerBound, (double) upperBound);
		return (int) Math.max(lowerBound, Math.min(upperBound, Math.round(value)));
	}

	/**
	 * As upstream: a normal sample (mean the midpoint and deviation a ninth of the range when null)
	 * redrawn until it falls inside {@code [lowerBound, upperBound]}. An empty or reversed range
	 * returns {@code lowerBound}.
	 */
	public static double truncatedNormalSample(double lowerBound, double upperBound, Double mean, Double std)
	{
		if (!(upperBound > lowerBound))
		{
			return lowerBound;
		}
		double centre = mean == null ? (lowerBound + upperBound) / 2 : mean;
		double deviation = std == null ? (upperBound - lowerBound) / 9 : std;
		if (!(deviation > 0))
		{
			return Math.max(lowerBound, Math.min(upperBound, centre));
		}
		while (true)
		{
			double sample = centre + deviation * ThreadLocalRandom.current().nextGaussian();
			if (sample >= lowerBound && sample <= upperBound)
			{
				return sample;
			}
		}
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
