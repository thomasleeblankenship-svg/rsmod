package net.runelite.client.plugins.microbot.util.antiban.enums;

/**
 * Activity categories (label only on mobile).
 * <p>
 * Mobile: the constants and display names of chsami/Microbot {@code util/antiban/enums/Category.java} at
 * {@code a2114401bbbc35e9762db9e8aa666d70ffcd24d8} (BSD 2-Clause), with no behaviour. Upstream attaches
 * anti-ban timing and busy-detection logic to each constant; that is an anti-detection feature and is
 * not ported. Kept so scripts that name these constants compile; nothing on mobile reads them.
 */
public enum Category
{
	COMBAT_MID("Combat/Mid"),
	SKILLING_AGILITY("Skilling/Agility"),
	PROCESSING("Processing"),
	COLLECTING("Collecting"),
	SKILLING_CRAFTING("Skilling/Crafting"),
	SKILLING_MAGIC("Skilling/Magic"),
	COMBAT_LOW("Combat/Low"),
	SKILLING_HERBLORE("Skilling/Herblore"),
	SKILLING_FLETCHING("Skilling/Fletching"),
	SKILLING_FISHING("Skilling/Fishing"),
	PROCESSING_MAGIC("Processing/Magic"),
	SKILLING_COOKING("Skilling/Cooking"),
	SKILLING_FIREMAKING("Skilling/Firemaking"),
	SKILLING_THIEVING("Skilling/Thieving"),
	SKILLING("Skilling"),
	COLLECTING_NONE("Collecting/None"),
	COMBAT_HIGH("Combat/High"),
	SKILLING_WOODCUTTING("Skilling/Woodcutting"),
	SKILLING_MINING("Skilling/Mining"),
	SKILLING_RUNECRAFT("Skilling/Runecraft"),
	SKILLING_SMITHING("Skilling/Smithing"),
	SKILLING_HUNTER("Skilling/Hunter"),
	SKILLING_FARMING("Skilling/Farming"),
	SKILLING_PRAYER("Skilling/Prayer"),
	SKILLING_CONSTRUCTION("Skilling/Construction");

	private final String name;

	Category(String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}
}
