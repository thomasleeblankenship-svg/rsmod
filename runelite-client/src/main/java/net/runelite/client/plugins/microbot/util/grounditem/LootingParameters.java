package net.runelite.client.plugins.microbot.util.grounditem;

/** Immutable named-loot request used by the uploaded woodcutting script. */
public final class LootingParameters
{
	private final int range;
	private final int minItems;
	private final int minQuantity;
	private final int minInvSlots;
	private final boolean delayedLooting;
	private final boolean antiLureProtection;
	private final String[] names;
	public LootingParameters(int range, int minItems, int minQuantity, int minInvSlots,
		boolean delayedLooting, boolean antiLureProtection, String... names)
	{
		this.range = Math.max(0, range);
		this.minItems = Math.max(1, minItems);
		this.minQuantity = Math.max(1, minQuantity);
		this.minInvSlots = Math.max(0, minInvSlots);
		this.delayedLooting = delayedLooting;
		this.antiLureProtection = antiLureProtection;
		this.names = names == null ? new String[0] : names.clone();
	}
	public int getRange() { return range; }
	public int getMinItems() { return minItems; }
	public int getMinQuantity() { return minQuantity; }
	public int getMinInvSlots() { return minInvSlots; }
	public boolean isDelayedLooting() { return delayedLooting; }
	public boolean isAntiLureProtection() { return antiLureProtection; }
	public String[] getNames() { return names.clone(); }
}
