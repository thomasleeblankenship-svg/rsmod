package net.runelite.client.plugins.microbot.util.antiban.enums;

import java.util.function.BooleanSupplier;
import net.runelite.api.AnimationID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Activity categories with upstream's "is the player busy" rule for each.
 * <p>
 * Constants, names and busy rules of chsami/Microbot {@code util/antiban/enums/Category.java} at
 * {@code a2114401bbbc35e9762db9e8aa666d70ffcd24d8} (BSD 2-Clause). Scripts call
 * {@code Rs2Antiban.getCategory().isBusy()} to wait while the player is still doing something; each
 * rule only reads game state (animating, moving, in combat, idle ticks). Upstream's anti-ban timing
 * attached to these categories is not ported.
 */
public enum Category
{
	COMBAT_MID("Combat/Mid", () -> Rs2Combat.inCombat()),
	SKILLING_AGILITY("Skilling/Agility", () -> Rs2Player.isMoving()),
	PROCESSING("Processing", () -> Rs2Player.isAnimating() || Microbot.isGainingExp),
	COLLECTING("Collecting", () -> Rs2Player.isMoving() || Rs2Player.isInteracting()),
	SKILLING_CRAFTING("Skilling/Crafting", () -> !Rs2Antiban.isIdle()),
	SKILLING_MAGIC("Skilling/Magic", () -> Rs2Player.isAnimating() || Microbot.isGainingExp),
	COMBAT_LOW("Combat/Low", () -> Rs2Combat.inCombat()),
	SKILLING_HERBLORE("Skilling/Herblore", () -> !Rs2Antiban.isIdle() || Microbot.isGainingExp),
	SKILLING_FLETCHING("Skilling/Fletching", () -> !Rs2Antiban.isIdle()),
	SKILLING_FISHING("Skilling/Fishing", () -> Rs2Player.isInteracting()),
	PROCESSING_MAGIC("Processing/Magic", () -> Rs2Player.isAnimating() && Microbot.isGainingExp),
	SKILLING_COOKING("Skilling/Cooking", () -> AntibanPlugin.isCooking()),
	SKILLING_FIREMAKING("Skilling/Firemaking", () -> Rs2Player.getPoseAnimation() == AnimationID.FIREMAKING),
	SKILLING_THIEVING("Skilling/Thieving", () -> Rs2Player.isAnimating()),
	SKILLING("Skilling", () -> !Rs2Player.isAnimating() || !Rs2Inventory.isFull()),
	COLLECTING_NONE("Collecting/None", () -> Rs2Player.isMoving()),
	COMBAT_HIGH("Combat/High", () -> Rs2Combat.inCombat()),
	SKILLING_WOODCUTTING("Skilling/Woodcutting", () -> Rs2Antiban.isWoodcutting()),
	SKILLING_MINING("Skilling/Mining", () -> AntibanPlugin.isMining()),
	SKILLING_RUNECRAFT("Skilling/Runecraft", () -> Rs2Inventory.contains("pure essence", "rune essence", "Daeyalt essence", "Dark essence fragment")),
	SKILLING_SMITHING("Skilling/Smithing", () -> !Rs2Antiban.isIdle()),
	SKILLING_HUNTER("Skilling/Hunter", () -> !Rs2Antiban.isIdle()),
	SKILLING_FARMING("Skilling/Farming", () -> Rs2Player.isAnimating() || Rs2Inventory.isFull()),
	SKILLING_PRAYER("Skilling/Prayer", () -> Rs2Player.isAnimating() || Rs2Inventory.isFull()),
	SKILLING_CONSTRUCTION("Skilling/Construction", () -> Rs2Player.isAnimating() || Rs2Inventory.isFull());

	private final String name;
	private final BooleanSupplier busy;

	Category(String name, BooleanSupplier busy)
	{
		this.name = name;
		this.busy = busy;
	}

	public String getName()
	{
		return name;
	}

	/** As upstream: false when logged out, else {@link #isBusyInternal()}. */
	public boolean isBusy()
	{
		return Microbot.isLoggedIn() && isBusyInternal();
	}

	/** As upstream: this category's busy rule. */
	public boolean isBusyInternal()
	{
		return busy.getAsBoolean();
	}
}
