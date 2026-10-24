package net.runelite.client.plugins.microbot.util.walker;

import java.util.List;
import java.util.Map;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WalkerPathReads;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/** C1-3: upstream's {@code Rs2Walker} path reads answer exactly what {@link WalkerPathReads} does. */
public class WalkerPathReadsDelegationTest
{
	private static final WorldPoint START = new WorldPoint(3222, 3218, 0);
	private static final WorldPoint GOAL = new WorldPoint(3210, 3424, 0);

	@Test
	public void everyPathReadDelegatesToWalkerPathReads()
	{
		List<WorldPoint> path = List.of(START, GOAL);
		List<Transport> transports = List.of(mock(Transport.class));
		Map<Integer, Integer> missing = Map.of(995, 30);
		Tile tile = mock(Tile.class);
		try (MockedStatic<WalkerPathReads> reads = mockStatic(WalkerPathReads.class))
		{
			reads.when(() -> WalkerPathReads.getWalkPath(START, GOAL)).thenReturn(path);
			reads.when(() -> WalkerPathReads.getWalkPath(GOAL)).thenReturn(path);
			reads.when(() -> WalkerPathReads.getTotalTiles(START, GOAL)).thenReturn(210);
			reads.when(() -> WalkerPathReads.getTotalTiles(GOAL)).thenReturn(211);
			reads.when(() -> WalkerPathReads.getTotalTilesFromPath(path, GOAL)).thenReturn(2);
			reads.when(() -> WalkerPathReads.getClosestTileIndex(path)).thenReturn(1);
			reads.when(() -> WalkerPathReads.isWalkableInCollisionMap(GOAL)).thenReturn(true);
			reads.when(() -> WalkerPathReads.getTile(GOAL)).thenReturn(tile);
			reads.when(() -> WalkerPathReads.getTransportsForDestination(GOAL, true)).thenReturn(transports);
			reads.when(() -> WalkerPathReads.getTransportsForDestination(GOAL, false, TransportType.FAIRY_RING)).thenReturn(transports);
			reads.when(() -> WalkerPathReads.getMissingTransportItemIdsWithQuantities(transports)).thenReturn(missing);

			assertSame(path, Rs2Walker.getWalkPath(START, GOAL));
			assertSame(path, Rs2Walker.getWalkPath(GOAL));
			assertEquals(210, Rs2Walker.getTotalTiles(START, GOAL));
			assertEquals(211, Rs2Walker.getTotalTiles(GOAL));
			assertEquals(2, Rs2Walker.getTotalTilesFromPath(path, GOAL));
			assertEquals(1, Rs2Walker.getClosestTileIndex(path));
			assertTrue(Rs2Walker.isWalkableInCollisionMap(GOAL));
			assertSame(tile, Rs2Walker.getTile(GOAL));
			assertSame(transports, Rs2Walker.getTransportsForDestination(GOAL, true));
			assertSame(transports, Rs2Walker.getTransportsForDestination(GOAL, false, TransportType.FAIRY_RING));
			assertSame(missing, Rs2Walker.getMissingTransportItemIdsWithQuantities(transports));
		}
	}
}
