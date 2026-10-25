package net.runelite.client.plugins.microbot.util;

import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.poh.PohTransport;
import net.runelite.client.plugins.microbot.util.poh.data.MountedGlory;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Batch AS: the last util leftovers. */
public class UtilActionBatchASTest
{
	@Test
	public void setupDepositsKeepListedIdsAndNames()
	{
		Rs2ItemModel rune = mock(Rs2ItemModel.class);
		when(rune.getId()).thenReturn(554);
		when(rune.getName()).thenReturn("Fire rune");
		assertTrue(Rs2Bank.isInventoryItemRetainedForSetupDeposit(rune, Set.of(554), null));
		assertTrue(Rs2Bank.isInventoryItemRetainedForSetupDeposit(rune, Set.of(), Map.of("rune", true)));
		assertFalse(Rs2Bank.isInventoryItemRetainedForSetupDeposit(rune, Set.of(), Map.of("rune", false)));
		assertTrue(Rs2Bank.isInventoryItemRetainedForSetupDeposit(rune, Set.of(), Map.of("FIRE RUNE", false)));
		assertFalse(Rs2Bank.isInventoryItemRetainedForSetupDeposit(null, Set.of(554), null));
	}

	@Test
	public void itemModelsFromCacheDescribeThemselves()
	{
		Rs2ItemModel coins = Rs2ItemModel.createFromCache(995, 100, 3);
		assertEquals(995, coins.getId());
		assertEquals(100, coins.getQuantity());
		assertEquals(3, coins.getSlot());
		assertTrue(coins.toString().contains("id: 995"));
		assertTrue(coins.toString().contains("compositionLoaded: false"));
	}

	@Test
	public void houseTeleportsAreTransports()
	{
		MountedGlory glory = MountedGlory.values()[0];
		PohTransport transport = new PohTransport(new WorldPoint(2954, 3224, 0), glory);
		assertEquals(TransportType.POH, transport.getType());
		assertEquals(glory.getDestination(), transport.getDestination());
		assertSame(glory, transport.getTeleport());
	}

	@Test
	public void edgeDecisionsAreRecorded()
	{
		assertFalse(Rs2Tile.isEdgePassable(null, null));
		assertEquals("bad-args", Rs2Tile.lastEdgeDecision());
		assertFalse(Rs2Tile.isEdgePassable(new WorldPoint(3200, 3200, 0), new WorldPoint(3203, 3200, 0)));
		assertEquals("not-adjacent", Rs2Tile.lastEdgeDecision());
	}
}
