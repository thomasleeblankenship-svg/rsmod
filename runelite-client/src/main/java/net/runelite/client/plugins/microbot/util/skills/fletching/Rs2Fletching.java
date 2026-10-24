package net.runelite.client.plugins.microbot.util.skills.fletching;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * Cancellation-aware, script-thread fletching. Returns success only after material consumption.
 * Also upstream's helpers for starting, combining, arrows, bolts and darts (util action batch U).
 */
public final class Rs2Fletching
{
	private static final AtomicLong CANCELLATION = new AtomicLong();

	private Rs2Fletching() { }

	public static boolean hasKnife()
	{
		return Rs2Inventory.hasItem("knife");
	}

	public static boolean fletchItems(int materialId, String output, String quantity)
	{
		if (!hasKnife() || !MobileActions.active() || !Rs2Inventory.hasItem(materialId)) return false;
		Rs2ItemModel knife = Rs2Inventory.get("knife");
		Rs2ItemModel material = Rs2Inventory.get(materialId);
		long generation = CANCELLATION.get();
		int before = amount(materialId);
		if (knife == null || material == null || !MobileActions.itemOnItem(knife.getSlot(), material.getSlot())) return false;
		if (!Rs2Inventory.waitFor(Rs2Widget::isProductionWidgetOpen, 5_000)) return false;
		if (!select(output, quantity, generation)) return false;
		return Rs2Inventory.waitFor(() -> CANCELLATION.get() != generation || amount(materialId) < before, 8_000)
			&& CANCELLATION.get() == generation && amount(materialId) < before;
	}

	private static boolean select(String output, String quantity, long generation)
	{
		if (!"All".equalsIgnoreCase(quantity)) return false;
		if (!Rs2Inventory.waitFor(() -> CANCELLATION.get() != generation
			|| Rs2Widget.enableQuantityOption(quantity), 2_000) || CANCELLATION.get() != generation) return false;
		return Rs2Inventory.waitFor(() -> CANCELLATION.get() != generation
			|| Rs2Widget.handleProcessingInterface(output), 2_000) && CANCELLATION.get() == generation;
	}

	public static boolean dropFletchedItems(String outputName)
	{
		if (outputName == null || outputName.trim().isEmpty()) return false;
		int count = Rs2Inventory.count(outputName);
		return count == 0 || Rs2Inventory.dropAmount(outputName, count, InteractOrder.STANDARD) == count;
	}

	public static boolean stringBows(String bowName)
	{
		if (bowName == null || bowName.trim().isEmpty() || !MobileActions.active()) return false;
		String needle = bowName.toLowerCase(Locale.ROOT);
		Rs2ItemModel bow = Rs2Inventory.items(i -> i.getName().toLowerCase(Locale.ROOT).contains(needle)
			&& i.getName().toLowerCase(Locale.ROOT).contains("(u)")).findFirst().orElse(null);
		Rs2ItemModel string = Rs2Inventory.get("bow string");
		if (string == null) string = Rs2Inventory.get("bowstring");
		if (bow == null || string == null) return false;
		long generation = CANCELLATION.get();
		int before = amount(bow.getId());
		if (!MobileActions.itemOnItem(string.getSlot(), bow.getSlot())) return false;
		if (!Rs2Inventory.waitFor(Rs2Widget::isProductionWidgetOpen, 5_000)) return false;
		if (!select(bowName, "All", generation)) return false;
		if (!Rs2Inventory.waitFor(() -> CANCELLATION.get() != generation || amount(bow.getId()) < before, 8_000)
			|| CANCELLATION.get() != generation || amount(bow.getId()) >= before) return false;
		return Rs2Inventory.waitFor(() -> CANCELLATION.get() != generation || amount(bow.getId()) == 0
			|| (!Rs2Inventory.hasItem("bow string") && !Rs2Inventory.hasItem("bowstring")), 120_000)
			&& CANCELLATION.get() == generation;
	}

	private static int amount(int itemId)
	{
		return Rs2Inventory.items(i -> i.getId() == itemId).mapToInt(Rs2ItemModel::getQuantity).sum();
	}

	/** Invalidates an in-progress production selection; there is no independent background input task. */
	public static void stopFletchingWhileMoving()
	{
		CANCELLATION.incrementAndGet();
	}

	// ---- Util action batch U (Claude 1): upstream's fletching helpers ----

	/** As upstream: knife on the material named {@code materialName}, opening the make interface. */
	public static boolean startFletching(String materialName)
	{
		return hasKnife() && Rs2Inventory.hasItem(materialName)
			&& Rs2Inventory.combine(Rs2Inventory.hasItem(net.runelite.api.gameval.ItemID.KNIFE) ? "knife" : "fletching knife", materialName);
	}

	/** As upstream: knife on the material with this ID. */
	public static boolean startFletching(int materialId)
	{
		if (!hasKnife() || !Rs2Inventory.hasItem(materialId)) return false;
		int knife = Rs2Inventory.hasItem(net.runelite.api.gameval.ItemID.KNIFE)
			? net.runelite.api.gameval.ItemID.KNIFE : net.runelite.api.gameval.ItemID.FLETCHING_KNIFE;
		return Rs2Inventory.combine(knife, materialId);
	}

	/** As upstream: uses one item on the other; each is a name ({@code String}) or an ID ({@code Integer}). */
	public static boolean combineItems(Object itemA, Object itemB)
	{
		Rs2ItemModel a = model(itemA);
		Rs2ItemModel b = model(itemB);
		return a != null && b != null && Rs2Inventory.combine(a, b);
	}

