package net.runelite.client.plugins.microbot.pouch;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Runecrafting essence pouches and what each currently holds, as far as {@link PouchTracker} has
 * observed. A pouch starts {@link #isUnknown() unknown} and becomes known once a Check message, a
 * "full"/"empty" message or a complete fill or empty pins its contents.
 * <p>
 * Mobile read model: written for RuneMobile from the Microbot API names. Filling, emptying and
 * checking (actions) are not part of it. State is mutated only by {@link PouchTracker} on the
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
