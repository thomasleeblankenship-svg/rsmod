package net.runelite.client.plugins.microbot.util.skills.fletching;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/** Cancellation-aware, script-thread fletching. Returns success only after material consumption. */
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
}
