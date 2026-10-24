package net.runelite.client.plugins.microbot.api;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.shortestpath.PohDevice;
import net.runelite.client.plugins.microbot.util.poh.PohDetector;
import net.runelite.client.plugins.microbot.util.poh.PohLayout;
import net.runelite.client.plugins.microbot.util.poh.data.HouseLocation;
import net.runelite.client.plugins.microbot.util.poh.data.JewelleryBoxType;
import net.runelite.client.plugins.microbot.util.poh.data.NexusPortal;
import net.runelite.client.plugins.microbot.util.poh.data.PohPortal;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** C2-1: the house is detected from one scan inside it plus its varbits, and stored as text. */
public class PohDetectionTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private ReadApiFixture f;

	@Before
	public void setUp() throws Exception
	{
		f = new ReadApiFixture();
		when(f.topLevel.getSizeX()).thenReturn(32);
		when(f.topLevel.getSizeY()).thenReturn(32);
		when(f.scene.isInstance()).thenReturn(true);
		when(f.localPlayer.getLocalLocation()).thenReturn(local(5, 5));
	}

	@After
	public void tearDown() throws Exception
	{
		f.close();
	}

	private static LocalPoint local(int x, int y)
	{
		return new LocalPoint((x << 7) + 64, (y << 7) + 64, ReadApiFixture.TOP_LEVEL_ID);
	}

	private void object(int id, String name, int x, int y)
	{
		ObjectComposition composition = mock(ObjectComposition.class);
		when(composition.getName()).thenAnswer(i -> f.onClientThread(name));
		when(f.client.getObjectDefinition(id)).thenReturn(composition);
		Tile tile = f.tiles[0][x][y];
		if (tile == null)
		{
			tile = mock(Tile.class);
			when(tile.getSceneLocation()).thenReturn(new Point(x, y));
			when(tile.getLocalLocation()).thenReturn(local(x, y));
			when(tile.getWorldLocation()).thenReturn(new WorldPoint(ReadApiFixture.BASE_X + x, ReadApiFixture.BASE_Y + y, 0));
			when(tile.getGameObjects()).thenReturn(new GameObject[5]);
			when(tile.getGroundItems()).thenReturn(new java.util.ArrayList<TileItem>());
			f.tiles[0][x][y] = tile;
		}
		GameObject object = mock(GameObject.class);
		when(object.getId()).thenReturn(id);
		when(object.getWorldView()).thenReturn(f.topLevel);
		when(object.getSceneMinLocation()).thenReturn(new Point(x, y));
		when(object.getLocalLocation()).thenReturn(local(x, y));
		when(object.getWorldLocation()).thenReturn(new WorldPoint(ReadApiFixture.BASE_X + x, ReadApiFixture.BASE_Y + y, 0));
		tile.getGameObjects()[0] = object;
	}

	@Test
	public void outsideTheHouseNothingIsDetected()
	{
		when(f.scene.isInstance()).thenReturn(false);
		assertNull(PohDetector.detect());
	}

	@Test
	public void portalsDevicesAndVarbitsMakeTheLayout()
	{
		object(ObjectID.POH_EXIT_PORTAL, "Portal", 5, 6);
		object(ObjectID.POH_PORTAL_MAG_VARROCK, "Varrock Portal", 7, 5);
		object(NexusPortal.PORTAL_IDS[0], "Portal Nexus", 9, 5);
		object(ObjectID.POH_JEWELLERY_BOX_3, "Ornate jewellery box", 11, 5);
		when(f.client.getVarbitValue(anyInt())).thenAnswer(i -> {
			int varbit = i.getArgument(0);
			if (varbit == VarbitID.POH_HOUSE_LOCATION)
			{
				return HouseLocation.RIMMINGTON.getVarbitValue();
			}
			return varbit == NexusPortal.VARBITS[0] ? 3 : 0;
		});

		PohLayout layout = PohDetector.detect();
		assertNotNull(layout);
		assertEquals(HouseLocation.RIMMINGTON, layout.getLocation());
		assertEquals(Set.of(PohPortal.VARROCK), layout.getPortals().keySet());
		assertEquals(ObjectID.POH_PORTAL_MAG_VARROCK, layout.getPortals().get(PohPortal.VARROCK).getObjectId());
		assertEquals("Varrock Portal", layout.getPortals().get(PohPortal.VARROCK).getName());
		assertEquals(JewelleryBoxType.ORNATE, layout.getJewelleryBox());
		assertEquals("Portal Nexus", layout.getDevices().get(PohDevice.NEXUS).getName());
		assertEquals("the nexus slot's varbit value names its destination", Set.of(NexusPortal.FALADOR), layout.getNexus());
		assertFalse(layout.getDevices().containsKey(PohDevice.FAIRY_RING));
	}

	@Test
	public void theStoredFormRoundTrips()
	{
		Map<PohPortal, PohLayout.Device> portals = new EnumMap<>(PohPortal.class);
		portals.put(PohPortal.LUMBRIDGE, new PohLayout.Device(13623, "Lumbridge|Portal"));
		Map<PohDevice, PohLayout.Device> devices = new EnumMap<>(PohDevice.class);
		devices.put(PohDevice.MOUNTED_GLORY, new PohLayout.Device(ObjectID.POH_TROPHY_AMULETOFGLORY_4, "Amulet of Glory"));
		PohLayout layout = new PohLayout(HouseLocation.YANILLE, portals, Set.of(NexusPortal.FALADOR, NexusPortal.CAMELOT),
			JewelleryBoxType.FANCY, devices);

		PohLayout back = PohLayout.parse(layout.serialize());
		assertEquals(HouseLocation.YANILLE, back.getLocation());
		assertEquals("separators are dropped from names", "Lumbridge Portal", back.getPortals().get(PohPortal.LUMBRIDGE).getName());
		assertEquals(Set.of(NexusPortal.FALADOR, NexusPortal.CAMELOT), back.getNexus());
		assertEquals(JewelleryBoxType.FANCY, back.getJewelleryBox());
		assertEquals(layout.getDevices(), back.getDevices());
		assertSame(PohLayout.EMPTY, PohLayout.parse("v0|broken"));
		assertSame(PohLayout.EMPTY, PohLayout.parse("v1|loc=NOWHERE"));
		assertFalse(PohLayout.EMPTY.isKnown());
	}
}
