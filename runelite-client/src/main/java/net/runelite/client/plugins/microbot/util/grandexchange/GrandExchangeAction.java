package net.runelite.client.plugins.microbot.util.grandexchange;

/** The Grand Exchange actions a {@link GrandExchangeRequest} can ask for (upstream Microbot 2.6.38). */
public enum GrandExchangeAction
{
	/** A buy offer. */
	BUY,
	/** Collecting completed offers or items. */
	COLLECT,
	/** A sell offer. */
	SELL
}
