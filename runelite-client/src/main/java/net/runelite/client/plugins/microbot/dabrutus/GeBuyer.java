package net.runelite.client.plugins.microbot.dabrutus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Da-Scurrius's {@code GeBuyer} (user 2026-10-03: "see scurrius for grandexchange logic"), without its spending
 * categories. Buys everything on a shopping list at the Grand Exchange in rounds. A
 * round places an offer for every item still missing, as many as there are free slots, at the guide price plus the
 * markup (+15% for each earlier offer of that item that did not fill, at least 5 gp more per step); waits up to 30 s
 * for all of them together; cancels the ones still buying; collects everything to the bank. Bought items stay in the
 * bank (the script's next bank visit withdraws them). An item whose {@link #MAX_OFFERS} offers did not fill is given up.
 */
final class GeBuyer
{
	static final int GE_BUMP_PERCENT = 15;
	private static final int GE_FILL_WAIT_MS = 30_000;
	static final int MAX_OFFERS = 6;

	public enum Result
	{
		/** Every order is bought (collected to the bank) or given up (see {@link Order#failed()}). */
		DONE,
		/** Still working on it (walking, offers not filled yet); call again. */
		WORKING
	}

	/** One line of the shopping list. */
	public static final class Order
	{
		final String name;
		final int id;
		final int qty;
		final int fallbackPrice;
		int bumps;
		/** Bought so far (filled offers, plus the part of a cancelled offer that filled). */
		int got;
		boolean failed;

		public Order(String name, int id, int qty, int fallbackPrice)
		{
			this.name = name;
			this.id = id;
			this.qty = qty;
			this.fallbackPrice = fallbackPrice;
		}

		public String name()
		{
			return name;
		}

		public int qty()
		{
			return qty;
		}

		/** Given up: {@link #MAX_OFFERS} offers did not fill. */
		public boolean failed()
		{
			return failed;
		}
	}

	private final Consumer<String> log;
	private int openFailures;

	public GeBuyer(Consumer<String> log)
	{
		this.log = log;
	}

	public void reset()
	{
		openFailures = 0;
	}

	/** One step of buying every order; {@link Result#DONE} once each is bought or given up. */
	public Result buyAll(List<Order> orders, int markupPercent)
	{
		List<Order> pending = new ArrayList<>();
		for (Order o : orders)
		{
			if (!o.failed && o.bumps >= MAX_OFFERS)
			{
				o.failed = true;
				log.accept("GE: giving up on " + o.name + " after " + MAX_OFFERS + " offers that did not fill");
			}
			if (!o.failed && o.got < o.qty)
			{
				pending.add(o);
			}
		}
		if (pending.isEmpty())
		{
			openFailures = 0;
			return Result.DONE;
		}
		if (!Rs2GrandExchange.isOpen())
		{
			if (Rs2Bank.isOpen())
			{
				Rs2Bank.closeBank();
				Global.sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
			}
			WorldPoint ge = BankLocation.GRAND_EXCHANGE.getWorldPoint();
			WorldPoint me = Rs2Player.getWorldLocation();
			if (me == null || me.distanceTo(ge) > 5)
			{
				Act.walk(ge, 5);
				return Result.WORKING;
			}
			if (!Rs2GrandExchange.openExchange())
			{
				if (++openFailures >= MAX_OFFERS)
				{
					pending.forEach(o -> o.failed = true);
					log.accept("GE: could not open the Grand Exchange; giving up on " + pending.size() + " item(s)");
				}
				return Result.WORKING;
			}
		}
		openFailures = 0;
		// Place every offer first, then wait once for all of them.
		List<Order> placed = new ArrayList<>();
		int slots = Rs2GrandExchange.getAvailableSlotsCount();
		for (Order o : pending)
		{
			if (placed.size() >= slots)
			{
				break;
			}
			// Never a second offer for an item that already has one (live 01:41: a Dragon sword, Warrior ring, glory
			// and Combat bracelet bought twice when an offer placed but buyItem said it failed, or filled unseen).
			int[] existing = existingOffer(o.id);
			if (existing != null)
			{
				if (existing[0] == 1)
				{
					o.got += existing[1];
					log.accept("GE: " + o.name + " already bought in a slot (" + existing[1] + "); collecting");
					Rs2GrandExchange.collectAllToBank();
					Global.sleep(600, 1200);
				}
				else
				{
					placed.add(o); // a live offer: wait for it like one placed now
				}
				continue;
			}
			int price = offerPrice(unitPrice(o), markupPercent, o.bumps);
			int want = o.qty - o.got;
			if (want <= 0)
			{
				continue;
			}
			log.accept("GE: buying " + want + " x " + o.name + " at " + price + " gp each (offer " + (o.bumps + 1) + ")");
			if (Rs2GrandExchange.buyItem(o.name, price, want))
			{
				placed.add(o);
				Global.sleep(400, 900);
			}
			else if (existingOffer(o.id) != null)
			{
				placed.add(o); // the click landed after all (natural mouse is asynchronous)
			}
			else
			{
				o.bumps++;
			}
		}
		if (placed.isEmpty())
		{
			Rs2GrandExchange.closeExchange();
			return Result.WORKING;
		}
		Global.sleep(600, 1200);
		Global.sleepUntil(Rs2GrandExchange::hasFinishedBuyingOffers, GE_FILL_WAIT_MS);
		for (Order o : placed)
		{
			int filledSoFar = stillBuying(o.id);
			if (filledSoFar >= 0)
			{
				// A new offer next to a live one would buy twice; cancel it (refund and partial fill go to the bank) and
				// offer only the rest next round.
				Rs2GrandExchange.abortOffer(o.name, true);
				Global.sleep(600, 1200);
				o.got += filledSoFar;
				o.bumps++;
			}
			else
			{
				o.got = o.qty;
			}
		}
		Rs2GrandExchange.collectAllToBank();
		Global.sleep(600, 1200);
		Rs2GrandExchange.closeExchange();
		return Result.WORKING;
	}

	/** The guide price, or the order's fallback when the guide price is unknown. */
	static int unitPrice(Order o)
	{
		int guide = Rs2GrandExchange.getPrice(o.id);
		return guide > 0 ? guide : Math.max(1, o.fallbackPrice);
	}

	/**
	 * Guide price plus the markup, plus {@link #GE_BUMP_PERCENT} for each earlier offer that did not fill; always at least
	 * 5 gp more per step, so cheap items climb too. (Same formula as Da-Agility and Da-Trainer.)
	 */
	static int offerPrice(int base, int markupPercent, int bumps)
	{
		long percent = base * (100L + markupPercent + bumps * GE_BUMP_PERCENT) / 100;
		return (int) Math.min(Integer.MAX_VALUE, Math.max(base + 5L * (bumps + 1), percent));
	}

	/** Coins at or below which {@link #byGold} buys its minimum, and at or above which its maximum. */
	static final long GOLD_LOW = 50_000, GOLD_HIGH = 500_000;

	/**
	 * How many to buy with {@code coins} (user 2026-10-09: "depending on gold"): {@code min} at {@link #GOLD_LOW} gp or
	 * less, {@code max} at {@link #GOLD_HIGH} or more, linear in between, rounded to 50.
	 */
	static int byGold(long coins, int min, int max)
	{
		double t = Math.max(0, Math.min(1, (double) (coins - GOLD_LOW) / (GOLD_HIGH - GOLD_LOW)));
		int qty = (int) Math.round((min + t * (max - min)) / 50) * 50;
		return Math.max(min, Math.min(max, qty));
	}

	/** A buy offer for {@code id} in any slot: {1 = filled, quantity} or {0 = still buying, quantity so far}; else null. */
	private static int[] existingOffer(int id)
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			for (GrandExchangeOffer o : Microbot.getClient().getGrandExchangeOffers())
			{
				if (o == null || o.getItemId() != id)
				{
					continue;
				}
				if (o.getState() == GrandExchangeOfferState.BOUGHT)
				{
					return new int[]{1, o.getQuantitySold()};
				}
				if (o.getState() == GrandExchangeOfferState.BUYING)
				{
					return new int[]{0, o.getQuantitySold()};
				}
			}
			return null;
		}).orElse(null);
	}

	/** How many a still-running buy offer for {@code id} has bought, or -1 when none is running. */
	private static int stillBuying(int id)
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			for (GrandExchangeOffer o : Microbot.getClient().getGrandExchangeOffers())
			{
				if (o != null && o.getItemId() == id && o.getState() == GrandExchangeOfferState.BUYING)
				{
					return o.getQuantitySold();
				}
			}
			return -1;
		}).orElse(-1);
	}
}
