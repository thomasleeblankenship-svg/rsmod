package net.runelite.client.plugins.microbot.shortestpath;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Path reads behind upstream's {@code Rs2Walker} API (Microbot 2.6.38), for the walker's one-line
 * statics: {@link #getWalkPath}, {@link #getTotalTiles}, {@link #getTotalTilesFromPath},
 * {@link #getClosestTileIndex}, {@link #isWalkableInCollisionMap}, {@link #getTile}, and the banking
 * reads {@link #getTransportsForDestination} and {@link #getMissingTransportItemIdsWithQuantities}.
 * <p>
 * Adaptations: routes come from {@link MobilePathfinder} (upstream: {@code Rs2PathApi}), so they
 * use mobile's transport policy and never bank items; the closest-index search measures steps on
 * the pathfinder's collision map, the map the route was planned on (upstream: a live-scene BFS).
 * Searches block the calling thread and must not run on the client thread.
 */
public final class WalkerPathReads
{
	/** Upstream's step budget for the closest-index search. */
	static final int CLOSEST_INDEX_STEP_BUDGET = 20;

	private static final int[][] DIRECTIONS = {
		{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}
	};

	private WalkerPathReads()
	{
	}

	/** As upstream: the tile path from {@code start} towards {@code target} (partial if blocked), or empty. */
	public static List<WorldPoint> getWalkPath(WorldPoint start, WorldPoint target)
	{
		if (start == null || target == null)
		{
			return Collections.emptyList();
		}
		return MobilePathfinder.find(start, target).getTiles();
	}

	public static List<WorldPoint> getWalkPath(WorldPoint target)
	{
		return getWalkPath(Rs2Player.getWorldLocation(), target);
	}

	/**
	 * As upstream: the number of path tiles from {@code start} to {@code destination}, or
	 * {@link Integer#MAX_VALUE} when the path ends on another floor or more than 4 tiles (on either
	 * axis) from the destination.
	 */
	public static int getTotalTiles(WorldPoint start, WorldPoint destination)
	{
		if (destination == null)
		{
			return Integer.MAX_VALUE;
		}
		return tilesIfEndsNear(getWalkPath(start, destination), destination, 5);
	}

	public static int getTotalTiles(WorldPoint destination)
	{
		return getTotalTiles(Rs2Player.getWorldLocation(), destination);
	}

	/** As upstream: {@code path.size()}, or {@link Integer#MAX_VALUE} unless it ends within 7 tiles of {@code destination}. */
	public static int getTotalTilesFromPath(List<WorldPoint> path, WorldPoint destination)
	{
		return path == null || destination == null ? Integer.MAX_VALUE : tilesIfEndsNear(path, destination, 8);
	}

	/** Upstream's check: two {@code size} x {@code size} areas around the path end and the destination overlap. */
	private static int tilesIfEndsNear(List<WorldPoint> path, WorldPoint destination, int size)
	{
		if (path.isEmpty())
		{
			return Integer.MAX_VALUE;
		}
		WorldPoint end = path.get(path.size() - 1);
		if (end.getPlane() != destination.getPlane()
			|| Math.abs(end.getX() - destination.getX()) >= size || Math.abs(end.getY() - destination.getY()) >= size)
		{
			return Integer.MAX_VALUE;
		}
		return path.size();
	}

	/**
	 * As upstream: the index of the path tile fewest steps from the player (within 20 steps), else
	 * of the tile nearest in a straight line; -1 for an empty path or no player.
	 */
	public static int getClosestTileIndex(List<WorldPoint> path)
	{
		PathfinderConfig config = MobilePathfinder.sharedConfig();
		return getClosestTileIndex(path, Rs2Player.getWorldLocation(), config == null ? null : config.getMap());
	}

	static int getClosestTileIndex(List<WorldPoint> path, WorldPoint player, CollisionMap map)
	{
		if (path == null || path.isEmpty() || player == null)
		{
			return -1;
		}
		Map<WorldPoint, Integer> steps = map == null ? Collections.emptyMap() : stepsFrom(map, player);
		int best = -1;
		int bestSteps = Integer.MAX_VALUE;
		for (int i = 0; i < path.size(); i++)
		{
			Integer distance = path.get(i) == null ? null : steps.get(path.get(i));
			if (distance != null && distance < bestSteps)
			{
				bestSteps = distance;
				best = i;
				if (distance == 0)
				{
					break;
				}
			}
		}
		if (best >= 0)
		{
			return best;
		}
		int closest = -1;
		int closestDistance = Integer.MAX_VALUE;
		for (int i = 0; i < path.size(); i++)
		{
			WorldPoint point = path.get(i);
			int distance = point == null ? Integer.MAX_VALUE : player.distanceTo(point);
			if (point != null && distance < closestDistance)
			{
				closestDistance = distance;
				closest = i;
			}
		}
		return closest;
	}

	/** Breadth-first step counts from {@code origin} on {@code map}, up to the step budget. */
	private static Map<WorldPoint, Integer> stepsFrom(CollisionMap map, WorldPoint origin)
	{
		Map<WorldPoint, Integer> steps = new HashMap<>();
		ArrayDeque<WorldPoint> queue = new ArrayDeque<>();
		steps.put(origin, 0);
		queue.add(origin);
		while (!queue.isEmpty())
		{
			WorldPoint point = queue.removeFirst();
			int distance = steps.get(point);
			if (distance >= CLOSEST_INDEX_STEP_BUDGET)
			{
				continue;
			}
			for (int[] d : DIRECTIONS)
			{
				WorldPoint next = point.dx(d[0]).dy(d[1]);
				if (!steps.containsKey(next) && map.canStep(point.getX(), point.getY(), point.getPlane(), d[0], d[1]))
				{
					steps.put(next, distance + 1);
					queue.addLast(next);
				}
			}
		}
		return steps;
	}

	/** Transport types upstream's bank planning prepares items or fares for. */
	private static final java.util.Set<TransportType> BANK_PLANNED = java.util.EnumSet.of(TransportType.TELEPORTATION_ITEM,
		TransportType.FAIRY_RING, TransportType.TELEPORTATION_SPELL, TransportType.CANOE, TransportType.BOAT,
		TransportType.CHARTER_SHIP, TransportType.SHIP, TransportType.MINECART, TransportType.MAGIC_CARPET,
		TransportType.SPIRIT_TREE);
	/** Types whose catalogue fare is paid in a currency item. */
	private static final java.util.Set<TransportType> CURRENCY_BASED = java.util.EnumSet.of(TransportType.BOAT,
		TransportType.CHARTER_SHIP, TransportType.SHIP, TransportType.MINECART, TransportType.MAGIC_CARPET,
		TransportType.TRANSPORT);

	public static List<Transport> getTransportsForDestination(WorldPoint destination, boolean useBankItems)
	{
		return getTransportsForDestination(destination, useBankItems, TransportType.TELEPORTATION_ITEM);
	}

	/**
	 * As upstream: plans from the player to {@code destination} (with the remembered bank's items
	 * when {@code useBankItems}) and returns, in route order, the transports on it that bank
	 * preparation covers: teleport items and spells, fairy rings, spirit trees, canoes, ships,
	 * boats, minecarts, magic carpets, and plain transports that cost a fare or need an item.
	 * Adaptation: the route holds one transport per step, so {@code prefTransportType} (upstream's
	 * tie-break between transports joining the same two tiles) has nothing to choose between.
	 */
	public static List<Transport> getTransportsForDestination(WorldPoint destination, boolean useBankItems,
		TransportType prefTransportType)
	{
		WorldPoint start = Rs2Player.getWorldLocation();
		if (destination == null || start == null)
		{
			return new java.util.ArrayList<>();
		}
		return bankPlanned(MobilePathfinder.find(start, destination, useBankItems));
	}

	static List<Transport> bankPlanned(PathResult route)
	{
		List<Transport> transports = new java.util.ArrayList<>();
		for (PathResult.TransportStep step : route.getTransports().values())
		{
			Transport transport = step.source();
			if (BANK_PLANNED.contains(transport.getType()) || plainTransportNeedsItems(transport))
			{
				transports.add(transport);
			}
		}
		return transports;
	}

	/** As upstream: a plain transport takes part in bank planning when it costs a fare or needs an item. */
	private static boolean plainTransportNeedsItems(Transport transport)
	{
		return transport.getType() == TransportType.TRANSPORT && (transport.getCurrencyAmount() > 0
			|| (transport.getItemIdRequirements() != null && !transport.getItemIdRequirements().isEmpty()));
	}

	/**
	 * As upstream: item id to quantity to withdraw for {@code transports}, from the remembered bank:
	 * a teleport spell's runes; a fare's coins or ecto-tokens; and, for each item requirement the
	 * player does not already carry, the alternative the bank holds enough of (else the most banked
	 * one), or the coins to buy it at the transport when none is banked but it is sold there. A
	 * fairy ring needs a Dramen or Lunar staff until the Lumbridge elite diary is done.
	 */
	public static Map<Integer, Integer> getMissingTransportItemIdsWithQuantities(List<Transport> transports)
	{
		return getMissingTransportItemIdsWithQuantities(transports,
			net.runelite.client.plugins.microbot.util.bank.Rs2Bank::count, WalkerPathReads::carriedItemQuantity,
			() -> Microbot.getVarbitValue(
				net.runelite.api.gameval.VarbitID.LUMBRIDGE_DIARY_ELITE_COMPLETE) == 1);
	}

	static Map<Integer, Integer> getMissingTransportItemIdsWithQuantities(List<Transport> transports,
		java.util.function.IntUnaryOperator banked, java.util.function.IntUnaryOperator carried,
		java.util.function.BooleanSupplier lumbridgeEliteDone)
	{
		Map<Integer, Integer> withdrawals = new HashMap<>();
		if (transports == null)
		{
			return withdrawals;
		}
		for (Transport transport : transports)
		{
			if (transport.getType() == TransportType.TELEPORTATION_SPELL)
			{
				spellRunes(transport).forEach((rune, quantity) -> withdrawals.merge(rune, quantity, Integer::sum));
				continue;
			}
			if (CURRENCY_BASED.contains(transport.getType()) && transport.getCurrencyAmount() > 0
				&& (transport.getItemIdRequirements() == null || transport.getItemIdRequirements().isEmpty()))
			{
				int currency = currencyItemId(transport.getCurrencyName());
				if (currency > 0)
				{
					withdrawals.merge(currency, transport.getCurrencyAmount(), Integer::sum);
				}
				continue;
			}
			for (TransportItemRequirement requirement : requirements(transport, lumbridgeEliteDone))
			{
				if (requirement.isSatisfiedBy(carried))
				{
					continue;
				}
				Integer preferred = null;
				int preferredBanked = 0;
				for (int itemId : requirement.getItemIds())
				{
					int required = requirement.getRequiredQuantity(itemId);
					if (required == 0)
					{
						continue;
					}
					int bank = Math.max(0, banked.applyAsInt(itemId));
					boolean satisfies = bank >= required;
					boolean preferredSatisfies = preferred != null && preferredBanked >= requirement.getRequiredQuantity(preferred);
					if (preferred == null || (satisfies && !preferredSatisfies)
						|| (satisfies == preferredSatisfies && bank > preferredBanked))
					{
						preferred = itemId;
						preferredBanked = bank;
					}
				}
				if (preferred != null && preferredBanked == 0)
				{
					PurchasableItemCatalog.PurchasableItem purchasable = requirement.getItemIds().stream()
						.map(PurchasableItemCatalog::byItemId).filter(java.util.Objects::nonNull).findFirst().orElse(null);
					int currency = purchasable == null ? -1 : currencyItemId(purchasable.costCurrencyName);
					if (currency > 0)
					{
						int needed = CURRENCY_BASED.contains(transport.getType()) ? 1 : requirement.getRequiredQuantity(purchasable.itemId);
						withdrawals.merge(currency, purchasable.costAmount * needed, Integer::sum);
						continue;
					}
				}
				if (preferred != null)
				{
					withdrawals.merge(preferred, requirement.getRequiredQuantity(preferred), Integer::sum);
				}
			}
		}
		return withdrawals;
	}

	/** The catalogue's item requirements; a fairy ring without any needs a staff until the elite diary. */
	private static List<TransportItemRequirement> requirements(Transport transport, java.util.function.BooleanSupplier lumbridgeEliteDone)
	{
		List<TransportItemRequirement> listed = transport.getItemRequirements() == null
			? Collections.emptyList() : transport.getItemRequirements();
		if (transport.getType() == TransportType.FAIRY_RING && listed.isEmpty() && !lumbridgeEliteDone.getAsBoolean())
		{
			return List.of(TransportItemRequirement.legacyAlternatives(java.util.Set.of(
				net.runelite.api.gameval.ItemID.DRAMEN_STAFF, net.runelite.api.gameval.ItemID.LUNAR_MOONCLAN_LIMINAL_STAFF)));
		}
		return listed;
	}

	/** As upstream: the runes one cast of the spell named in {@code displayInfo} still needs. */
	private static Map<Integer, Integer> spellRunes(Transport transport)
	{
		Map<Integer, Integer> runes = new HashMap<>();
		if (transport.getDisplayInfo() == null)
		{
			return runes;
		}
		String name = transport.getDisplayInfo().contains(":")
			? transport.getDisplayInfo().split(":")[0].trim().toLowerCase() : transport.getDisplayInfo();
		net.runelite.client.plugins.microbot.util.magic.Rs2Spells spell =
			net.runelite.client.plugins.microbot.util.magic.Rs2Magic.getRs2Spell(name);
		if (spell != null)
		{
			net.runelite.client.plugins.microbot.util.magic.Rs2Magic.getRequiredRunes(spell, 1, true)
				.forEach((rune, quantity) -> runes.put(rune.getItemId(), quantity));
		}
		return runes;
	}

	private static int currencyItemId(String currencyName)
	{
		String currency = currencyName == null ? "" : currencyName.trim().toLowerCase();
		switch (currency)
		{
			case "coins":
				return net.runelite.api.gameval.ItemID.COINS;
			case "ecto-token":
				return net.runelite.api.gameval.ItemID.ECTOTOKEN;
			default:
				return -1;
		}
	}

	/** As upstream: carried quantity, counting an equipped item as at least one. */
	private static int carriedItemQuantity(int itemId)
	{
		int quantity = net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.itemQuantity(itemId);
		net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel equipped =
			net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.get(itemId);
		return equipped == null ? quantity : quantity + Math.max(1, equipped.getQuantity());
	}

	/**
	 * As upstream ({@code Rs2PathApi.hasWalkableTileWithin(tile, 0)}): the tile is open in the
	 * pathfinder's collision map (mapped and not blocked on all four sides).
	 */
	public static boolean isWalkableInCollisionMap(WorldPoint tile)
	{
		PathfinderConfig config = MobilePathfinder.sharedConfig();
		return config != null && isWalkableInCollisionMap(config.getMap(), tile);
	}

	static boolean isWalkableInCollisionMap(CollisionMap map, WorldPoint tile)
	{
		return tile != null && map.hasRegion(tile.getX(), tile.getY())
			&& !map.isBlocked(tile.getX(), tile.getY(), tile.getPlane());
	}

	/**
	 * As upstream: the live scene tile at {@code point} (resolved into the instance when in one), or
	 * {@code null} outside the loaded scene. Read it on the client thread.
	 */
	public static Tile getTile(WorldPoint point)
	{
		if (point == null)
		{
			return null;
		}
		return MobileActions.read(() ->
		{
			Client client = Microbot.getClient();
			WorldView view = client.getTopLevelWorldView();
			if (view == null || view.getScene() == null)
			{
				return null;
			}
			WorldPoint target = point;
			if (view.isInstance())
			{
				target = WorldPoint.toLocalInstance(view, point).stream().findFirst().orElse(null);
				if (target == null)
				{
					return null;
				}
			}
			LocalPoint local = LocalPoint.fromWorld(view, target);
			if (local == null)
			{
				return null;
			}
			Tile[][][] tiles = view.getScene().getTiles();
			return tiles == null ? null : tiles[point.getPlane()][local.getSceneX()][local.getSceneY()];
		}, null);
	}
}
