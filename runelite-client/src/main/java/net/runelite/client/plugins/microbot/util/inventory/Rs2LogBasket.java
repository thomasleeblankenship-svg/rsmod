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
			this.logType = logType;
			this.isEmpty = quantity == 0;
		}
	}

	private static Rs2ItemModel basket()
	{
		return Rs2Inventory.items(i -> i.getName().toLowerCase(Locale.ROOT).contains("log basket"))
			.findFirst().orElse(null);
	}

	public static boolean hasLogBasket() { return basket() != null; }

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
