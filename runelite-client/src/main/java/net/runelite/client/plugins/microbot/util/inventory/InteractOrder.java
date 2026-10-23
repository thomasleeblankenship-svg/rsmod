package net.runelite.client.plugins.microbot.util.inventory;

/** The desktop script's inventory traversal modes. */
public enum InteractOrder
{
	STANDARD, EFFICIENT_ROW, COLUMN, EFFICIENT_COLUMN, ZIGZAG, RANDOM;

	public static InteractOrder random()
	{
		return values()[java.util.concurrent.ThreadLocalRandom.current().nextInt(values().length)];
	}
}
