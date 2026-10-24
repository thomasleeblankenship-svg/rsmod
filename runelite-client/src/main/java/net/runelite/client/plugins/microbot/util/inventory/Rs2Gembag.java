package net.runelite.client.plugins.microbot.util.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.text.Rs2TextSanitizer;

/**
 * Gem bag contents (upstream Microbot 2.6.38 {@code Rs2Gembag}), tracked from events: "Check"
 * messages set the counts, "You just found a ..." adds one gem to an open bag, and Fill / Empty
 * are measured on the next inventory change. Counts are unknown until the first check. RuneMobile
 * adaptation: Fill/Empty compare the inventory before the click with the next inventory event
 * instead of upstream's waiting thread; the events arrive from {@code MicrobotReadServices}.
 * {@code checkGemBag} (an action) is added by Claude 1.
 */
public final class Rs2Gembag
{
	/** The five gems, in the bag's slot order. */
	enum Gem
	{
		SAPPHIRE(ItemID.UNCUT_SAPPHIRE),
		EMERALD(ItemID.UNCUT_EMERALD),
		RUBY(ItemID.UNCUT_RUBY),
		DIAMOND(ItemID.UNCUT_DIAMOND),
		DRAGONSTONE(ItemID.UNCUT_DRAGONSTONE);

		final int itemId;

		Gem(int itemId)
		{
			this.itemId = itemId;
		}

		static Gem byItemId(int itemId)
		{
			for (Gem gem : values())
			{
				if (gem.itemId == itemId)
				{
					return gem;
				}
			}
			return null;
		}
	}

	private static final Pattern GEM_PATTERN = Pattern.compile("You just found (?:an|a) (sapphire|emerald|ruby|diamond|dragonstone)!", Pattern.CASE_INSENSITIVE);
	private static final Pattern CHECK_PATTERN = Pattern.compile("Sapphires: (\\d+) / Emeralds: (\\d+) / Rubies: (\\d+) / Diamonds: (\\d+) / Dragonstones: (\\d+)");
	private static final Pattern BR_TAG = Pattern.compile("(?i)<br\\s*/?>");
	private static final List<Integer> GEM_BAG_IDS = List.of(ItemID.GEM_BAG, ItemID.GEM_BAG_OPEN);

	private static final Map<Gem, Integer> COUNTS = new EnumMap<>(Gem.class);
	private static boolean unknown = true;
	/** Uncut gems in the inventory when Fill or Empty was clicked; {@code null} when nothing is pending. */
	private static Map<Gem, Integer> pendingBefore;
	private static boolean pendingFill;

	private Rs2Gembag()
	{
	}

	/** As upstream: the gem bag item ids (closed, open). */
	public static List<Integer> getGemBagItemIds()
	{
		return GEM_BAG_IDS;
	}

	/** As upstream: the known contents as item models (quantity = count, slot = bag slot); empty while unknown. */
	public static synchronized List<Rs2ItemModel> getGemBagContents()
	{
		if (COUNTS.isEmpty())
		{
			return Collections.emptyList();
		}
		Map<Gem, Integer> counts = new EnumMap<>(COUNTS);
		return MobileActions.read(() ->
		{
			List<Rs2ItemModel> models = new ArrayList<>();
			for (Map.Entry<Gem, Integer> entry : counts.entrySet())
			{
				ItemComposition definition = Microbot.getClient().getItemDefinition(entry.getKey().itemId);
				models.add(new Rs2ItemModel(new Item(entry.getKey().itemId, entry.getValue()), definition, null, entry.getKey().ordinal()));
			}
			return models;
		}, Collections.emptyList());
	}

	/** As upstream: the count of the gem named {@code gem} (for example "Uncut ruby"); 0 while unknown. */
	public static synchronized int getGemCount(String gem)
	{
		if (!hasGemBag() || unknown || gem == null)
		{
			return 0;
		}
		for (Gem g : Gem.values())
		{
			if (gem.equalsIgnoreCase("uncut " + g.name().toLowerCase(Locale.ROOT)))
			{
				return COUNTS.getOrDefault(g, 0);
			}
		}
		return 0;
	}

	/** As upstream: all gems in the bag; 0 while unknown. */
	public static synchronized int getTotalGemCount()
	{
		return !hasGemBag() || unknown ? 0 : COUNTS.values().stream().mapToInt(Integer::intValue).sum();
	}

	/** As upstream: a gem slot holds 60. */
	public static synchronized boolean isAnyGemSlotFull()
	{
		return hasGemBag() && !unknown && COUNTS.values().stream().anyMatch(count -> count == 60);
	}

