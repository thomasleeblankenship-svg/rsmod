package net.runelite.client.plugins.microbot.api;

import net.runelite.client.plugins.microbot.api.player.data.SalvagingAnimations;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** api batch 2: ground item reads and salvaging. */
public class ApiBatchTwoTest
{
	@Test
	public void definitionReadsFallBackWithoutAClient()
	{
		Rs2TileItemModel item = mock(Rs2TileItemModel.class, CALLS_REAL_METHODS);
		assertEquals(0, item.getTotalGeValue());
		assertEquals(0L, item.getTotalValue());
		assertFalse(item.isTradeable());
		assertFalse(item.isProfitableToHighAlch());
	}

	@Test
	public void salvagingIsRecognisedByAnimation()
	{
		Rs2PlayerModel player = mock(Rs2PlayerModel.class, CALLS_REAL_METHODS);
		doReturn(SalvagingAnimations.SALVAGING_ANIMATIONS[0]).when(player).getAnimation();
		assertTrue(player.isSalvaging());
		doReturn(-1).when(player).getAnimation();
		assertFalse(player.isSalvaging());
	}
}
