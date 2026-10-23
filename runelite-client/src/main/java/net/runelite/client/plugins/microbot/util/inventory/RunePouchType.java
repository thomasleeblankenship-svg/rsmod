package net.runelite.client.plugins.microbot.util.inventory;

/** The rune pouch variants by item id ({@link Rs2RunePouch#POUCH_IDS} in enum form). */
public enum RunePouchType
{
	STANDARD(Rs2RunePouch.POUCH_IDS[0]),
	UPGRADED(Rs2RunePouch.POUCH_IDS[1]),
	LARGE(Rs2RunePouch.POUCH_IDS[2]),
	DIVINE(Rs2RunePouch.POUCH_IDS[3]),
	DIVINE_LARGE(Rs2RunePouch.POUCH_IDS[4]);

	private final int itemId;

	RunePouchType(int itemId)
	{
		this.itemId = itemId;
	}

	public int getItemId()
	{
		return itemId;
	}
}
