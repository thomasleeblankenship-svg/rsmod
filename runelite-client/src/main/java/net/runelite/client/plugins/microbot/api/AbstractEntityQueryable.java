/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/AbstractEntityQueryable.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.actor.Rs2ActorModel;

/**
 * Filter/terminal logic shared by every entity query; filter semantics are
 * upstream's.
 * <p>
 * Distances use {@link WorldPoint#distanceTo(WorldPoint)}: Chebyshev tiles, and
 * {@link Integer#MAX_VALUE} for a different plane, so {@code within} and
 * {@code nearest} never match another floor. The local-player anchor is the
 * upstream {@code Rs2PlayerModel} location (projected to the main world when on
 * a boat world view). Adaptation: the source stream comes from the owning
 * cache passed in by {@code query()} instead of a static {@code Microbot}
 * lookup, and the player anchor is read through {@link MicrobotReadServices}.
 */
public abstract class AbstractEntityQueryable<
	Q extends IEntityQueryable<Q, E>,
	E extends IEntity
	>
	implements IEntityQueryable<Q, E>
{
	protected final MicrobotReadServices services;
	protected Stream<E> source;

	protected AbstractEntityQueryable(MicrobotReadServices services, Stream<E> source)
	{
		this.services = services;
		this.source = source;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Q fromWorldView()
	{
		WorldView worldView = Rs2ActorModel.localPlayerWorldView(services);
		if (worldView == null)
		{
			this.source = Stream.empty();
			return (Q) this;
		}

		this.source = this.source
			.filter(o -> o.getWorldView() != null && o.getWorldView().getId() == worldView.getId());

		return (Q) this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Q where(Predicate<E> predicate)
	{
		source = source.filter(predicate);
		return (Q) this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Q within(int distance)
	{
		WorldPoint playerLoc = Rs2ActorModel.localPlayerLocation(services);
		if (playerLoc == null)
		{
			this.source = Stream.empty();
			return (Q) this;
		}

		this.source = this.source
			.filter(o ->
			{
				WorldPoint loc = o.getWorldLocation();
				return loc != null && loc.distanceTo(playerLoc) <= distance;
			});

		return (Q) this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Q within(WorldPoint anchor, int distance)
	{
		if (anchor == null)
		{
			this.source = Stream.empty();
			return (Q) this;
		}

		this.source = this.source
			.filter(o ->
			{
				WorldPoint loc = o.getWorldLocation();
				return loc != null && loc.distanceTo(anchor) <= distance;
			});

		return (Q) this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Q withName(String name)
	{
		if (name == null)
		{
			this.source = Stream.empty();
			return (Q) this;
		}

		this.source = this.source.filter(x ->
		{
			String n = x.getName();
			return n != null && n.equalsIgnoreCase(name);
		});

		return (Q) this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Q withNameContains(String substring)
	{
		if (substring == null || substring.isEmpty())
		{
			this.source = Stream.empty();
			return (Q) this;
		}

		String needle = substring.toLowerCase();
		this.source = this.source.filter(x ->
		{
			String n = x.getName();
			return n != null && n.toLowerCase().contains(needle);
		});

		return (Q) this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Q withNames(String... names)
	{
		if (names == null || names.length == 0)
		{
			this.source = Stream.empty();
			return (Q) this;
		}

		this.source = this.source.filter(x ->
		{
			String n = x.getName();
			if (n == null)
			{
				return false;
			}
			return Arrays.stream(names).anyMatch(n::equalsIgnoreCase);
		});

		return (Q) this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Q withId(int id)
	{
		this.source = this.source.filter(x -> x.getId() == id);
		return (Q) this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Q withIds(int... ids)
	{
		if (ids == null || ids.length == 0)
		{
			this.source = Stream.empty();
			return (Q) this;
		}

		this.source = this.source.filter(x ->
		{
			int entityId = x.getId();
			for (int id : ids)
			{
				if (entityId == id)
				{
					return true;
				}
			}
			return false;
		});

		return (Q) this;
	}

	@Override
	public E first()
	{
		return source.findFirst().orElse(null);
	}

	@Override
	public E nearest()
	{
		return nearest(Integer.MAX_VALUE);
	}

	@Override
	public E nearest(int maxDistance)
	{
		WorldPoint playerLoc = Rs2ActorModel.localPlayerLocation(services);
		if (playerLoc == null)
		{
			return null;
		}

		return nearest(playerLoc, maxDistance);
	}

	@Override
	public E nearest(WorldPoint anchor, int maxDistance)
	{
		if (anchor == null)
		{
			return null;
		}

		return source
			.map(entity ->
			{
				WorldPoint loc = entity.getWorldLocation();
				int distance = (loc != null) ? loc.distanceTo(anchor) : Integer.MAX_VALUE;
				return new EntityDistance<>(entity, distance);
			})
			// Upstream keeps MAX_VALUE (other plane / no location) when maxDistance is MAX_VALUE;
			// a different plane is never "nearest", so it is excluded here.
			.filter(pair -> pair.distance != Integer.MAX_VALUE && pair.distance <= maxDistance)
			.min(Comparator.comparingInt(pair -> pair.distance))
			.map(pair -> pair.entity)
			.orElse(null);
	}

	@Override
	public List<E> toList()
	{
		return source.collect(Collectors.toList());
	}

	@Override
	public int count()
	{
		return (int) source.count();
	}

	private static final class EntityDistance<E>
	{
		final E entity;
		final int distance;

		EntityDistance(E entity, int distance)
		{
			this.entity = entity;
			this.distance = distance;
		}
	}
}
