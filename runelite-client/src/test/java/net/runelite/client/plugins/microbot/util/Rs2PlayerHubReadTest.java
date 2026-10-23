package net.runelite.client.plugins.microbot.util;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.api.WorldView;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Hub item (1) {@code Rs2Player} reads against real read services, event bus and player cache. */
public class Rs2PlayerHubReadTest
{
	static
	{
		// JavaBridge captures this flag on first load; set it before any test reaches the bridge.
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final EventBus events = new EventBus();
	private Client client;
	private Player local;
	private Player other;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarps()).thenReturn(new int[4000]);
		WorldView topLevel = mock(WorldView.class);
		when(topLevel.getId()).thenReturn(-1);
		when(client.getTopLevelWorldView()).thenReturn(topLevel);
		when(client.getWorldView(anyInt())).thenAnswer(i -> (int) i.getArgument(0) == -1 ? topLevel : null);
		local = player("Me");
		other = player("Other");
		when(client.getLocalPlayer()).thenReturn(local);
		@SuppressWarnings("unchecked")
		IndexedObjectSet<Player> players = mock(IndexedObjectSet.class);
		when(players.iterator()).thenAnswer(i -> List.of(local, other).iterator());
		doAnswer(i -> players).when(topLevel).players();

		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			onClientThread.set(true);
			try { ((Runnable) call.getArgument(0)).run(); }
			finally { onClientThread.set(false); }
			return null;
		}).when(thread).invoke(any(Runnable.class));
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
		post(gameState(GameState.LOGGED_IN));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	private Player player(String name)
	{
		Player player = mock(Player.class);
		when(player.getName()).thenReturn(name);
		return player;
	}

	private void post(Object event)
	{
		onClientThread.set(true);
		try { events.post(event); }
		finally { onClientThread.set(false); }
	}

	private static GameStateChanged gameState(GameState state)
	{
		GameStateChanged event = new GameStateChanged();
		event.setGameState(state);
		return event;
	}

	private static HitsplatApplied hitsplat(net.runelite.api.Actor actor, boolean mine, boolean others)
	{
		Hitsplat splat = mock(Hitsplat.class);
		when(splat.isMine()).thenReturn(mine);
		when(splat.isOthers()).thenReturn(others);
		HitsplatApplied event = new HitsplatApplied();
		event.setActor(actor);
		event.setHitsplat(splat);
		return event;
	}

	@Test
	public void combatFollowsUpstreamHitsplatCasesAndClearsOnLogout()
	{
		assertFalse(Rs2Player.isInCombat());
		post(hitsplat(other, false, true));
		assertFalse("another player's hitsplat on someone else is not our combat", Rs2Player.isInCombat());

		post(hitsplat(mock(NPC.class), true, false));
		assertTrue("our hitsplat on an NPC", Rs2Player.isInCombat());

		post(gameState(GameState.LOGIN_SCREEN));
		assertFalse(Rs2Player.isInCombat());

		post(gameState(GameState.LOGGED_IN));
		post(hitsplat(local, false, false));
		assertTrue("a hit on the local player", Rs2Player.isInCombat());
	}

	@Test
	public void staminaFollowsTheVarbitAndClearsOnLogout()
	{
		VarbitChanged stamina = new VarbitChanged();
		stamina.setVarbitId(Varbits.STAMINA_EFFECT);
		stamina.setValue(20);
		post(stamina);
		assertTrue(Rs2Player.hasStaminaBuffActive());

		stamina.setValue(0);
		post(stamina);
		assertFalse(Rs2Player.hasStaminaBuffActive());

		stamina.setValue(5);
		post(stamina);
		post(gameState(GameState.HOPPING));
		assertFalse(Rs2Player.hasStaminaBuffActive());
	}

	@Test
	public void energyLevelsWorldAndRunReadTheClient()
	{
		when(client.getEnergy()).thenReturn(7350);
		when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(45);
		when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(60);
		when(client.getWorld()).thenReturn(308);
		when(client.getVarpValue(173)).thenReturn(1);

		assertEquals(73, Rs2Player.getRunEnergy());
		assertEquals(45, Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS));
		assertEquals(75.0, Rs2Player.getHealthPercentage(), 0.0);
		assertEquals(308, Rs2Player.getWorld());
		assertTrue(Rs2Player.isRunEnabled());
	}

	@Test
	public void healthPercentageIsZeroWithoutARealLevel()
	{
		assertEquals(0.0, Rs2Player.getHealthPercentage(), 0.0);
	}

	@Test
	public void playersExcludeTheLocalPlayerUnlessAsked()
	{
		assertEquals(List.of("Other"), Rs2Player.getPlayers(p -> true).map(Rs2PlayerModel::getName).collect(Collectors.toList()));
		assertEquals(2, Rs2Player.getPlayers(p -> true, true).count());
		assertEquals(0, Rs2Player.getPlayers(p -> "Nobody".equals(p.getName())).count());

		Rs2PlayerModel me = Rs2Player.getLocalPlayer();
		assertSame(local, me.getPlayer());
		assertEquals("Me", me.getName());
	}

	@Test
	public void interactingReturnsTheLiveActor()
	{
		NPC npc = mock(NPC.class);
		when(local.getInteracting()).thenReturn(npc);
		assertSame(npc, Rs2Player.getInteracting());

		when(client.getLocalPlayer()).thenReturn(null);
		assertNull(Rs2Player.getInteracting());
		assertNull(Rs2Player.getLocalPlayer());
	}
}
