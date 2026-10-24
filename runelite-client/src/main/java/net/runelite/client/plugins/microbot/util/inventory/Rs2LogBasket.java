package net.runelite.client.plugins.microbot.util.inventory;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

/** Log-basket actions use observed inventory/bank changes; unknown contents remain unknown. */
public final class Rs2LogBasket
{
	public static final int LOG_BASKET_CAPACITY = 28;
	private static final Pattern CONTENTS = Pattern.compile("(\\d+)\\s*x\\s*([a-zA-Z\\s]*logs?)", Pattern.CASE_INSENSITIVE);

	private Rs2LogBasket() { }

	public static final class BasketContents
	{
		public final int quantity;
		public final String logType;
		public final boolean isEmpty;

		public BasketContents(int quantity, String logType)
		{
			this.quantity = quantity;
			this.logType = logType != null ? logType : "";
			this.isEmpty = quantity == 0;
		}

		/** As upstream: an empty basket (also upstream's answer when the contents could not be read). */
		public static BasketContents empty()
		{
			return new BasketContents(0, "");
		}
	}

	/**
	 * As upstream: the basket's contents from its "Check" message, or {@link BasketContents#empty()}
	 * when there is no basket or the message was not recognised. Use {@link #getCurrentBasketContents()}
	 * to tell "empty" from "unknown".
	 */
	public static BasketContents checkBasketContents()
	{
		BasketContents contents = getCurrentBasketContents();
		return contents != null ? contents : BasketContents.empty();
	}

	/**
	 * As upstream: at an open bank, empties the basket, withdraws up to {@code logsToWithdraw} logs
	 * (at most 27, leaving the basket's slot) and fills the basket from the bank. True once the basket
	 * was filled or the bank has no more of {@code logId}.
	 */
	public static boolean handleLogBasketBanking(int logId, int logsToWithdraw)
	{
		if (!hasLogBasket() || !Rs2Bank.isOpen())
		{
			return false;
		}
		emptyLogBasketAtBank();
		int withdraw = Math.min(logsToWithdraw, 27);
		if (withdraw > 0 && Rs2Bank.withdrawX(logId, withdraw))
		{
			int target = Math.min(withdraw, Rs2Bank.count(logId));
			Rs2Inventory.waitFor(() -> Rs2Inventory.count(logId) >= target, 3_000);
		}
		return fillLogBasketFromBank(logId);
	}

	private static boolean fillLogBasketFromBank(int logId)
	{
		if (!hasLogBasket() || !Rs2Bank.isOpen())
		{
			return false;
		}
		int bankLogs = Rs2Bank.count(logId);
		if (bankLogs <= 0)
		{
			return true;
		}
		int free = Rs2Inventory.emptySlotCount();
		if (free <= 0)
		{
			return false;
		}
		int before = Rs2Inventory.count(logId);
		return Rs2Bank.withdrawX(logId, Math.min(Math.min(bankLogs, LOG_BASKET_CAPACITY), free))
			&& Rs2Inventory.waitFor(() -> Rs2Inventory.count(logId) > before, 3_000)
			&& fillLogBasket();
	}

	private static Rs2ItemModel basket()
	{
		return Rs2Inventory.items(i -> i.getName().toLowerCase(Locale.ROOT).contains("log basket"))
			.findFirst().orElse(null);
	}

	public static boolean hasLogBasket() { return basket() != null; }

	// ---- Batch AP (Claude 1): upstream's basket reads ----

	/** As upstream: the open basket's id when carried, else the closed basket's. */
	public static int getLogBasketId()
	{
		return Rs2Inventory.hasItem(net.runelite.api.gameval.ItemID.LOG_BASKET_OPEN)
			? net.runelite.api.gameval.ItemID.LOG_BASKET_OPEN : net.runelite.api.gameval.ItemID.LOG_BASKET_CLOSED;
	}

	/**
	 * As upstream: fill when the basket (checked with "Check") has room and there are at least
	 * {@code minLogsToFill} of {@code logId}, or at most 3 free slots.
	 */
	public static boolean shouldFillBasket(int logId, int minLogsToFill)
	{
		if (!hasLogBasket())
		{
			return false;
		}
		BasketContents contents = checkBasketContents();
		if (contents.quantity >= LOG_BASKET_CAPACITY)
		{
			return false;
		}
		return Rs2Inventory.count(logId) >= minLogsToFill || Rs2Inventory.emptySlotCount() <= 3;
	}

	/** As upstream: {@link #shouldFillBasket(int, int)} with at least 15 logs. */
	public static boolean shouldFillBasket(int logId)
	{
		return shouldFillBasket(logId, 15);
	}

	/** As upstream's estimate: 53 logs with a basket (28 + about 25 in the inventory), else 25. */
	public static int getEffectiveLogCapacity()
	{
		return hasLogBasket() ? 53 : 25;
	}

	/** As upstream: at least 15 of {@code logId} and at most 3 free slots. */
	public static boolean isInventoryNearFull(int logId)
	{
		return Rs2Inventory.count(logId) >= 15 && Rs2Inventory.emptySlotCount() <= 3;
	}

