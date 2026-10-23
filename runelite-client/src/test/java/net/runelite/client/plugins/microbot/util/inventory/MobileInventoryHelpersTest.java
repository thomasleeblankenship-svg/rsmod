package net.runelite.client.plugins.microbot.util.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.Item;
import org.junit.Test;
import static org.junit.Assert.*;

/** Traversal invariants protect tool slots; unknown basket messages must not become empty baskets. */
public class MobileInventoryHelpersTest
{
	private List<Rs2ItemModel> slots()
	{
		List<Rs2ItemModel> result = new ArrayList<>();
		for (int slot = 0; slot < 28; slot++) result.add(new Rs2ItemModel(new Item(1511, 1), null, null, slot));
		return result;
	}

	private List<Integer> order(InteractOrder mode)
	{
		return Rs2Inventory.ordered(slots(), mode).stream().map(Rs2ItemModel::getSlot).collect(Collectors.toList());
	}

	@Test
	public void columnModeVisitsEverySlotBeforeNextColumn()
	{
		assertEquals(List.of(0, 4, 8, 12, 16, 20, 24, 1), order(InteractOrder.COLUMN).subList(0, 8));
	}

	@Test
	public void serpentineRowAndColumnReverseAlternateLines()
	{
		assertEquals(List.of(0, 1, 2, 3, 7, 6, 5, 4), order(InteractOrder.EFFICIENT_ROW).subList(0, 8));
		assertEquals(List.of(0, 4, 8, 12, 16, 20, 24, 25, 21, 17, 13, 9, 5, 1),
			order(InteractOrder.EFFICIENT_COLUMN).subList(0, 14));
	}

	@Test
	public void everyTraversalIsPermutationAndDoesNotMutateSource()
	{
		for (InteractOrder mode : InteractOrder.values())
		{
			List<Rs2ItemModel> original = slots();
			List<Rs2ItemModel> ordered = Rs2Inventory.ordered(original, mode);
			assertEquals(28, ordered.size());
			assertEquals(28, ordered.stream().map(Rs2ItemModel::getSlot).distinct().count());
			for (int i = 0; i < 28; i++) assertEquals(i, original.get(i).getSlot());
		}
	}

	@Test
	public void basketParsesTaggedMessageAndExplicitEmpty()
	{
		Rs2LogBasket.BasketContents contents = Rs2LogBasket.parse("The basket contains:<br>28 x Oak logs");
		assertNotNull(contents);
		assertEquals(28, contents.quantity);
		assertEquals("Oak logs", contents.logType);
		assertFalse(contents.isEmpty);
		assertTrue(Rs2LogBasket.parse("Your basket is empty.").isEmpty);
	}

	@Test
	public void malformedAndOversizedBasketMessagesRemainUnknown()
	{
		assertNull(Rs2LogBasket.parse(null));
		assertNull(Rs2LogBasket.parse("Click here to continue"));
		assertNull(Rs2LogBasket.parse("Nothing happens."));
		assertNull(Rs2LogBasket.parse("The basket contains 99 x Oak logs"));
		assertNull(Rs2LogBasket.parse("The basket contains 999999999999999 x Oak logs"));
	}
}
