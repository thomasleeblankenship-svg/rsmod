package net.runelite.client.plugins.microbot.util.grounditem;

/**
 * A loot request with upstream's constructors: by item names, or by value (a stack worth more than
 * {@code minValue} in total, each item worth less than {@code maxValue}, skipping
 * {@code ignoredNames}). Only {@code eatFoodForSpace} can change after construction.
 */
public final class LootingParameters
{
	private final int minValue;
	private final int maxValue;
	private final int range;
	private final int minItems;
	private final int minQuantity;
	private final int minInvSlots;
	private final boolean delayedLooting;
	private final boolean antiLureProtection;
	private final String[] names;
	private final String[] ignoredNames;
	private volatile boolean eatFoodForSpace;

	/** As upstream: loot by name. */
	public LootingParameters(int range, int minItems, int minQuantity, int minInvSlots,
		boolean delayedLooting, boolean antiLureProtection, String... names)
	{
		this(0, 0, range, minItems, minQuantity, minInvSlots, delayedLooting, antiLureProtection, names, null);
	}

	/** As upstream: loot by value. */
	public LootingParameters(int minValue, int maxValue, int range, int minItems, int minInvSlots,
		boolean delayedLooting, boolean antiLureProtection)
	{
		this(minValue, maxValue, range, minItems, 1, minInvSlots, delayedLooting, antiLureProtection, null, null);
	}

	/** As upstream: loot by value, skipping items whose name is in {@code ignoredNames} (ignoring case). */
	public LootingParameters(int minValue, int maxValue, int range, int minItems, int minInvSlots,
		boolean delayedLooting, boolean antiLureProtection, String[] ignoredNames)
	{
		this(minValue, maxValue, range, minItems, 1, minInvSlots, delayedLooting, antiLureProtection, null, ignoredNames);
	}

	private LootingParameters(int minValue, int maxValue, int range, int minItems, int minQuantity, int minInvSlots,
		boolean delayedLooting, boolean antiLureProtection, String[] names, String[] ignoredNames)
	{
		this.minValue = minValue;
		this.maxValue = maxValue;
		this.range = Math.max(0, range);
		this.minItems = Math.max(1, minItems);
		this.minQuantity = Math.max(1, minQuantity);
		this.minInvSlots = Math.max(0, minInvSlots);
		this.delayedLooting = delayedLooting;
		this.antiLureProtection = antiLureProtection;
		this.names = names == null ? new String[0] : names.clone();
		this.ignoredNames = ignoredNames == null ? new String[0] : ignoredNames.clone();
	}

	public int getMinValue() { return minValue; }
	public int getMaxValue() { return maxValue; }
	public int getRange() { return range; }
	public int getMinItems() { return minItems; }
	public int getMinQuantity() { return minQuantity; }
	public int getMinInvSlots() { return minInvSlots; }
	public boolean isDelayedLooting() { return delayedLooting; }
	public boolean isAntiLureProtection() { return antiLureProtection; }
	public String[] getNames() { return names.clone(); }
	public String[] getIgnoredNames() { return ignoredNames.clone(); }
	/** As upstream: whether a script may eat food to make room for loot (read by the script). */
	public boolean isEatFoodForSpace() { return eatFoodForSpace; }
	public void setEatFoodForSpace(boolean eatFoodForSpace) { this.eatFoodForSpace = eatFoodForSpace; }
}
