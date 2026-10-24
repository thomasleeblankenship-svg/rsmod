/*
 * Adapted from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/grounditem/Rs2LootEngine.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.grounditem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Upstream's loot builder: scripts add "intents" (by value, by name, untradables, coins, arrows,
 * bones, ashes, runes, or a custom predicate) over the ground items in range, then {@link Builder#loot()}
 * picks the union up, nearest first, through the script's loot action (usually
 * {@link Rs2GroundItem#coreLoot}).
 * <p>
 * As upstream: anti-lure protection keeps to the player's own drops, delayed looting waits until
 * an intent's soonest item is within 150 ticks of despawning, and food may be eaten for space.
 * Mobile adaptation: the ground items come from the read-only {@link Rs2GroundItem#getGroundItems()}.
 */
public final class Rs2LootEngine
{
	private static final int DESPAWN_DELAY_THRESHOLD_TICKS = 150;

	private Rs2LootEngine()
	{
	}

	/** Starts a loot pass with these parameters. */
	public static Builder with(LootingParameters params)
	{
		return new Builder(params);
	}

	/** One loot pass: intents collect candidates, {@link #loot()} picks them up. */
	public static final class Builder
	{
		private final LootingParameters params;
		private Consumer<GroundItem> lootAction = gi -> { };
		private final Map<String, List<GroundItem>> candidateBuckets = new LinkedHashMap<>();
		private List<GroundItem> candidatePool;

		private Builder(LootingParameters params)
		{
			this.params = Objects.requireNonNull(params, "params");
		}

		public Builder withLootAction(Consumer<GroundItem> lootAction)
		{
			this.lootAction = Objects.requireNonNull(lootAction, "lootAction");
			return this;
		}

		/** Items worth more than the minimum value whose unit price is under the maximum. */
		public Builder addByValue()
		{
			Predicate<GroundItem> byValue = gi ->
			{
				int qty = Math.max(1, gi.getQuantity());
				long price = gi.getGePrice();
				return price > params.getMinValue() && price / qty < params.getMaxValue();
			};
			collect("byValue", byValue, toLowerTrimmedSet(params.getIgnoredNames()));
			return this;
		}

		/** Items whose name contains one of the parameters' names. */
		public Builder addByNames()
		{
			Set<String> needles = toLowerTrimmedSet(params.getNames());
			if (needles.isEmpty())
			{
				return this;
			}
			collect("byNames", gi -> needles.stream().anyMatch(safeLower(gi.getName())::contains), null);
			return this;
		}

		public Builder addUntradables()
		{
			collect("untradables", gi -> !gi.isTradeable() && gi.getId() != ItemID.COINS, null);
			return this;
		}

		public Builder addCoins()
		{
			collect("coins", gi -> gi.getId() == ItemID.COINS, null);
			return this;
		}

		/** Arrows, skipping stacks of one. */
		public Builder addArrows()
		{
			return addArrows(1);
		}

		/** Arrows; a stackable stack must hold more than {@code minStackExclusive}. */
		public Builder addArrows(int minStackExclusive)
		{
			collect("arrows[min>" + minStackExclusive + "]", gi -> safeLower(gi.getName()).contains("arrow")
				&& (!gi.isStackable() || gi.getQuantity() > minStackExclusive), null);
			return this;
		}

		public Builder addBones()
		{
			collect("bones", gi -> safeLower(gi.getName()).contains("bones"), null);
			return this;
		}

		public Builder addAshes()
		{
			collect("ashes", gi ->
			{
				String n = safeLower(gi.getName());
				return "ashes".equals(n) || n.contains(" ashes");
			}, null);
			return this;
		}

		/** Runes, skipping stacks of one. */
		public Builder addRunes()
		{
			return addRunes(1);
		}

		/** Runes (name contains " rune"); a stackable stack must hold more than {@code minStackExclusive}. */
		public Builder addRunes(int minStackExclusive)
		{
			collect("runes[min>" + minStackExclusive + "]", gi -> safeLower(gi.getName()).contains(" rune")
				&& (!gi.isStackable() || gi.getQuantity() > minStackExclusive), null);
			return this;
		}

		/** Any custom intent; {@code ignoredLower} names (lower case) are skipped. */
		public Builder addCustom(String label, Predicate<GroundItem> predicate, Set<String> ignoredLower)
		{
			collect(label == null ? "custom" : label, predicate, ignoredLower);
			return this;
		}