	/** As upstream: a gem bag (closed or open) is in the inventory. */
	public static boolean hasGemBag()
	{
		return GEM_BAG_IDS.stream().anyMatch(Rs2Inventory::hasItem);
	}

	public static boolean isGemBagOpen()
	{
		return Rs2Inventory.hasItem(ItemID.GEM_BAG_OPEN);
	}

	/** As upstream: a gem bag is carried and its contents have not been checked yet. */
	public static synchronized boolean isUnknown()
	{
		return unknown && hasGemBag();
	}

	/** As upstream: forgets the contents (they stay unknown until the next check). */
	public static synchronized void resetGemBagContents()
	{
		COUNTS.clear();
	}

	/** Chat messages: a "Check" result sets the counts; a found gem adds one to an open bag. */
	public static void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		String message = event.getMessage();
		if (message == null)
		{
			return;
		}
		Matcher found = GEM_PATTERN.matcher(message);
		if (found.find() && hasGemBag() && isGemBagOpen())
		{
			add(Gem.valueOf(found.group(1).toUpperCase(Locale.ROOT)), 1);
		}
		String withBreaks = BR_TAG.matcher(Rs2TextSanitizer.normalizeGameText(message)).replaceAll(" / ");
		String cleaned = Rs2TextSanitizer.normalizeApostrophes(Rs2TextSanitizer.stripTags(Rs2TextSanitizer.decodeKnownEntities(withBreaks))).trim();
		Matcher check = CHECK_PATTERN.matcher(cleaned);
		if (check.find())
		{
			synchronized (Rs2Gembag.class)
			{
				COUNTS.clear();
				for (Gem gem : Gem.values())
				{
					COUNTS.put(gem, Integer.parseInt(check.group(gem.ordinal() + 1)));
				}
				unknown = false;
			}
		}
	}

	/** "Fill" / "Empty" on an open gem bag: remember the inventory gems, settled by the next inventory change. */
	public static void onMenuOptionClicked(MenuOptionClicked event)
	{
		String option = event.getMenuOption();
		String target = event.getMenuTarget() == null ? "" : Rs2UiHelper.stripColTags(event.getMenuTarget());
		if (option == null || !target.toLowerCase(Locale.ROOT).contains("gem bag") || !hasGemBag() || !isGemBagOpen())
		{
			return;
		}
		if ("empty".equalsIgnoreCase(option) && Rs2Bank.isOpen())
		{
			synchronized (Rs2Gembag.class)
			{
				COUNTS.clear();
				unknown = false;
			}
			return;
		}
		if ("empty".equalsIgnoreCase(option) || "fill".equalsIgnoreCase(option))
		{
			Map<Gem, Integer> before = inventoryGems(Rs2Inventory.all());
			synchronized (Rs2Gembag.class)
			{
				pendingBefore = before;
				pendingFill = "fill".equalsIgnoreCase(option);
			}
		}
	}

	/** Settles a pending Fill (gems left the inventory) or Empty (gems arrived) from the new inventory. */
	public static void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INVENTORY.getId())
		{
			return;
		}
		Map<Gem, Integer> before;
		boolean fill;
		synchronized (Rs2Gembag.class)
		{
			before = pendingBefore;
			fill = pendingFill;
			pendingBefore = null;
		}
		if (before == null || event.getItemContainer() == null)
		{
			return;
		}
		Map<Gem, Integer> after = new HashMap<>();
		for (Item item : event.getItemContainer().getItems())
		{
			Gem gem = item == null ? null : Gem.byItemId(item.getId());
			if (gem != null)
			{
				after.merge(gem, 1, Integer::sum);
			}
		}
		for (Gem gem : Gem.values())
		{
			int change = after.getOrDefault(gem, 0) - before.getOrDefault(gem, 0);
			if (fill && change < 0)
			{
				add(gem, -change);
			}
			else if (!fill && change > 0)
			{
				add(gem, -change);
			}
		}
	}

	private static synchronized void add(Gem gem, int quantity)
	{
		COUNTS.merge(gem, quantity, Integer::sum);
	}

	private static Map<Gem, Integer> inventoryGems(List<Rs2ItemModel> items)
	{
		Map<Gem, Integer> gems = new HashMap<>();
		for (Rs2ItemModel item : items)
		{
			Gem gem = Gem.byItemId(item.getId());
			if (gem != null)
			{
				gems.merge(gem, 1, Integer::sum);
			}
		}
		return gems;
	}

	/** Clears all tracked state (logout). */
	public static synchronized void clear()
	{
		COUNTS.clear();
		unknown = true;
		pendingBefore = null;
	}
}
