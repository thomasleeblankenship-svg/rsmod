package net.runelite.client.plugins.microbot.pouch;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;

/**
 * Keeps {@link Pouch} contents current from what the game shows: the pouch's Fill, Empty, Check
 * and Take menu clicks, the Check reply and the "full"/"nothing to empty" messages, and the
 * inventory's essence and free-space counts after a fill or empty. A click is matched to a result
 * only within three game ticks.
 * <p>
 * Owned by {@code MicrobotReadServices}, which routes the events here on the client thread and
 * calls {@link #reset()} on logout and shutdown.
 */
public final class PouchTracker
{
	private static final int INVENTORY_SIZE = 28;
	private static final int MATCH_TICKS = 3;
	private static final Pattern CHECK_REPLY = Pattern.compile(
		"^There (?:is|are) ([a-z-]+)(?: pure| daeyalt| guardian)? essences? in this pouch\\.$");
	private static final List<String> UNITS = List.of("no", "one", "two", "three", "four", "five", "six",
		"seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
		"seventeen", "eighteen", "nineteen");
	private static final List<String> TENS = List.of("", "", "twenty", "thirty", "forty");

	private static final class Click
	{
		final Pouch pouch;
		final int sign;
		final int expiresAtTick;

		Click(Pouch pouch, int sign, int expiresAtTick)
		{
			this.pouch = pouch;
			this.sign = sign;
			this.expiresAtTick = expiresAtTick;
		}
	}

	private final Client client;
	private final Deque<Click> transfers = new ArrayDeque<>();
	private final Deque<Click> checks = new ArrayDeque<>();
	private int lastEssence = -1;
	private int lastSpace = -1;

	public PouchTracker(Client client)
	{
		this.client = client;
	}

	/** Forgets every pending click and all pouch contents. */
	public void reset()
	{
		transfers.clear();
		checks.clear();
		lastEssence = -1;
		lastSpace = -1;
		Pouch.resetAll();
	}

	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		Pouch pouch = Pouch.forItem(event.getItemId());
		if (pouch == null || event.getMenuOption() == null)
		{
			return;
		}
		int expires = client.getTickCount() + MATCH_TICKS;
		switch (event.getMenuOption().toLowerCase(Locale.ROOT))
		{
			case "fill":
				transfers.add(new Click(pouch, 1, expires));
				break;
			case "empty":
				transfers.add(new Click(pouch, -1, expires));
				break;
			case "check":
				checks.add(new Click(pouch, 0, expires));
				break;
			case "take":
				// A dropped pouch loses its contents.
				pouch.setHolding(0, 0);
				break;
			default:
				break;
		}
	}

	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE || event.getMessage() == null)
		{
			return;
		}
		String message = event.getMessage();
		Matcher reply = CHECK_REPLY.matcher(message);
		// "There is no essence in this pouch." also answers an Empty, so it is a Check reply only
		// while a Check click is waiting.
		if (!checks.isEmpty() && reply.matches())
		{
			int amount = parseCount(reply.group(1));
			Click click = nextLive(checks);
			if (click != null && amount >= 0)
			{
				click.pouch.setHolding(amount, Integer.MAX_VALUE);
				click.pouch.setUnknown(false);
			}
			return;
		}
		if (message.contains("You cannot add any more"))
		{
			Click click = nextLive(transfers);
			if (click != null)
			{
				click.pouch.setHolding(holdAmount(click.pouch), holdAmount(click.pouch));
				click.pouch.setUnknown(false);
			}
		}
		else if (message.contains("There is no"))
		{
			Click click = nextLive(transfers);
			if (click != null)
			{
				click.pouch.setHolding(0, 0);
				click.pouch.setUnknown(false);
			}
		}
	}

	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV || event.getItemContainer() == null)
		{
			return;
		}
		Item[] items = event.getItemContainer().getItems();
		int essence = 0;
		int space = Math.max(0, INVENTORY_SIZE - items.length);
		for (Item item : items)
		{
			int id = item.getId();
			if (id == -1)
			{
				space++;
			}
			else if (id == ItemID.BLANKRUNE_HIGH || id == ItemID.BLANKRUNE_DAEYALT || id == ItemID.GOTR_GUARDIAN_ESSENCE)
			{
				essence++;
			}
			Pouch pouch = Pouch.forItem(id);
			if (pouch != null)
			{
				pouch.setDegraded(Pouch.isDegradedItem(id));
			}
		}

		if (!transfers.isEmpty() && lastEssence >= 0)
		{
			applyTransfers(lastEssence, lastSpace, essence);
		}
		else
		{
			transfers.clear();
		}
		lastEssence = essence;
		lastSpace = space;
	}

	/** Moves the observed essence change into the clicked pouches, oldest click first. */
	private void applyTransfers(int essenceBefore, int spaceBefore, int essenceAfter)
	{
		int essence = essenceBefore;
		int space = spaceBefore;
		while (essence != essenceAfter)
		{
			Click click = nextLive(transfers);
			if (click == null)
			{
				return;
			}
			Pouch pouch = click.pouch;
			int capacity = holdAmount(pouch);
			boolean fill = click.sign > 0;
			int available = fill ? essence : space;
			int moved = Math.min(fill ? capacity - pouch.getHolding() : pouch.getHolding(), available);
			if (available >= capacity)
			{
				// Enough essence (or space) for the whole pouch, so its contents are now certain.
				pouch.setUnknown(false);
			}
			pouch.setHolding(pouch.getHolding() + click.sign * moved, capacity);
			essence -= click.sign * moved;
			space += click.sign * moved;
			if (moved == 0)
			{
				return;
			}
		}
	}

	private Click nextLive(Deque<Click> queue)
	{
		int tick = client.getTickCount();
		while (!queue.isEmpty())
		{
			Click click = queue.poll();
			if (click.expiresAtTick >= tick)
			{
				return click;
			}
		}
		return null;
	}

	private int holdAmount(Pouch pouch)
	{
		return pouch.capacityFor(client.getBoostedSkillLevel(Skill.RUNECRAFT));
	}

	/** "no", "seven", "twenty-three", ... "forty" as a number; -1 when not a count word. */
	static int parseCount(String words)
	{
		int unit = UNITS.indexOf(words);
		if (unit >= 0)
		{
			return unit;
		}
		String[] parts = words.split("-", 2);
		int tens = TENS.indexOf(parts[0]);
		if (tens < 2)
		{
			return -1;
		}
		if (parts.length == 1)
		{
			return tens * 10;
		}
		int ones = UNITS.indexOf(parts[1]);
		return ones >= 1 && ones <= 9 ? tens * 10 + ones : -1;
	}
}
