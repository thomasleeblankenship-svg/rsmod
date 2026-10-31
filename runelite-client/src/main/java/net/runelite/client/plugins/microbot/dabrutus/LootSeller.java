package net.runelite.client.plugins.microbot.dabrutus;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Sells Brutus's drops from the bank when the account is out of food and can't afford any (user 2026-10-04: "sell loot
 * GE if out of food"). Da-Scurrius's {@code LootSeller}: withdraw as notes at the Grand Exchange bank, list at 90% of the
 * guide price, wait, relist what didn't sell at 75% once; whatever is left goes back to the bank. Only his drops are
 * sold (see {@link #LOOT}); air runes (cowbell charges), bones, armour and the Mooleta never are.
 */
final class LootSeller
{
	enum Result
	{
		WORKING, DONE
	}

	private enum Phase
	{
		WITHDRAW, LIST, WAIT, COLLECT
	}

	static final long WAIT_MS = 60_000;
	static final int[] PRICE_PERCENT = {90, 75};

	/** Brutus drops worth selling (seen in the 2026-10-03 logs), lower case. */
	static final Set<String> LOOT = new HashSet<>(Arrays.asList("raw t-bone steak", "mind rune", "chaos rune",
		"iron arrow", "potato seed", "cowhide", "oak logs", "acorn"));

	private final Consumer<String> log;
	private Phase phase = Phase.WITHDRAW;
	private int round;
	private long waitUntil;
	private final Map<String, Integer> ids = new HashMap<>();
	/** Guide price when picked at the bank (at the GE the lookup can return 0). */
	private final Map<String, Integer> guide = new HashMap<>();

	LootSeller(Consumer<String> log)
	{
		this.log = log;
	}

	void reset()
	{
		phase = Phase.WITHDRAW;
		round = 0;
		ids.clear();
		guide.clear();
	}

	/** Ranged mode: arrows are ammunition, never sold. */
	static volatile boolean keepArrows;
	/** Magic mode: runes are ammunition, never sold. */
	static volatile boolean keepRunes;

	static boolean isLoot(String name)
	{
		if (name == null || (keepArrows && name.toLowerCase(Locale.ROOT).contains("arrow"))
			|| (keepRunes && name.toLowerCase(Locale.ROOT).endsWith(" rune")))
		{
			return false;
		}
		return LOOT.contains(name.toLowerCase(Locale.ROOT));
	}

	/** Bank items to sell (bank open): his drops with a guide price. */
	static List<Rs2ItemModel> sellable()
	{
		return Rs2Bank.bankItems().stream()
			.filter(i -> i.getName() != null && i.isTradeable() && isLoot(i.getName()) && i.getQuantity() > 0)
			.filter(i -> Rs2GrandExchange.getPrice(i.getId()) > 0)
			.collect(Collectors.toList());
	}

	/** One step. DONE when everything is sold or back in the bank and the coins are collected to the bank. */
	Result tick()
	{
		switch (phase)
		{
			case WITHDRAW:
				return withdraw();
			case LIST:
				list();
				return Result.WORKING;
			case WAIT:
				if (System.currentTimeMillis() >= waitUntil || stillSelling().isEmpty())
				{
					phase = Phase.COLLECT;
				}
				return Result.WORKING;
			default:
				return collect();
		}
	}

	private Result withdraw()
	{
		if (!Rs2Bank.isOpen())
		{
			if (!nearGe())
			{
				return Result.WORKING;
			}
			Rs2Bank.openBank();
			Global.sleepUntil(Rs2Bank::isOpen, 3000);
			return Result.WORKING;
		}
		if (!Rs2Inventory.isEmpty())
		{
			Rs2Bank.depositAll();
			Global.sleep(500, 800);
			return Result.WORKING;
		}
		List<Rs2ItemModel> items = sellable().stream().limit(8).collect(Collectors.toList());
		if (items.isEmpty())
		{
			Rs2Bank.closeBank();
			return Result.DONE;
		}
		Rs2Bank.setWithdrawAsNote();
		Global.sleep(300, 600);
		StringBuilder text = new StringBuilder();
		for (Rs2ItemModel i : items)
		{
			ids.put(i.getName(), i.getId());
			guide.put(i.getName(), Rs2GrandExchange.getPrice(i.getId()));
			text.append(text.length() > 0 ? ", " : "").append(i.getQuantity()).append(" x ").append(i.getName());
			Rs2Bank.withdrawAll(i.getName(), true);
			Global.sleep(300, 600);
		}
		Rs2Bank.setWithdrawAsItem();
		log.accept("out of food: selling loot for food money: " + text);
		Rs2Bank.closeBank();
		phase = Phase.LIST;
		return Result.WORKING;
	}

	private void list()
	{
		if (!Rs2GrandExchange.isOpen())
		{
			if (nearGe())
			{
				Rs2GrandExchange.openExchange();
			}
			return;
		}
		int percent = PRICE_PERCENT[Math.min(round, PRICE_PERCENT.length - 1)];
		for (Map.Entry<String, Integer> e : ids.entrySet())
		{
			if (Rs2GrandExchange.getAvailableSlotsCount() <= 0)
			{
				break;
			}
			int qty = held(e.getKey());
			if (qty <= 0)
			{
				continue;
			}
			int base = Math.max(Rs2GrandExchange.getPrice(e.getValue()), guide.getOrDefault(e.getKey(), 0));
			if (base <= 0)
			{
				log.accept("GE: not selling " + e.getKey() + ": no guide price");
				continue;
			}
			int price = Math.max(1, (int) ((long) base * percent / 100));
			if (Rs2GrandExchange.sellItem(e.getKey(), qty, price))
			{
				log.accept(String.format("GE: selling %d x %s at %,d gp (%d%% of guide)", qty, e.getKey(), price, percent));
			}
			Global.sleep(800, 1400);
		}
		waitUntil = System.currentTimeMillis() + WAIT_MS;
		phase = Phase.WAIT;
	}

	private Result collect()
	{
		if (!Rs2GrandExchange.isOpen() && !Rs2GrandExchange.openExchange())
		{
			return Result.WORKING;
		}
		Set<String> unsold = stillSelling();
		for (String name : unsold)
		{
			String key = ids.keySet().stream().filter(k -> k.equalsIgnoreCase(name)).findFirst().orElse(name);
			Rs2GrandExchange.abortOffer(key, true);
			Global.sleep(600, 1200);
		}
		Rs2GrandExchange.collectAllToBank();
		Global.sleep(800, 1200);
		Rs2GrandExchange.closeExchange();
		ids.clear();
		phase = Phase.WITHDRAW;
		if (unsold.isEmpty())
		{
			round = 0;
			return Result.DONE; // one batch, then buy food and fight
		}
		round++;
		if (round < PRICE_PERCENT.length)
		{
			log.accept("GE: " + unsold.size() + " item(s) unsold, relisting lower");
			return Result.WORKING;
		}
		log.accept("GE: left unsold (back in the bank): " + unsold);
		round = 0;
		return Result.DONE;
	}

	/** Quantity carried of {@code name}, noted or not. */
	private static int held(String name)
	{
		return Rs2Inventory.items().filter(i -> i.getName() != null && i.getName().equalsIgnoreCase(name))
			.mapToInt(Rs2ItemModel::getQuantity).sum();
	}

	private static boolean nearGe()
	{
		WorldPoint ge = BankLocation.GRAND_EXCHANGE.getWorldPoint();
		WorldPoint me = Rs2Player.getWorldLocation();
		if (me == null || me.distanceTo(ge) > 6)
		{
			Act.walk(ge, 5);
			return false;
		}
		return true;
	}

	private static Set<String> stillSelling()
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Set<String> names = new HashSet<>();
			for (GrandExchangeOffer o : Microbot.getClient().getGrandExchangeOffers())
			{
				if (o != null && o.getState() == GrandExchangeOfferState.SELLING)
				{
					names.add(Microbot.getClient().getItemDefinition(o.getItemId()).getName().toLowerCase(Locale.ROOT));
				}
			}
			return names;
		}).orElse(new HashSet<>());
	}
}
