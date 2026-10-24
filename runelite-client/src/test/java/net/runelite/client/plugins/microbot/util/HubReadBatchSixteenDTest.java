package net.runelite.client.plugins.microbot.util;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Read batch 16d (player, player model, prayer and spellbook reads) against upstream 2.6.38 semantics. */
public class HubReadBatchSixteenDTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final EventBus events = new EventBus();
	private Client client;
	private Player player;
	private WorldView view;
	private Scene scene;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		// LOGIN_SCREEN keeps the varbit cache from holding values between assertions.
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			onClientThread.set(true);
			try
			{
				((Runnable) call.getArgument(0)).run();
			}
			finally
			{
				onClientThread.set(false);
			}
			return null;
		}).when(thread).invoke(any(Runnable.class));
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class), List.of(new MicrobotReadServices(client, thread, events)));
		player = mock(Player.class);
		view = mock(WorldView.class);
		scene = mock(Scene.class);
		when(view.getScene()).thenReturn(scene);
		when(client.getTopLevelWorldView()).thenReturn(view);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getWorldView()).thenReturn(view);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	@Test
	public void playerStateReads()
	{
		assertSame(view, Rs2Player.getWorldView());
		when(player.getGraphic()).thenReturn(111);
		assertEquals(111, Rs2Player.getGraphicId());
		assertFalse(Rs2Player.isInMulti());
		when(client.getVarbitValue(VarbitID.MULTIWAY_INDICATOR)).thenReturn(1);
		assertTrue(Rs2Player.isInMulti());

		when(client.getVarbitValue(VarbitID.IRONMAN)).thenReturn(2);
		assertTrue(Rs2Player.isIronman());
		assertFalse(Rs2Player.isGroupIronman());
		when(client.getVarbitValue(VarbitID.IRONMAN)).thenReturn(5);
		assertFalse(Rs2Player.isIronman());
		assertTrue(Rs2Player.isGroupIronman());

		int[] varps = new int[4000];
		when(client.getVarps()).thenReturn(varps);
		when(client.getVarpValue(281)).thenReturn(1000);
		assertTrue(Rs2Player.hasCompletedTutorialIsland());
		when(client.getVarpValue(281)).thenReturn(999);
		assertFalse(Rs2Player.hasCompletedTutorialIsland());

		assertFalse("not in an instance", Rs2Player.isInPoh());
		when(scene.isInstance()).thenReturn(true);
		assertFalse("instance without a house", Rs2Player.isInPoh());
		when(client.getVarbitValue(VarbitID.POH_HOUSE_LOCATION)).thenReturn(3);
		assertTrue(Rs2Player.isInPoh());

		assertFalse("surface", Rs2Player.isInCave());
		when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 9600, 0));
		assertTrue(Rs2Player.isInCave());
		when(view.isInstance()).thenReturn(true);
		assertFalse("instanced", Rs2Player.isInCave());
	}

	@Test
	public void areaIntersectionAndMovement()
	{
		assertTrue(Rs2Player.isPlayerAreaIntersecting(new WorldPoint(3202, 3200, 0), 3, 3, 3, 3));
		assertFalse(Rs2Player.isPlayerAreaIntersecting(new WorldPoint(3204, 3200, 0), 3, 3, 3, 3));
		assertFalse("other plane", Rs2Player.isPlayerAreaIntersecting(new WorldPoint(3200, 3200, 1), 3, 3, 3, 3));
		assertFalse("negative span", Rs2Player.isPlayerAreaIntersecting(new WorldPoint(3200, 3200, 0), -1, 3, 3, 3));
		assertFalse(Rs2Player.isPlayerAreaIntersecting(null, 1, 1, 1, 1));

		Player other = mock(Player.class);
		when(other.getPoseAnimation()).thenReturn(819);
		when(other.getIdlePoseAnimation()).thenReturn(808);
		when(other.getTeam()).thenReturn(7);
		PlayerComposition composition = mock(PlayerComposition.class);
		when(other.getPlayerComposition()).thenReturn(composition);
		onClientThread.set(true);
		Rs2PlayerModel model = new Rs2PlayerModel(client, other);
		onClientThread.set(false);
		assertTrue(Rs2Player.isMoving(model));
		when(other.getPoseAnimation()).thenReturn(808);
		assertFalse(Rs2Player.isMoving(model));
		assertFalse(Rs2Player.isMoving((Rs2PlayerModel) null));
		assertEquals(7, model.getTeam());
		assertSame(composition, model.getPlayerComposition());
	}

	@Test
	public void prayerReads()
	{
		assertFalse(Rs2Prayer.hasAnyQuickPrayers());
		int index = Rs2PrayerEnum.PIETY.getQuickPrayerIndex();
		when(client.getVarbitValue(VarbitID.QUICKPRAYER_SELECTED)).thenReturn(1 << index);
		assertTrue(Rs2Prayer.hasAnyQuickPrayers());
		assertTrue(Rs2Prayer.isQuickPrayerSet(Rs2PrayerEnum.PIETY));
		assertFalse(Rs2Prayer.isQuickPrayerSet(Rs2PrayerEnum.RIGOUR));

		assertFalse(Rs2Prayer.isMeleePrayerActive());
		when(client.getVarbitValue(Rs2PrayerEnum.CHIVALRY.getVarbit())).thenReturn(1);
		assertTrue(Rs2Prayer.isMeleePrayerActive());
		assertFalse(Rs2Prayer.isRangePrayerActive());
		when(client.getVarbitValue(Rs2PrayerEnum.EAGLE_EYE.getVarbit())).thenReturn(1);
		assertTrue(Rs2Prayer.isRangePrayerActive());
		assertFalse(Rs2Prayer.isMagePrayerActive());
		when(client.getVarbitValue(Rs2PrayerEnum.AUGURY.getVarbit())).thenReturn(1);
		assertTrue(Rs2Prayer.isMagePrayerActive());

		when(client.getRealSkillLevel(Skill.PRAYER)).thenReturn(65);
		when(client.getRealSkillLevel(Skill.DEFENCE)).thenReturn(70);
		assertFalse("Knight Waves not done", Rs2Prayer.isChivalryUnlocked());
		when(client.getVarbitValue(VarbitID.KR_KNIGHTWAVES_STATE)).thenReturn(8);
		assertTrue(Rs2Prayer.isChivalryUnlocked());
		assertFalse("Prayer 65 is below Piety", Rs2Prayer.isPietyUnlocked());
		when(client.getRealSkillLevel(Skill.PRAYER)).thenReturn(70);
		assertTrue(Rs2Prayer.isPietyUnlocked());
	}

	@Test
	public void emptySceneHasNoSwitchSourcesNorGroundItems()
	{
		assertFalse(Rs2Spellbook.isAltarNearby());
		assertFalse(Rs2Spellbook.isTyssNearby());
		assertFalse("not in an instance", Rs2Spellbook.canSwitchInPOH());
		assertFalse(Rs2Player.isStandingOnGroundItem());
		when(client.getLocalPlayer()).thenReturn(null);
		assertNull(Rs2Player.getWorldView());
		assertEquals(-1, Rs2Player.getGraphicId());
	}
}
