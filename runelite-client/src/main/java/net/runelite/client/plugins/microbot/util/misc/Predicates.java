package net.runelite.client.plugins.microbot.util.misc;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/** As upstream: reusable stream predicates. */
public final class Predicates
{
	private Predicates()
	{
	}

	/**
	 * As upstream: passes the first element seen for each value of {@code propertyExtractor} (for
	 * {@code stream.filter(...)}); thread-safe, and stateful, so use one per stream.
	 */
	public static <T> Predicate<T> distinctByProperty(Function<? super T, ?> propertyExtractor)
	{
		Set<Object> seen = ConcurrentHashMap.newKeySet();
		return t -> seen.add(propertyExtractor.apply(t));
	}
}