	/** As upstream: how many of {@code logId} the inventory holds. */
	public static int getLogCount(int logId)
	{
		return Rs2Inventory.count(logId);
	}

	/** As upstream: a basket is carried and the workflow banks. */
	public static boolean shouldUseLogBasket(boolean willBank)
	{
		return hasLogBasket() && willBank;
	}

	/** As upstream: an open basket counts as holding logs; a closed one is checked. */
	public static boolean hasLogs()
	{
		if (!hasLogBasket())
		{
			return false;
		}
		return Rs2Inventory.hasItem(net.runelite.api.gameval.ItemID.LOG_BASKET_OPEN) || checkBasketContents().quantity > 0;
	}

	/** As upstream: a status suffix for overlays, empty without a basket. */
	public static String getLogBasketStatus(int logId)
	{
		if (!hasLogBasket())
		{
			return "";
		}
		boolean open = Rs2Inventory.hasItem(net.runelite.api.gameval.ItemID.LOG_BASKET_OPEN);
		return String.format(" | Basket: %s (%d logs)", open ? "Open" : "Available", Rs2Inventory.count(logId));
	}

	/** As upstream: the checked basket holds 28 logs. */
	public static boolean isBasketFull()
	{
		return checkBasketContents().quantity >= LOG_BASKET_CAPACITY;
	}

	/** As upstream: the checked basket is empty (also when the check could not be read). */
	public static boolean isBasketEmpty()
	{
		return checkBasketContents().isEmpty;
	}

	public static boolean fillLogBasket()
	{
		Rs2ItemModel basket = basket();
		if (basket == null) return false;
		int before = logSlots();
		return before > 0 && Rs2Inventory.waitFor(() -> MobileActions.inventory(basket.getSlot(), basket.getId(), "Fill"), 2_000)
			&& Rs2Inventory.waitFor(() -> logSlots() < before, 3_000);
	}

	private static int logSlots()
	{
		return Rs2Inventory.count(i -> i.getName().toLowerCase(Locale.ROOT).endsWith("logs"));
	}

	public static boolean emptyLogBasketAtBank()
	{
		Rs2ItemModel basket = basket();
		if (basket == null || !Rs2Bank.isOpen()) return false;
		int before = bankLogs();
		return Rs2Inventory.waitFor(() -> MobileActions.inventory(basket.getSlot(), basket.getId(), "Empty"), 2_000)
			&& Rs2Inventory.waitFor(() -> bankLogs() > before, 3_000);
	}

	private static int bankLogs()
	{
		return MobileActions.read(() ->
		{
			ItemContainer bank = Microbot.getClient().getItemContainer(InventoryID.BANK);
			if (bank == null || bank.getItems() == null) return 0;
			return Arrays.stream(bank.getItems()).filter(i ->
			{
				if (i == null || i.getId() < 0) return false;
				ItemComposition definition = Microbot.getClient().getItemDefinition(i.getId());
				return definition != null && definition.getName() != null
					&& definition.getName().toLowerCase(Locale.ROOT).endsWith("logs");
			}).mapToInt(i -> i.getQuantity()).sum();
		}, 0);
	}

	/** Returns null if Check did not yield a recognized message, never assumes an empty basket. */
	public static BasketContents getCurrentBasketContents()
	{
		Rs2ItemModel basket = basket();
		if (basket == null) return null;
		// Dismiss an old dialogue first; its text is not evidence of this Check action.
		if (message() != null && !closeMessage()) return null;
		if (!Rs2Inventory.waitFor(() -> MobileActions.inventory(basket.getSlot(), basket.getId(), "Check"), 2_000)) return null;
		if (!Rs2Inventory.waitFor(() -> parse(message()) != null, 2_000)) return null;
		BasketContents result = parse(message());
		return closeMessage() ? result : null;
	}

	private static boolean closeMessage()
	{
		return Rs2Inventory.waitFor(() -> MobileActions.key(java.awt.event.KeyEvent.VK_SPACE), 2_000)
			&& Rs2Inventory.waitFor(() -> message() == null, 2_000);
	}

	private static String message()
	{
		return MobileActions.read(() ->
		{
			Widget message = Microbot.getClient().getWidget(193, 2);
			return message == null || message.isHidden() ? null : message.getText();
		}, null);
	}

	static BasketContents parse(String text)
	{
		if (text == null) return null;
		String plain = text.replaceAll("<[^>]*>", " ").replace("&nbsp;", " ");
		Matcher matcher = CONTENTS.matcher(plain);
		if (matcher.find())
		{
			try
			{
				int count = Integer.parseInt(matcher.group(1));
				return count <= LOG_BASKET_CAPACITY ? new BasketContents(count, matcher.group(2).trim()) : null;
			}
			catch (NumberFormatException exception) { return null; }
		}
		String lower = plain.toLowerCase(Locale.ROOT);
		return lower.contains("basket") && (lower.contains("empty") || lower.contains("nothing"))
			? new BasketContents(0, "") : null;
	}
}
