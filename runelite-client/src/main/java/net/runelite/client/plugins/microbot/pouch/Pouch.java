package net.runelite.client.plugins.microbot.pouch;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Runecrafting essence pouches and what each currently holds, as far as {@link PouchTracker} has
 * observed. A pouch starts {@link #isUnknown() unknown} and becomes known once a Check message, a
 * "full"/"empty" message or a complete fill or empty pins its contents.
 * <p>
 * Mobile model: written for RuneMobile from the Microbot API names. Actions dispatch one inventory
 * option and wait for an observed result. State is mutated only by {@link PouchTracker} on the
 * client thread; reads may happen from any thread.
 */
public enum Pouch
{
	SMALL(new int[]{ItemID.RCU_POUCH_SMALL}, 1, new int[]{3}, new int[]{3}),
	MEDIUM(new int[]{ItemID.RCU_POUCH_MEDIUM, ItemID.RCU_POUCH_MEDIUM_DEGRADE}, 25, new int[]{6}, new int[]{3}),
	LARGE(new int[]{ItemID.RCU_POUCH_LARGE, ItemID.RCU_POUCH_LARGE_DEGRADE}, 50, new int[]{9}, new int[]{7}),
	GIANT(new int[]{ItemID.RCU_POUCH_GIANT, ItemID.RCU_POUCH_GIANT_DEGRADE}, 75, new int[]{12}, new int[]{9}),
	/** Capacity grows with boosted Runecraft: 25, 50, 75 and 85. */
	COLOSSAL(new int[]{ItemID.RCU_POUCH_COLOSSAL, ItemID.RCU_POUCH_COLOSSAL_DEGRADE, ItemID.DEVIOUS_GLOWINGPOUCH_COLOSSAL},
		25, new int[]{8, 16, 27, 40}, new int[]{6, 13, 23, 35});

	private final int[] itemIds;
	private final int levelRequired;
	private final int[] capacity;
	private final int[] degradedCapacity;
	private volatile int holding;
	private volatile boolean unknown = true;
	private volatile boolean degraded;
	private static final Pattern ACTION_CHECK_REPLY = Pattern.compile(
		"^There (?:is|are) ([a-z-]+)(?: pure| daeyalt| guardian)? essences? in this pouch\\.$");

	Pouch(int[] itemIds, int levelRequired, int[] capacity, int[] degradedCapacity)
	{
		this.itemIds = itemIds;
		this.levelRequired = levelRequired;
		this.capacity = capacity;
		this.degradedCapacity = degradedCapacity;
	}

	/** The pouch for an item id (intact or degraded), or {@code null}. */
	public static Pouch forItem(int itemId)
	{
		for (Pouch pouch : values())
		{
			for (int id : pouch.itemIds)
			{
				if (id == itemId)
				{
					return pouch;
				}
			}
		}
		return null;
	}

	/** Whether {@code itemId} is a degraded pouch. */
	static boolean isDegradedItem(int itemId)
	{
		return itemId == ItemID.RCU_POUCH_MEDIUM_DEGRADE || itemId == ItemID.RCU_POUCH_LARGE_DEGRADE
			|| itemId == ItemID.RCU_POUCH_GIANT_DEGRADE || itemId == ItemID.RCU_POUCH_COLOSSAL_DEGRADE;
	}

	public int[] getItemIds()
	{
		return itemIds.clone();
	}

	public int getLevelRequired()
	{
		return levelRequired;
	}

	/** Essence the pouch holds now (0 while unknown and never observed). */
	public int getHolding()
	{
		return holding;
	}

	public boolean isUnknown()
	{
		return unknown;
	}

	public boolean isDegraded()
	{
		return degraded;
	}

	/** How much the pouch holds when full, for its degraded state and (colossal) Runecraft level. */
	public int getHoldAmount()
	{
		return capacityFor(Rs2Player.getBoostedSkillLevel(Skill.RUNECRAFT));
	}

	int capacityFor(int runecraftLevel)
	{
		int[] table = degraded ? degradedCapacity : capacity;
		if (table.length == 1)
		{
			return table[0];
		}
		int tier = runecraftLevel >= 85 ? 3 : runecraftLevel >= 75 ? 2 : runecraftLevel >= 50 ? 1 : 0;
		return table[tier];
	}

	/** Space left: capacity minus holding. */
	public int getRemaining()
	{
		return getHoldAmount() - holding;
	}

	public boolean hasRequiredRunecraftingLevel()
	{
		return Rs2Player.getRealSkillLevel(Skill.RUNECRAFT) >= levelRequired;
	}

	public boolean hasPouchInInventory()
	{
		return Rs2Inventory.hasItem(itemIds);
	}

	/** One Fill option, confirmed by a tracker update; never assumes the pouch is full. */
	public boolean fill()
	{
		return act("Fill");
	}

	/** One Empty option, confirmed by a tracker update; never invents an essence count. */
	public boolean empty()
	{
		return act("Empty");
	}

	/** One Check option, confirmed by its game-message reply, even if the count is unchanged. */
	public boolean check()
	{
		return act("Check");
	}

	private boolean act(String option)
	{
		Client client = Microbot.getClient();
		ScriptInputSession owner = MobileActions.currentPermission();
		long epoch = ScriptInputSession.inputEpoch();
		if (client == null || client.isClientThread() || !owns(owner, epoch) || !hasRequiredRunecraftingLevel()) return false;
		Rs2ItemModel item = Rs2Inventory.items().filter(i -> forItem(i.getId()) == this).findFirst().orElse(null);
		if (item == null) return false;
		boolean checking = "Check".equals(option);
		boolean filling = "Fill".equals(option);
		if (filling && (getRemaining() <= 0 || !Rs2Inventory.hasItem(ItemID.BLANKRUNE_HIGH,
			ItemID.BLANKRUNE_DAEYALT, ItemID.GOTR_GUARDIAN_ESSENCE))) return false;
		if ("Empty".equals(option) && (getHolding() <= 0 || Rs2Inventory.isFull())) return false;
		int before = getHolding();
		boolean wasUnknown = isUnknown();
		AtomicBoolean replyObserved = new AtomicBoolean();
		AtomicInteger checkTick = new AtomicInteger(Integer.MIN_VALUE);
		EventBus events = Microbot.getEventBus();
		if (events == null) return false;
		EventBus.Subscriber click = null;
		EventBus.Subscriber reply = null;
		try
		{
			if (checking)
			{
				// Track this invocation's Check, with the same three-tick window as PouchTracker.
				// Priority -1 runs after the real tracker, so only its observed count is accepted.
				click = events.register(MenuOptionClicked.class, event ->
				{
					if (client.isClientThread() && event.getItemId() == item.getId()
						&& "Check".equalsIgnoreCase(event.getMenuOption())) checkTick.set(client.getTickCount());
				}, -1f);
				reply = events.register(ChatMessage.class, event ->
				{
					int started = checkTick.get();
					if (!client.isClientThread() || started == Integer.MIN_VALUE || !owns(owner, epoch)
						|| client.getTickCount() - started > 3 || event.getType() != ChatMessageType.GAMEMESSAGE
						|| event.getMessage() == null) return;
					Matcher match = ACTION_CHECK_REPLY.matcher(event.getMessage());
					if (match.matches() && !isUnknown() && PouchTracker.parseCount(match.group(1)) == getHolding())
						replyObserved.set(true);
				}, -1f);
			}
			if (!owns(owner, epoch) || !Rs2Inventory.interact(item, option)) return false;
			return Global.sleepUntil(() -> !owns(owner, epoch) || (checking ? replyObserved.get()
				: getHolding() != before || isUnknown() != wasUnknown), 2_000) && owns(owner, epoch)
				&& (checking ? replyObserved.get() : getHolding() != before || isUnknown() != wasUnknown);
		}
		finally
		{
			if (click != null) events.unregister(click);
			if (reply != null) events.unregister(reply);
		}
	}

	private static boolean owns(ScriptInputSession owner, long epoch)
	{
		return owner != null && MobileActions.currentPermission() == owner && owner.allows(epoch) && MobileActions.active() && Microbot.isLoggedIn();
	}

	void setHolding(int amount, int holdAmount)
	{
		holding = Math.max(0, Math.min(amount, holdAmount));
	}

	void setUnknown(boolean value)
	{
		unknown = value;
	}

	void setDegraded(boolean value)
	{
		degraded = value;
	}

	/** Forgets everything observed (new session). */
	static void resetAll()
	{
		for (Pouch pouch : values())
		{
			pouch.holding = 0;
			pouch.unknown = true;
			pouch.degraded = false;
		}
	}
}
