package net.runelite.client.plugins.microbot.api;

import net.runelite.api.Actor;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Cached NPC models are {@link Actor}s, as upstream: snapshot getters keep capture-tick values,
 * every other {@code Actor} method reads the live NPC on the client thread (the fixture's mocks
 * throw if read on any other thread). Mocked RuneLite interfaces; not live-game evidence.
 */
public class ActorModelCompatTest
{
	private ReadApiFixture f;

	@Before
	public void setUp() throws Exception
	{
		f = new ReadApiFixture();
	}

	@After
	public void tearDown() throws Exception
	{
		f.close();
	}

	private WorldPoint at(int dx, int dy)
	{
		return new WorldPoint(ReadApiFixture.BASE_X + 5 + dx, ReadApiFixture.BASE_Y + 5 + dy, 0);
	}

	@Test
	public void npcModelIsAnActorWithLiveClientThreadReads()
	{
		NPC npc = f.npc(1, "Jad", at(2, 0));
		WorldArea area = new WorldArea(at(2, 0), 5, 5);
		doAnswer(i -> f.onClientThread(area)).when(npc).getWorldArea();
		doAnswer(i -> f.onClientThread(true)).when(npc).hasSpotAnim(1376);

		Rs2NpcModel model = f.services.getRs2NpcCache().query().first();
		Actor actor = model;
		assertSame(area, actor.getWorldArea());
		assertTrue(actor.hasSpotAnim(1376));
		assertFalse(actor.hasSpotAnim(1));
		assertSame(npc, model.getActor());
	}

	@Test
	public void snapshotGettersKeepCaptureValuesWhileOtherGettersAreLive()
	{
		NPC npc = f.npc(1, "Jad", at(2, 0));
		Rs2NpcModel model = f.services.getRs2NpcCache().query().first();

		doAnswer(i -> f.onClientThread("Renamed")).when(npc).getName();
		doAnswer(i -> f.onClientThread(at(9, 0))).when(npc).getWorldLocation();
		doAnswer(i -> f.onClientThread(7)).when(npc).getOrientation();

		assertEquals("Jad", model.getName());
		assertEquals(at(2, 0), model.getWorldLocation());
		assertEquals(7, model.getOrientation());
	}

	@Test
	public void headIconIsTheFirstSetOverheadSprite()
	{
		NPC npc = f.npc(1, "Hunllef", at(1, 0));
		Rs2NpcModel model = f.services.getRs2NpcCache().query().first();

		doAnswer(i -> f.onClientThread(null)).when(npc).getOverheadSpriteIds();
		assertNull(model.getHeadIcon());

		doAnswer(i -> f.onClientThread(new short[]{-1, (short) HeadIcon.RANGED.ordinal()})).when(npc).getOverheadSpriteIds();
		assertEquals(HeadIcon.RANGED, model.getHeadIcon());

		doAnswer(i -> f.onClientThread(new short[]{-1, -1})).when(npc).getOverheadSpriteIds();
		assertNull(model.getHeadIcon());

		doAnswer(i -> f.onClientThread(new short[]{(short) 999})).when(npc).getOverheadSpriteIds();
		assertNull("unknown sprite id is not a crash", model.getHeadIcon());
	}
}