		/**
		 * Picks up every collected item once (nearest first, enough quantity, room for it) and
		 * returns whether none of them is left on the ground.
		 */
		public boolean loot()
		{
			WorldPoint me = Rs2Player.getWorldLocation();
			Map<String, GroundItem> unique = new LinkedHashMap<>();
			for (List<GroundItem> list : candidateBuckets.values())
			{
				for (GroundItem gi : list)
				{
					unique.putIfAbsent(uniqueKey(gi), gi);
				}
			}
			List<GroundItem> toLoot = new ArrayList<>(unique.values());
			if (me != null)
			{
				toLoot.sort(Comparator.comparingInt(gi -> gi.getLocation().distanceTo(me)));
			}
			Set<String> targetKeys = unique.keySet();
			return Rs2GroundItem.runWhilePaused(() ->
			{
				for (GroundItem gi : toLoot)
				{
					if (gi.getQuantity() >= params.getMinQuantity() && ensureSpaceFor(gi, params))
					{
						lootAction.accept(gi);
					}
				}
				return groundItems().stream().noneMatch(gi -> targetKeys.contains(uniqueKey(gi)));
			});
		}

		private void collect(String label, Predicate<GroundItem> itemPredicate, Set<String> ignoredLower)
		{
			if (candidatePool == null)
			{
				candidatePool = groundItems().stream().filter(baseRangeAndOwnershipFilter(params)).collect(Collectors.toList());
			}
			List<GroundItem> matching = candidatePool.stream().filter(itemPredicate).collect(Collectors.toList());
			if (matching.size() < params.getMinItems())
			{
				candidateBuckets.put(label, Collections.emptyList());
				return;
			}
			if (params.isDelayedLooting())
			{
				int soonest = matching.stream().mapToInt(Rs2GroundItem::calculateDespawnTime).min().orElse(Integer.MAX_VALUE);
				if (soonest > DESPAWN_DELAY_THRESHOLD_TICKS)
				{
					candidateBuckets.put(label, Collections.emptyList());
					return;
				}
			}
			candidateBuckets.put(label, matching.stream()
				.filter(gi -> gi.getQuantity() >= params.getMinQuantity() && passesIgnoredNames(gi, ignoredLower))
				.collect(Collectors.toList()));
		}
	}

	private static List<GroundItem> groundItems()
	{
		com.google.common.collect.Table<WorldPoint, Integer, GroundItem> table = Rs2GroundItem.getGroundItems();
		return table == null ? List.of() : new ArrayList<>(table.values());
	}

	private static Predicate<GroundItem> baseRangeAndOwnershipFilter(LootingParameters params)
	{
		WorldPoint me = Rs2Player.getWorldLocation();
		boolean anti = params.isAntiLureProtection();
		return gi -> me != null && gi.getLocation() != null && gi.getLocation().distanceTo(me) < params.getRange()
			&& (!anti || gi.getOwnership() == TileItem.OWNERSHIP_SELF);
	}

	private static boolean passesIgnoredNames(GroundItem gi, Set<String> ignoredLower)
	{
		if (ignoredLower == null || ignoredLower.isEmpty())
		{
			return true;
		}
		String name = safeLower(gi.getName());
		return ignoredLower.stream().noneMatch(name::contains);
	}

	private static boolean ensureSpaceFor(GroundItem gi, LootingParameters params)
	{
		if (Rs2Inventory.emptySlotCount() > params.getMinInvSlots())
		{
			return true;
		}
		if (params.isEatFoodForSpace() && !Rs2GroundItem.canTakeGroundItem(gi) && !Rs2Inventory.getInventoryFood().isEmpty()
			&& Rs2Player.eatAt(100))
		{
			Rs2Player.waitForAnimation();
		}
		return Rs2GroundItem.canTakeGroundItem(gi);
	}

	private static Set<String> toLowerTrimmedSet(String[] values)
	{
		if (values == null || values.length == 0)
		{
			return Collections.emptySet();
		}
		Set<String> out = new HashSet<>(values.length);
		for (String s : values)
		{
			if (s != null && !s.trim().isEmpty())
			{
				out.add(s.trim().toLowerCase(Locale.ROOT));
			}
		}
		return out;
	}

	private static String safeLower(String s)
	{
		return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
	}

	private static String uniqueKey(GroundItem gi)
	{
		WorldPoint wp = gi.getLocation();
		return gi.getId() + "@" + (wp == null ? "?" : wp.getX() + "," + wp.getY() + "," + wp.getPlane());
	}
}