	/** As upstream: uses the item named {@code itemNameA} on {@code itemNameB}. */
	public static boolean combineItems(String itemNameA, String itemNameB)
	{
		return Rs2Inventory.combine(itemNameA, itemNameB);
	}

	/** As upstream: uses the item with ID {@code itemIdA} on {@code itemIdB}. */
	public static boolean combineItems(int itemIdA, int itemIdB)
	{
		return Rs2Inventory.combine(itemIdA, itemIdB);
	}

	private static Rs2ItemModel model(Object item)
	{
		if (item instanceof String) return Rs2Inventory.get((String) item);
		if (item instanceof Integer) return Rs2Inventory.get((int) (Integer) item);
		return null;
	}

	/** As upstream: waits up to 5 s for the make interface. */
	public static boolean waitForFletchingInterface()
	{
		return Rs2Inventory.waitFor(Rs2Widget::isProductionWidgetOpen, 5_000);
	}

	/** As upstream: the make interface is open. */
	public static boolean isFletchingInterfaceOpen()
	{
		return Rs2Widget.isProductionWidgetOpen();
	}

	/** As upstream: waits up to a minute for {@code materialName} to be used up. */
	public static boolean waitForFletchingCompletion(String materialName)
	{
		return Rs2Inventory.waitFor(() -> !Rs2Inventory.hasItem(materialName), 60_000);
	}

	/** As upstream: waits for the fletching animation to start, then up to {@code timeoutSeconds} for it to stop. */
	public static boolean waitForFletchingCompletion(int timeoutSeconds)
	{
		Rs2Inventory.waitFor(net.runelite.client.plugins.microbot.util.player.Rs2Player::isAnimating, 2_000);
		return Rs2Inventory.waitFor(() -> !net.runelite.client.plugins.microbot.util.player.Rs2Player.isAnimating(), timeoutSeconds * 1_000);
	}

	/** As upstream: picks {@code quantity} and then {@code optionName} in the make interface. */
	private static boolean makeOption(String optionName, String quantity)
	{
		if (!waitForFletchingInterface()) return false;
		Rs2Widget.enableQuantityOption(quantity);
		return Rs2Inventory.waitFor(() -> Rs2Widget.handleProcessingInterface(optionName), 2_000);
	}

	/** As upstream: arrow shafts with feathers, through the make interface. */
	public static boolean makeHeadlessArrows(String quantity)
	{
		return combineItems("arrow shaft", "feather") && makeOption("Headless arrow", quantity)
			&& waitForFletchingCompletion("arrow shaft");
	}

	/** As upstream: arrow shafts with feathers, without waiting on the interface. */
	public static boolean makeHeadlessArrowsFast()
	{
		return Rs2Inventory.hasItem("arrow shaft") && Rs2Inventory.hasItem("feather") && Rs2Inventory.combine("arrow shaft", "feather");
	}

	/** As upstream: headless arrows with {@code arrowTipName}. */
	public static boolean makeArrows(String arrowTipName, String quantity)
	{
		return combineItems("headless arrow", arrowTipName) && makeOption("Arrow", quantity) && waitForFletchingCompletion(arrowTipName);
	}

	/** As upstream: unfinished bolts with feathers. */
	public static boolean makeBolts(String unfinishedBoltName, String quantity)
	{
		return combineItems(unfinishedBoltName, "feather") && makeOption("Bolt", quantity) && waitForFletchingCompletion(unfinishedBoltName);
	}

	/** As upstream: tips onto bolts. */
	public static boolean addBoltTips(String boltName, String boltTipName, String quantity)
	{
		return combineItems(boltName, boltTipName) && makeOption("Bolt", quantity) && waitForFletchingCompletion(boltTipName);
	}

	/** As upstream: unfinished bolts with feathers, without the interface. */
	public static boolean makeBoltsFast(String unfinishedBoltName)
	{
		return Rs2Inventory.hasItem(unfinishedBoltName) && Rs2Inventory.hasItem("feather") && Rs2Inventory.combine(unfinishedBoltName, "feather");
	}

	/** As upstream: tips onto bolts, without the interface. */
	public static boolean addBoltTipsFast(String boltName, String boltTipName)
	{
		return Rs2Inventory.hasItem(boltName) && Rs2Inventory.hasItem(boltTipName) && Rs2Inventory.combine(boltName, boltTipName);
	}

	/** As upstream: dart tips with feathers. */
	public static boolean makeDarts(String dartTipName, String quantity)
	{
		return combineItems(dartTipName, "feather") && makeOption("Dart", quantity) && waitForFletchingCompletion(dartTipName);
	}

	/** As upstream: dart tips with feathers, without the interface. */
	public static boolean makeDartsFast(String dartTipName)
	{
		return Rs2Inventory.hasItem(dartTipName) && Rs2Inventory.hasItem("feather") && Rs2Inventory.combine(dartTipName, "feather");
	}

	/** As upstream: the dart tips with this ID with feathers, without the interface. */
	public static boolean makeDartsFast(int dartTipId)
	{
		Rs2ItemModel feather = Rs2Inventory.get("feather");
		return Rs2Inventory.hasItem(dartTipId) && feather != null && Rs2Inventory.combine(dartTipId, feather.getId());
	}
}
