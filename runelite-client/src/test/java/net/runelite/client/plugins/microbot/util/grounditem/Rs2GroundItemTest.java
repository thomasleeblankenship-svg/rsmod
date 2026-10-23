package net.runelite.client.plugins.microbot.util.grounditem;

import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class Rs2GroundItemTest
{
	@Test
	public void lootingHonorsOwnershipQuantityAndNamedFilter()
	{
		Rs2TileItemModel item = mock(Rs2TileItemModel.class);
		when(item.getName()).thenReturn("Bird nest");
		when(item.getQuantity()).thenReturn(1);
		when(item.isLootAble()).thenReturn(true);
		LootingParameters anyLootable = new LootingParameters(15, 1, 1, 1, false, false, "NEST");
		assertTrue(Rs2GroundItem.accepts(item, anyLootable));
		LootingParameters ownOnly = new LootingParameters(15, 1, 1, 1, false, true, "nest");
		assertFalse(Rs2GroundItem.accepts(item, ownOnly));
		when(item.isOwned()).thenReturn(true);
		assertTrue(Rs2GroundItem.accepts(item, ownOnly));
		when(item.isLootAble()).thenReturn(false);
		assertFalse(Rs2GroundItem.accepts(item, anyLootable));
		when(item.isLootAble()).thenReturn(true);
		when(item.getQuantity()).thenReturn(0);
		assertFalse(Rs2GroundItem.accepts(item, anyLootable));
	}

	@Test
	public void parametersDefensivelyCopyNames()
	{
		String[] names = {"seed"};
		LootingParameters parameters = new LootingParameters(15, 1, 1, 1, false, false, names);
		names[0] = "axe";
		String[] read = parameters.getNames();
		assertEquals("seed", read[0]);
		read[0] = "axe";
		assertEquals("seed", parameters.getNames()[0]);
	}
}
