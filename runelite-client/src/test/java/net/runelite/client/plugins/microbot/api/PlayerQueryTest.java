package net.runelite.client.plugins.microbot.api;

import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.GameState;
import net.runelite.api.HeadIcon;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code Rs2PlayerCache} over {@link ReadApiFixture}: every live read must happen on the fake client
 * thread, the snapshot is reused within a tick (upstream {@code Rs2PlayerCacheTest}) and logout or a
 * stalled client thread never serves stale players.
 */
public class PlayerQueryTest
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

	private WorldPoint at(int dx, int dy, int plane)
	{
		return new WorldPoint(ReadApiFixture.BASE_X + 5 + dx, ReadApiFixture.BASE_Y + 5 + dy, plane);
	}

	private List<String> names(List<Rs2PlayerModel> players)
	{
		return players.stream().map(Rs2PlayerModel::getName).sorted().collect(Collectors.toList());
	}

	@Test
	public void servicesExposeOneCache()
	{
		assertSame(f.services.getRs2PlayerCache(), MicrobotReadServices.require().getRs2PlayerCache());
	}

	@Test
	public void queriesFilterByNameAndDistanceOnThePlayersPlane()
	{
		f.player(1, "Zezima", at(2, 1, 0));
		f.player(2, "Woox", at(-12, 0, 0));
		f.player(3, "Faraway", at(20, 0, 0));
		f.player(4, "Upstairs", at(1, 0, 1));

		assertEquals(List.of("Woox", "Zezima"), names(f.services.getRs2PlayerCache().query().within(15).toList()));
		assertEquals("Zezima", f.services.getRs2PlayerCache().query().nearest().getName());
		assertEquals(1, f.services.getRs2PlayerCache().query().withName("zezima").count());
		assertEquals(4, f.services.getRs2PlayerCache().getStream().count());
	}

	@Test
	public void modelCapturesRelationshipAndOverheadStateOnTheClientThread()
	{
		Player player = f.player(7, "Friend", at(1, 1, 0));
		when(player.getCombatLevel()).thenAnswer(i -> f.onClientThread(99));
		when(player.getAnimation()).thenAnswer(i -> f.onClientThread(879));
		when(player.isFriend()).thenAnswer(i -> f.onClientThread(true));
		when(player.isClanMember()).thenAnswer(i -> f.onClientThread(true));
		when(player.isFriendsChatMember()).thenAnswer(i -> f.onClientThread(false));
		doAnswer(i -> f.onClientThread(0)).when(player).getSkullIcon();
		when(player.getOverheadIcon()).thenAnswer(i -> f.onClientThread(HeadIcon.MELEE));

		Rs2PlayerModel model = f.services.getRs2PlayerCache().query().withName("Friend").first();

		assertEquals(7, model.getId());
		assertEquals(99, model.getCombatLevel());
		assertEquals(879, model.getAnimation());
		assertTrue(model.isFriend());
		assertTrue(model.isClanMember());
		assertFalse(model.isFriendsChatMember());
		assertEquals(0, model.getSkullIcon());
		assertEquals(HeadIcon.MELEE, model.getOverheadIcon());
		assertSame(player, model.getPlayer());
	}

	@Test
	public void sameTickReusesTheSnapshotAndTheNextTickRescans()
	{
		Player player = f.player(1, "Zezima", at(1, 0, 0));
		assertEquals(1, f.services.getRs2PlayerCache().getStream().count());
		assertEquals(1, f.services.getRs2PlayerCache().getStream().count());
		verify(player, times(1)).getName();

		f.tick++;
		assertEquals(1, f.services.getRs2PlayerCache().getStream().count());
		verify(player, times(2)).getName();
	}

	@Test
	public void logoutEmptiesTheSnapshot()
	{
		f.player(1, "Zezima", at(1, 0, 0));
		assertEquals(1, f.services.getRs2PlayerCache().getStream().count());

		f.gameState(GameState.LOGIN_SCREEN);

		assertEquals(0, f.services.getRs2PlayerCache().getStream().count());
	}

	@Test
	public void stalledClientThreadYieldsAnEmptyStream()
	{
		f.player(1, "Zezima", at(1, 0, 0));
		f.clientThreadStalled = true;

		assertEquals(0, f.services.getRs2PlayerCache().getStream().count());
		assertNull(f.services.getRs2PlayerCache().query().nearest());
	}
}
